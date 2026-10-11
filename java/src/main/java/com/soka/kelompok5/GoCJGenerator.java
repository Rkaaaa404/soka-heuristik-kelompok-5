package com.soka.kelompok5;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * ==============================================================================
 * GoCJGenerator (Adaptasi Generator Enhanced GoCJ)
 * ==============================================================================
 * Mengadaptasi generator Enhanced GoCJ yang dipublikasikan bersama dataset:
 * "GoCJ: Google Cloud Jobs Dataset for Distributed and Cloud Computing Infrastructures"
 * (Hussain & Aleem, MDPI Data 2018; Mendeley Data b7bp6xhrcd/1)
 * serta:
 * "Enhanced GoCJ: Google Cloud Jobs Dataset with SLA Classes and Arrival Times"
 * (Nawaz, Hussain, Khattak, Mendeley Data r93dfjvvv6/5).
 *
 * Dataset GoCJ merepresentasikan INDEPENDENT TASKS (Bag-of-Tasks / Meta-Tasks)
 * yang diambil dari trace cluster Google produksi.
 *
 * Format Output: 3 Kolom CSV tanpa header:
 *   1. Job Length  : Instruksi komputasi dalam Million Instructions (MI)
 *   2. SLA Class   : Level of Priority (1 = Low ~18%, 2 = Medium ~50%, 3 = High ~32%)
 *                    [CATATAN PENTING: SLA adalah Prioritas Layanan, BUKAN dependensi task]
 *   3. Arrival Time: Waktu kedatangan task dalam detik (Proses Poisson non-homogen)
 * ==============================================================================
 */
public class GoCJGenerator {

    // Parameter kedatangan Poisson non-homogen (Enhanced GoCJ)
    private static final double BASE_LAMBDA = 0.62;        // Laju kedatangan dasar (jobs/detik)
    private static final double PEAK_INTENSITY = 1.5;      // Faktor beban puncak jam kerja (08:00 - 18:00)
    private static final double OFFPEAK_INTENSITY = 0.7;   // Faktor beban tenang di luar jam kerja
    private static final int BUSINESS_START = 8;
    private static final int BUSINESS_END = 18;

    // Ambang batas kumulatif distribusi SLA
    private static final double SLA1_THRESHOLD = 0.18;     // 18% Low Priority
    private static final double SLA2_THRESHOLD = 0.68;     // 50% Medium Priority (0.18 + 0.50)

    private final List<Long> seedJobSizes = new ArrayList<>();
    private final Random random;

    public GoCJGenerator() {
        this.random = new Random(42); // Default reproducible seed
    }

    public GoCJGenerator(long seed) {
        this.random = new Random(seed);
    }

    /**
     * Membaca 50 nilai seed ukuran job dari Original_Enhanced_Dataset.txt.
     */
    public void loadSeedDataset(File seedFile) throws IOException {
        seedJobSizes.clear();
        try (BufferedReader br = new BufferedReader(new FileReader(seedFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    try {
                        seedJobSizes.add(Long.parseLong(line));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        if (seedJobSizes.isEmpty()) {
            throw new IOException("File seed kosong atau tidak valid: " + seedFile.getAbsolutePath());
        }
    }

    /**
     * Algoritma Monte Carlo Bootstrapping untuk sampling ukuran instruksi job (MI).
     */
    public long sampleJobSize() {
        int index = random.nextInt(seedJobSizes.size());
        return seedJobSizes.get(index);
    }

    /**
     * Menetapkan SLA Priority Level (1, 2, 3) berdasarkan distribusi empiris cluster Google
     * dengan size-based bias (task berukuran raksasa memiliki probabilitas lebih tinggi menjadi SLA-3).
     */
    public int assignSlaClass(long jobSize) {
        double r = random.nextDouble();
        if (jobSize >= 525_000) {
            r = Math.max(0.0, r - 0.25);
        } else if (jobSize >= 150_000) {
            r = Math.max(0.0, r - 0.10);
        } else if (jobSize <= 55_000) {
            r = Math.min(1.0, r + 0.10);
        }

        if (r < SLA1_THRESHOLD) {
            return 1; // Low Priority
        } else if (r < SLA2_THRESHOLD) {
            return 2; // Medium Priority
        } else {
            return 3; // High Priority
        }
    }

    /**
     * Menghitung faktor intensitas kedatangan berdasarkan pola diurnal siklus 24 jam.
     */
    private double getIntensityFactor(double currentArrivalTime) {
        int secondsInDay = (int) (currentArrivalTime % 86400);
        int hour = secondsInDay / 3600;
        if (hour >= BUSINESS_START && hour < BUSINESS_END) {
            return PEAK_INTENSITY;
        } else {
            return OFFPEAK_INTENSITY;
        }
    }

    /**
     * Membangkitkan dataset dengan N jobs dan menyimpannya ke format CSV.
     */
    public void generateDataset(int numJobs, File outputFile) throws IOException {
        if (seedJobSizes.isEmpty()) {
            throw theoreticalEmptySeed();
        }

        double arrivalTime = 0.0;
        File parent = outputFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
            for (int i = 0; i < numJobs; i++) {
                long jobSize = sampleJobSize();
                int sla = assignSlaClass(jobSize);

                double intensity = getIntensityFactor(arrivalTime);
                double effectiveLambda = BASE_LAMBDA * intensity;
                double interArrival = -Math.log(Math.max(1e-9, random.nextDouble())) / effectiveLambda;
                arrivalTime += interArrival;
                long roundedArrival = Math.round(arrivalTime);

                writer.println(jobSize + "," + sla + "," + roundedArrival);
            }
        }
    }

    private IllegalStateException theoreticalEmptySeed() {
        return new IllegalStateException("Seed dataset belum dimuat. Panggil loadSeedDataset() terlebih dahulu.");
    }

    /**
     * Mencari file seed pada path relatif yang umum digunakan.
     */
    public static File findSeedFile() {
        String[] candidates = {
            "gocj_dataset/Original_Enhanced_Dataset.txt",
            "../gocj_dataset/Original_Enhanced_Dataset.txt"
        };
        for (String c : candidates) {
            File f = new File(c);
            if (f.exists() && f.isFile()) {
                return f;
            }
        }
        return null;
    }

    public static File findDatasetDir() {
        String[] candidates = {
            "gocj_dataset",
            "../gocj_dataset"
        };
        for (String c : candidates) {
            File f = new File(c);
            if (f.exists() && f.isDirectory()) {
                return f;
            }
        }
        File f = new File("gocj_dataset");
        f.mkdirs();
        return f;
    }

    /**
     * Main runner untuk membangkitkan variasi dataset skala besar (7k, 8k, 9k, 10k).
     */
    public static void main(String[] args) throws Exception {
        File seedFile = findSeedFile();
        if (seedFile == null) {
            System.err.println("[ERROR] File seed Original_Enhanced_Dataset.txt tidak ditemukan!");
            System.exit(1);
        }

        File datasetDir = findDatasetDir();
        GoCJGenerator generator = new GoCJGenerator(2024);
        generator.loadSeedDataset(seedFile);
        System.out.println("[GoCJGenerator] Dimuat " + generator.seedJobSizes.size() + " nilai seed dari: " + seedFile.getPath());

        int[] targetScales;
        if (args.length > 0) {
            targetScales = new int[args.length];
            for (int i = 0; i < args.length; i++) {
                targetScales[i] = Integer.parseInt(args[i]);
            }
        } else {
            targetScales = new int[]{7000, 8000, 9000, 10000};
        }

        for (int scale : targetScales) {
            File outFile = new File(datasetDir, "GoCJ_Dataset_" + scale + ".csv");
            System.out.println("[GoCJGenerator] Membangkitkan " + scale + " tasks -> " + outFile.getPath());
            generator.generateDataset(scale, outFile);
            System.out.println("  -> Berhasil dibuat: " + outFile.getName() + " (" + outFile.length() + " bytes)");
        }

        System.out.println("[GoCJGenerator] Dataset GoCJ skala 7.000 s/d 10.000 tasks berhasil dibangkitkan.");
    }
}
