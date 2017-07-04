package com.badlogicgames.gwen;

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress

fun startWebInterface (port: Int = 8777) {
    val server = HttpServer.create(InetSocketAddress(port), 0);
    server.createContext("/", WebInterface());
    server.start();
}

val MIMETYPE_PLAINTEXT = "text/plain";
val MIMETYPE_HTML = "text/html";
val MIMETYPE_BINARY = "application/octet-stream"
val MIMETYPE_JSON = "application/json"
val MIMETYPE_PNG = "image/png"
val MIMETYPE_JPEG = "image/jpeg"
val MIMETYPE_GIF = "image/gif"

class WebInterface : HttpHandler {
    override fun handle(request: HttpExchange) {
        when (request.requestURI.path) {
            "/" -> respond(request, File("assets/web/index.html").readText().toByteArray(), MIMETYPE_HTML)
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
        var root = File("assets/web/").absolutePath;
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
                    else -> type = MIMETYPE_BINARY
                }
                respond(request, file.readBytes(), type);
            }
        }
    }
}
