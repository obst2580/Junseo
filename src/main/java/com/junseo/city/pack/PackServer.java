package com.junseo.city.pack;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 리소스팩 zip 하나만 내려 주는 아주 작은 웹 서버.
 * 주소: http://(서버 주소):(포트)/(SHA-1).zip
 */
final class PackServer {
    private final HttpServer http;
    private final ExecutorService pool;

    PackServer(String bind, int port, byte[] zip, String sha1) throws IOException {
        InetSocketAddress address = bind == null || bind.isBlank() ? new InetSocketAddress(port) : new InetSocketAddress(bind, port);
        http = HttpServer.create(address, 32);
        pool = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "JunseoCity-ResourcePack");
            t.setDaemon(true);
            return t;
        });
        http.setExecutor(pool);
        String path = "/" + sha1 + ".zip";
        http.createContext("/", exchange -> handle(exchange, path, zip));
    }

    private static void handle(HttpExchange exchange, String path, byte[] zip) throws IOException {
        try (exchange) {
            String method = exchange.getRequestMethod();
            boolean head = "HEAD".equals(method);
            if (!exchange.getRequestURI().getPath().equals(path) || !(head || "GET".equals(method))) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", "application/zip");
            exchange.getResponseHeaders().set("Cache-Control", "public, max-age=31536000, immutable");
            if (head) {
                exchange.getResponseHeaders().set("Content-Length", String.valueOf(zip.length));
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            exchange.sendResponseHeaders(200, zip.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(zip);
            }
        }
    }

    void start() {
        http.start();
    }

    void stop() {
        http.stop(0);
        pool.shutdownNow();
    }

    int port() {
        return http.getAddress().getPort();
    }
}
