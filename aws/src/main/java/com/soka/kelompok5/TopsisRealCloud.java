package com.soka.kelompok5;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Production CLI and orchestration controller for AWS TOPSIS real-workload execution.
 */
public final class TopsisRealCloud {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration EXECUTE_TIMEOUT = Duration.ofSeconds(125);
    private static final long CALIBRATION_ITERATIONS = 200_000L;

    public record WorkerConfig(
            String hostId,
            String vmId,
            String endpoint,
            int slots,
            double modeledHourlyRateUsd
    ) {}

    public record ClusterConfig(
            String datacenterId,
            String region,
            String availabilityZone,
            List<WorkerConfig> workers
    ) {}

    public record HealthResult(
            WorkerConfig config,
            String hostname,
            int slots,
            int busySlots
    ) {}

    public static class TaskExecutionRecord {
        public final int taskId;
        public final double lengthMi;
        public final int sla;
        public final double originalArrival;
        public final double arrival;
        public final String hostId;
        public final String vmId;
        public final long iterations;
        public final double dispatch;
        public final double complete;
        public final double execution;
        public final Double cpu;
        public final String checksum;

        public TaskExecutionRecord(int taskId, double lengthMi, int sla, double originalArrival, double arrival,
                                   String hostId, String vmId, long iterations, double dispatch, double complete,
                                   double execution, Double cpu, String checksum) {
            this.taskId = taskId;
            this.lengthMi = lengthMi;
            this.sla = sla;
            this.originalArrival = originalArrival;
            this.arrival = arrival;
            this.hostId = hostId;
            this.vmId = vmId;
            this.iterations = iterations;
            this.dispatch = dispatch;
            this.complete = complete;
            this.execution = execution;
            this.cpu = cpu;
            this.checksum = checksum;
        }
    }

    public static void main(String[] args) {
        if (args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0])) {
            printUsage();
            return;
        }

        String command = args[0];
        try {
            switch (command) {
                case "preflight" -> handlePreflight(args);
                case "run" -> handleRun(args);
                default -> {
                    System.err.println("Perintah tidak dikenal: " + command);
                    printUsage();
                    System.exit(1);
                }
            }
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.out.println("Penggunaan CLI AWS TOPSIS:");
        System.out.println("  preflight --config <path-ke-json>");
        System.out.println("  run --config <path-ke-json> --dataset <path-ke-csv> --output <direktori-baru> " +
                "[--limit <N>] [--iterations-per-mi <double>] [--arrival-scale <double>]");
    }

    private static String requireToken() {
        String token = System.getenv("SOKA_WORKER_TOKEN");
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Variabel lingkungan SOKA_WORKER_TOKEN wajib disetel dan tidak boleh kosong.");
        }
        return token.trim();
    }

    public static ClusterConfig loadConfig(Path configPath) throws IOException {
        if (!Files.isRegularFile(configPath)) {
            throw new IllegalArgumentException("File konfigurasi tidak ditemukan: " + configPath);
        }
        String content = Files.readString(configPath, StandardCharsets.UTF_8);
        JsonObject json = JsonParser.parseString(content).getAsJsonObject();

        String datacenterId = json.has("datacenterId") ? json.get("datacenterId").getAsString() : null;
        String region = json.has("region") ? json.get("region").getAsString() : null;
        String availabilityZone = json.has("availabilityZone") ? json.get("availabilityZone").getAsString() : null;

        if (datacenterId == null || datacenterId.isBlank()
                || region == null || region.isBlank()
                || availabilityZone == null || availabilityZone.isBlank()) {
            throw new IllegalArgumentException("Konfigurasi wajib memiliki datacenterId, region, dan availabilityZone.");
        }

        if (!json.has("workers") || !json.get("workers").isJsonArray()) {
            throw new IllegalArgumentException("Konfigurasi wajib memiliki array 'workers'.");
        }

        JsonArray arr = json.getAsJsonArray("workers");
        List<WorkerConfig> workers = new ArrayList<>();
        Set<String> seenVmIds = new HashSet<>();
        Set<String> seenEndpoints = new HashSet<>();
        Map<String, Integer> workersPerHost = new HashMap<>();

        for (JsonElement el : arr) {
            JsonObject w = el.getAsJsonObject();
            String hostId = w.get("hostId").getAsString();
            String vmId = w.get("vmId").getAsString();
            String endpoint = w.get("endpoint").getAsString();
            int slots = w.get("slots").getAsInt();
            double rate = w.get("modeledHourlyRateUsd").getAsDouble();

            if (hostId == null || hostId.isBlank() || vmId == null || vmId.isBlank()) {
                throw new IllegalArgumentException("Setiap worker wajib memiliki hostId dan vmId.");
            }
            if (!seenVmIds.add(vmId)) {
                throw new IllegalArgumentException("vmId duplikat: " + vmId);
            }
            if (!seenEndpoints.add(endpoint)) {
                throw new IllegalArgumentException("endpoint duplikat: " + endpoint);
            }
            URI uri = URI.create(endpoint);
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException("Endpoint harus berupa URI HTTP/HTTPS yang valid: " + endpoint);
            }
            if (slots <= 0) {
                throw new IllegalArgumentException("Slots worker harus positif: " + slots + " pada VM " + vmId);
            }
            if (!Double.isFinite(rate) || rate <= 0) {
                throw new IllegalArgumentException("modeledHourlyRateUsd harus bernilai positif dan finite: " + rate);
            }

            workersPerHost.merge(hostId, 1, Integer::sum);
            workers.add(new WorkerConfig(hostId, vmId, endpoint, slots, rate));
        }

        if (workersPerHost.size() < 2) {
            throw new IllegalArgumentException("Konfigurasi wajib memiliki minimal 2 Host ID berbeda. Terdeteksi: " + workersPerHost.size());
        }
        for (Map.Entry<String, Integer> entry : workersPerHost.entrySet()) {
            if (entry.getValue() < 2) {
                throw new IllegalArgumentException("Host " + entry.getKey() + " memiliki " + entry.getValue() + " worker. Wajib minimal 2 worker per host.");
            }
        }

        return new ClusterConfig(datacenterId, region, availabilityZone, List.copyOf(workers));
    }

    private static void handlePreflight(String[] args) throws Exception {
        Path configPath = null;
        for (int i = 1; i < args.length; i++) {
            if ("--config".equals(args[i]) && i + 1 < args.length) {
                configPath = Path.of(args[++i]);
            }
        }
        if (configPath == null) {
            System.err.println("Parameter --config wajib diisi.");
            printUsage();
            System.exit(1);
        }

        String token = requireToken();
        ClusterConfig config = loadConfig(configPath);
        HttpClient client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();

        List<HealthResult> results = performPreflight(client, config, token);
        printPreflightTable(config, results);
        System.out.println("\n[PREFLIGHT] SUKSES: Semua " + results.size() + " worker aktif, terautentikasi, dan siap menerima tugas.");
    }

    public static List<HealthResult> performPreflight(HttpClient client, ClusterConfig config, String token) throws Exception {
        List<HealthResult> results = new ArrayList<>();
        for (WorkerConfig worker : config.workers()) {
            URI healthUri = URI.create(worker.endpoint() + "/health");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(healthUri)
                    .timeout(HEALTH_TIMEOUT)
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();

            HttpResponse<String> response;
            try {
                response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (Exception e) {
                throw new IllegalStateException("Gagal menghubungi worker " + worker.vmId() + " (" + worker.endpoint() + "): " + e.getMessage(), e);
            }

            if (response.statusCode() != 200) {
                throw new IllegalStateException("Worker " + worker.vmId() + " mengembalikan HTTP status " + response.statusCode() + ": " + response.body());
            }

            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            String dc = json.get("datacenterId").getAsString();
            String host = json.get("hostId").getAsString();
            String vm = json.get("vmId").getAsString();
            String hostname = json.get("hostname").getAsString();
            int slots = json.get("slots").getAsInt();
            int busySlots = json.get("busySlots").getAsInt();

            if (!config.datacenterId().equals(dc)) {
                throw new IllegalStateException("Mismatch datacenterId pada worker " + worker.vmId() + ": diharapkan " + config.datacenterId() + ", didapat " + dc);
            }
            if (!worker.hostId().equals(host)) {
                throw new IllegalStateException("Mismatch hostId pada worker " + worker.vmId() + ": diharapkan " + worker.hostId() + ", didapat " + host);
            }
            if (!worker.vmId().equals(vm)) {
                throw new IllegalStateException("Mismatch vmId pada worker " + worker.vmId() + ": diharapkan " + worker.vmId() + ", didapat " + vm);
            }
            if (worker.slots() != slots) {
                throw new IllegalStateException("Mismatch slots pada worker " + worker.vmId() + ": diharapkan " + worker.slots() + ", didapat " + slots);
            }
            if (busySlots != 0) {
                throw new IllegalStateException("Worker " + worker.vmId() + " sedang sibuk dengan " + busySlots + " task aktif.");
            }

            results.add(new HealthResult(worker, hostname, slots, busySlots));
        }
        return results;
    }

    private static void printPreflightTable(ClusterConfig config, List<HealthResult> results) {
        System.out.println("\n=== STATUS PREFLIGHT WORKER AWS TOPSIS ===");
        System.out.printf(Locale.US, "Datacenter ID: %s | Region: %s | AZ: %s%n", config.datacenterId(), config.region(), config.availabilityZone());
        System.out.println("+---------------+--------------+----------------------+-------+-------------+----------------------+");
        System.out.println("| Host ID       | VM ID        | Guest Hostname       | Slots | Hourly Rate | Endpoint             |");
        System.out.println("+---------------+--------------+----------------------+-------+-------------+----------------------+");
        for (HealthResult r : results) {
            System.out.printf(Locale.US, "| %-13s | %-12s | %-20s | %-5d | $%-10.2f | %-20s |%n",
                    r.config().hostId(), r.config().vmId(), r.hostname(), r.slots(), r.config().modeledHourlyRateUsd(), r.config().endpoint());
        }
        System.out.println("+---------------+--------------+----------------------+-------+-------------+----------------------+");
    }

    private static void handleRun(String[] args) throws Exception {
        Path configPath = null;
        Path datasetPath = null;
        Path outputPath = null;
        Integer limit = null;
        double iterationsPerMi = 10.0;
        double arrivalScale = 1.0;

        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--config" -> { if (++i < args.length) configPath = Path.of(args[i]); }
                case "--dataset" -> { if (++i < args.length) datasetPath = Path.of(args[i]); }
                case "--output" -> { if (++i < args.length) outputPath = Path.of(args[i]); }
                case "--limit" -> { if (++i < args.length) limit = Integer.parseInt(args[i]); }
                case "--iterations-per-mi" -> { if (++i < args.length) iterationsPerMi = Double.parseDouble(args[i]); }
                case "--arrival-scale" -> { if (++i < args.length) arrivalScale = Double.parseDouble(args[i]); }
                default -> throw new IllegalArgumentException("Argumen run tidak valid: " + args[i]);
            }
        }

        if (configPath == null || datasetPath == null || outputPath == null) {
            throw new IllegalArgumentException("Parameter --config, --dataset, dan --output wajib disertakan.");
        }

        String token = requireToken();
        executeRun(configPath, datasetPath, outputPath, limit, iterationsPerMi, arrivalScale, token);
    }

    public static void executeRun(Path configPath, Path datasetPath, Path outputPath, Integer limit,
                                  double iterationsPerMi, double arrivalScale, String token) throws Exception {
        if (!Double.isFinite(iterationsPerMi) || iterationsPerMi <= 0) {
            throw new IllegalArgumentException("iterations-per-mi harus bernilai positif dan finite: " + iterationsPerMi);
        }
        if (!Double.isFinite(arrivalScale) || arrivalScale <= 0) {
            throw new IllegalArgumentException("arrival-scale harus bernilai positif dan finite: " + arrivalScale);
        }

        // Rule: Create --output only if it does not exist. Refuse existing directories.
        if (Files.exists(outputPath)) {
            throw new IllegalArgumentException("Direktori output sudah ada: " + outputPath + ". Harap gunakan direktori baru.");
        }

        ClusterConfig config = loadConfig(configPath);

        // Read and validate full CSV first
        List<GocjWorkload.TaskItem> allTasks = GocjWorkload.loadCpu(datasetPath);
        if (limit != null) {
            if (limit <= 0 || limit > allTasks.size()) {
                throw new IllegalArgumentException("Limit (" + limit + ") harus antara 1 dan " + allTasks.size());
            }
            allTasks = allTasks.subList(0, limit);
        }

        // Validate workload iterations and arrival scaling
        List<GocjWorkload.TaskItem> scaledTasks = new ArrayList<>(allTasks.size());
        for (GocjWorkload.TaskItem orig : allTasks) {
            double iterDouble = Math.ceil(orig.lengthMi * iterationsPerMi);
            if (!Double.isFinite(iterDouble) || iterDouble <= 0 || iterDouble > Long.MAX_VALUE / 2.0) {
                throw new IllegalArgumentException("Perhitungan iterasi tidak valid atau overflow untuk task ID " + orig.id);
            }
            long iter = (long) iterDouble;

            double scaledArrival = orig.arrivalTime * arrivalScale;
            if (!Double.isFinite(scaledArrival) || scaledArrival < 0) {
                throw new IllegalArgumentException("Skala arrival tidak valid untuk task ID " + orig.id);
            }
            scaledTasks.add(new GocjWorkload.TaskItem(orig.id, (double) iter, orig.sla, scaledArrival, 0.0));
        }

        // Prepare output directory
        Files.createDirectories(outputPath);

        HttpClient client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();

        // Step 1: Preflight
        System.out.println("[CONTROLLER] Memverifikasi status kesehatan worker...");
        List<HealthResult> healthResults = performPreflight(client, config, token);
        printPreflightTable(config, healthResults);

        // Step 2: Calibrate
        System.out.println("\n[CONTROLLER] Mengkalibrasi throughput SHA-256 worker secara konkuren...");
        double[] measuredIps = calibrateCluster(client, config, token);
        for (int i = 0; i < config.workers().size(); i++) {
            WorkerConfig w = config.workers().get(i);
            System.out.printf(Locale.US, "  - VM %-12s: %.2f iterasi/detik (median kalibrasi)%n", w.vmId(), measuredIps[i]);
        }

        // Step 3: Schedule with TOPSIS
        System.out.println("\n[CONTROLLER] Menjadwalkan " + scaledTasks.size() + " task dengan Multi-Kriteria TOPSIS...");
        List<TopsisScheduler.Resource> resources = new ArrayList<>();
        for (WorkerConfig w : config.workers()) {
            resources.add(new TopsisScheduler.Resource(w.slots(), w.modeledHourlyRateUsd()));
        }

        long schedStart = System.nanoTime();
        int[] assignments = TopsisScheduler.schedule(
                scaledTasks,
                resources,
                (task, resIdx) -> task.lengthMi / measuredIps[resIdx],
                0.5,
                0.5
        );
        double schedulerRuntimeMs = (System.nanoTime() - schedStart) / 1_000_000.0;
        System.out.printf(Locale.US, "[CONTROLLER] Penjadwalan selesai dalam %.4f ms.%n", schedulerRuntimeMs);

        // Step 4: Dispatch Workload
        System.out.println("\n[CONTROLLER] Memulai dispatch workload real ke worker...");
        executeWorkload(client, config, token, allTasks, scaledTasks, assignments, measuredIps,
                datasetPath.toString(), outputPath, iterationsPerMi, arrivalScale, schedulerRuntimeMs, healthResults);
    }

    public static double[] calibrateCluster(HttpClient client, ClusterConfig config, String token) throws Exception {
        int numWorkers = config.workers().size();
        List<List<Double>> workerSamples = new ArrayList<>(numWorkers);
        for (int i = 0; i < numWorkers; i++) workerSamples.add(new ArrayList<>());

        // 1 discarded warm-up wave
        runCalibrationWave(client, config, token, null);

        // 3 measured waves
        for (int wave = 1; wave <= 3; wave++) {
            runCalibrationWave(client, config, token, workerSamples);
        }

        double[] medians = new double[numWorkers];
        for (int i = 0; i < numWorkers; i++) {
            List<Double> samples = workerSamples.get(i);
            if (samples.isEmpty()) throw new IllegalStateException("Tidak ada data kalibrasi untuk worker " + config.workers().get(i).vmId());
            Collections.sort(samples);
            int size = samples.size();
            double median = (size % 2 == 1) ? samples.get(size / 2) : (samples.get(size / 2 - 1) + samples.get(size / 2)) / 2.0;
            if (!Double.isFinite(median) || median <= 0) {
                throw new IllegalStateException("Throughput kalibrasi tidak valid untuk VM " + config.workers().get(i).vmId() + ": " + median);
            }
            medians[i] = median;
        }

        return medians;
    }

    private static void runCalibrationWave(HttpClient client, ClusterConfig config, String token, List<List<Double>> workerSamples) throws Exception {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        int numWorkers = config.workers().size();

        for (int i = 0; i < numWorkers; i++) {
            final int workerIndex = i;
            WorkerConfig worker = config.workers().get(i);
            for (int slot = 0; slot < worker.slots(); slot++) {
                CompletableFuture<Void> fut = CompletableFuture.runAsync(() -> {
                    try {
                        JsonObject payload = new JsonObject();
                        payload.addProperty("taskId", -1);
                        payload.addProperty("iterations", CALIBRATION_ITERATIONS);

                        HttpRequest req = HttpRequest.newBuilder()
                                .uri(URI.create(worker.endpoint() + "/execute"))
                                .timeout(EXECUTE_TIMEOUT)
                                .header("Authorization", "Bearer " + token)
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload)))
                                .build();

                        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                        if (resp.statusCode() != 200) {
                            throw new IllegalStateException("Gagal kalibrasi pada VM " + worker.vmId() + ": HTTP " + resp.statusCode());
                        }

                        JsonObject resJson = JsonParser.parseString(resp.body()).getAsJsonObject();
                        double execSec = resJson.get("executionSeconds").getAsDouble();
                        if (execSec <= 0 || !Double.isFinite(execSec)) {
                            throw new IllegalStateException("Durasi kalibrasi tidak valid: " + execSec);
                        }
                        if (workerSamples != null) {
                            double ips = CALIBRATION_ITERATIONS / execSec;
                            synchronized (workerSamples.get(workerIndex)) {
                                workerSamples.get(workerIndex).add(ips);
                            }
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
                futures.add(fut);
            }
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    private static void executeWorkload(
            HttpClient client,
            ClusterConfig config,
            String token,
            List<GocjWorkload.TaskItem> originalTasks,
            List<GocjWorkload.TaskItem> scaledTasks,
            int[] assignments,
            double[] measuredIps,
            String datasetName,
            Path outputDir,
            double iterationsPerMi,
            double arrivalScale,
            double schedulerRuntimeMs,
            List<HealthResult> healthResults
    ) throws Exception {
        int numWorkers = config.workers().size();
        ExecutorService[] workerExecutors = new ExecutorService[numWorkers];
        for (int i = 0; i < numWorkers; i++) {
            workerExecutors[i] = Executors.newFixedThreadPool(config.workers().get(i).slots());
        }

        AtomicBoolean aborted = new AtomicBoolean(false);
        CopyOnWriteArrayList<TaskExecutionRecord> records = new CopyOnWriteArrayList<>();
        List<CompletableFuture<Void>> taskFutures = new ArrayList<>();

        long epochNanos = System.nanoTime();
        Exception failureException = null;

        try {
            for (int i = 0; i < scaledTasks.size(); i++) {
                if (aborted.get()) break;

                final GocjWorkload.TaskItem scaled = scaledTasks.get(i);
                final GocjWorkload.TaskItem original = originalTasks.get(i);
                final int assignedWorkerIndex = assignments[i];
                final WorkerConfig targetWorker = config.workers().get(assignedWorkerIndex);

                // Delay until target scaled arrival relative to workload epoch
                long targetNanos = epochNanos + (long) (scaled.arrivalTime * 1_000_000_000L);
                long delayNanos = targetNanos - System.nanoTime();
                if (delayNanos > 0) {
                    long delayMillis = delayNanos / 1_000_000L;
                    int delayNanosPart = (int) (delayNanos % 1_000_000L);
                    Thread.sleep(delayMillis, delayNanosPart);
                }

                if (aborted.get()) break;

                CompletableFuture<Void> fut = CompletableFuture.runAsync(() -> {
                    if (aborted.get()) return;

                    long dispatchNanos = System.nanoTime();
                    double dispatchSec = (dispatchNanos - epochNanos) / 1_000_000_000.0;

                    try {
                        long iterations = (long) scaled.lengthMi;
                        JsonObject payload = new JsonObject();
                        payload.addProperty("taskId", scaled.id);
                        payload.addProperty("iterations", iterations);

                        HttpRequest req = HttpRequest.newBuilder()
                                .uri(URI.create(targetWorker.endpoint() + "/execute"))
                                .timeout(EXECUTE_TIMEOUT)
                                .header("Authorization", "Bearer " + token)
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload)))
                                .build();

                        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                        long completeNanos = System.nanoTime();
                        double completeSec = (completeNanos - epochNanos) / 1_000_000_000.0;

                        if (resp.statusCode() != 200) {
                            aborted.set(true);
                            throw new IllegalStateException("Worker " + targetWorker.vmId() + " gagal memproses task " + scaled.id
                                    + ": HTTP " + resp.statusCode() + " " + resp.body());
                        }

                        JsonObject resJson = JsonParser.parseString(resp.body()).getAsJsonObject();
                        int resTaskId = resJson.get("taskId").getAsInt();
                        long resIter = resJson.get("iterations").getAsLong();
                        String resDc = resJson.get("datacenterId").getAsString();
                        String resHost = resJson.get("hostId").getAsString();
                        String resVm = resJson.get("vmId").getAsString();
                        double resExecSec = resJson.get("executionSeconds").getAsDouble();
                        Double resCpuSec = resJson.has("cpuSeconds") && !resJson.get("cpuSeconds").isJsonNull()
                                ? resJson.get("cpuSeconds").getAsDouble() : null;
                        String resChecksum = resJson.get("checksum").getAsString();

                        if (resTaskId != scaled.id || resIter != iterations
                                || !config.datacenterId().equals(resDc)
                                || !targetWorker.hostId().equals(resHost)
                                || !targetWorker.vmId().equals(resVm)
                                || resExecSec <= 0 || !Double.isFinite(resExecSec)
                                || resChecksum == null || resChecksum.length() != 64) {
                            aborted.set(true);
                            throw new IllegalStateException("Validasi response tidak cocok untuk task ID " + scaled.id);
                        }

                        records.add(new TaskExecutionRecord(
                                original.id, original.lengthMi, original.sla, original.arrivalTime, scaled.arrivalTime,
                                targetWorker.hostId(), targetWorker.vmId(), iterations,
                                dispatchSec, completeSec, resExecSec, resCpuSec, resChecksum
                        ));
                    } catch (Exception e) {
                        aborted.set(true);
                        throw new RuntimeException("Kegagalan pada task " + scaled.id + ": " + e.getMessage(), e);
                    }
                }, workerExecutors[assignedWorkerIndex]);

                taskFutures.add(fut);
            }

            // Wait for all tasks
            CompletableFuture.allOf(taskFutures.toArray(new CompletableFuture[0])).get();
        } catch (Exception e) {
            failureException = e;
            aborted.set(true);
        } finally {
            for (ExecutorService es : workerExecutors) {
                es.shutdownNow();
            }
        }

        // Sort records by original task arrival, then id
        List<TaskExecutionRecord> sortedRecords = new ArrayList<>(records);
        sortedRecords.sort(Comparator.comparingDouble((TaskExecutionRecord r) -> r.arrival).thenComparingInt(r -> r.taskId));

        if (failureException != null || sortedRecords.size() != scaledTasks.size()) {
            writeOutputs(outputDir, config, healthResults, measuredIps, sortedRecords, datasetName,
                    scaledTasks.size(), iterationsPerMi, arrivalScale, schedulerRuntimeMs,
                    failureException != null ? failureException.getMessage() : "Jumlah task tidak lengkap", false);
            throw new IllegalStateException("Workload eksekusi riil GAGAL. Bukti parsial disimpan di " + outputDir
                    + ". Error: " + (failureException != null ? failureException.getMessage() : "Task belum lengkap"));
        }

        writeOutputs(outputDir, config, healthResults, measuredIps, sortedRecords, datasetName,
                scaledTasks.size(), iterationsPerMi, arrivalScale, schedulerRuntimeMs, null, true);

        System.out.println("\n[CONTROLLER] SUKSES: Semua " + sortedRecords.size() + " task selesai dieksekusi.");
        System.out.println("[CONTROLLER] Hasil disimpan di direktori: " + outputDir.toAbsolutePath().normalize());
    }

    private static void writeOutputs(
            Path outputDir,
            ClusterConfig config,
            List<HealthResult> healthResults,
            double[] measuredIps,
            List<TaskExecutionRecord> records,
            String datasetName,
            int tasksRequested,
            double iterationsPerMi,
            double arrivalScale,
            double schedulerRuntimeMs,
            String errorMessage,
            boolean success
    ) throws IOException {
        // 1. real_tasks.csv
        Path tasksCsv = outputDir.resolve("real_tasks.csv");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(tasksCsv, StandardCharsets.UTF_8))) {
            pw.println("TaskId,Length_MI,SLA,OriginalArrival_s,Arrival_s,HostId,VmId,Iterations,Dispatch_s,Complete_s,Execution_s,Cpu_s,Checksum");
            for (TaskExecutionRecord r : records) {
                pw.printf(Locale.US, "%d,%.4f,%d,%.4f,%.4f,%s,%s,%d,%.6f,%.6f,%.6f,%s,%s%n",
                        r.taskId, r.lengthMi, r.sla, r.originalArrival, r.arrival, r.hostId, r.vmId,
                        r.iterations, r.dispatch, r.complete, r.execution,
                        r.cpu != null ? String.format(Locale.US, "%.6f", r.cpu) : "",
                        r.checksum);
            }
        }

        // Calculate per-VM metrics
        Map<String, Double> vmBusyMap = new HashMap<>();
        Map<String, Integer> vmTaskCountMap = new HashMap<>();
        for (WorkerConfig w : config.workers()) {
            vmBusyMap.put(w.vmId(), 0.0);
            vmTaskCountMap.put(w.vmId(), 0);
        }

        double maxCompleteSec = 0.0;
        for (TaskExecutionRecord r : records) {
            vmBusyMap.merge(r.vmId, r.execution, Double::sum);
            vmTaskCountMap.merge(r.vmId, 1, Integer::sum);
            if (r.complete > maxCompleteSec) {
                maxCompleteSec = r.complete;
            }
        }

        // 2. real_vm_allocation.csv
        Path vmAllocCsv = outputDir.resolve("real_vm_allocation.csv");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(vmAllocCsv, StandardCharsets.UTF_8))) {
            pw.println("DatacenterId,HostId,VmId,WorkerHostname,Slots,ModeledHourlyRate_USD,MeasuredIterationsPerSecond,BusyTime_s,TaskCount");
            for (int i = 0; i < config.workers().size(); i++) {
                WorkerConfig w = config.workers().get(i);
                String hostname = (healthResults != null && i < healthResults.size()) ? healthResults.get(i).hostname() : "unknown";
                double ips = (measuredIps != null && i < measuredIps.length) ? measuredIps[i] : 0.0;
                double busy = vmBusyMap.getOrDefault(w.vmId(), 0.0);
                int count = vmTaskCountMap.getOrDefault(w.vmId(), 0);
                pw.printf(Locale.US, "%s,%s,%s,%s,%d,%.4f,%s,%.6f,%d%n",
                        config.datacenterId(), w.hostId(), w.vmId(), hostname, w.slots(),
                        w.modeledHourlyRateUsd(),
                        ips > 0 ? String.format(Locale.US, "%.2f", ips) : "",
                        busy, count);
            }
        }

        // Aggregate metrics
        int numWorkers = config.workers().size();
        double sumBusy = 0.0;
        double minBusy = Double.MAX_VALUE;
        double maxBusy = 0.0;
        double totalCost = 0.0;

        for (WorkerConfig w : config.workers()) {
            double busy = vmBusyMap.getOrDefault(w.vmId(), 0.0);
            sumBusy += busy;
            if (busy < minBusy) minBusy = busy;
            if (busy > maxBusy) maxBusy = busy;
            totalCost += Math.ceil(busy / 3600.0) * w.modeledHourlyRateUsd();
        }

        double meanBusy = numWorkers > 0 ? sumBusy / numWorkers : 0.0;
        double degreeOfImbalance = meanBusy > 0 ? (maxBusy - minBusy) / meanBusy : 0.0;

        // 3. real_summary.json
        Path summaryJson = outputDir.resolve("real_summary.json");
        JsonObject summary = new JsonObject();
        summary.addProperty("status", success ? "SUCCESS" : "FAILED");
        summary.addProperty("mode", "real");
        summary.addProperty("dataset", datasetName);
        summary.addProperty("tasksRequested", tasksRequested);
        summary.addProperty("tasksCompleted", records.size());
        summary.addProperty("iterationsPerMi", iterationsPerMi);
        summary.addProperty("arrivalScale", arrivalScale);

        if (success) {
            summary.addProperty("makespanSeconds", maxCompleteSec);
            summary.addProperty("modeledCostUsd", totalCost);
            summary.addProperty("degreeOfImbalance", degreeOfImbalance);
            summary.addProperty("schedulerRuntimeMs", schedulerRuntimeMs);
        } else {
            summary.add("makespanSeconds", null);
            summary.add("modeledCostUsd", null);
            summary.add("degreeOfImbalance", null);
            summary.add("schedulerRuntimeMs", null);
            summary.addProperty("error", errorMessage != null ? errorMessage : "Eksekusi gagal");
        }

        Files.writeString(summaryJson, GSON.toJson(summary), StandardCharsets.UTF_8);

        if (success) {
            printExecutionSummary(records.size(), maxCompleteSec, totalCost, degreeOfImbalance, schedulerRuntimeMs);
        }
    }

    private static void printExecutionSummary(int count, double makespan, double cost, double di, double runtimeMs) {
        System.out.println("\n+-------------------------------------------------------------+");
        System.out.println("|            RINGKASAN EKSEKUSI REAL WORKLOAD AWS             |");
        System.out.println("+-------------------------------------------------------------+");
        System.out.printf(Locale.US, "| Tasks Completed      : %-36d |%n", count);
        System.out.printf(Locale.US, "| Makespan             : %-33.4f s |%n", makespan);
        System.out.printf(Locale.US, "| Modeled Total Cost   : $%-35.4f |%n", cost);
        System.out.printf(Locale.US, "| Degree of Imbalance  : %-36.6f |%n", di);
        System.out.printf(Locale.US, "| Scheduler Runtime    : %-32.4f ms |%n", runtimeMs);
        System.out.println("+-------------------------------------------------------------+");
    }
}
