package pl.gigabajt.cmr;

public class GBInstallResultReceiver
        extends android.content.BroadcastReceiver {
    @Override
    public void onReceive(
            android.content.Context context,
            android.content.Intent result) {
        int status=result.getIntExtra(
            android.content.pm.PackageInstaller.EXTRA_STATUS,
            android.content.pm.PackageInstaller.STATUS_FAILURE);

        if(status==android.content.pm.PackageInstaller
                .STATUS_PENDING_USER_ACTION) {
            android.content.Intent confirmation =
                (android.content.Intent) result.getParcelableExtra(
                    android.content.Intent.EXTRA_INTENT);
            if(confirmation!=null) {
                confirmation.addFlags(
                    android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    context.startActivity(confirmation);
                } catch(Exception e) {
                    android.util.Log.e(
                        "GBInstall","Potwierdzenie instalacji",e);
                }
            }
        } else if(status!=android.content.pm.PackageInstaller
                .STATUS_SUCCESS) {
            android.util.Log.e("GBInstall",
                "Status: "+status+" "+
                result.getStringExtra(
                    android.content.pm.PackageInstaller
                        .EXTRA_STATUS_MESSAGE));
        }
    }
}
