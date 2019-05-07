package com.plugin;

import android.app.DownloadManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DownloadService extends Service {

    public static final String TAG = "DownloadService";
    public static final String DOWNLOAD_URL_KEY = "DOWNLOAD_URL_KEY";
    public static final Integer DOWNLOAD_HANDLE = 0;

    private String mDownloadFilePath;
    private String mDownloadUrl;
    private Long mDownloadId;
    private DownloadBinder mBinder;
    private DownloadManager mDownloadManager;
    private DownloadObserver mDownloadObserver;
    private DownloadReceiver mDownloadReceiver;
    private Handler mDownloadHandle;
    private ScheduledExecutorService mScheduledExecutorService;
    private Runnable mUpdateProgressRunnable;
    private ProgressListener mProgressListener;
    private Boolean mDownloadIsOk = false;

    @Override
    public void onCreate() {
        super.onCreate();
        mBinder = new DownloadBinder();
        mDownloadHandle = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                if (msg.what == DOWNLOAD_HANDLE) {
                    // 接受到下载进度消息
                    DownloadData downloadData = (DownloadData) msg.obj;
                    mProgressListener.onProgress(downloadData);
                    if (downloadData.totalSize > 0) {
                        if (!mDownloadIsOk) {
                            Log.d(TAG, "下载中" + downloadData.toString());
                        } else {
                            Uri downIdUri = mDownloadManager.getUriForDownloadedFile(mDownloadId);
                            Uri apkUri = getApkUri(downIdUri);
                            Log.d(TAG, "下载完成,文件路径为:" + downIdUri.getPath());
                            mProgressListener.onInstall(apkUri);
                            closeExecutorService();
                        }
                    }
                }
                return false;
            }
        });
    }

    @Override
    public IBinder onBind(Intent intent) {
        mDownloadUrl = intent.getStringExtra(DOWNLOAD_URL_KEY);
        Log.d(TAG, "下载服务绑定成功，开始下载文件");
        download();
        return mBinder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterBroadcast();
        unregisterContentObserver();
        Log.d(TAG, "下载服务销毁");
    }

    private String getDownloadFilePath() {
        if (mDownloadFilePath == null) {
            DateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMddHHmmss",
                    Locale.getDefault());
            mDownloadFilePath = simpleDateFormat.format(new Date()) + ".apk";
        }
        return mDownloadFilePath;
    }

    private Uri getApkUri(Uri apkUri) {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            String filePath = "file://"
                    + Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    .getAbsolutePath()
                    + "/"
                    + getDownloadFilePath();
            apkUri = Uri.parse(filePath);
            Log.d(TAG, apkUri.getPath());
        }

        return apkUri;
    }

    private Intent createInstallIntent(Uri apkUri) {
        apkUri = getApkUri(apkUri);
        Intent installIntent = new Intent(Intent.ACTION_VIEW);
        installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        return installIntent;
    }

    private void download() {
        // 获取下载管理器
        mDownloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        // 注册观察者
        registerContentObserver();
        //创建下载请求
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(mDownloadUrl));
        // 设置用于下载时的网络状态
        // request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
        // 设置通知栏可见
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
        // 设置漫游下禁止下载
        request.setAllowedOverRoaming(false);
        // 设置下载描述
        request.setDescription(AndroidUpdatePlugin.downloadDesc);
        // 设置下载标题
        request.setTitle(AndroidUpdatePlugin.downloadTitle);
        // 允许下载的文件被系统download应用管理
        request.setVisibleInDownloadsUi(true);
        // 设置文件保存地址
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, getDownloadFilePath());
        // setting mime type
        request.setMimeType("application/vnd.android.package-archive");
        // 把下载请求添加到下载队列中
        mDownloadId = mDownloadManager.enqueue(request);
        // 注册广播接收器
        registerBroadcast();
    }

    private void registerBroadcast() {
        IntentFilter intentFilter = new IntentFilter();
        mDownloadReceiver = new DownloadReceiver();
        intentFilter.addAction(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        registerReceiver(mDownloadReceiver, intentFilter);
    }

    private void unregisterBroadcast() {
        if (mDownloadReceiver != null) {
            unregisterReceiver(mDownloadReceiver);
            mDownloadReceiver = null;
        }
    }

    private void registerContentObserver() {
        mDownloadObserver = new DownloadObserver();
        getContentResolver().registerContentObserver(Uri.parse("content://downloads/my_downloads"),
                true, mDownloadObserver);
    }

    private void unregisterContentObserver() {
        if (mDownloadReceiver != null) {
            getContentResolver().unregisterContentObserver(mDownloadObserver);
        }
    }

    private void closeExecutorService() {
        if (mScheduledExecutorService != null && !mScheduledExecutorService.isShutdown()) {
            mScheduledExecutorService.shutdown();
            mScheduledExecutorService = null;
        }
        if (mDownloadHandle != null) {
            mDownloadHandle.removeCallbacksAndMessages(null);
        }
    }

    private void updateDownloadProgress() {
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(mDownloadId);
        DownloadData downloadData = new DownloadData();
        Cursor cursor = mDownloadManager.query(query);
        try {
            if (cursor != null && cursor.moveToFirst()) {
                // 已经下载的文件大小
                downloadData.completeSize = cursor.getInt(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                // 下载文件总大小
                downloadData.totalSize = cursor.getInt(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                // 下载状态
                downloadData.downloadStatus = cursor.getInt(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                // 关闭
                cursor.close();
            }
        } catch (Exception e) {
            Log.d(TAG, "获取下载进度失败");
            e.printStackTrace();
        }
        // 发送下载进度消息
        mDownloadHandle.sendMessage(mDownloadHandle.obtainMessage(DOWNLOAD_HANDLE, downloadData));
    }

    /**
     * 设置进度监听对象，调用服务的activity可以用这个对象来获取当前的下载状态
     *
     * @param listener 监听器
     */
    public void setProgressListener(ProgressListener listener) {
        mProgressListener = listener;
    }


    class DownloadBinder extends Binder {

        DownloadService getService() {
            return DownloadService.this;
        }
    }

    public class DownloadObserver extends ContentObserver {

        DownloadObserver() {
            super(mDownloadHandle);
            mScheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
            mUpdateProgressRunnable = new Runnable() {
                @Override
                public void run() {
                    // 更新进度条-DownloadHandle发送消息给下载服务
                    updateDownloadProgress();
                }
            };
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            if (mScheduledExecutorService != null) {
                mScheduledExecutorService.scheduleAtFixedRate(mUpdateProgressRunnable, 0, 2, TimeUnit.SECONDS);
            }
        }
    }

    public class DownloadReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "接收到广播消息");
            String action = intent.getAction();
            long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0);
            if (downloadId == 0 || downloadId != mDownloadId || mDownloadManager == null) {
                return;
            }
            if (action != null && action.equals(DownloadManager.ACTION_DOWNLOAD_COMPLETE)) {
                mDownloadIsOk = true;
                Log.d(TAG, "接受到下载完成广播");
            }
        }
    }

    public interface ProgressListener {
        void onProgress(DownloadData downloadData);

        void onInstall(Uri apkUri);
    }

    public class DownloadData {
        int completeSize;
        int totalSize;
        int downloadStatus;
        Boolean isOk;

        DownloadData() {
            completeSize = 0;
            totalSize = 0;
            downloadStatus = 0;
            isOk = false;
        }

        @Override
        public String toString() {
            return "(" + completeSize + "," + totalSize + "," + downloadStatus + "," + isOk + ")";
        }
    }
}
