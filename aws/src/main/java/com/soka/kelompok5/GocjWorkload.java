package com.soka.kelompok5;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Loader and data model for GoCJ (Google Cloud Jobs) workload datasets.
 * Preserves stable 0-based row IDs matching CSV line order, then sorts by arrival time then ID.
 */
public final class GocjWorkload {
    private GocjWorkload() {}

    public static class TaskItem {
        public final int id;
        public final double lengthMi;
        public final int sla;           // Level of Priority (1=Low, 2=Medium, 3=High)
        public final double arrivalTime; // Arrival time in seconds
        public final double ioBytes;    // Synthetic I/O transfer size in bytes (0 for real CPU workload)

        public TaskItem(int id, double lengthMi, int sla, double arrivalTime, double ioBytes) {
            this.id = id;
            this.lengthMi = lengthMi;
            this.sla = sla;
            this.arrivalTime = arrivalTime;
            this.ioBytes = ioBytes;
        }
    }

    public static List<TaskItem> loadSimulation(Path dataset, int expectedTasks, long seed) {
        List<TaskItem> tasks = load(dataset, new Random(seed));
        if (tasks.size() != expectedTasks) {
            throw new IllegalArgumentException(dataset + ": jumlah task " + tasks.size() + ", wajib " + expectedTasks);
        }
        return tasks;
    }

    public static List<TaskItem> loadCpu(Path dataset) {
        return load(dataset, null);
    }

    private static List<TaskItem> load(Path dataset, Random random) {
        if (dataset == null) throw new IllegalArgumentException("Dataset path tidak boleh null");
        List<TaskItem> tasks = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(dataset, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String[] columns = line.trim().split(",", -1);
                try {
                    if (columns.length != 3) throw new IllegalArgumentException("Harus tepat tiga kolom");
                    double length = Double.parseDouble(columns[0].trim());
                    int sla = Integer.parseInt(columns[1].trim());
                    double arrival = Double.parseDouble(columns[2].trim());
                    if (!Double.isFinite(length) || length <= 0 || length > Long.MAX_VALUE / 2.0
                            || sla < 1 || sla > 3 || !Double.isFinite(arrival) || arrival < 0) {
                        throw new IllegalArgumentException("Length harus positif dan finite, SLA 1–3, arrival nonnegatif dan finite");
                    }
                    double ioBytes = random == null ? 0.0 : (50 + random.nextDouble() * 450) * 1_000_000.0;
                    tasks.add(new TaskItem(tasks.size(), length, sla, arrival, ioBytes));
                } catch (IllegalArgumentException error) {
                    throw new IllegalArgumentException(dataset + ": baris " + lineNumber + ": " + error.getMessage(), error);
                }
            }
        } catch (IOException error) {
            throw new UncheckedIOException("Gagal membaca dataset wajib: " + dataset, error);
        }
        if (tasks.isEmpty()) {
            throw new IllegalArgumentException(dataset + ": dataset kosong");
        }
        tasks.sort(Comparator.comparingDouble((TaskItem task) -> task.arrivalTime).thenComparingInt(task -> task.id));
        return List.copyOf(tasks);
    }
}
