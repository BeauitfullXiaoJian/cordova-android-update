package com.plugin;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.Manifest;
import android.content.Context;
import android.widget.Toast;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.json.JSONArray;
import org.json.JSONException;


public class AndroidUpdatePlugin extends CordovaPlugin implements ServiceConnection,
        DownloadService.ProgressListener {

    public static final String TAG = "FbxPluginLog";

    public static final Integer REQUEST_STORAGE_WRITE = 1;

    public static final Integer INSTALL_PACKAGES_REQUEST_CODE = 2;

    public CallbackContext mCallbackContext;

    private Uri mApkUri;

    private String downloadUrl;

    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {

        this.mCallbackContext = callbackContext;
        Activity mainActivity = cordova.getActivity();
        Log.d(TAG, "调用应用更新服务");
        downloadUrl = args.getString(0);
        cordova.requestPermissions(AndroidUpdatePlugin.this, REQUEST_STORAGE_WRITE,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE});
        Log.d(TAG, "请求本地存储授权");
        return true;
    }

    /**
     * 请求安装应用授权
     */
    private void requestApkInstall() {

        // 判断是否允许安装第三方来源apk--26版本以上需要请求权限
        Log.d(TAG, "SDK_INIT:" + Build.VERSION.SDK_INT);
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
            PackageManager packageManager = cordova.getActivity().getPackageManager();

            // 如果不允许安装，跳转到开启授权页面
            if (!packageManager.canRequestPackageInstalls()) {
                Log.d(TAG, "请求安装未知应用来源的权限");
                cordova.requestPermissions(AndroidUpdatePlugin.this,
                        INSTALL_PACKAGES_REQUEST_CODE,
                        new String[]{Manifest.permission.REQUEST_INSTALL_PACKAGES});
            } else {
                // 手动通知授权成功
                try {
                    onRequestPermissionResult(INSTALL_PACKAGES_REQUEST_CODE,
                            new String[]{},
                            new int[]{PackageManager.PERMISSION_GRANTED});
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        } else {
            try {
                onRequestPermissionResult(INSTALL_PACKAGES_REQUEST_CODE,
                        new String[]{},
                        new int[]{PackageManager.PERMISSION_GRANTED});
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // 如果是来自第三方包安装授权，那么执行安装请求（安装请求中会校验是否授权了）
        if (requestCode == INSTALL_PACKAGES_REQUEST_CODE) {
            requestApkInstall();
        }
    }

    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {

        Activity mainActivity = cordova.getActivity();

        super.onRequestPermissionResult(requestCode, permissions, grantResults);
        Log.d(TAG, "接收到请求回调");

        // 存储卡写入权限
        if (requestCode == REQUEST_STORAGE_WRITE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "成功授权，可以写入本地文件");
                Intent intent = new Intent(mainActivity, DownloadService.class);
                intent.putExtra(DownloadService.DOWNLOAD_URL_KEY,
                        downloadUrl);
                mainActivity.bindService(intent, AndroidUpdatePlugin.this,
                        Context.BIND_AUTO_CREATE);
            } else {
                Log.d(TAG, "拒绝授权，无法写入本地文件");
                Toast.makeText(mainActivity.getApplicationContext(), "拒绝授权,无法下载文件",
                        Toast.LENGTH_SHORT).show();
            }
        }

        // 第三方应用安装权限
        if (requestCode == INSTALL_PACKAGES_REQUEST_CODE) {
            Log.d(TAG, "长度" + grantResults.length);
            if (grantResults.length == 0 || grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "成功授权，允许安装第三方应用");
                Intent installIntent = new Intent(Intent.ACTION_VIEW);
                installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                installIntent.setDataAndType(mApkUri, "application/vnd.android.package-archive");
                cordova.getActivity().startActivity(installIntent);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.d(TAG, "拒绝授权，不允许安装第三方应用");
                Toast.makeText(mainActivity.getApplicationContext(), "拒绝授权,无法安装最新版本应用",
                        Toast.LENGTH_SHORT).show();
                // 跳转到允许设置页面
                Uri packageURI = Uri.parse("package:" + mainActivity.getPackageName());
                Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageURI);
                cordova.startActivityForResult(AndroidUpdatePlugin.this, intent, INSTALL_PACKAGES_REQUEST_CODE);
            }
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        DownloadService.DownloadBinder binder = (DownloadService.DownloadBinder) service;
        binder.getService().setProgressListener(AndroidUpdatePlugin.this);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }

    @Override
    public void onInstall(Uri apkUri) {
        Log.d(TAG, "请求安装应用");
        mApkUri = apkUri;
        Log.d(TAG, apkUri.getPath());
        requestApkInstall();
    }

    @Override
    public void onProgress(DownloadService.DownloadData downloadData) {
        Log.d(TAG, downloadData.toString());
    }
}