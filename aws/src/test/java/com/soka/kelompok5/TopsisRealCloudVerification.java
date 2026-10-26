package com.soka.kelompok5;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
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
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Executable verification suite for AWS TOPSIS real-workload deployment.
 */
public final class TopsisRealCloudVerification {
    private static final Gson GSON = new Gson();

    public static void main(String[] args) throws Exception {
        System.out.println("--- 1. Verifikasi SHA-256 Digest ---");
        verifySha256Digest();

        System.out.println("--- 2. Verifikasi Validasi Input & CSV ---");
        verifyInputAndCsvValidation();

        System.out.println("--- 3. Verifikasi TOPSIS Core Scheduler ---");
        verifyTopsisScheduler();

        System.out.println("--- 4. Verifikasi Worker HTTP (Auth, Payload, 429 Saturation) ---");
        verifyWorkerHttpBehavior();

        System.out.println("--- 5. Verifikasi Penolakan Preflight (Mismatch & Unreachable) ---");
        verifyPreflightRejections();

        System.out.println("--- 6. Verifikasi End-to-End Loopback 4-Worker Workload ---");
        verifyEndToEndLoopbackWorkload();

        System.out.println("\n=======================================================");
        System.out.println("PASS: Semua pengujian verifikasi real cloud berhasil!");
        System.out.println("=======================================================");
        System.exit(0);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void expectFailure(Class<? extends Throwable> expected, Runnable action) {
        try {
            action.run();
        } catch (Throwable error) {
            Throwable curr = error;
            while (curr != null) {
                if (expected.isInstance(curr)) return;
                curr = curr.getCause();
            }
            throw new AssertionError("Diharapkan " + expected.getSimpleName() + ", didapat " + error, error);
        }
        throw new AssertionError("Diharapkan exception: " + expected.getSimpleName());
    }

    private static void verifySha256Digest() {
        String hash1 = CpuWorkload.run(1);
        check("66687aadf862bd776c8fc18b8e9f8e20089714856ee233b3902a591d0d5f2925".equals(hash1),
                "Hash 1 iterasi tidak sesuai: " + hash1);

        String hash2 = CpuWorkload.run(2);
        check("2b32db6c2c0a6235fb1397e8225ea85e0f0e6e8c7b126d0016ccbde0e667151e".equals(hash2),
                "Hash 2 iterasi tidak sesuai: " + hash2);

        expectFailure(IllegalArgumentException.class, () -> CpuWorkload.run(0));
        expectFailure(IllegalArgumentException.class, () -> CpuWorkload.run(-10));
    }

    private static void verifyInputAndCsvValidation() throws Exception {
        Path tempDir = Files.createTempDirectory("test-csv-val-");
        try {
            Path validCsv = tempDir.resolve("valid.csv");
            Files.writeString(validCsv, "25000,3,10\n15000,1,0\n20000,2,10\n");

            List<GocjWorkload.TaskItem> tasks = GocjWorkload.loadCpu(validCsv);
            check(tasks.size() == 3, "Harus memuat 3 task");
            check(tasks.get(0).id == 1 && tasks.get(1).id == 0 && tasks.get(2).id == 2,
                    "Urutan task harus berdasarkan arrival kemudian ID");
            for (GocjWorkload.TaskItem t : tasks) {
                check(t.ioBytes == 0.0, "Workload CPU real tidak boleh memiliki synthetic I/O bytes");
            }

            Path malformedAtEnd = tempDir.resolve("malformed_end.csv");
            Files.writeString(malformedAtEnd, "25000,3,10\n15000,1,0\nbad_row,not_num\n");
            // Harus gagal meskipun limit = 1 karena seluruh file dibaca dan divalidasi terlebih dahulu
            expectFailure(IllegalArgumentException.class, () -> GocjWorkload.loadCpu(malformedAtEnd));

            Path emptyCsv = tempDir.resolve("empty.csv");
            Files.writeString(emptyCsv, "");
            expectFailure(IllegalArgumentException.class, () -> GocjWorkload.loadCpu(emptyCsv));

            expectFailure(UncheckedIOException.class, () -> GocjWorkload.loadCpu(tempDir.resolve("non_existent.csv")));
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private static void verifyTopsisScheduler() {
        List<TopsisScheduler.Resource> resources = List.of(
                new TopsisScheduler.Resource(2, 0.12), // Medium: 2 slots
                new TopsisScheduler.Resource(1, 0.05)  // Small: 1 slot
        );

        List<GocjWorkload.TaskItem> tasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            tasks.add(new GocjWorkload.TaskItem(i, 250_000, 2, 0, 0));
        }

        // Estimator durasi: resource 0 butuh 100s, resource 1 butuh 250s
        TopsisScheduler.DurationEstimator estimator = (task, idx) -> idx == 0 ? 100.0 : 250.0;

        int[] assignments = TopsisScheduler.schedule(tasks, resources, estimator, 1.0, 0.0);
        check(Arrays.equals(assignments, new int[]{0, 0, 0, 0, 1}),
                "Medium VM (2 slot) harus menjadwalkan 2 tugas paralel sebelum antrean memprediksi durasi lebih lama: "
                        + Arrays.toString(assignments));

        // Reversal input check
        List<GocjWorkload.TaskItem> reversed = new ArrayList<>(tasks);
        java.util.Collections.reverse(reversed);
        int[] revAssignments = TopsisScheduler.schedule(reversed, resources, estimator, 1.0, 0.0);
        check(Arrays.equals(revAssignments, new int[]{1, 0, 0, 0, 0}),
                "Penjadwalan pada input terbalik harus konsisten: " + Arrays.toString(revAssignments));

        // Identical resources distribution
        List<TopsisScheduler.Resource> identical = List.of(
                new TopsisScheduler.Resource(1, 0.05),
                new TopsisScheduler.Resource(1, 0.05)
        );
        List<GocjWorkload.TaskItem> spaced = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            spaced.add(new GocjWorkload.TaskItem(i, 100_000, 2, i * 1000, 0));
        }
        int[] spacedAssignments = TopsisScheduler.schedule(spaced, identical, (t, idx) -> 100.0, 0.5, 0.5);
        check(Arrays.equals(spacedAssignments, new int[]{0, 1, 0, 1}),
                "Tugas dengan skor seimbang harus terdistribusi merata: " + Arrays.toString(spacedAssignments));
    }

    private static void verifyWorkerHttpBehavior() throws Exception {
        String token = "test-secret-token-" + UUID.randomUUID();
        TopsisWorker.WorkerConfig config = new TopsisWorker.WorkerConfig(
                "test-dc", "test-host", "test-vm", 2, "127.0.0.1", 0, token
        );

        TopsisWorker.RunningWorker worker = TopsisWorker.startServer(config);
        int port = worker.port();
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        String baseUri = "http://127.0.0.1:" + port;
        List<CompletableFuture<Void>> saturatingRequests = new ArrayList<>();
        try {
            // 1. Auth check
            HttpRequest reqNoAuth = HttpRequest.newBuilder().uri(URI.create(baseUri + "/health")).GET().build();
            HttpResponse<String> respNoAuth = client.send(reqNoAuth, HttpResponse.BodyHandlers.ofString());
            check(respNoAuth.statusCode() == 401, "Harus mengembalikan 401 jika tanpa header Auth");

            HttpRequest reqWrongAuth = HttpRequest.newBuilder().uri(URI.create(baseUri + "/health"))
                    .header("Authorization", "Bearer wrong-token").GET().build();
            HttpResponse<String> respWrongAuth = client.send(reqWrongAuth, HttpResponse.BodyHandlers.ofString());
            check(respWrongAuth.statusCode() == 401, "Harus mengembalikan 401 jika token salah");

            HttpRequest reqHealth = HttpRequest.newBuilder().uri(URI.create(baseUri + "/health"))
                    .header("Authorization", "Bearer " + token).GET().build();
            HttpResponse<String> respHealth = client.send(reqHealth, HttpResponse.BodyHandlers.ofString());
            check(respHealth.statusCode() == 200, "Health check terautentikasi harus 200");
            JsonObject healthJson = JsonParser.parseString(respHealth.body()).getAsJsonObject();
            check(healthJson.get("busySlots").getAsInt() == 0, "Initial busySlots harus 0");

            // 2. Invalid Payload check
            HttpRequest reqBadPayload = HttpRequest.newBuilder().uri(URI.create(baseUri + "/execute"))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"taskId\": 1, \"iterations\": -50}"))
                    .build();
            HttpResponse<String> respBadPayload = client.send(reqBadPayload, HttpResponse.BodyHandlers.ofString());
            check(respBadPayload.statusCode() == 400, "Payload negatif harus mengembalikan 400");

            // 3. Saturation check (2 slots)
            for (int i = 0; i < 2; i++) {
                final int id = 900 + i;
                CompletableFuture<Void> fut = CompletableFuture.runAsync(() -> {
                    try {
                        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(baseUri + "/execute"))
                                .header("Authorization", "Bearer " + token)
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString("{\"taskId\": " + id + ", \"iterations\": " + Long.MAX_VALUE + "}"))
                                .build();
                        client.send(req, HttpResponse.BodyHandlers.ofString());
                    } catch (Exception ignored) {}
                });
                saturatingRequests.add(fut);
            }

            // Tunggu hingga /health melaporkan busySlots == 2
            boolean saturated = false;
            for (int attempt = 0; attempt < 50; attempt++) {
                HttpResponse<String> h = client.send(reqHealth, HttpResponse.BodyHandlers.ofString());
                JsonObject j = JsonParser.parseString(h.body()).getAsJsonObject();
                if (j.get("busySlots").getAsInt() == 2) {
                    saturated = true;
                    break;
                }
                Thread.sleep(50);
            }
            check(saturated, "Worker harus melaporkan 2 slot sibuk");

            // Request ke-3 harus segera ditolak dengan 429
            HttpRequest reqThird = HttpRequest.newBuilder().uri(URI.create(baseUri + "/execute"))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"taskId\": 999, \"iterations\": 1000}"))
                    .build();
            HttpResponse<String> respThird = client.send(reqThird, HttpResponse.BodyHandlers.ofString());
            check(respThird.statusCode() == 429, "Request melebihi slot harus mengembalikan 429 Too Many Requests");

            // Health check tetap harus dapat diakses meskipun compute slots penuh
            HttpResponse<String> respHealthSaturated = client.send(reqHealth, HttpResponse.BodyHandlers.ofString());
            check(respHealthSaturated.statusCode() == 200, "Health check harus tetap responsif saat slot penuh");
        } finally {
            worker.close();
        }
    }

    private static void verifyPreflightRejections() throws Exception {
        String token = "preflight-test-token-" + UUID.randomUUID();
        TopsisWorker.WorkerConfig wc = new TopsisWorker.WorkerConfig(
                "aws-sydney", "soka-host-1", "soka-vm-s1", 1, "127.0.0.1", 0, token
        );
        TopsisWorker.RunningWorker worker = TopsisWorker.startServer(wc);
        int port = worker.port();
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        try {
            // Mismatch VM ID
            TopsisRealCloud.ClusterConfig mismatchConfig = new TopsisRealCloud.ClusterConfig(
                    "aws-sydney", "ap-southeast-2", "ap-southeast-2a",
                    List.of(
                            new TopsisRealCloud.WorkerConfig("soka-host-1", "soka-vm-WRONG", "http://127.0.0.1:" + port, 1, 0.05),
                            new TopsisRealCloud.WorkerConfig("soka-host-1", "soka-vm-m1", "http://127.0.0.1:18002", 2, 0.12),
                            new TopsisRealCloud.WorkerConfig("soka-host-2", "soka-vm-s2", "http://127.0.0.1:18003", 1, 0.05),
                            new TopsisRealCloud.WorkerConfig("soka-host-2", "soka-vm-m2", "http://127.0.0.1:18004", 2, 0.12)
                    )
            );
            expectFailure(IllegalStateException.class, () -> {
                try {
                    TopsisRealCloud.performPreflight(client, mismatchConfig, token);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            // Unreachable worker
            TopsisRealCloud.ClusterConfig unreachableConfig = new TopsisRealCloud.ClusterConfig(
                    "aws-sydney", "ap-southeast-2", "ap-southeast-2a",
                    List.of(
                            new TopsisRealCloud.WorkerConfig("soka-host-1", "soka-vm-s1", "http://127.0.0.1:" + port, 1, 0.05),
                            new TopsisRealCloud.WorkerConfig("soka-host-1", "soka-vm-m1", "http://127.0.0.1:1", 2, 0.12), // Unreachable
                            new TopsisRealCloud.WorkerConfig("soka-host-2", "soka-vm-s2", "http://127.0.0.1:18003", 1, 0.05),
                            new TopsisRealCloud.WorkerConfig("soka-host-2", "soka-vm-m2", "http://127.0.0.1:18004", 2, 0.12)
                    )
            );
            expectFailure(IllegalStateException.class, () -> {
                try {
                    TopsisRealCloud.performPreflight(client, unreachableConfig, token);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } finally {
            worker.close();
        }
    }

    private static void verifyEndToEndLoopbackWorkload() throws Exception {
        String token = "loopback-e2e-token-" + UUID.randomUUID();
        int[] slots = {1, 2, 1, 2};
        String[] hostIds = {"soka-host-1", "soka-host-1", "soka-host-2", "soka-host-2"};
        String[] vmIds = {"soka-vm-s1", "soka-vm-m1", "soka-vm-s2", "soka-vm-m2"};
        double[] rates = {0.05, 0.12, 0.05, 0.12};

        List<TopsisWorker.RunningWorker> workers = new ArrayList<>();
        List<TopsisRealCloud.WorkerConfig> workerConfigs = new ArrayList<>();

        for (int i = 0; i < 4; i++) {
            TopsisWorker.WorkerConfig wc = new TopsisWorker.WorkerConfig(
                    "aws-sydney", hostIds[i], vmIds[i], slots[i], "127.0.0.1", 0, token
            );
            TopsisWorker.RunningWorker worker = TopsisWorker.startServer(wc);
            workers.add(worker);
            int port = worker.port();
            workerConfigs.add(new TopsisRealCloud.WorkerConfig(hostIds[i], vmIds[i], "http://127.0.0.1:" + port, slots[i], rates[i]));
        }
        Path tempDir = Files.createTempDirectory("test-e2e-loopback-");
        try {
            // Fixture dataset: 4 rows
            Path fixtureCsv = tempDir.resolve("fixture.csv");
            Files.writeString(fixtureCsv, "100000,1,0\n100000,2,0\n100000,3,0\n100000,2,2\n");

            // Config JSON
            Path configJson = tempDir.resolve("config.json");
            TopsisRealCloud.ClusterConfig cluster = new TopsisRealCloud.ClusterConfig(
                    "aws-sydney", "ap-southeast-2", "ap-southeast-2a", workerConfigs
            );
            Files.writeString(configJson, GSON.toJson(cluster));

            Path outputDir = tempDir.resolve("results-e2e");

            // Eksekusi run controller secara langsung dengan token sesi
            TopsisRealCloud.executeRun(configJson, fixtureCsv, outputDir, null, 1.0, 0.01, token);
            // Verifikasi output
            Path summaryPath = outputDir.resolve("real_summary.json");
            check(Files.isRegularFile(summaryPath), "real_summary.json harus dibuat");
            JsonObject summary = JsonParser.parseString(Files.readString(summaryPath)).getAsJsonObject();
            check("SUCCESS".equals(summary.get("status").getAsString()), "Status harus SUCCESS");
            check(summary.get("tasksRequested").getAsInt() == 4, "tasksRequested harus 4");
            check(summary.get("tasksCompleted").getAsInt() == 4, "tasksCompleted harus 4");

            Path tasksPath = outputDir.resolve("real_tasks.csv");
            List<String> taskLines = Files.readAllLines(tasksPath);
            check(taskLines.size() == 5, "real_tasks.csv harus memiliki 1 header + 4 baris data");

            // Validasi checksum SHA-256 untuk 100,000 iterasi
            String expectedChecksum = "b422bc9c0646a432433c2410991c95e2d89758e3b4f540aca863389f28a11379";
            for (int i = 1; i <= 4; i++) {
                String[] cols = taskLines.get(i).split(",");
                long iter = Long.parseLong(cols[7]);
                double arrival = Double.parseDouble(cols[4]);
                double dispatch = Double.parseDouble(cols[8]);
                String checksum = cols[12];
                check(iter == 100_000L, "Iterasi harus 100000");
                check(expectedChecksum.equals(checksum), "Checksum tidak sesuai: " + checksum);
                check(dispatch >= arrival - 0.05, "Dispatch (" + dispatch + ") tidak boleh mendahului arrival (" + arrival + ")");
            }

            Path allocPath = outputDir.resolve("real_vm_allocation.csv");
            List<String> allocLines = Files.readAllLines(allocPath);
            check(allocLines.size() == 5, "real_vm_allocation.csv harus memiliki 1 header + 4 baris worker");

            int totalAllocated = 0;
            for (int i = 1; i <= 4; i++) {
                String[] cols = allocLines.get(i).split(",");
                totalAllocated += Integer.parseInt(cols[8]);
            }
            check(totalAllocated == 4, "Total task teralokasi harus tepat 4");
        } finally {
            for (TopsisWorker.RunningWorker w : workers) w.close();
            deleteRecursively(tempDir);
        }
    }

    private static void deleteRecursively(Path path) {
        if (!Files.exists(path)) return;
        try {
            if (Files.isDirectory(path)) {
                try (var stream = Files.list(path)) {
                    for (Path child : stream.toList()) deleteRecursively(child);
                }
            }
            Files.deleteIfExists(path);
        } catch (IOException ignored) {}
    }
}
