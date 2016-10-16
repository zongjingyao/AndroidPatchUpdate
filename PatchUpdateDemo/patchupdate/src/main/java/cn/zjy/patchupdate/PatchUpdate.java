package cn.zjy.patchupdate;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
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

    public static final int NO_ERROR = 1;
    public static final int ERROR_WRONG_ARGS = 0;
    public static final int ERROR_DOWNLOAD_PATCH_FAILED = -1;
    public static final int ERROR_DOWNLOAD_APK_FAILED = -2;
    public static final int ERROR_PATCH_NAME_IS_INVALID = -3;
    public static final int ERROR_OLD_APK_NOT_EXISTS = -4;
    public static final int ERROR_MERGE_PATCH_FAILED = -5;
    public static final int ERROR_CHECK_MD5_FAILED = -6;

    private PatchUpdate() {
    }

    public static void update(Context context, final String patchUrl, final String fullApkUrl,
                              final String newVersion, final String fileSeparator, final PatchUpdateListener listener) {
        final Context applicationContext = context.getApplicationContext();
        new Thread() {
            @Override
            public void run() {
                int ret = updateByPatch(applicationContext, patchUrl, newVersion, fileSeparator, null);
                Log.d(TAG, "updateByPatch: " + ret);
                if (ret != NO_ERROR && TextUtils.isEmpty(fullApkUrl)) {
                    if (listener != null) listener.onFailure(ret);
                    return;
                }

                updateByFullApk(applicationContext, fullApkUrl, newVersion, listener);
            }
        }.start();
    }

    private static int updateByPatch(Context context, String patchUrl, String newVersion,
                                     String fileSeparator, PatchUpdateListener listener) {
        if (context == null || TextUtils.isEmpty(patchUrl) || TextUtils.isEmpty(fileSeparator))
            return ERROR_WRONG_ARGS;

        String patchPath = downloadPatch(patchUrl);
        if (TextUtils.isEmpty(patchPath) || !new File(patchPath).exists()) {
            if (listener != null) listener.onFailure(ERROR_DOWNLOAD_PATCH_FAILED);
            return ERROR_DOWNLOAD_PATCH_FAILED;
        }
        String patchName = new File(patchPath).getName();
        String[] parts = patchName.split(fileSeparator);
        if (parts.length < 5) {
            if (listener != null) listener.onFailure(ERROR_PATCH_NAME_IS_INVALID);
            return ERROR_PATCH_NAME_IS_INVALID;
        }
        if (TextUtils.isEmpty(newVersion)) newVersion = parts[2];
        String newApkMd5 = parts[4].substring(0, parts[4].lastIndexOf("."));

        String oldApkPath = getOldApkPath(context);
        if (TextUtils.isEmpty(oldApkPath) || !new File(oldApkPath).exists()) {
            if (listener != null) listener.onFailure(ERROR_OLD_APK_NOT_EXISTS);
            return ERROR_OLD_APK_NOT_EXISTS;
        }
        String mergedApkPath = getNewApkPath(context, newVersion);
        BsPatch.merge(oldApkPath, mergedApkPath, patchPath);

        if (!new File(mergedApkPath).exists()) {
            if (listener != null) listener.onFailure(ERROR_MERGE_PATCH_FAILED);
            return ERROR_MERGE_PATCH_FAILED;
        }

        String mergedApkMd5 = getMd5(mergedApkPath);
        if (mergedApkMd5.equalsIgnoreCase(newApkMd5)) {
            if (listener != null) listener.onSuccess(mergedApkPath);
        } else {
            if (listener != null) listener.onFailure(ERROR_CHECK_MD5_FAILED);
            return ERROR_CHECK_MD5_FAILED;
        }
        return NO_ERROR;
    }

    private static void updateByFullApk(Context context, String fullApkUrl, String newVersion,
                                        PatchUpdateListener listener) {
        if (context == null || TextUtils.isEmpty(fullApkUrl) || TextUtils.isEmpty(newVersion))
            return;

        String apkPath = getNewApkPath(context, newVersion);
        File apk = new File(apkPath);
        if (apk.exists()) apk.delete();

        if (!download(fullApkUrl, apkPath) || !new File(apkPath).exists()) {
            if (listener != null) listener.onFailure(ERROR_DOWNLOAD_APK_FAILED);
        } else {
            if (listener != null) listener.onSuccess(apkPath);
        }
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

    public static String getVersionName(Context context) {
        String version = "0.0.1";
        PackageManager pm = context.getPackageManager();
        try {
            PackageInfo packageInfo = pm.getPackageInfo(context.getPackageName(), 0);
            version = packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
//            e.printStackTrace();
        }
        return version;
    }

    /**
     * download patch file
     *
     * @param patchUrl patch file url
     * @return patch file name. (name-old version-new version-channel-md5.patch)
     */
    private static String downloadPatch(String patchUrl) {
        File downloadFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!downloadFolder.exists()) downloadFolder.mkdirs();

        String patchName = patchUrl.substring(patchUrl.lastIndexOf("/") + 1);
        String patchPath = downloadFolder.getAbsolutePath() + File.separator + patchName;
        if (download(patchUrl, patchPath)) {
            return patchPath;
        }

        return null;
    }

    private static boolean download(String strUrl, String path) {
        boolean ret = true;
        try {
            URL url = new URL(strUrl);
            URLConnection conn = url.openConnection();
            InputStream is = conn.getInputStream();
            OutputStream os = new FileOutputStream(path);
            byte[] buffer = new byte[1024];
            int len;
            while ((len = is.read(buffer)) != -1) {
                os.write(buffer, 0, len);
            }
            os.close();
            is.close();
        } catch (Exception e) {
            ret = false;
        }
        return ret;
    }

    private static String getOldApkPath(Context context) {
        context = context.getApplicationContext();
        ApplicationInfo applicationInfo = context.getApplicationInfo();
        String apkPath = applicationInfo.sourceDir;
        Log.d(TAG, "old apk: " + apkPath);
        return apkPath;
    }

    public interface PatchUpdateListener {
        void onSuccess(String newApk);

        void onFailure(int error);
    }

}
