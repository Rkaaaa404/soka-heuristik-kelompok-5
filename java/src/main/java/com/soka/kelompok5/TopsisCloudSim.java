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
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;

/**
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
 * SKENARIO PENGUJIAN:
 * - 10 Variasi Skala Task: 1.000, 2.000, ..., 10.000 tasks.
 * - 3 Kali Repeated Runs per variasi untuk uji konsistensi statistik (Mean ± Std Dev).
 * ==============================================================================
 */
public class TopsisCloudSim {

    // Konfigurasi host default & simulasi
    public static final int HOSTS_PER_DATACENTER = 4;
    public static final int[] VMS_PER_TYPE_PER_HOST = {4, 4, 2};
    public static final int VMS_PER_HOST = Arrays.stream(VMS_PER_TYPE_PER_HOST).sum();
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
    // ─────────────────────────────────────────────────────────────
    // 1B. KONFIGURASI TOPOLOGI CLOUD (DATACENTER, HOST, ALOKASI VM)
    // ─────────────────────────────────────────────────────────────

    public static class HostConfig {
        public final int smallVms;
        public final int mediumVms;
        public final int largeVms;

        public HostConfig(int smallVms, int mediumVms, int largeVms) {
            this.smallVms = smallVms;
            this.mediumVms = mediumVms;
            this.largeVms = largeVms;
        }

        public int totalVms() {
            return smallVms + mediumVms + largeVms;
        }

        public int totalPes() {
            return smallVms * VmType.SMALL.pes + mediumVms * VmType.MEDIUM.pes + largeVms * VmType.LARGE.pes;
        }

        public long totalRamMb() {
            return smallVms * VmType.SMALL.ramMb + mediumVms * VmType.MEDIUM.ramMb + largeVms * VmType.LARGE.ramMb;
        }
    }

    public static class DatacenterConfig {
        public final String name;
        public final List<HostConfig> hosts;

        public DatacenterConfig(String name, List<HostConfig> hosts) {
            this.name = name;
            this.hosts = List.copyOf(hosts);
        }

        /** Helper jika semua host dalam satu datacenter memiliki jumlah VM yang seragam */
        public static DatacenterConfig ofUniformHosts(String name, int hostCount, int smallVms, int mediumVms, int largeVms) {
            List<HostConfig> list = new ArrayList<>();
            for (int i = 0; i < hostCount; i++) {
                list.add(new HostConfig(smallVms, mediumVms, largeVms));
            }
            return new DatacenterConfig(name, list);
        }
    }

    /**
     * Konfigurasi Topologi Infrastruktur Cloud: Datacenter, Host, dan Alokasi VM.
     *
     * KONFIGURASI BAWAAN (AS-IS):
     * - Datacenter 1: 4 Host (Host 0-3), masing-masing 4 Small, 4 Medium, 2 Large (VM 0-39)
     * - Datacenter 2: 4 Host (Host 4-7), masing-masing 4 Small, 4 Medium, 2 Large (VM 40-79)
     *
     * CARA MENAMBAHKAN DATACENTER 3 (atau datacenter berikutnya):
     * Cukup tambahkan DatacenterConfig baru ke dalam list 'topology' di bawah!
     *
     * Contoh:
     * // Jika semua host di Datacenter 3 seragam (misal 2 host, tiap host 3 Small, 2 Medium, 1 Large):
     * topology.add(DatacenterConfig.ofUniformHosts("Datacenter 3", 2, 3, 2, 1));
     *
     * // Jika tiap host di Datacenter 3 memiliki komposisi VM yang berbeda:
     * topology.add(new DatacenterConfig("Datacenter 3", List.of(
     *     new HostConfig(5, 2, 1), // Host 8: 5 Small, 2 Medium, 1 Large
     *     new HostConfig(2, 4, 2)  // Host 9: 2 Small, 4 Medium, 2 Large
     * )));
     */
    public static List<DatacenterConfig> buildInfrastructureTopology() {
        List<DatacenterConfig> topology = new ArrayList<>();

        // ── DATACENTER 1 (AS-IS: 4 Host, 4 Small, 4 Medium, 2 Large) ──
        topology.add(DatacenterConfig.ofUniformHosts("Datacenter 1", 4, 4, 4, 2));

        // ── DATACENTER 2 (AS-IS: 4 Host, 4 Small, 4 Medium, 2 Large) ──
        topology.add(DatacenterConfig.ofUniformHosts("Datacenter 2", 4, 4, 4, 2));

        // ── TAMBAHKAN DATACENTER 3 DI SINI JIKA DIPERLUKAN (CONTOH SIAP PAKAI): ──
        // topology.add(DatacenterConfig.ofUniformHosts("Datacenter 3", 2, 3, 2, 1));

        return List.copyOf(topology);
    }

    public static final List<DatacenterConfig> TOPOLOGY = buildInfrastructureTopology();
    public static final int DATACENTER_COUNT = TOPOLOGY.size();
    public static final int HOST_COUNT = TOPOLOGY.stream().mapToInt(dc -> dc.hosts.size()).sum();

    private static Map<Integer, Integer> buildHostToDcMap() {
        Map<Integer, Integer> map = new HashMap<>();
        int currentHostId = 0;
        for (int dcIndex = 0; dcIndex < TOPOLOGY.size(); dcIndex++) {
            for (int h = 0; h < TOPOLOGY.get(dcIndex).hosts.size(); h++) {
                map.put(currentHostId++, dcIndex);
            }
        }
        return Map.copyOf(map);
    }

    private static final Map<Integer, Integer> HOST_TO_DC_ID = buildHostToDcMap();

    /**
     * Membangun daftar VM heterogen secara deterministik dari konfigurasi TOPOLOGY:
     * - Host 0-3 di Datacenter 1 (VM 0-39)
     * - Host 4-7 di Datacenter 2 (VM 40-79)
     * - Host & VM tambahan jika ada Datacenter baru di TOPOLOGY
     */
    private static List<VmSpec> buildVmSpecs() {
        List<VmSpec> list = new ArrayList<>();
        int currentHostId = 0;
        for (int dcIndex = 0; dcIndex < TOPOLOGY.size(); dcIndex++) {
            DatacenterConfig dc = TOPOLOGY.get(dcIndex);
            for (HostConfig hc : dc.hosts) {
                int hostId = currentHostId++;
                for (int i = 0; i < hc.smallVms; i++) {
                    list.add(new VmSpec(list.size(), VmType.SMALL, dcIndex, hostId));
                }
                for (int i = 0; i < hc.mediumVms; i++) {
                    list.add(new VmSpec(list.size(), VmType.MEDIUM, dcIndex, hostId));
                }
                for (int i = 0; i < hc.largeVms; i++) {
                    list.add(new VmSpec(list.size(), VmType.LARGE, dcIndex, hostId));
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
        System.out.printf("  Arsitektur: %d Datacenter, %d Host, %d VM Heterogen%n",
                DATACENTER_COUNT, HOST_COUNT, ALL_VM_SPECS.size());
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
            System.out.printf(Locale.US, "  => Rata-rata Skala %5d: Makespan = %8.2f +/- %-5.2f s | Cost = $%5.2f +/- %-4.2f | DI = %6.4f +/- %-6.4f | Runtime = %5.2f +/- %-5.2f ms%n",
                    scale, agg.meanMakespan, agg.stdMakespan, agg.meanCost, agg.stdCost, agg.meanDi, agg.stdDi, agg.meanRuntimeMs, agg.stdRuntimeMs);
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
        int currentHostId = 0;

        for (int dcIndex = 0; dcIndex < TOPOLOGY.size(); dcIndex++) {
            DatacenterConfig dcConfig = TOPOLOGY.get(dcIndex);
            List<Host> hosts = new ArrayList<>();
            for (HostConfig hc : dcConfig.hosts) {
                int hostPes = Math.max(HOST_PES, hc.totalPes());
                long hostRam = Math.max(HOST_RAM_MB, hc.totalRamMb());
                hosts.add(createHost(currentHostId++, hostPes, HOST_MIPS, hostRam, HOST_BW_MBPS, HOST_STORAGE_MB));
            }
            var policy = new VmAllocationPolicySimple((allocationPolicy, vm) -> {
                VmSpec spec = ALL_VM_SPECS.get(Math.toIntExact(vm.getId()));
                return allocationPolicy.getHostList().stream()
                        .filter(h -> h.getId() == spec.hostId)
                        .findFirst();
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
            if (dcHosts.stream().noneMatch(h -> h.getId() == spec.hostId)
                    || dcHosts.stream().anyMatch(h -> HOST_TO_DC_ID.getOrDefault(Math.toIntExact(h.getId()), -1) != spec.dcId)) {
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
    private static Host createHost(int id, int pes, double mips, long ramMb, long bwMbps, long storageMb) {
        List<Pe> peList = new ArrayList<>();
        for (int p = 0; p < pes; p++) {
            peList.add(new PeSimple(mips));
        }
        Host host = new HostSimple(ramMb, bwMbps, storageMb, peList);
        host.setId(id);
        host.setVmScheduler(new VmSchedulerSpaceShared());
        return host;
    }

    static List<Host> createHostList(int startId, int count) {
        List<Host> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(createHost(startId + i, HOST_PES, HOST_MIPS, HOST_RAM_MB, HOST_BW_MBPS, HOST_STORAGE_MB));
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
        System.out.println("+---------------+--------------+-------------+------------------------------------+------------+-------------+");
        System.out.println("| Datacenter    | PhysicalHost | Range VM ID | Komposisi Tipe VM                  | Total vCPU | Total RAM   |");
        System.out.println("+---------------+--------------+-------------+------------------------------------+------------+-------------+");
        List<Integer> hostIds = ALL_VM_SPECS.stream().map(s -> s.hostId).distinct().sorted().toList();
        for (int hostId : hostIds) {
            final int selectedHost = hostId;
            List<VmSpec> specs = ALL_VM_SPECS.stream().filter(spec -> spec.hostId == selectedHost).toList();
            int pes = specs.stream().mapToInt(spec -> spec.type.pes).sum();
            long ram = specs.stream().mapToLong(spec -> spec.type.ramMb).sum();
            int dcId = specs.get(0).dcId;
            String composition = Arrays.stream(VmType.values())
                    .map(type -> {
                        long count = specs.stream().filter(s -> s.type == type).count();
                        return count > 0 ? (count + " " + type.label) : null;
                    })
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.joining(", "));
            String vmRange = specs.isEmpty() ? "Tidak Ada VM " : (specs.size() == 1
                    ? String.format("VM %02d      ", specs.get(0).id)
                    : String.format("VM %02d - %02d ", specs.get(0).id, specs.get(specs.size() - 1).id));
            System.out.printf("| Datacenter %-2d | Host %-7d | %s | %-34s | %-10d | %-11d |%n",
                    dcId + 1, hostId, vmRange, composition, pes, ram);
        }
        System.out.println("+---------------+--------------+-------------+------------------------------------+------------+-------------+");
        System.out.printf("Standar Host Default: %d PE @ %.0f MIPS | %d MB RAM | %d MB Storage | %d Mbps BW%n%n",
                HOST_PES, HOST_MIPS, HOST_RAM_MB, HOST_STORAGE_MB, HOST_BW_MBPS);
    }

    private static void printFinalSummaryTable(List<AggregatedResult> results) {
        String sep = "+-------+-----------------------+------------------+---------------------+------------------+";
        System.out.println("\n" + sep);
        System.out.printf(Locale.US, "|                 RINGKASAN METRIK TOPSIS (MEAN +/- STD DEV, %d RUNS)                  |%n", RUNS_PER_SCALE);
        System.out.println(sep);
        System.out.println("| Tasks |     Makespan (s)      |  Total Cost ($)  |   DI (Imbalance)    |   Runtime (ms)   |");
        System.out.println("|       |    (Mean +/- Std)     |  (Mean +/- Std)  |   (Mean +/- Std)    |  (Mean +/- Std)  |");
        System.out.println(sep);
        for (AggregatedResult r : results) {
            String mStr = String.format(Locale.US, "%9.2f +/- %-5.2f", r.meanMakespan, r.stdMakespan);
            String cStr = String.format(Locale.US, "%5.2f +/- %-4.2f", r.meanCost, r.stdCost);
            String dStr = String.format(Locale.US, "%6.4f +/- %-6.4f", r.meanDi, r.stdDi);
            String rStr = String.format(Locale.US, "%6.2f +/- %-5.2f", r.meanRuntimeMs, r.stdRuntimeMs);
            System.out.printf(Locale.US, "| %5d |  %-19s  |  %-14s  |  %-17s  | %-16s |%n",
                    r.nTasks, mStr, cStr, dStr, rStr);
        }
        System.out.println(sep);
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
