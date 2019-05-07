# Android应用更新插件

## 安装

`cordova plugin add cordova-android-update`

## 使用

```js

// 旧版方法仍可用 
// window.NativePlugin && window.NativePlugin.androidInstallApk('https://qd.myapp.com/myapp/qqteam/Androidlite/qqlite_3.7.1.704_android_r110206_GuanWang_537057973_release_10000484.apk');


/**
 * 下载应用并安装
 */
window['NativePlugin'] && window['NativePlugin'].call(
    'downloadApk',
    [
        // 应用下载地址
        'https://qd.myapp.com/myapp/qqteam/Androidlite/qqlite_3.7.1.704_android_r110206_GuanWang_537057973_release_10000484.apk',
        // 通知标题
        'XXXX应用更新',
        // 通知描述信息
        '正在下载'
    ]
);

/**
 * 请求录音权限
 */
window['NativePlugin'] && window['NativePlugin'].call(
    'requestRecorder',
    [],
    (code) => {
        // 0 失败, 1 授权成功-开始下载
        alert(code);
    }
);
```