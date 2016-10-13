package cn.zjy.patchupdate;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;

/**
 * Created by zongjingyao on 16/10/12.
 */

public class PatchUpdate {
    private static final String TAG = "PatchUpdate";

    static {
        System.loadLibrary("bspatch");
    }

    public static void update(final Context context, final String patchUrl, final PatchUpdateListener listener) {
        if (context == null || TextUtils.isEmpty(patchUrl) || listener == null) return;

        new Thread() {
            @Override
            public void run() {
                String patchPath = downloadPatch(patchUrl);
                if (!new File(patchPath).exists()) {
                    listener.onFailure("download patch failed!");
                    return;
                }
                String[] parts = patchPath.split("--");
                if (parts.length < 4) {
                    listener.onFailure("patch name is invalid!");
                    return;
                }
                String newVersion = parts[2];
                String newMd5 = parts[3];

                String oldApkPath = getOldApkPath(context);
                if (TextUtils.isEmpty(oldApkPath) || !new File(oldApkPath).exists()) {
                    listener.onFailure("old apk not exists!");
                    return;
                }
                String newApkPath = getNewApkPath(context, newVersion);
                mergePatch(oldApkPath, newApkPath, patchPath);

                if (!new File(newApkPath).exists()) {
                    listener.onFailure("merge patch failed!");
                    return;
                }

                String mergedApkMd5 = getMd5(newApkPath);
                if (mergedApkMd5.equalsIgnoreCase(newMd5)) {
                    listener.onSuccess(newApkPath);
                } else {
                    listener.onFailure("check md5 failed!");
                }
            }
        }.start();
    }

    public static void install(Context context, String apkPath) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.setDataAndType(Uri.fromFile(new File(apkPath)),
                "application/vnd.android.package-archive");
        context.startActivity(i);
    }

    private static String getMd5(String apkPath) {
        String md5 = "";
        InputStream is = null;
        try {
            is = new FileInputStream(apkPath);
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = new byte[1024];
            int len;
            while ((len = is.read(bytes)) > 0) {
                digest.update(bytes, 0, len);
            }

            byte[] digests = digest.digest();
            StringBuilder buffer = new StringBuilder();
            for (byte b : digests) {
                String hexString = Integer.toHexString(b & 0xff);
                if (hexString.length() == 1) {
                    buffer.append("0").append(hexString);
                } else {
                    buffer.append(hexString);
                }
            }
            md5 = buffer.toString();
        } catch (Exception e) {
//            e.printStackTrace();
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
//                    e.printStackTrace();
                }
            }
        }

        return md5;
    }

    private static String getNewApkPath(Context context, String targetVersion) {
        File downloadFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!downloadFolder.exists()) downloadFolder.mkdirs();
        String apkName = context.getApplicationContext().getApplicationInfo().packageName + "_" + targetVersion + ".apk";
        return downloadFolder.getAbsolutePath() + File.separator + apkName;
    }

    /**
     * download patch file
     *
     * @param patchUrl patch file url
     * @return patch file name. (name--old version--new version--md5)
     */
    private static String downloadPatch(String patchUrl) {
        File downloadFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!downloadFolder.exists()) downloadFolder.mkdirs();

        String patchName = patchUrl.substring(patchUrl.lastIndexOf("/") + 1);
        File patchFile = new File(downloadFolder, patchName);

        try {
            URL url = new URL(patchUrl);
            URLConnection conn = url.openConnection();
            OutputStream os = new FileOutputStream(patchFile);
            InputStream is = conn.getInputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = is.read(buffer)) != -1) {
                os.write(buffer, 0, len);
            }
            os.close();
            is.close();
        } catch (Exception e) {
//            e.printStackTrace();
        }

        return patchFile.getAbsolutePath();
    }

    private static String getOldApkPath(Context context) {
        context = context.getApplicationContext();
        ApplicationInfo applicationInfo = context.getApplicationInfo();
        String apkPath = applicationInfo.sourceDir;
        Log.d(TAG, "old apk: " + apkPath);
        return apkPath;
    }

    public static native int mergePatch(String oldFilePath, String newFilePath, String patch);

    public interface PatchUpdateListener {
        void onSuccess(String newApk);

        void onFailure(String error);
    }

}
