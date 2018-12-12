var exec = require('cordova/exec');
var NativePlugin = {};
NativePlugin.androidInstallApk = function (apkUrl, success, error) {
    exec(success, error, 'AndroidUpdatePlugin', '', [apkUrl]);
};
module.exports = NativePlugin;