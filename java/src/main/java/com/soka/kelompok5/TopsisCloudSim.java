package com.soka.kelompok5;

import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerSpaceShared;
import org.cloudsimplus.schedulers.vm.VmSchedulerSpaceShared;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * Simulasi Penjadwalan Cloudlet Menggunakan CloudSim Plus & Algoritma TOPSIS
 * Berdasarkan Desain Proyek Kelompok 5 - Strategi Optimasi Komputasi Awan (SOKA)
 * 
 * Infrastruktur:
 * - 1 Datacenter
 * - 4 Physical Host (Host 1-2: 8 core @ 10k MIPS; Host 3-4: 16 core @ 20k MIPS)
 * - 10 VM Heterogen (4 Small, 4 Medium, 2 Large)
 * - Workload: GoCJ Real Trace & Sintetis (Uniform & Normal Distribution)
 * - Multi-Objective TOPSIS: Minimasi Makespan & Total Biaya Eksekusi (Bobot 0.5 : 0.5)
 */
public class TopsisCloudSim {

    // Struktur metadata konfigurasi VM
    public static class VmSpec {
        public final int id;
        public final String type;
        public final double mips;
        public final int pes;
        public final long ramMb;
        public final double bwBytesPerSec; // 125 MB/s (1 Gbps)
        public final double hourlyRate;

        public VmSpec(int id, String type, double mips, int pes, long ramMb, double bwBytesPerSec, double hourlyRate) {
            this.id = id;
            this.type = type;
            this.mips = mips;
            this.pes = pes;
            this.ramMb = ramMb;
            this.bwBytesPerSec = bwBytesPerSec;
            this.hourlyRate = hourlyRate;
        }
    }

    // Definisi 10 VM heterogen sesuai tabel desain proyek & notebook
    private static final List<VmSpec> VM_SPECS = Arrays.asList(
        // 4 Small VMs (VM 0-3): 1 vCPU (1.000 MIPS), RAM 2 GB, $0.05/jam, BW 1 Gbps
        new VmSpec(0, "Small", 1000, 1, 2048, 125_000_000, 0.05),
        new VmSpec(1, "Small", 1000, 1, 2048, 125_000_000, 0.05),
        new VmSpec(2, "Small", 1000, 1, 2048, 125_000_000, 0.05),
        new VmSpec(3, "Small", 1000, 1, 2048, 125_000_000, 0.05),
        // 4 Medium VMs (VM 4-7): 2 vCPU (2.500 MIPS), RAM 4 GB, $0.12/jam, BW 1 Gbps
        new VmSpec(4, "Medium", 2500, 1, 4096, 125_000_000, 0.12),
        new VmSpec(5, "Medium", 2500, 1, 4096, 125_000_000, 0.12),
        new VmSpec(6, "Medium", 2500, 1, 4096, 125_000_000, 0.12),
        new VmSpec(7, "Medium", 2500, 1, 4096, 125_000_000, 0.12),
        // 2 Large VMs (VM 8-9): 4 vCPU (5.000 MIPS), RAM 8 GB, $0.25/jam, BW 1 Gbps
        new VmSpec(8, "Large", 5000, 1, 8192, 125_000_000, 0.25),
        new VmSpec(9, "Large", 5000, 1, 8192, 125_000_000, 0.25)
    );

    // Representasi Task/Workload
    public static class TaskItem {
        public final int id;
        public final double lengthMi;
        public final int sla;
        public final double arrivalTime;
        public final double ioBytes;

        public TaskItem(int id, double lengthMi, int sla, double arrivalTime, double ioBytes) {
            this.id = id;
            this.lengthMi = lengthMi;
            this.sla = sla;
            this.arrivalTime = arrivalTime;
            this.ioBytes = ioBytes;
        }
    }

    // Hasil Pengujian
    public static class SimulationResult {
        public final String workload;
        public final int nTasks;
        public final double makespan;
        public final double totalCost;
        public final double degreeOfImbalance;
        public final double algoRuntimeMs;
        public final double[] vmBusyTimes;
        public final int[] vmTaskCounts;

        public SimulationResult(String workload, int nTasks, double makespan, double totalCost,
                                double degreeOfImbalance, double algoRuntimeMs,
                                double[] vmBusyTimes, int[] vmTaskCounts) {
            this.workload = workload;
            this.nTasks = nTasks;
            this.makespan = makespan;
            this.totalCost = totalCost;
            this.degreeOfImbalance = degreeOfImbalance;
            this.algoRuntimeMs = algoRuntimeMs;
            this.vmBusyTimes = vmBusyTimes;
            this.vmTaskCounts = vmTaskCounts;
        }
    }

    public static void main(String[] args) {
        int[] taskCounts = {100, 200, 500, 1000};

        System.out.println("=========================================================================================");
        System.out.println("  SIMULASI CLOUDSIM: PENJADWALAN CLOUDLET DENGAN MULTI-KRITERIA TOPSIS (SOKA KELOMPOK 5) ");
        System.out.println("  Arsitektur: 1 Datacenter, 4 Physical Hosts, 10 Heterogeneous VMs (Non-Preemptive)      ");
        System.out.println("=========================================================================================\n");

        // 1. Eksekusi Dataset Riil GoCJ
        System.out.println("Menjalankan simulasi batch task (100, 200, 500, 1000)...\n");
        List<SimulationResult> gocjResults = new ArrayList<>();
        List<SimulationResult> uniformResults = new ArrayList<>();
        List<SimulationResult> normalResults = new ArrayList<>();

        List<SimulationResult> allResults = new ArrayList<>();

        for (int n : taskCounts) {
            List<TaskItem> gocjTasks = loadGocjWorkload(n, 42);
            SimulationResult rg = runCloudSimSimulation("GoCJ (Real Trace)", gocjTasks, n);
            gocjResults.add(rg);
            allResults.add(rg);

            List<TaskItem> uniformTasks = generateSyntheticWorkload(n, "uniform", 42);
            SimulationResult ru = runCloudSimSimulation("Synthetic Uniform", uniformTasks, n);
            uniformResults.add(ru);
            allResults.add(ru);

            List<TaskItem> normalTasks = generateSyntheticWorkload(n, "normal", 42);
            SimulationResult rn = runCloudSimSimulation("Synthetic Normal", normalTasks, n);
            normalResults.add(rn);
            allResults.add(rn);
        }

        printResultTable("Ringkasan Hasil: GoCJ Real Trace", gocjResults);
        printResultTable("Ringkasan Hasil: Sintetis Uniform", uniformResults);
        printResultTable("Ringkasan Hasil: Sintetis Normal", normalResults);

        // Ekspor data ke file CSV agar dapat divisualisasikan oleh Python / Excel
        exportResultsToCsv(allResults);
        exportVmDistributionCsv(gocjResults.get(gocjResults.size() - 1));
    }
    /**
     * Menjalankan satu siklus simulasi CloudSim Plus dengan scheduler TOPSIS
     */
    public static SimulationResult runCloudSimSimulation(String workloadName, List<TaskItem> tasks, int nTasks) {
        CloudSimPlus simulation = new CloudSimPlus();

        // 1. Buat Datacenter (4 Host fisik sesuai tabel Desain Proyek)
        Datacenter datacenter = createDatacenter(simulation);

        // 2. Buat Broker CloudSim
        DatacenterBroker broker = new DatacenterBrokerSimple(simulation);

        // 3. Buat 10 VM Heterogen
        List<Vm> vmList = new ArrayList<>();
        for (VmSpec spec : VM_SPECS) {
            Vm vm = new VmSimple(spec.id, spec.mips, spec.pes);
            vm.setRam(spec.ramMb)
              .setBw(1000) // 1 Gbps
              .setSize(50000)
              .setCloudletScheduler(new CloudletSchedulerSpaceShared());
            vmList.add(vm);
        }
        broker.submitVmList(vmList);

        // 4. Jalankan Algoritma TOPSIS untuk Menentukan Mapping Task -> VM
        long t0 = System.nanoTime();
        int[] taskToVmMapping = scheduleTasksTopsis(tasks, VM_SPECS, 0.5, 0.5);
        long algoDurationNs = System.nanoTime() - t0;
        double algoRuntimeMs = algoDurationNs / 1_000_000.0;

        // 5. Konversi Task menjadi Cloudlet CloudSim Plus
        List<Cloudlet> cloudletList = new ArrayList<>();
        for (int i = 0; i < tasks.size(); i++) {
            TaskItem task = tasks.get(i);
            int chosenVmId = taskToVmMapping[i];
            Vm chosenVm = vmList.get(chosenVmId);
            VmSpec chosenSpec = VM_SPECS.get(chosenVmId);

            // Perhitungan durasi eksekusi: Komputasi CPU + Transfer I/O
            double ioDuration = task.ioBytes / chosenSpec.bwBytesPerSec;
            long effectiveMi = (long) (task.lengthMi + (ioDuration * chosenSpec.mips));

            Cloudlet cloudlet = new CloudletSimple(task.id, effectiveMi, 1);
            cloudlet.setUtilizationModel(new UtilizationModelFull());
            cloudlet.setVm(chosenVm);
            cloudletList.add(cloudlet);
        }
        broker.submitCloudletList(cloudletList);

        // 6. Jalankan Simulasi Engine CloudSim
        simulation.start();

        // 7. Hitung Metrik Evaluasi: Makespan, Total Cost, DI
        List<Cloudlet> finishedCloudlets = broker.getCloudletFinishedList();
        double[] vmFinishTimes = new double[VM_SPECS.size()];
        double[] vmBusyTimes = new double[VM_SPECS.size()];
        int[] vmTaskCounts = new int[VM_SPECS.size()];

        for (Cloudlet c : finishedCloudlets) {
            int vmId = (int) c.getVm().getId();
            vmTaskCounts[vmId]++;
            double finish = c.getFinishTime();
            double duration = c.getActualCpuTime();

            if (finish > vmFinishTimes[vmId]) {
                vmFinishTimes[vmId] = finish;
            }
            vmBusyTimes[vmId] += duration;
        }

        double makespan = Arrays.stream(vmFinishTimes).max().orElse(0.0);

        // Total Cost sewa VM: Pembulatan ke atas per jam pemakaian (sesuai notebook)
        double totalCost = 0.0;
        for (int i = 0; i < VM_SPECS.size(); i++) {
            if (vmBusyTimes[i] > 0) {
                double hours = Math.ceil(vmBusyTimes[i] / 3600.0);
                totalCost += hours * VM_SPECS.get(i).hourlyRate;
            }
        }

        // Degree of Imbalance (DI): (T_max - T_min) / T_avg
        double tMax = Arrays.stream(vmFinishTimes).max().orElse(0.0);
        double tMin = Arrays.stream(vmFinishTimes).min().orElse(0.0);
        double tAvg = Arrays.stream(vmFinishTimes).average().orElse(0.0);
        double di = (tAvg > 0) ? (tMax - tMin) / tAvg : 0.0;

        return new SimulationResult(workloadName, nTasks, makespan, totalCost, di, algoRuntimeMs, vmBusyTimes, vmTaskCounts);
    }

    /**
     * Membangun 4 Physical Host sesuai Desain Proyek:
     * - Host 0 & 1: 8 Core @ 10.000 MIPS, RAM 32 GB, Storage 1 TB, BW 10 Gbps
     * - Host 2 & 3: 16 Core @ 20.000 MIPS, RAM 64 GB, Storage 2 TB, BW 10 Gbps
     */
    private static Datacenter createDatacenter(CloudSimPlus simulation) {
        List<Host> hostList = new ArrayList<>();

        // Host 0 & 1 (2 unit)
        for (int i = 0; i < 2; i++) {
            List<Pe> peList = new ArrayList<>();
            for (int p = 0; p < 8; p++) {
                peList.add(new PeSimple(10000));
            }
            Host host = new HostSimple(32768, 10000, 1000000, peList);
            host.setVmScheduler(new VmSchedulerSpaceShared());
            hostList.add(host);
        }

        // Host 2 & 3 (2 unit)
        for (int i = 2; i < 4; i++) {
            List<Pe> peList = new ArrayList<>();
            for (int p = 0; p < 16; p++) {
                peList.add(new PeSimple(20000));
            }
            Host host = new HostSimple(65536, 10000, 2000000, peList);
            host.setVmScheduler(new VmSchedulerSpaceShared());
            hostList.add(host);
        }

        return new DatacenterSimple(simulation, hostList);
    }

    /**
     * Implementasi Lengkap Algoritma TOPSIS Multi-Kriteria:
     * Menyeimbangkan Completion Time dan Execution Cost dengan bobot w_time = 0.5, w_cost = 0.5.
     */
    private static int[] scheduleTasksTopsis(List<TaskItem> tasks, List<VmSpec> vms, double wTime, double wCost) {
        int numVms = vms.size();
        double[] predictedReadyTime = new double[numVms];
        int[] assignments = new int[tasks.size()];

        for (int tIdx = 0; tIdx < tasks.size(); tIdx++) {
            TaskItem task = tasks.get(tIdx);
            double[][] matrix = new double[numVms][2];

            // Hitung Completion Time dan Cost untuk setiap calon VM
            for (int j = 0; j < numVms; j++) {
                VmSpec vm = vms.get(j);
                double execTime = (task.lengthMi / vm.mips) + (task.ioBytes / vm.bwBytesPerSec);
                double compTime = predictedReadyTime[j] + execTime;
                double cost = execTime * (vm.hourlyRate / 3600.0);

                matrix[j][0] = compTime;
                matrix[j][1] = cost;
            }

            // 1. Normalisasi Matriks Keputusan (Vektor Normalization)
            double sumSqTime = 0.0;
            double sumSqCost = 0.0;
            for (int j = 0; j < numVms; j++) {
                sumSqTime += matrix[j][0] * matrix[j][0];
                sumSqCost += matrix[j][1] * matrix[j][1];
            }
            double normTime = Math.sqrt(sumSqTime);
            double normCost = Math.sqrt(sumSqCost);

            double[][] weightedMatrix = new double[numVms][2];
            for (int j = 0; j < numVms; j++) {
                weightedMatrix[j][0] = (normTime > 0 ? (matrix[j][0] / normTime) : 0.0) * wTime;
                weightedMatrix[j][1] = (normCost > 0 ? (matrix[j][1] / normCost) : 0.0) * wCost;
            }

            // 2. Tentukan Solusi Ideal Positif (A+) dan Solusi Ideal Negatif (A-)
            // Keduanya adalah cost criteria: nilai minimum adalah yang terbaik
            double aPlusTime = Double.MAX_VALUE;
            double aPlusCost = Double.MAX_VALUE;
            double aMinusTime = -Double.MAX_VALUE;
            double aMinusCost = -Double.MAX_VALUE;

            for (int j = 0; j < numVms; j++) {
                if (weightedMatrix[j][0] < aPlusTime) aPlusTime = weightedMatrix[j][0];
                if (weightedMatrix[j][1] < aPlusCost) aPlusCost = weightedMatrix[j][1];

                if (weightedMatrix[j][0] > aMinusTime) aMinusTime = weightedMatrix[j][0];
                if (weightedMatrix[j][1] > aMinusCost) aMinusCost = weightedMatrix[j][1];
            }

            // 3. Hitung Jarak Euclidean ke Solusi Ideal
            double maxScore = -1.0;
            int bestVmId = 0;

            for (int j = 0; j < numVms; j++) {
                double diffPlusTime = weightedMatrix[j][0] - aPlusTime;
                double diffPlusCost = weightedMatrix[j][1] - aPlusCost;
                double dPlus = Math.sqrt(diffPlusTime * diffPlusTime + diffPlusCost * diffPlusCost);

                double diffMinusTime = weightedMatrix[j][0] - aMinusTime;
                double diffMinusCost = weightedMatrix[j][1] - aMinusCost;
                double dMinus = Math.sqrt(diffMinusTime * diffMinusTime + diffMinusCost * diffMinusCost);

                // 4. Skor Kedekatan Relatif
                double score = dMinus / (dPlus + dMinus + 1e-9);

                // 5. Pilih alternatif dengan skor kedekatan tertinggi
                if (score > maxScore) {
                    maxScore = score;
                    bestVmId = j;
                }
            }

            assignments[tIdx] = bestVmId;

            // Perbarui waktu siap VM terpilih untuk task berikutnya
            VmSpec chosenVm = vms.get(bestVmId);
            double execTime = (task.lengthMi / chosenVm.mips) + (task.ioBytes / chosenVm.bwBytesPerSec);
            predictedReadyTime[bestVmId] += execTime;
        }

        return assignments;
    }

    /**
     * Memuat dataset riil Google Cloud Jobs (GoCJ_Dataset_1000.csv)
     */
    private static List<TaskItem> loadGocjWorkload(int nTasks, long seed) {
        Random rand = new Random(seed);
        File datasetFile = findGocjDatasetFile(nTasks);
        List<TaskItem> list = new ArrayList<>();

        if (datasetFile != null && datasetFile.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(datasetFile))) {
                String line;
                int count = 0;
                while ((line = br.readLine()) != null && count < nTasks) {
                    String[] parts = line.trim().split(",");
                    if (parts.length >= 3) {
                        double length = Double.parseDouble(parts[0].trim());
                        int sla = Integer.parseInt(parts[1].trim());
                        double arrival = Double.parseDouble(parts[2].trim());
                        double ioMb = 50.0 + rand.nextDouble() * (500.0 - 50.0);
                        double ioBytes = ioMb * 1024.0 * 1024.0;

                        list.add(new TaskItem(count, length, sla, arrival, ioBytes));
                        count++;
                    }
                }
                System.out.printf("  [GoCJ Loader] Berhasil memuat %d tasks dari file real trace: %s\n", list.size(), datasetFile.getName());
            } catch (IOException | NumberFormatException e) {
                System.err.println("Gagal membaca GoCJ dataset file: " + e.getMessage() + ". Menggunakan fallback sintetis.");
            }
        } else {
            System.err.println("  [GoCJ Loader WARNING] File GoCJ dataset tidak ditemukan, beralih ke generator sintetis.");
        }

        // Jika baris file belum mencukupi, lengkapi dengan sintetis
        while (list.size() < nTasks) {
            int idx = list.size();
            double length = 1000.0 + rand.nextDouble() * (50000.0 - 1000.0);
            double ioMb = 50.0 + rand.nextDouble() * (500.0 - 50.0);
            list.add(new TaskItem(idx, length, 1, 0.0, ioMb * 1024.0 * 1024.0));
        }

        return list;
    }

    /**
     * Generator Beban Kerja Sintetis:
     * - 'uniform': Panjang 1.000 - 50.000 MI, I/O 50 - 500 MB
     * - 'normal' : Mean 25.500 MI (std 8.000), I/O Mean 275 MB (std 75)
     */
    private static List<TaskItem> generateSyntheticWorkload(int nTasks, String distribution, long seed) {
        Random rand = new Random(seed);
        List<TaskItem> list = new ArrayList<>();

        for (int i = 0; i < nTasks; i++) {
            double length;
            double ioMb;

            if ("normal".equalsIgnoreCase(distribution)) {
                length = 25500.0 + rand.nextGaussian() * 8000.0;
                length = Math.max(1000.0, Math.min(50000.0, length));

                ioMb = 275.0 + rand.nextGaussian() * 75.0;
                ioMb = Math.max(50.0, Math.min(500.0, ioMb));
            } else { // default 'uniform'
                length = 1000.0 + rand.nextDouble() * (50000.0 - 1000.0);
                ioMb = 50.0 + rand.nextDouble() * (500.0 - 50.0);
            }

            double ioBytes = ioMb * 1024.0 * 1024.0;
            list.add(new TaskItem(i, length, 1, 0.0, ioBytes));
        }
        return list;
    }

    /**
     * Mencari path file dataset GoCJ
     */
    private static File findGocjDatasetFile(int nTasks) {
        String[] dirCandidates = {
            "gocj_dataset",
            "../gocj_dataset",
            "../../gocj_dataset",
            "D:/main/Documents/kuliah/sem5/SOKA/task-4/gocj_dataset",
            "D:/main/Documents/kuliah/sem5/SOKA/gocj_dataset"
        };
        // 1. Coba file spesifik jumlah task: GoCJ_Dataset_{nTasks}.csv
        for (String dir : dirCandidates) {
            File f = new File(dir, "GoCJ_Dataset_" + nTasks + ".csv");
            if (f.exists()) {
                return f;
            }
        }
        // 2. Fallback ke file master 1000 tasks: GoCJ_Dataset_1000.csv
        for (String dir : dirCandidates) {
            File f = new File(dir, "GoCJ_Dataset_1000.csv");
            if (f.exists()) {
                return f;
            }
        }
        return null;
    }

    /**
     * Menampilkan tabel hasil simulasi yang terformat rapi
     */
    private static void printResultTable(String title, List<SimulationResult> results) {
        System.out.println("--- " + title + " ---");
        System.out.printf("%-8s %-16s %-16s %-12s %-14s\n", "Tasks", "Makespan (s)", "Total Cost ($)", "DI", "Runtime (ms)");
        for (SimulationResult r : results) {
            System.out.printf("%-8d %-16.6f %-16.2f %-12.6f %-14.4f\n",
                    r.nTasks, r.makespan, r.totalCost, r.degreeOfImbalance, r.algoRuntimeMs);
        }
        System.out.println();
    }
    /**
     * Ekspor metrik evaluasi ke file CSV
     */
    private static void exportResultsToCsv(List<SimulationResult> results) {
        File targetDir = getExportDir();
        File csvFile = new File(targetDir, "cloudsim_metrics.csv");
        try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(csvFile))) {
            pw.println("Workload,Tasks,Makespan_s,TotalCost_USD,DI,Runtime_ms");
            for (SimulationResult r : results) {
                pw.printf(Locale.US, "%s,%d,%.6f,%.2f,%.6f,%.4f\n",
                        r.workload, r.nTasks, r.makespan, r.totalCost, r.degreeOfImbalance, r.algoRuntimeMs);
            }
            System.out.println("Data metrik berhasil diekspor ke: " + csvFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Gagal mengekspor metrics CSV: " + e.getMessage());
        }
    }

    /**
     * Ekspor distribusi alokasi VM ke file CSV untuk 1000 tasks
     */
    private static void exportVmDistributionCsv(SimulationResult res1000) {
        File targetDir = getExportDir();
        File csvFile = new File(targetDir, "cloudsim_vm_allocation_1000.csv");
        try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(csvFile))) {
            pw.println("VmId,VmType,Mips,HourlyRate,BusyTime_s,TaskCount");
            for (int i = 0; i < VM_SPECS.size(); i++) {
                VmSpec spec = VM_SPECS.get(i);
                pw.printf(Locale.US, "%d,%s,%.0f,%.2f,%.2f,%d\n",
                        spec.id, spec.type, spec.mips, spec.hourlyRate,
                        res1000.vmBusyTimes[i], res1000.vmTaskCounts[i]);
            }
            System.out.println("Data distribusi VM berhasil diekspor ke: " + csvFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Gagal mengekspor VM allocation CSV: " + e.getMessage());
        }
    }

    private static File getExportDir() {
        String[] candidates = {"docs", "../docs", "task-4/docs"};
        for (String c : candidates) {
            File dir = new File(c);
            if (dir.exists() && dir.isDirectory()) {
                return dir;
            }
        }
        return new File(".");
    }
}
