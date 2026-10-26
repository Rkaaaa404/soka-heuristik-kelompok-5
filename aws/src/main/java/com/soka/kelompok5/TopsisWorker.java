package com.soka.kelompok5;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Standalone HTTP worker service for AWS TOPSIS workload execution.
 * Exposes /health (GET) and /execute (POST) authenticated via Bearer token.
 */
public final class TopsisWorker {
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    public record WorkerConfig(
            String datacenterId,
            String hostId,
            String vmId,
            int slots,
            String bind,
            int port,
            String token
    ) {}

    public record HealthResponse(
            String datacenterId,
            String hostId,
            String vmId,
            String hostname,
            int slots,
            int busySlots
    ) {}

    public record ExecuteRequest(
            int taskId,
            long iterations
    ) {}

    public record ExecuteResponse(
            int taskId,
            long iterations,
            String datacenterId,
            String hostId,
            String vmId,
            String hostname,
            double executionSeconds,
            Double cpuSeconds,
            String checksum
    ) {}

    public record RunningWorker(HttpServer server, ExecutorService executor) implements AutoCloseable {
        public int port() { return server.getAddress().getPort(); }
        @Override
        public void close() {
            server.stop(0);
            executor.shutdownNow();
        }
    }
    public static void main(String[] args) {
        String datacenterId = null;
        String hostId = null;
        String vmId = null;
        int slots = -1;
        String bind = "127.0.0.1";
        int port = 8080;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--datacenter-id" -> { if (++i < args.length) datacenterId = args[i]; }
                case "--host-id" -> { if (++i < args.length) hostId = args[i]; }
                case "--vm-id" -> { if (++i < args.length) vmId = args[i]; }
                case "--slots" -> { if (++i < args.length) slots = Integer.parseInt(args[i]); }
                case "--bind" -> { if (++i < args.length) bind = args[i]; }
                case "--port" -> { if (++i < args.length) port = Integer.parseInt(args[i]); }
                default -> {
                    System.err.println("Argumen tidak dikenali: " + args[i]);
                    printUsageAndExit();
                }
            }
        }

        if (datacenterId == null || datacenterId.isBlank()
                || hostId == null || hostId.isBlank()
                || vmId == null || vmId.isBlank()
                || slots <= 0) {
            System.err.println("Error: --datacenter-id, --host-id, --vm-id, dan --slots (>0) wajib diisi.");
            printUsageAndExit();
        }

        int availableCores = Runtime.getRuntime().availableProcessors();
        if (slots > availableCores) {
            System.err.println("Error: slots (" + slots + ") melebihi CPU core yang tersedia (" + availableCores + ").");
            System.exit(1);
        }

        String token = System.getenv("SOKA_WORKER_TOKEN");
        if (token == null || token.isBlank()) {
            System.err.println("Error: Variabel lingkungan SOKA_WORKER_TOKEN wajib disetel dan tidak boleh kosong.");
            System.exit(1);
        }

        WorkerConfig config = new WorkerConfig(datacenterId, hostId, vmId, slots, bind, port, token.trim());
        try {
            RunningWorker worker = startServer(config);
            System.out.println("WORKER_READY port=" + worker.port() + " vm=" + vmId);
            System.out.flush();
            Thread.currentThread().join();
        } catch (InterruptedException ignored) {
        } catch (IOException e) {
            System.err.println("Gagal menjalankan server HTTP worker: " + e.getMessage());
            System.exit(1);
        }
    }

    public static RunningWorker startServer(WorkerConfig config) throws IOException {
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            hostname = "unknown-host";
        }

        Semaphore slotSemaphore = new Semaphore(config.slots());
        ExecutorService executor = Executors.newFixedThreadPool(config.slots() + 2, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            return thread;
        });

        HttpServer server = HttpServer.create(new InetSocketAddress(config.bind(), config.port()), 0);
        server.setExecutor(executor);

        server.createContext("/health", new HealthHandler(config, hostname, slotSemaphore));
        server.createContext("/execute", new ExecuteHandler(config, hostname, slotSemaphore));

        server.start();

        RunningWorker runningWorker = new RunningWorker(server, executor);
        Runtime.getRuntime().addShutdownHook(new Thread(runningWorker::close));

        return runningWorker;
    }

    private static void printUsageAndExit() {
        System.err.println("Penggunaan: java TopsisWorker --datacenter-id <id> --host-id <id> --vm-id <id> --slots <n> [--bind <addr>] [--port <port>]");
        System.exit(1);
    }

    private static boolean authenticate(HttpExchange exchange, String expectedToken) {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return false;
        }
        String token = authHeader.substring("Bearer ".length()).trim();
        return expectedToken.equals(token);
    }

    private static void sendJsonResponse(HttpExchange exchange, int statusCode, Object body) throws IOException {
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        JsonObject err = new JsonObject();
        err.addProperty("error", message);
        sendJsonResponse(exchange, statusCode, err);
    }

    private static class HealthHandler implements HttpHandler {
        private final WorkerConfig config;
        private final String hostname;
        private final Semaphore slotSemaphore;

        HealthHandler(WorkerConfig config, String hostname, Semaphore slotSemaphore) {
            this.config = config;
            this.hostname = hostname;
            this.slotSemaphore = slotSemaphore;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method Not Allowed");
                return;
            }
            if (!authenticate(exchange, config.token())) {
                sendError(exchange, 401, "Unauthorized");
                return;
            }
            int busy = config.slots() - slotSemaphore.availablePermits();
            HealthResponse resp = new HealthResponse(
                    config.datacenterId(),
                    config.hostId(),
                    config.vmId(),
                    hostname,
                    config.slots(),
                    Math.max(0, busy)
            );
            sendJsonResponse(exchange, 200, resp);
        }
    }

    private static class ExecuteHandler implements HttpHandler {
        private final WorkerConfig config;
        private final String hostname;
        private final Semaphore slotSemaphore;
        private final ThreadMXBean threadMXBean;

        ExecuteHandler(WorkerConfig config, String hostname, Semaphore slotSemaphore) {
            this.config = config;
            this.hostname = hostname;
            this.slotSemaphore = slotSemaphore;
            this.threadMXBean = ManagementFactory.getThreadMXBean();
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method Not Allowed");
                return;
            }
            if (!authenticate(exchange, config.token())) {
                sendError(exchange, 401, "Unauthorized");
                return;
            }

            ExecuteRequest req;
            try (InputStream is = exchange.getRequestBody()) {
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                if (!json.has("taskId") || !json.has("iterations")) {
                    sendError(exchange, 400, "taskId dan iterations wajib disertakan");
                    return;
                }
                int taskId = json.get("taskId").getAsInt();
                long iterations = json.get("iterations").getAsLong();
                if (iterations <= 0) {
                    sendError(exchange, 400, "iterations harus bernilai positif");
                    return;
                }
                req = new ExecuteRequest(taskId, iterations);
            } catch (Exception e) {
                sendError(exchange, 400, "Format JSON tidak valid: " + e.getMessage());
                return;
            }

            if (!slotSemaphore.tryAcquire()) {
                sendError(exchange, 429, "Seluruh slot komputasi worker sedang penuh");
                return;
            }

            boolean released = false;
            try {
                long startNanos = System.nanoTime();
                boolean cpuSupported = threadMXBean.isCurrentThreadCpuTimeSupported() && threadMXBean.isThreadCpuTimeEnabled();
                long startCpuNanos = cpuSupported ? threadMXBean.getCurrentThreadCpuTime() : 0;

                long deadlineNanos = startNanos + TimeUnit.MILLISECONDS.toNanos(CpuWorkload.DEFAULT_DEADLINE_MS);
                String checksum;
                try {
                    checksum = CpuWorkload.run(req.iterations(), deadlineNanos);
                } catch (TimeoutException e) {
                    slotSemaphore.release();
                    released = true;
                    sendError(exchange, 408, e.getMessage());
                    return;
                }

                double executionSeconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
                Double cpuSeconds = null;
                if (cpuSupported) {
                    long cpuElapsed = threadMXBean.getCurrentThreadCpuTime() - startCpuNanos;
                    cpuSeconds = cpuElapsed >= 0 ? cpuElapsed / 1_000_000_000.0 : null;
                }

                ExecuteResponse resp = new ExecuteResponse(
                        req.taskId(),
                        req.iterations(),
                        config.datacenterId(),
                        config.hostId(),
                        config.vmId(),
                        hostname,
                        executionSeconds,
                        cpuSeconds,
                        checksum
                );

                slotSemaphore.release();
                released = true;

                sendJsonResponse(exchange, 200, resp);
                System.out.printf(Locale.US, "[WORKER] VM %s finished task %d: %d iter in %.4fs (checksum %s)%n",
                        config.vmId(), req.taskId(), req.iterations(), executionSeconds, checksum);
            } catch (Exception e) {
                if (!released) {
                    slotSemaphore.release();
                    released = true;
                }
                sendError(exchange, 500, "Kegagalan eksekusi worker: " + e.getMessage());
            } finally {
                if (!released) {
                    slotSemaphore.release();
                }
            }
        }
    }
}
