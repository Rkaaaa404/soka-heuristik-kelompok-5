package com.soka.kelompok5;

import ch.qos.logback.classic.Level;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicySimple;
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
import org.cloudsimplus.util.Log;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;

/**
 * ==============================================================================
 * SIMULASI PENJADWALAN CLOUDLET DENGAN MULTI-KRITERIA TOPSIS (CLOUDSIM PLUS)
 * Strategi Optimasi Komputasi Awan (SOKA) - Kelompok 5
 * ==============================================================================
 *
 * ARSITEKTUR INFRASTRUKTUR CLOUD:
 * - 2 Datacenter Fisik (DC 1: Host 0-3, DC 2: Host 4-7)
 * - 8 Physical Host HOMOGEN (Masing-masing 24 Core @ 10.000 MIPS, 64 GB RAM, 2 TB Storage)
 * - 80 Virtual Machine HETEROGEN (10 VM per Host: 4 Small, 4 Medium, 2 Large)
 *   -> Total Kapasitas: 160 vCPU, 320 GB RAM, tarif sewa agregat $9.44/jam
 *
 * PEMETAAN DETERMINISTIK (SETIAP HOST MENAMPUNG VM):
 * - DC 1: Host 0 (VM 0-9),   Host 1 (VM 10-19), Host 2 (VM 20-29), Host 3 (VM 30-39)
 * - DC 2: Host 4 (VM 40-49), Host 5 (VM 50-59), Host 6 (VM 60-69), Host 7 (VM 70-79)
 *
 * KARAKTERISTIK WORKLOAD GOOGLE CLOUD JOBS (GoCJ):
 * - GoCJ merepresentasikan INDEPENDENT TASKS (Bag-of-Tasks / Meta-Tasks).
 * - Tidak memiliki graph precedence / ketergantungan antar-task.
 * - Kolom SLA adalah SERVICE LEVEL AGREEMENT (Tingkat Prioritas: 1=Low, 2=Medium, 3=High),
 *   BUKAN task dependency!
 *
 * SKENARIO PENGUJIAN:
 * - 10 Variasi Skala Task: 1.000, 2.000, ..., 10.000 tasks.
 * - 3 Kali Repeated Runs per variasi untuk uji konsistensi statistik (Mean ± Std Dev).
 * ==============================================================================
 */
public class TopsisCloudSim {

    private static final int DATACENTER_COUNT = 2;
    private static final int HOSTS_PER_DATACENTER = 4;
    private static final int HOST_COUNT = DATACENTER_COUNT * HOSTS_PER_DATACENTER;
    private static final int[] VMS_PER_TYPE_PER_HOST = {4, 4, 2};
    private static final int VMS_PER_HOST = Arrays.stream(VMS_PER_TYPE_PER_HOST).sum();
    private static final int HOST_PES = 24;
    private static final double HOST_MIPS = 10_000;
    private static final long HOST_RAM_MB = 65_536;
    private static final long HOST_STORAGE_MB = 2_000_000;
    private static final long HOST_BW_MBPS = 10_000;
    private static final long VM_STORAGE_MB = 50_000;
    private static final long VM_BW_MBPS = 1_000;
    private static final double BYTES_PER_MB = 1_000_000.0;
    private static final double VM_BW_BYTES_PER_SECOND = VM_BW_MBPS * BYTES_PER_MB / 8;
    private static final double SECONDS_PER_HOUR = 3_600.0;
    private static final double TIME_WEIGHT = 0.5;
    private static final double COST_WEIGHT = 0.5;
    private static final int RUNS_PER_SCALE = 3;
    private static final int MAX_TASKS = 10_000;
    private static final int TASK_SCALE_STEP = 1_000;
    private static final Comparator<TaskItem> TASK_ORDER = Comparator
            .comparingDouble((TaskItem task) -> task.arrivalTime).thenComparingInt(task -> task.id);

    // ─────────────────────────────────────────────────────────────
    // 1. DEFINISI TEMPLATE VM (HETEROGEN) & SPESIFIKASI HOST (HOMOGEN)
    // ─────────────────────────────────────────────────────────────

    public enum VmType {
        SMALL("Small", 1000.0, 1, 2048, VM_BW_BYTES_PER_SECOND, 0.05),
        MEDIUM("Medium", 2500.0, 2, 4096, VM_BW_BYTES_PER_SECOND, 0.12),
        LARGE("Large", 5000.0, 4, 8192, VM_BW_BYTES_PER_SECOND, 0.25);

        public final String label;
        public final double mips;
        public final int pes;
        public final long ramMb;
        public final double bwBytesPerSec;
        public final double hourlyRate;

        VmType(String label, double mips, int pes, long ramMb, double bwBytesPerSec, double hourlyRate) {
            this.label = label;
            this.mips = mips;
            this.pes = pes;
            this.ramMb = ramMb;
            this.bwBytesPerSec = bwBytesPerSec;
            this.hourlyRate = hourlyRate;
        }
    }

    public static class VmSpec {
        public final int id;
        public final VmType type;
        public final int dcId;      // 0 = Datacenter 1, 1 = Datacenter 2
        public final int hostId;    // 0 s/d 7
        public VmSpec(int id, VmType type, int dcId, int hostId) {
            this.id = id;
            this.type = type;
            this.dcId = dcId;
            this.hostId = hostId;
        }
    }

    /**
     * Membangun daftar 80 VM heterogen dengan pola 10 VM per host:
     * - Host 0-3 di Datacenter 1 (VM 0-39)
     * - Host 4-7 di Datacenter 2 (VM 40-79)
     */
    private static List<VmSpec> buildVmSpecs() {
        List<VmSpec> list = new ArrayList<>();
        for (int hostId = 0; hostId < HOST_COUNT; hostId++) {
            for (VmType type : VmType.values()) {
                for (int i = 0; i < VMS_PER_TYPE_PER_HOST[type.ordinal()]; i++) {
                    list.add(new VmSpec(list.size(), type, hostId / HOSTS_PER_DATACENTER, hostId));
                }
            }
        }
        return List.copyOf(list);
    }

    public static final List<VmSpec> ALL_VM_SPECS = buildVmSpecs();

    // ─────────────────────────────────────────────────────────────
    // 2. MODEL DATA WORKLOAD & METRIK HASIL SIMULASI
    // ─────────────────────────────────────────────────────────────

    public static class TaskItem {
        public final int id;
        public final double lengthMi;
        public final int sla;           // Level of Priority (1=Low, 2=Medium, 3=High)
        public final double arrivalTime; // Waktu kedatangan (detik)
        public final double ioBytes;    // Ukuran transfer I/O (bytes)

        public TaskItem(int id, double lengthMi, int sla, double arrivalTime, double ioBytes) {
            this.id = id;
            this.lengthMi = lengthMi;
            this.sla = sla;
            this.arrivalTime = arrivalTime;
            this.ioBytes = ioBytes;
        }
    }

    public static class SingleRunResult {
        public final int runId;
        public final int nTasks;
        public final double makespan;
        public final double totalCost;
        public final double degreeOfImbalance;
        public final double algoRuntimeMs;
        public final double[] vmBusyTimes;
        public final int[] vmTaskCounts;

        public SingleRunResult(int runId, int nTasks, double makespan, double totalCost,
                               double degreeOfImbalance, double algoRuntimeMs,
                               double[] vmBusyTimes, int[] vmTaskCounts) {
            this.runId = runId;
            this.nTasks = nTasks;
            this.makespan = makespan;
            this.totalCost = totalCost;
            this.degreeOfImbalance = degreeOfImbalance;
            this.algoRuntimeMs = algoRuntimeMs;
            this.vmBusyTimes = vmBusyTimes;
            this.vmTaskCounts = vmTaskCounts;
        }
    }

    public static class AggregatedResult {
        public final int nTasks;
        public final double meanMakespan;
        public final double stdMakespan;
        public final double meanCost;
        public final double stdCost;
        public final double meanDi;
        public final double stdDi;
        public final double meanRuntimeMs;
        public final double stdRuntimeMs;

        public AggregatedResult(int nTasks, List<SingleRunResult> runs) {
            this.nTasks = nTasks;
            if (runs.size() < 2) throw new IllegalArgumentException("Agregasi membutuhkan minimal dua run");

            double sumMakespan = 0, sumCost = 0, sumDi = 0, sumRt = 0;
            int n = runs.size();
            for (SingleRunResult r : runs) {
                sumMakespan += r.makespan;
                sumCost += r.totalCost;
                sumDi += r.degreeOfImbalance;
                sumRt += r.algoRuntimeMs;
            }
            this.meanMakespan = sumMakespan / n;
            this.meanCost = sumCost / n;
            this.meanDi = sumDi / n;
            this.meanRuntimeMs = sumRt / n;

            double varMakespan = 0, varCost = 0, varDi = 0, varRt = 0;
            for (SingleRunResult r : runs) {
                varMakespan += Math.pow(r.makespan - meanMakespan, 2);
                varCost += Math.pow(r.totalCost - meanCost, 2);
                varDi += Math.pow(r.degreeOfImbalance - meanDi, 2);
                varRt += Math.pow(r.algoRuntimeMs - meanRuntimeMs, 2);
            }
            // Standar deviasi sampel, sama dengan pandas.std(ddof=1).
            this.stdMakespan = Math.sqrt(varMakespan / (n - 1));
            this.stdCost = Math.sqrt(varCost / (n - 1));
            this.stdDi = Math.sqrt(varDi / (n - 1));
            this.stdRuntimeMs = Math.sqrt(varRt / (n - 1));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 3. MAIN RUNNER (1.000 S/D 10.000 TASKS, 3 RUNS MASING-MASING)
    // ─────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        // Reduksi log internal CloudSim Plus agar output terminal bersih dan fokus
        Log.setLevel(Level.ERROR);

        System.out.println("=========================================================================================");
        System.out.println("  SIMULASI CLOUDSIM PLUS: EVALUASI TOPSIS SKALA MASIF GOOGLE CLOUD JOBS (1K - 10K TASKS)");
        System.out.printf("  Arsitektur: %d Datacenter, %d Host Homogen, %d VM Heterogen (%d VM/Host)%n",
                DATACENTER_COUNT, HOST_COUNT, ALL_VM_SPECS.size(), VMS_PER_HOST);
        System.out.printf("  Pengujian: %d Repeated Runs per Skala Task%n", RUNS_PER_SCALE);
        System.out.println("=========================================================================================\n");

        printAllocationArchitectureTable();

        Path projectRoot = findProjectRoot();

        List<AggregatedResult> aggregatedResults = new ArrayList<>();
        List<SingleRunResult> allSingleRuns = new ArrayList<>();
        SingleRunResult lastScaleRun = null;

        System.out.printf("%n[INFO] Benchmark multi-skala (total %d run simulasi)...%n",
                MAX_TASKS / TASK_SCALE_STEP * RUNS_PER_SCALE);

        for (int scale = TASK_SCALE_STEP; scale <= MAX_TASKS; scale += TASK_SCALE_STEP) {
            System.out.printf("\n--- Menjalankan Skala %d Tasks (%d runs) ---\n", scale, RUNS_PER_SCALE);
            List<SingleRunResult> scaleRuns = new ArrayList<>();

            for (int run = 1; run <= RUNS_PER_SCALE; run++) {
                long ioSeed = 42L + (scale * 10L) + run;
                Path dataset = projectRoot.resolve("gocj_dataset/GoCJ_Dataset_" + scale + ".csv");
                List<TaskItem> tasks = loadGocjWorkload(dataset, scale, ioSeed);

                SingleRunResult res = executeSimulation(scale, run, tasks);
                scaleRuns.add(res);
                allSingleRuns.add(res);
                lastScaleRun = res;

                System.out.printf(Locale.US, "  Run %d/%d: Makespan = %.2f s | Cost = $%.2f | DI = %.4f | TOPSIS Runtime = %.2f ms\n",
                        run, RUNS_PER_SCALE, res.makespan, res.totalCost, res.degreeOfImbalance, res.algoRuntimeMs);
            }

            AggregatedResult agg = new AggregatedResult(scale, scaleRuns);
            aggregatedResults.add(agg);
            System.out.printf(Locale.US, "  => Rata-rata Skala %d: Makespan = %.2f \u00b1 %.2f s | Cost = $%.2f \u00b1 %.2f | DI = %.4f \u00b1 %.4f\n",
                    scale, agg.meanMakespan, agg.stdMakespan, agg.meanCost, agg.stdCost, agg.meanDi, agg.stdDi);
        }

        printFinalSummaryTable(aggregatedResults);

        // Ekspor ke CSV di folder docs/
        exportMetricsCsv(projectRoot.resolve("docs"), allSingleRuns);
        if (lastScaleRun != null) {
            exportVmAllocationCsv(projectRoot.resolve("docs"), lastScaleRun);
        }

        System.out.println("\n[SELESAI] Seluruh simulasi selesai dengan sukses. Data CSV telah diperbarui di folder docs/.");
    }

    // ─────────────────────────────────────────────────────────────
    // 4. EKSEKUSI ENGINE SIMULASI CLOUDSIM PLUS DENGAN 2 DATACENTER
    // ─────────────────────────────────────────────────────────────

    static SingleRunResult executeSimulation(int nTasks, int runId, List<TaskItem> tasks) {
        if (tasks.size() != nTasks) throw new IllegalArgumentException("Jumlah task tidak sesuai skala");
        CloudSimPlus simulation = new CloudSimPlus();
        DatacenterBroker broker = createBroker(simulation);
        List<Vm> vms = createVms();
        broker.submitVmList(vms);

        long started = System.nanoTime();
        int[] assignments = scheduleTasksTopsis(tasks, ALL_VM_SPECS, TIME_WEIGHT, COST_WEIGHT);
        double runtimeMs = (System.nanoTime() - started) / 1_000_000.0;

        broker.submitCloudletList(createCloudlets(tasks, assignments, vms));
        simulation.start();
        validateVmAllocation(vms);
        List<Cloudlet> finished = broker.getCloudletFinishedList();
        validateFinishedTasks(tasks, assignments, finished);
        return calculateMetrics(nTasks, runId, runtimeMs, finished);
    }

    static DatacenterBroker createBroker(CloudSimPlus simulation) {
        List<Datacenter> datacenters = new ArrayList<>();
        for (int dcIndex = 0; dcIndex < DATACENTER_COUNT; dcIndex++) {
            List<Host> hosts = createHostList(dcIndex * HOSTS_PER_DATACENTER, HOSTS_PER_DATACENTER);
            var policy = new VmAllocationPolicySimple((allocationPolicy, vm) -> {
                VmSpec spec = ALL_VM_SPECS.get(Math.toIntExact(vm.getId()));
                return Optional.of(allocationPolicy.getHostList().get(spec.hostId % HOSTS_PER_DATACENTER));
            });
            datacenters.add(new DatacenterSimple(simulation, hosts, policy));
        }
        DatacenterBroker broker = new DatacenterBrokerSimple(simulation);
        broker.setDatacenterMapper((lastDc, vm) ->
                datacenters.get(ALL_VM_SPECS.get(Math.toIntExact(vm.getId())).dcId));
        return broker;
    }

    static List<Vm> createVms() {
        List<Vm> vms = new ArrayList<>();
        for (VmSpec spec : ALL_VM_SPECS) {
            Vm vm = new VmSimple(spec.id, spec.type.mips, spec.type.pes);
            vm.setRam(spec.type.ramMb).setBw(VM_BW_MBPS).setSize(VM_STORAGE_MB)
                    .setCloudletScheduler(new CloudletSchedulerSpaceShared());
            vms.add(vm);
        }
        return vms;
    }

    static double executionTime(TaskItem task, VmType type) {
        // I/O adalah tambahan durasi tanpa contention, dikonversi menjadi MI CPU.
        // Pembulatan identik pada prediksi TOPSIS dan panjang cloudlet.
        return effectiveLengthMi(task, type) / type.mips;
    }

    private static long effectiveLengthMi(TaskItem task, VmType type) {
        return (long) Math.ceil(task.lengthMi + task.ioBytes / type.bwBytesPerSec * type.mips);
    }

    static List<Cloudlet> createCloudlets(List<TaskItem> tasks, int[] assignments, List<Vm> vms) {
        List<Cloudlet> cloudlets = new ArrayList<>();
        for (int index = 0; index < tasks.size(); index++) {
            TaskItem task = tasks.get(index);
            int vmIndex = assignments[index];
            VmSpec spec = ALL_VM_SPECS.get(vmIndex);
            Cloudlet cloudlet = new CloudletSimple(task.id, effectiveLengthMi(task, spec.type), 1);
            cloudlet.setUtilizationModelCpu(new UtilizationModelFull());
            cloudlet.setUtilizationModelRam(new UtilizationModelDynamic(0));
            cloudlet.setUtilizationModelBw(new UtilizationModelDynamic(0));
            cloudlet.setSubmissionDelay(task.arrivalTime);
            cloudlet.setVm(vms.get(vmIndex));
            cloudlets.add(cloudlet);
        }
        return cloudlets;
    }

    static void validateVmAllocation(List<Vm> vms) {
        if (vms.size() != ALL_VM_SPECS.size()) {
            throw new IllegalStateException("Jumlah VM tidak sesuai konfigurasi");
        }
        for (int index = 0; index < vms.size(); index++) {
            VmSpec spec = ALL_VM_SPECS.get(index);
            Vm vm = vms.get(index);
            Host host = vm.getHost();
            if (vm.getId() != spec.id || host == Host.NULL || host.getId() != spec.hostId) {
                throw new IllegalStateException("Pemetaan host salah untuk VM " + spec.id);
            }
            List<Host> dcHosts = host.getDatacenter().getHostList();
            if (dcHosts.size() != HOSTS_PER_DATACENTER
                    || dcHosts.stream().anyMatch(h -> h.getId() / HOSTS_PER_DATACENTER != spec.dcId)) {
                throw new IllegalStateException("Datacenter salah untuk VM " + spec.id);
            }
        }
    }

    static void validateFinishedTasks(List<TaskItem> tasks, int[] assignments, List<Cloudlet> finished) {
        if (finished.size() != tasks.size()) {
            throw new IllegalStateException("Task selesai " + finished.size() + "/" + tasks.size());
        }
        var taskIndexById = new HashMap<Long, Integer>();
        for (int index = 0; index < tasks.size(); index++) {
            TaskItem task = tasks.get(index);
            if (taskIndexById.put((long) task.id, index) != null) {
                throw new IllegalArgumentException("ID task duplikat: " + task.id);
            }
        }
        var seen = new HashSet<Long>();
        for (Cloudlet cloudlet : finished) {
            Integer taskIndex = taskIndexById.get(cloudlet.getId());
            if (taskIndex == null || !seen.add(cloudlet.getId()) || cloudlet.getStatus() != Cloudlet.Status.SUCCESS) {
                throw new IllegalStateException("Task tidak valid/duplikat/gagal: " + cloudlet.getId());
            }
            TaskItem task = tasks.get(taskIndex);
            if (cloudlet.getVm().getId() != ALL_VM_SPECS.get(assignments[taskIndex]).id) {
                throw new IllegalStateException("Task selesai pada VM yang salah: " + task.id);
            }
            if (cloudlet.getExecStartTime() + 1e-9 < task.arrivalTime) {
                throw new IllegalStateException("Task mulai sebelum arrival time: " + task.id);
            }
        }
    }

    private static SingleRunResult calculateMetrics(int nTasks, int runId, double runtimeMs,
                                                   List<Cloudlet> finished) {
        int vmCount = ALL_VM_SPECS.size();
        double[] finishTimes = new double[vmCount];
        double[] busyTimes = new double[vmCount];
        int[] taskCounts = new int[vmCount];
        for (Cloudlet cloudlet : finished) {
            int vmIndex = Math.toIntExact(cloudlet.getVm().getId());
            finishTimes[vmIndex] = Math.max(finishTimes[vmIndex], cloudlet.getFinishTime());
            busyTimes[vmIndex] += cloudlet.getActualCpuTime();
            taskCounts[vmIndex]++;
        }
        double makespan = Arrays.stream(finishTimes).max().orElse(0);
        double totalCost = 0;
        for (int index = 0; index < vmCount; index++) {
            totalCost += Math.ceil(busyTimes[index] / SECONDS_PER_HOUR) * ALL_VM_SPECS.get(index).type.hourlyRate;
        }
        // T pada DI adalah jumlah durasi task, bukan timestamp selesai (yang mencakup idle).
        var busyStats = Arrays.stream(busyTimes).summaryStatistics();
        double di = busyStats.getAverage() > 0
                ? (busyStats.getMax() - busyStats.getMin()) / busyStats.getAverage() : 0;
        return new SingleRunResult(runId, nTasks, makespan, totalCost, di, runtimeMs, busyTimes, taskCounts);
    }

    /**
     * Membuat daftar Host fisik homogen:
     * - 24 Core CPU @ 10.000 MIPS (cukup menampung 20 vCPU dari 10 VM)
     * - 64 GB RAM / 65.536 MB (cukup menampung 40 GB RAM dari 10 VM)
     * - 2 TB Storage (2.000.000 MB)
     * - 10 Gbps Bandwidth (10.000 Mbps)
     * - VmSchedulerSpaceShared (alokasi core dedicated non-preemptive)
     */
    private static List<Host> createHostList(int startId, int count) {
        List<Host> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            List<Pe> peList = new ArrayList<>();
            for (int p = 0; p < HOST_PES; p++) {
                peList.add(new PeSimple(HOST_MIPS));
            }
            Host host = new HostSimple(HOST_RAM_MB, HOST_BW_MBPS, HOST_STORAGE_MB, peList);
            host.setId(startId + i);
            host.setVmScheduler(new VmSchedulerSpaceShared());
            list.add(host);
        }
        return list;
    }

    // ─────────────────────────────────────────────────────────────
    // 5. IMPLEMENTASI MULTI-KRITERIA TOPSIS DENGAN ANTREAN PER PE
    // ─────────────────────────────────────────────────────────────

    static int[] scheduleTasksTopsis(List<TaskItem> tasks, List<VmSpec> vms, double wTime, double wCost) {
        int numVms = vms.size();
        double[][] peReadyTimes = new double[numVms][];
        int[] assignedTaskCounts = new int[numVms];
        for (int index = 0; index < numVms; index++) {
            peReadyTimes[index] = new double[vms.get(index).type.pes];
        }
        List<Integer> taskOrder = new ArrayList<>();
        for (int index = 0; index < tasks.size(); index++) taskOrder.add(index);
        taskOrder.sort(Comparator.comparing(tasks::get, TASK_ORDER));
        int[] assignments = new int[tasks.size()];

        for (int tIdx : taskOrder) {
            TaskItem task = tasks.get(tIdx);
            double[][] matrix = new double[numVms][2];

            for (int j = 0; j < numVms; j++) {
                VmSpec vm = vms.get(j);
                double execTime = executionTime(task, vm.type);
                double compTime = Math.max(task.arrivalTime, peReadyTimes[j][earliestPe(peReadyTimes[j])]) + execTime;
                double cost = execTime * (vm.type.hourlyRate / SECONDS_PER_HOUR);

                matrix[j][0] = compTime;
                matrix[j][1] = cost;
            }

            // Normalisasi Matriks Keputusan (Vektor Normalization)
            double sumSqTime = 0.0;
            double sumSqCost = 0.0;
            for (int j = 0; j < numVms; j++) {
                sumSqTime += matrix[j][0] * matrix[j][0];
                sumSqCost += matrix[j][1] * matrix[j][1];
            }
            double normTime = Math.sqrt(sumSqTime);
            double normCost = Math.sqrt(sumSqCost);

            double[][] weighted = new double[numVms][2];
            for (int j = 0; j < numVms; j++) {
                weighted[j][0] = (normTime > 0 ? (matrix[j][0] / normTime) : 0.0) * wTime;
                weighted[j][1] = (normCost > 0 ? (matrix[j][1] / normCost) : 0.0) * wCost;
            }

            // Solusi Ideal Positif (A+) dan Solusi Ideal Negatif (A-) [Cost Criteria: nilai minimum terbaik]
            double aPlusTime = Double.MAX_VALUE;
            double aPlusCost = Double.MAX_VALUE;
            double aMinusTime = -Double.MAX_VALUE;
            double aMinusCost = -Double.MAX_VALUE;

            for (int j = 0; j < numVms; j++) {
                if (weighted[j][0] < aPlusTime) aPlusTime = weighted[j][0];
                if (weighted[j][1] < aPlusCost) aPlusCost = weighted[j][1];

                if (weighted[j][0] > aMinusTime) aMinusTime = weighted[j][0];
                if (weighted[j][1] > aMinusCost) aMinusCost = weighted[j][1];
            }

            // Jarak Euclidean & Skor Kedekatan Relatif
            double maxScore = -1.0;
            int bestVmIndex = 0;

            for (int j = 0; j < numVms; j++) {
                double diffPlusTime = weighted[j][0] - aPlusTime;
                double diffPlusCost = weighted[j][1] - aPlusCost;
                double dPlus = Math.sqrt(diffPlusTime * diffPlusTime + diffPlusCost * diffPlusCost);

                double diffMinusTime = weighted[j][0] - aMinusTime;
                double diffMinusCost = weighted[j][1] - aMinusCost;
                double dMinus = Math.sqrt(diffMinusTime * diffMinusTime + diffMinusCost * diffMinusCost);

                double score = dMinus / (dPlus + dMinus + 1e-9);

                // Equal TOPSIS scores: distribute tasks before using index order.
                if (score > maxScore || (score == maxScore
                        && assignedTaskCounts[j] < assignedTaskCounts[bestVmIndex])) {
                    maxScore = score;
                    bestVmIndex = j;
                }
            }

            assignments[tIdx] = bestVmIndex;
            assignedTaskCounts[bestVmIndex]++;

            // Perbarui estimasi waktu siap VM untuk task berikutnya
            int peIndex = earliestPe(peReadyTimes[bestVmIndex]);
            peReadyTimes[bestVmIndex][peIndex] = matrix[bestVmIndex][0];
        }

        return assignments;
    }

    // ─────────────────────────────────────────────────────────────
    // 6. LOADER WORKLOAD RESMI GOCJ (GOOGLE CLOUD JOBS)
    // ─────────────────────────────────────────────────────────────

    private static int earliestPe(double[] readyTimes) {
        int earliest = 0;
        for (int index = 1; index < readyTimes.length; index++) {
            if (readyTimes[index] < readyTimes[earliest]) earliest = index;
        }
        return earliest;
    }

    static List<TaskItem> loadGocjWorkload(Path dataset, int expectedTasks, long seed) {
        Random random = new Random(seed);
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
                    double ioBytes = (50 + random.nextDouble() * 450) * BYTES_PER_MB;
                    tasks.add(new TaskItem(tasks.size(), length, sla, arrival, ioBytes));
                } catch (IllegalArgumentException error) {
                    throw new IllegalArgumentException(dataset + ": baris " + lineNumber + ": " + error.getMessage(), error);
                }
            }
        } catch (IOException error) {
            throw new UncheckedIOException("Gagal membaca dataset wajib: " + dataset, error);
        }
        if (tasks.size() != expectedTasks) {
            throw new IllegalArgumentException(dataset + ": jumlah task " + tasks.size() + ", wajib " + expectedTasks);
        }
        tasks.sort(TASK_ORDER);
        return List.copyOf(tasks);
    }

    private static Path findProjectRoot() {
        Path directory = Path.of("").toAbsolutePath().normalize();
        while (directory != null) {
            if (Files.isRegularFile(directory.resolve("java/build.gradle")) && Files.isDirectory(directory.resolve("docs"))) {
                return directory;
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("Jalankan program dari direktori proyek atau subdirektorinya");
    }

    // ─────────────────────────────────────────────────────────────
    // 7. CETAK TABEL & EKSPOR CSV KE FOLDER DOCS/
    // ─────────────────────────────────────────────────────────────

    private static void printAllocationArchitectureTable() {
        System.out.println("Tabel Arsitektur Pemetaan Virtual Machine ke Physical Host:");
        System.out.println("+---------------+--------------+-------------+---------------------------+------------+-------------+");
        System.out.println("| Datacenter    | PhysicalHost | Range VM ID | Komposisi Tipe VM (10 VM) | Total vCPU | Total RAM   |");
        System.out.println("+---------------+--------------+-------------+---------------------------+------------+-------------+");
        for (int hostId = 0; hostId < HOST_COUNT; hostId++) {
            final int selectedHost = hostId;
            List<VmSpec> specs = ALL_VM_SPECS.stream().filter(spec -> spec.hostId == selectedHost).toList();
            int pes = specs.stream().mapToInt(spec -> spec.type.pes).sum();
            long ram = specs.stream().mapToLong(spec -> spec.type.ramMb).sum();
            String composition = Arrays.stream(VmType.values())
                    .map(type -> VMS_PER_TYPE_PER_HOST[type.ordinal()] + " " + type.label)
                    .collect(java.util.stream.Collectors.joining(", "));
            System.out.printf("| Datacenter %-2d | Host %-7d | VM %02d - %02d  | %-25s | %-10d | %-11d |%n",
                    hostId / HOSTS_PER_DATACENTER + 1, hostId, specs.get(0).id,
                    specs.get(specs.size() - 1).id, composition, pes, ram);
        }
        System.out.println("+---------------+--------------+-------------+---------------------------+------------+-------------+");
        System.out.printf("Host: %d PE @ %.0f MIPS | %d MB RAM | %d MB Storage | %d Mbps BW%n%n",
                HOST_PES, HOST_MIPS, HOST_RAM_MB, HOST_STORAGE_MB, HOST_BW_MBPS);
    }

    private static void printFinalSummaryTable(List<AggregatedResult> results) {
        System.out.println("\n=========================================================================================");
        System.out.printf("  RINGKASAN TOPSIS (MEAN \u00b1 STD DEV SAMPEL, %d RUNS)%n", RUNS_PER_SCALE);
        System.out.println("=========================================================================================");
        System.out.printf("%-8s %-24s %-20s %-20s %-20s\n", "Tasks", "Makespan (s)", "Total Cost ($)", "DI", "Runtime (ms)");
        System.out.println("-----------------------------------------------------------------------------------------");
        for (AggregatedResult r : results) {
            System.out.printf(Locale.US, "%-8d %10.2f \u00b1 %-8.2f %8.2f \u00b1 %-8.2f %8.4f \u00b1 %-8.4f %8.2f \u00b1 %-8.2f\n",
                    r.nTasks, r.meanMakespan, r.stdMakespan, r.meanCost, r.stdCost, r.meanDi, r.stdDi, r.meanRuntimeMs, r.stdRuntimeMs);
        }
        System.out.println("=========================================================================================");
    }

    private static void exportMetricsCsv(Path targetDir, List<SingleRunResult> runs) {
        File csvFile = targetDir.resolve("cloudsim_metrics.csv").toFile();
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(csvFile.toPath(), StandardCharsets.UTF_8))) {
            pw.println("Workload,Tasks,Run,Makespan_s,TotalCost_USD,DI,Runtime_ms");
            for (SingleRunResult r : runs) {
                pw.printf(Locale.US, "GoCJ,%d,%d,%.6f,%.2f,%.6f,%.4f\n",
                        r.nTasks, r.runId, r.makespan, r.totalCost, r.degreeOfImbalance, r.algoRuntimeMs);
            }
            if (pw.checkError()) throw new IOException("Gagal menulis " + csvFile);
            System.out.println("[EXPORT] Data metrik berhasil diekspor ke: " + csvFile.getAbsolutePath());
        } catch (IOException e) {
            throw new UncheckedIOException("Gagal mengekspor metrics CSV", e);
        }
    }

    private static void exportVmAllocationCsv(Path targetDir, SingleRunResult runResult) {
        File csvFile = targetDir.resolve("cloudsim_vm_allocation.csv").toFile();
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(csvFile.toPath(), StandardCharsets.UTF_8))) {
            pw.println("DatacenterId,HostId,VmId,VmType,Mips,HourlyRate,BusyTime_s,TaskCount");
            for (int i = 0; i < ALL_VM_SPECS.size(); i++) {
                VmSpec spec = ALL_VM_SPECS.get(i);
                pw.printf(Locale.US, "DC_%d,Host_%d,%d,%s,%.0f,%.2f,%.2f,%d\n",
                        spec.dcId + 1, spec.hostId, spec.id, spec.type.label, spec.type.mips,
                        spec.type.hourlyRate, runResult.vmBusyTimes[i], runResult.vmTaskCounts[i]);
            }
            if (pw.checkError()) throw new IOException("Gagal menulis " + csvFile);
            System.out.println("[EXPORT] Data alokasi 80 VM berhasil diekspor ke: " + csvFile.getAbsolutePath());
        } catch (IOException e) {
            throw new UncheckedIOException("Gagal mengekspor VM allocation CSV", e);
        }
    }

}
