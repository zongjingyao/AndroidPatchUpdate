var http = require("http");
var url = require('url');
var fs = require('fs');

var APK_DIR;
var NEW_VERSION;
var APP_NAME;
var SEPARATOR;
var EXT;
var PATCH_EXT;
var PATCH_FOLDER;
var BASE_URL;

function sendVersionInfo(response, clientVersion, channel) {
    console.log(clientVersion, channel);

    var patchName = APP_NAME + SEPARATOR + clientVersion + SEPARATOR + NEW_VERSION + SEPARATOR + channel;
    var patchFolderPath = __dirname + '/' + APK_DIR + '/' + NEW_VERSION + '/' + PATCH_FOLDER;
    var patchPath = null;
    try {
        var files = fs.readdirSync(patchFolderPath);
        var i;
        for (i = 0; i < files.length; i++) {
            if (files[i].startsWith(patchName) && files[i].endsWith(PATCH_EXT)) {
                patchPath = NEW_VERSION + '/' + PATCH_FOLDER + '/' + files[i];
                break;
            }
        }
    } catch (e) {
        console.log(e);
    }
    var patchUrl = patchPath == null ? null : BASE_URL + '/download?path=' + patchPath;
    var newVersionUrl = BASE_URL + '/download?path=' + NEW_VERSION + '/' + APP_NAME + SEPARATOR + NEW_VERSION + SEPARATOR + channel + EXT;
    var info = {
        'new_version': NEW_VERSION,
        'channel': channel,
        'new_version_url': newVersionUrl,
        'patch_url': patchUrl
    };

    response.writeHead(200, { "Content-Type": "text/json" });
    response.write(JSON.stringify(info));
    response.end();
}

function sendFile(response, path) {
    console.log(path);
    if (!path) {
        response.writeHead(404, { 'Content-Type': 'text/plain' });
        response.end();
        return;
    }
    path = __dirname + '/' + APK_DIR + '/' + path;
    console.log(path);
    fs.exists(path, function (exists) {
        if (!exists) {
            response.writeHead(404, { 'Content-Type': 'text/plain' });
            response.end();
            return;
        }
        fs.readFile(path, 'binary', function (err, file) {
            if (err) {
                response.writeHead(500, { 'Content-Type': 'text/plain' });
                response.end();
                return;
            }

            response.writeHead(200, { 'Content-Type': 'text/plain' });
            response.write(file, 'binary');
            response.end();
        });
    });
}

function loadConfig() {
    fs.readFile('config.json', function (err, file) {
        var config = JSON.parse(file.toString());
        NEW_VERSION = config.new_version;
        APP_NAME = config.app_name;
        SEPARATOR = config.separator;
        EXT = config.ext;
        PATCH_EXT = config.patch_ext;
        PATCH_FOLDER = config.patch_folder;
        BASE_URL = config.base_url;
        APK_DIR = config.apk_dir;
        console.log(NEW_VERSION, APP_NAME, SEPARATOR, EXT, PATCH_EXT, PATCH_FOLDER, BASE_URL, APK_DIR);
        createServer();
    });
}

function createServer() {
    http.createServer(function (request, response) {
        console.log('request received');
        var parsed = url.parse(request.url, true);
        var args = parsed.query;
        var pathName = parsed.pathname;
        console.log(pathName);
        if (pathName == '/check') {
            sendVersionInfo(response, args.version, args.channel);
        } else if (pathName == '/download') {
            sendFile(response, args.path);
        }
    }).listen(8888);
}

loadConfig();
