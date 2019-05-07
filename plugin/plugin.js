var exec = require('cordova/exec');
var NativePlugin = {};
NativePlugin.androidInstallApk = function (apkUrl, success, error) {
    exec(success, error, 'AndroidUpdatePlugin', 'downloadApk', [apkUrl]);
};
NativePlugin.call = function (callName, params, success, error) {
    exec(success, error, 'AndroidUpdatePlugin', callName, params);
};
module.exports = NativePlugin;