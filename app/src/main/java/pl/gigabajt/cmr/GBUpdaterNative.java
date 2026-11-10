package pl.gigabajt.cmr;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import androidx.core.content.FileProvider;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipFile;

public final class GBUpdaterNative {
    private final Activity activity;
    private final WebView web;
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private static final String OWNER = "romansliboda";
    private static final String REPO = "gigabajt-cmr-android";

    public GBUpdaterNative(Activity activity, WebView web) {
        this.activity = activity;
        this.web = web;
    }

    private void report(String state, int percent, String message) {
        activity.runOnUiThread(() -> {
            try {
                JSONObject d = new JSONObject();
                d.put("state", state);
                d.put("percent", percent);
                d.put("message", message);
                web.evaluateJavascript(
                    "window.dispatchEvent(new CustomEvent('gb-updater-native',{detail:" + d.toString() + "}));",
                    null);
            } catch (Exception ignored) { }
        });
    }

    @JavascriptInterface
    public String getVersionName(){
      try{
        return activity.getPackageManager()
          .getPackageInfo(activity.getPackageName(),0).versionName;
      }catch(Exception e){return "";}
    }

    @JavascriptInterface
    public void start(String url, String version) {
        if (!busy.compareAndSet(false, true)) return;
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                Uri u = Uri.parse(url);
                String prefix = "/" + OWNER + "/" + REPO + "/releases/download/";
                if (!"https".equalsIgnoreCase(u.getScheme()) ||
                    !"github.com".equalsIgnoreCase(u.getHost()) ||
                    !u.getPath().startsWith(prefix) ||
                    !u.getPath().toLowerCase(java.util.Locale.ROOT).endsWith(".apk"))
                    throw new SecurityException("Niedozwolony adres APK");

                File folder = new File(activity.getCacheDir(), "gb-updates");
                if (!folder.exists() && !folder.mkdirs())
                    throw new java.io.IOException("Brak katalogu aktualizacji");
                File apk = new File(folder, "gigabajt-update.apk");
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                connection.setInstanceFollowRedirects(true);
                connection.connect();
                int code = connection.getResponseCode();
                if (code < 200 || code >= 300)
                    throw new java.io.IOException("HTTP " + code);
                long total = connection.getContentLengthLong();
                long read = 0;
                int lastProgress = -1;
                report("downloading", 0, "Pobieranie aktualizacji");
                try (InputStream in = connection.getInputStream();
                     FileOutputStream out = new FileOutputStream(apk)) {
                    byte[] buffer = new byte[16384];
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        out.write(buffer, 0, n);
                        read += n;
                        int progress = total > 0 ? (int) Math.min(99, read * 100 / total) : -1;
                        if (progress != lastProgress) {
                            report("downloading", progress, total > 0 ? "Pobieranie " + progress + "%" : "Pobieranie...");
                            lastProgress = progress;
                        }
                    }
                    out.getFD().sync();
                }
                if (read == 0 || (total > 0 && read != total))
                    throw new java.io.IOException("Niepelny plik APK");
                try (ZipFile z = new ZipFile(apk)) {
                    if (z.getEntry("AndroidManifest.xml") == null || z.getEntry("classes.dex") == null)
                        throw new java.io.IOException("Nieprawidlowy plik APK");
                }
                PackageManager pm = activity.getPackageManager();
                PackageInfo found = pm.getPackageArchiveInfo(apk.getAbsolutePath(), 0);
                PackageInfo installed = pm.getPackageInfo(activity.getPackageName(), 0);
                if (found == null || !activity.getPackageName().equals(found.packageName))
                    throw new SecurityException("Inny identyfikator aplikacji");
                long oldCode = Build.VERSION.SDK_INT >= 28 ? installed.getLongVersionCode() : installed.versionCode;
                long newCode = Build.VERSION.SDK_INT >= 28 ? found.getLongVersionCode() : found.versionCode;
                if (newCode <= oldCode)
                    throw new SecurityException("Numer aktualizacji nie jest nowszy");
                report("downloaded", 100, "Pobrano. Informacje o zmianach");
                // GIGABAJT CHMURKA 5 SEKUND
                Thread.sleep(5200);
                activity.runOnUiThread(() -> {
                    try {
                        gbInstallSession(activity, apk);
                        report("installing", 100, "Potwierdz instalacje w Androidzie");
                    } catch (Exception e) {
                        report("error", 0, "Nie mozna uruchomic instalatora: " + e.getMessage());
                        busy.set(false);
                    }
                });
            } catch (Exception e) {
                report("error", 0, "Aktualizacja przerwana: " + e.getMessage());
            } finally {
                if (connection != null) connection.disconnect();
                busy.set(false);
            }
        }, "GBUpdaterDownload").start();
    }

private static void gbInstallSession(
        android.app.Activity activity, java.io.File apk) {
    new Thread(() -> {
        android.content.pm.PackageInstaller pi =
            activity.getPackageManager().getPackageInstaller();
        int id = -1;
        boolean committed = false;
        try {
            if (!apk.isFile() || apk.length()==0)
                throw new java.io.IOException("Brak APK");

            if (android.os.Build.VERSION.SDK_INT >= 26 &&
                !activity.getPackageManager()
                    .canRequestPackageInstalls()) {
                activity.runOnUiThread(() -> {
                    android.content.Intent settings =
                        new android.content.Intent(
                            android.provider.Settings
                            .ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            android.net.Uri.parse(
                                "package:"+activity.getPackageName()));
                    activity.startActivity(settings);
                });
                return;
            }

            android.content.pm.PackageInstaller.SessionParams params =
                new android.content.pm.PackageInstaller.SessionParams(
                    android.content.pm.PackageInstaller
                        .SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(activity.getPackageName());
            params.setSize(apk.length());
            id=pi.createSession(params);

            try (android.content.pm.PackageInstaller.Session session =
                     pi.openSession(id)) {
                try (java.io.InputStream input =
                         new java.io.FileInputStream(apk);
                     java.io.OutputStream output =
                         session.openWrite("base.apk",0,apk.length())) {
                    byte[] buf=new byte[65536];
                    int n;
                    while ((n=input.read(buf))!=-1)
                        output.write(buf,0,n);
                    session.fsync(output);
                }

                android.content.Intent result =
                    new android.content.Intent(
                        activity,GBInstallResultReceiver.class);

                int flags=android.app.PendingIntent.FLAG_UPDATE_CURRENT;
                if (android.os.Build.VERSION.SDK_INT >= 31)
                    flags |= android.app.PendingIntent.FLAG_MUTABLE;

                android.app.PendingIntent pending =
                    android.app.PendingIntent.getBroadcast(
                        activity,id,result,flags);

                session.commit(pending.getIntentSender());
                committed=true;
            }
        } catch(Exception e) {
            android.util.Log.e("GBInstall","Instalacja",e);
            if(id>=0 && !committed) {
                try { pi.abandonSession(id); }
                catch(Exception ignored) {}
            }
            activity.runOnUiThread(() ->
                android.widget.Toast.makeText(
                    activity,"Blad instalacji: "+e.getMessage(),
                    android.widget.Toast.LENGTH_LONG).show());
        }
    },"GB-Installer").start();
}

}
