function downloadApk() {

    // window.NativePlugin && window.NativePlugin.androidInstallApk('https://qd.myapp.com/myapp/qqteam/Androidlite/qqlite_3.7.1.704_android_r110206_GuanWang_537057973_release_10000484.apk');

    window['NativePlugin'] && window['NativePlugin'].call(
        'downloadApk',
        [
            'https://qd.myapp.com/myapp/qqteam/Androidlite/qqlite_3.7.1.704_android_r110206_GuanWang_537057973_release_10000484.apk',
            'XXXX应用更新',
            '正在下载'
        ]
    );
}

function requestRecorder() {
    window['NativePlugin'] && window['NativePlugin'].call(
        'requestRecorder',
        [],
        (code) => {
            // 0 失败, 1 授权成功-开始下载
            alert(code);
        }
    );
}