package com.badlogicgames.gwen;

import com.esotericsoftware.minlog.Log
import com.google.gson.Gson
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.io.FileWriter
import java.net.InetSocketAddress

fun startWebInterface (port: Int = 8777) {
    val server = HttpServer.create(InetSocketAddress(port), 0);
    server.createContext("/", WebInterface());
    server.start();
}

val MIMETYPE_PLAINTEXT = "text/plain";
val MIMETYPE_HTML = "text/html";
val MIMETYPE_CSS = "text/css";
val MIMETYPE_BINARY = "application/octet-stream"
val MIMETYPE_JSON = "application/json"
val MIMETYPE_PNG = "image/png"
val MIMETYPE_JPEG = "image/jpeg"
val MIMETYPE_GIF = "image/gif"
val MIMETYPE_JS = "text/javascript"

class WebInterface : HttpHandler {
    override fun handle(request: HttpExchange) {
        when (request.requestURI.path) {
            "/" -> respond(request, File(appPath, "assets/web/index.html").readText().toByteArray(), MIMETYPE_HTML)
            "/projectSave" -> handleProjectSave(request);
            "/accountSave" -> handleAccountSave(request);
            "/status" -> handleStatus(request);
            else -> handleFile(request);
        }
    }

    private fun respond(request: HttpExchange, content: ByteArray, type: String,  status: Int = 200) {
        request.responseHeaders.add("Content-Type", type);
        request.sendResponseHeaders(status, content.size.toLong());
        request.responseBody.use {
            it.write(content);
        }
    }

    private fun error(request: HttpExchange, message: String, status: Int) {
        respond(request, message.toByteArray(), MIMETYPE_PLAINTEXT, status);
    }

    private fun handleFile(request: HttpExchange) {
        var root = File(appPath, "assets/web/").absolutePath;
        var file = File(root + request.requestURI.path).canonicalFile;
        when {
            !file.path.startsWith(root) -> error(request, "(403) Forbidden", 403)
            !file.exists() || file.isDirectory -> error(request, "(404) Not found", 404);
            else -> {
                val extension = file.extension;
                val type: String
                when (extension.toLowerCase()) {
                    "png" -> type = MIMETYPE_PNG
                    "jpg", "jpeg" -> type = MIMETYPE_JPEG
                    "gif" -> type = MIMETYPE_GIF
                    "html" -> type = MIMETYPE_HTML
                    "css" -> type = MIMETYPE_CSS
                    "js" -> type = MIMETYPE_JS
                    else -> type = MIMETYPE_BINARY
                }
                respond(request, file.readBytes(), type);
            }
        }
    }

    private fun parseParams(request: HttpExchange): Map<String, String> {
        val params = mutableMapOf<String, String>();
        if (request.requestURI.query == null) return params;
        for (param in request.requestURI.query.split("&")) {
            if (param.contains("=")) {
                val tokens = param.split("=");
                params.put(tokens[0], tokens[1]);
            }
        }
        return params;
    }

    private fun handleProjectSave(request: HttpExchange) {
        val params = parseParams(request);
        val id = params.get("clientId");
        val secret = params.get("clientSecret");

        Log.info("Saving project config");
        if (id != null && !id.isEmpty() && secret != null && !secret.isEmpty()) {
            config = GwenConfig(id, secret);
            try {
                FileWriter(File(appPath, "gwen.json")).use {
                    Gson().toJson(config, it);
                }
            } catch(t: Throwable) {
                Log.error("Couldn't save config", t);
                error(request, "Couldn't save config", 400);
                File(appPath, "gwen.json").delete();
                oauth?.let{ it.deleteCredentials(); }
            }
            gwen.stop();
            oauth?.let{ it.deleteCredentials(); }
            oauth = loadOAuth(config!!);
            handleStatus(request);
        } else {
            error(request, "Invalid client id & secret", 400);
        }
    }

    private fun handleAccountSave(request: HttpExchange) {
        var params = parseParams(request);
        val code = params.get("code");

        Log.info("Got OAuth code, saving account");
        if (code != null && !code.isEmpty()) {
            val oa = loadOAuth((config!!));
            try {
                oa.requestAccessToken(code);
            } catch(t: Throwable) {
                Log.error("Couldn't authorize", t);
                error(request, "Couldn't authorize", 400);
                return;
            }
            if (oa.isAuthorized()) {
                oauth = oa;
                gwen.stop();
                gwen.start(oa);
                handleStatus(request);
            } else {
                error(request, "Invalid code, authorization failed", 400);
            }
        } else {
            error(request, "Invalid code, authorization failed", 400);
        }
    }

    private fun handleStatus(request: HttpExchange) {
        val status: GwenStatus;
        val oauth = oauth;
        status = GwenStatus(config == null, oauth == null || !oauth.isAuthorized(), oauth?.getAuthorizationURL(), gwen.running);
        respond(request, Gson().toJson(status).toByteArray(), MIMETYPE_JSON);
    }
}
