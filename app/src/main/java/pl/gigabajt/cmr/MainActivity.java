package pl.gigabajt.cmr;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.GeolocationPermissions;
import android.webkit.MimeTypeMap;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.DownloadListener;
import android.webkit.WebSettings;
import android.widget.Toast;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.content.Context;
import android.util.Base64;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;
import android.webkit.WebResourceResponse;

import java.io.OutputStream;
import java.net.URLConnection;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import org.json.JSONObject;

public class MainActivity extends Activity {
 private static final int PICK_FILE=110;
 private static final int GEO_PERMISSION=111;
 private WebView web;
 private ValueCallback<Uri[]> fileCallback;
 private GeolocationPermissions.Callback locationCallback;
 private String locationOrigin;
 private int printSequence=0;

 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  web=new WebView(this);setContentView(web);
  WebSettings settings=web.getSettings();
  settings.setJavaScriptEnabled(true);
  settings.setDomStorageEnabled(true);
  settings.setDatabaseEnabled(true);
  settings.setAllowFileAccess(false);
  settings.setMediaPlaybackRequiresUserGesture(false);
  final WebViewAssetLoader loader=new WebViewAssetLoader.Builder()
       .addPathHandler("/assets/",new WebViewAssetLoader.AssetsPathHandler(this)).build();
  web.setWebViewClient(new WebViewClientCompat(){
   @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
    return loader.shouldInterceptRequest(request.getUrl());
   }
   @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
    String address=request.getUrl().toString();
    if(address.startsWith("https://appassets.androidplatform.net/assets/")) return false;
    if(address.startsWith("https://") || address.startsWith("http://")) return false;
    return true;
   }
  });
  web.setWebChromeClient(new WebChromeClient(){
   @Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> callback, FileChooserParams params){
    if(fileCallback!=null) fileCallback.onReceiveValue(null);
    fileCallback=callback;
    try {startActivityForResult(params.createIntent(),PICK_FILE);return true;}
    catch(Exception e){fileCallback=null;Toast.makeText(MainActivity.this,"Nie można otworzyć wyboru pliku",Toast.LENGTH_LONG).show();return false;}
   }
   @Override public void onGeolocationPermissionsShowPrompt(String origin,GeolocationPermissions.Callback cb){
    if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED){cb.invoke(origin,true,false);return;}
    locationOrigin=origin;locationCallback=cb;
    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},GEO_PERMISSION);
   }
  });
  web.addJavascriptInterface(new NativeBridge(),"AndroidCMR");
  web.setDownloadListener((url,userAgent,disposition,mime,length)->{
   if(url.startsWith("http://")||url.startsWith("https://")){
    try {DownloadManager.Request r=new DownloadManager.Request(Uri.parse(url));r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
     r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,"Gigabajt-CMR-download");
     ((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(r);
    } catch(Exception e){Toast.makeText(this,"Nie można pobrać pliku",Toast.LENGTH_LONG).show();}
   }
  });
  web.loadUrl("https://appassets.androidplatform.net/assets/index.html");
 }
 public class NativeBridge {
  // Only two fixed HTTPS Overpass endpoints are allowed; query is never a URL.
  @JavascriptInterface public void searchOverpass(String query,String id){
   if(query==null || query.length()>16000 || id==null || !id.matches("[0-9]{1,12}"))return;
   new Thread(()->{
    String[] hosts={"https://overpass-api.de/api/interpreter","https://overpass.private.coffee/api/interpreter"};
    StringBuilder diagnostics=new StringBuilder();
    for(String host:hosts){
     HttpURLConnection con=null;
     String server=host.contains("private.coffee")?"Private.coffee":"Overpass DE";
     try{
      // GET matches the small browser-tested Overpass request format.
      URL url=new URL(host+"?data="+URLEncoder.encode(query,"UTF-8"));
      con=(HttpURLConnection)url.openConnection();
      con.setRequestMethod("GET");
      con.setConnectTimeout(15000);con.setReadTimeout(45000);
      con.setRequestProperty("Accept","application/json, */*");
      con.setRequestProperty("User-Agent","GigabajtCMR/13.0 (Android; contact: github.com/romansliboda/gigabajt-cmr-android)");
      int status=con.getResponseCode();
      if(status!=200){
       if(diagnostics.length()>0)diagnostics.append("; ");
       diagnostics.append(server).append(": HTTP ").append(status);
       continue;
      }
      ByteArrayOutputStream data=new ByteArrayOutputStream();
      try(InputStream input=con.getInputStream()){
       byte[] buf=new byte[8192];int n;
       while((n=input.read(buf))!=-1){data.write(buf,0,n);if(data.size()>5_000_000)throw new IllegalStateException("Zbyt duża odpowiedź mapy");}
      }
      String json=data.toString("UTF-8");
      if(!json.trim().startsWith("{"))throw new IllegalStateException("Nieprawidłowa odpowiedź serwera");
      nativeMapResult(id,true,json);return;
     }catch(Exception ex){
      if(diagnostics.length()>0)diagnostics.append("; ");
      diagnostics.append(server).append(": ").append(ex.getClass().getSimpleName());
     }finally{if(con!=null)con.disconnect();}
    }
    nativeMapResult(id,false,"Błędy serwerów: "+diagnostics.toString());
   },"CMR-OSM").start();
  }
  @JavascriptInterface public void saveFile(String name,String mime,String base64){
   // Never parse HTML input as a filesystem path; MediaStore writes into Downloads.
   try {
    String safeName=name.replaceAll("[^\\p{L}\\p{N}._-]","_");
    if(safeName.isEmpty()) safeName="Gigabajt-CMR";
    byte[] bytes=Base64.decode(base64,Base64.DEFAULT);
    ContentValues values=new ContentValues();
    values.put(MediaStore.Downloads.DISPLAY_NAME,safeName);
    values.put(MediaStore.Downloads.MIME_TYPE,mime==null||mime.isEmpty()?"application/octet-stream":mime);
    values.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/Gigabajt-CMR");
    Uri uri=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
    if(uri==null) throw new IllegalStateException("Could not create download");
    try(OutputStream output=getContentResolver().openOutputStream(uri)){if(output==null)throw new IllegalStateException("Could not open download");output.write(bytes);}
    runOnUiThread(()->Toast.makeText(MainActivity.this,"Zapisano w Pobrane/Gigabajt-CMR",Toast.LENGTH_LONG).show());
   }catch(Exception e){runOnUiThread(()->Toast.makeText(MainActivity.this,"Błąd zapisu: "+e.getMessage(),Toast.LENGTH_LONG).show());}
  }
  @JavascriptInterface public void printCMR(String html){
   runOnUiThread(()->{
    WebView printable=new WebView(MainActivity.this);
    printable.getSettings().setJavaScriptEnabled(false);
    printable.setWebViewClient(new WebViewClient(){
     @Override public void onPageFinished(WebView view,String url){
      PrintManager pm=(PrintManager)getSystemService(Context.PRINT_SERVICE);
      pm.print("Gigabajt CMR "+(++printSequence),view.createPrintDocumentAdapter("Gigabajt CMR"),new PrintAttributes.Builder().build());
    }});
    printable.loadDataWithBaseURL("https://appassets.androidplatform.net/assets/",html,"text/html","UTF-8",null);
   });
  }
 }
 private void nativeMapResult(String id,boolean ok,String payload){
  String js="window.gbOverpassNativeResult("+JSONObject.quote(id)+","+(ok?"true":"false")+","+JSONObject.quote(payload)+")";
  runOnUiThread(()->{if(web!=null)web.evaluateJavascript(js,null);});
 }
 @Override protected void onActivityResult(int request,int result,Intent data){
  super.onActivityResult(request,result,data);
  if(request==PICK_FILE&&fileCallback!=null){
   fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result,data));fileCallback=null;
  }
 }
 @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){
  super.onRequestPermissionsResult(request,permissions,grants);
  if(request==GEO_PERMISSION&&locationCallback!=null){
   boolean approved=false;for(int result:grants)if(result==PackageManager.PERMISSION_GRANTED)approved=true;
   locationCallback.invoke(locationOrigin,approved,false);locationCallback=null;locationOrigin=null;
  }
 }
 @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else super.onBackPressed();}
}
