package com.badlogicgames.gwen;

import com.esotericsoftware.minlog.Log
import com.google.gson.Gson
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.io.FileWriter
import java.net.InetSocketAddress

fun startWebInterface(port: Int = 8777) {
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
        // redirect to setup pages
        if (request.requestURI.path.endsWith(".html") || request.requestURI.path.equals("/")) {
            if (config == null && !request.requestURI.path.equals("/setup-project.html")) {
                redirect(request, "/setup-project.html")
                return;
            } else {
                val oa = oauth;
                if (config != null && (oa == null || !oa.isAuthorized()) && !request.requestURI.path.equals("/setup-oauth.html")) {
                    redirect(request, "/setup-oauth.html")
                    return;
                };
            }
        }

        // Laugh, but it works! :D
        when (request.requestURI.path) {
            "/" -> respond(request, File(appPath, "assets/web/index.html").readText().toByteArray(), MIMETYPE_HTML)
            "/projectSave" -> handleProjectSave(request);
            "/authorizationUrl" -> handleAuthorizationUrl(request);
            "/accountSave" -> handleAccountSave(request);
            "/models" -> handleModels(request);
            "/modelSave" -> handleModelSave(request);
            "/modelDelete" -> handleModelDelete(request);
            "/status" -> handleStatus(request);
            "/config" -> handleGetConfig(request);
            "/configSave" -> handleSetConfig(request);
            else -> handleFile(request);
        }
    }

    private fun respond(request: HttpExchange, content: ByteArray, type: String, status: Int = 200) {
        request.responseHeaders.add("Content-Type", type);
        request.sendResponseHeaders(status, content.size.toLong());
        request.responseBody.use {
            it.write(content);
        }
    }

    private fun error(request: HttpExchange, message: String, status: Int) {
        respond(request, message.toByteArray(), MIMETYPE_PLAINTEXT, status);
    }

    private fun redirect(request: HttpExchange, url: String) {
        request.responseHeaders.add("Location", url);
        request.sendResponseHeaders(302, 0);
        request.responseBody.close();
    }

    private fun handleFile(request: HttpExchange) {
        val root = File(appPath, "assets/web/").absolutePath;
        val file = File(root + request.requestURI.path).canonicalFile;
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
        val id = params["clientId"];
        val secret = params["clientSecret"];

        Log.info("Saving project config");
        if (id != null && !id.isEmpty() && secret != null && !secret.isEmpty()) {
            config = GwenConfig(id, secret, true, false, 8778);
            try {
                FileWriter(File(appPath, "gwen.json")).use {
                    Gson().toJson(config, it);
                }
            } catch(t: Throwable) {
                Log.error("Couldn't save config", t);
                error(request, "Couldn't save config", 400);
                File(appPath, "gwen.json").delete();
                oauth?.deleteCredentials()
            }
            gwen.stop();
            oauth?.deleteCredentials()
            oauth = loadOAuth(config!!);
            redirect(request, "/");
        } else {
            error(request, "Invalid client id & secret", 400);
        }
    }

    private fun handleAccountSave(request: HttpExchange) {
        val params = parseParams(request);
        val code = params["code"];

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
                gwen.start(config!!, oa);
                redirect(request, "/");
            } else {
                error(request, "Invalid code, authorization failed", 400);
            }
        } else {
            error(request, "Invalid code, authorization failed", 400);
        }
    }

    private fun handleModels(request: HttpExchange) {
        respond(request, Gson().toJson(gwen.models).toByteArray(), MIMETYPE_JSON);
    }

    class FileHandler : FormDataHandler() {
        var parts = emptyMap<String, MultiPart>()
        override fun handle(httpExchange: HttpExchange, parts: MutableList<MultiPart>) {
            val result = mutableMapOf<String, MultiPart>()
            for (part in parts) {
                result[part.name] = part;
            }
            this.parts = result;
        }
    }

    private fun handleModelSave(request: HttpExchange) {
        val fileHandler = FileHandler()
        fileHandler.handle(request)
        val parts = fileHandler.parts;

        if (parts["modelName"] == null || parts["modelType"] == null || parts["file"] == null) {
            Log.error("Couldn't save model, request incomplete");
            error(request, "Couldn't save model, request incomplete", 400);
        } else {
            try {
                gwen.addModel(parts["modelName"]!!.value, parts["file"]!!.filename, GwenModelType.valueOf(parts["modelType"]!!.value), parts["file"]!!.bytes);
                handleModels(request);
            } catch(t: Throwable) {
                Log.error("Couldn't save model", t);
                error(request, "Couldn't save model", 400);
            }
        }
    }

    private fun handleModelDelete(request: HttpExchange) {
        val params = parseParams(request);
        val modelName = params["name"];

        if (modelName != null) {
            try {
                gwen.deleteModel(modelName);
                handleModels(request);
            } catch(t: Throwable) {
                Log.error("Couldn't delete model $modelName", t);
                error(request, "Couldn't delete model", 400);
            }
        } else {
            error(request, "Couldn't delete model", 400);
        }
    }

    private fun handleAuthorizationUrl(request: HttpExchange) {
        respond(request, """{ "authorizationUrl": "${oauth?.getAuthorizationURL()}" }""".toByteArray(), MIMETYPE_JSON);
    }

    private fun  handleStatus(request: HttpExchange) {
        val params = parseParams(request);
        val timeStamp = System.currentTimeMillis();
        val requestTimeStamp = params["timeStamp"];
        val logs = Gson().toJson(logger.getSince(if (requestTimeStamp != null) requestTimeStamp.toLong() else 0));
        respond(request, """{ "status": ${gwen.running}, "log": ${logs}, "timeStamp": $timeStamp }""".toByteArray(), MIMETYPE_JSON);
    }

    private fun  handleGetConfig(request: HttpExchange) {
        val config = config;
        if (config == null) {
            error(request, "Gwen is not configured", 400);
        } else {
            respond(request, Gson().toJson(config).toByteArray(), MIMETYPE_JSON);
        }
    }

    private fun  handleSetConfig(request: HttpExchange) {
        val params = parseParams(request);
        val playAudioLocally = params["playAudioLocally"]?.toBoolean();
        val recordStereo = params["recordStereo"]?.toBoolean();
        val pubSubPort = params["pubSubPort"]?.toInt();

        if (playAudioLocally == null || recordStereo == null || pubSubPort == null) {
            error(request, "Invalid config", 400);
        } else {
            val config = config;
            if (config == null) {
                error(request, "Gwen is not configured", 400);
            } else {
                val newConfig = GwenConfig(config.clientId, config.clientSecret, playAudioLocally, recordStereo, pubSubPort);
                val oauth = oauth;
                if (oauth == null) {
                    error(request, "Gwen is not configured", 400);
                } else {
                    gwen.stop();
                    gwen.start(newConfig, oauth);
                    com.badlogicgames.gwen.config = newConfig;
                    FileWriter(File(appPath, "gwen.json")).use {
                        Gson().toJson(newConfig, it);
                    }
                    handleGetConfig(request);
                }
            }
        }
    }
}
