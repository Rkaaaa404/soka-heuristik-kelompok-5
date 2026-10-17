package com.soka.kelompok5;

import ch.qos.logback.classic.Level;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.util.Log;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.soka.kelompok5.TopsisCloudSim.*;

/** Executable regression checks; fixtures never modify the GoCJ CSV files. */
public final class TopsisCloudSimVerification {
    public static void main(String[] args) throws Exception {
        Log.setLevel(Level.ERROR);
        verifyMandatoryCsv();
        verifyPerPeScheduling();
        verifyArrivalsAndParallelExecution();
        verifySampleStandardDeviation();
        System.out.println("PASS: mandatory CSV, per-PE TOPSIS, arrivals, parallel execution, mapping, completeness, sample std dev");
    }

    private static void verifyMandatoryCsv() throws Exception {
        Path directory = Files.createTempDirectory(Path.of("build"), "workload-verification-");
        Path fixture = directory.resolve("workload.csv");
        try {
            Files.writeString(fixture, "25000,3,10\n15000,1,0\n20000,2,10\n");
            List<TaskItem> tasks = loadGocjWorkload(fixture, 3, 42);
            check(tasks.get(0).id == 1 && tasks.get(1).id == 0 && tasks.get(2).id == 2,
                    "Tasks must be ordered by arrival, then ID");
            List<TaskItem> repeated = loadGocjWorkload(fixture, 3, 42);
            List<TaskItem> differentSeed = loadGocjWorkload(fixture, 3, 43);
            for (int index = 0; index < tasks.size(); index++) {
                TaskItem task = tasks.get(index);
                check(task.ioBytes >= 50_000_000 && task.ioBytes < 500_000_000, "I/O must use decimal MB");
                check(task.ioBytes == repeated.get(index).ioBytes, "I/O seed must be reproducible");
                check(task.ioBytes != differentSeed.get(index).ioBytes, "I/O must vary across seeds");
            }
            expectFailure(IllegalArgumentException.class, () -> loadGocjWorkload(fixture, 4, 42));
            expectFailure(IllegalArgumentException.class, () -> loadGocjWorkload(fixture, 2, 42));
            expectFailure(UncheckedIOException.class, () -> loadGocjWorkload(directory.resolve("missing.csv"), 3, 42));
            for (String invalid : List.of("0,1,0", "15000,4,0", "15000,1,-1", "NaN,1,0",
                    "Infinity,1,0", "15000,1,NaN", "15000,1,Infinity", "15000,1", "15000,1,0,extra",
                    "length,sla,arrival", "")) {
                Files.writeString(fixture, invalid + "\n");
                expectFailure(IllegalArgumentException.class, () -> loadGocjWorkload(fixture, 1, 42));
            }
        } finally {
            Files.deleteIfExists(fixture);
            Files.delete(directory);
        }
    }

    private static void verifyPerPeScheduling() {
        List<VmSpec> vms = List.of(new VmSpec(4, VmType.MEDIUM, 0, 0), new VmSpec(0, VmType.SMALL, 0, 0));
        List<TaskItem> tasks = new ArrayList<>();
        for (int id = 0; id < 5; id++) tasks.add(new TaskItem(id, 250_000, 2, 0, 0));
        check(Arrays.equals(scheduleTasksTopsis(tasks, vms, 1, 0), new int[]{0, 0, 0, 0, 1}),
                "Medium VM must schedule two tasks in parallel before predicting a longer queue");
        tasks.add(new TaskItem(5, 250_000, 2, 1000, 0));
        check(scheduleTasksTopsis(tasks, vms, 1, 0)[5] == 0, "Arrival must release an idle PE before estimating completion");
        List<TaskItem> reversed = new ArrayList<>(tasks);
        java.util.Collections.reverse(reversed);
        int[] assignments = scheduleTasksTopsis(reversed, vms, 1, 0);
        check(Arrays.equals(assignments, new int[]{0, 1, 0, 0, 0, 0}),
                "Scheduling order must not depend on input list order");
        List<VmSpec> identical = List.of(new VmSpec(0, VmType.SMALL, 0, 0),
                new VmSpec(10, VmType.SMALL, 0, 1));
        List<TaskItem> spaced = new ArrayList<>();
        for (int id = 0; id < 4; id++) spaced.add(new TaskItem(id, 100_000, 2, id * 1000, 0));
        check(Arrays.equals(scheduleTasksTopsis(spaced, identical, 0.5, 0.5), new int[]{0, 1, 0, 1}),
                "Equal scores must distribute tasks across identical idle VMs");
    }

    private static void verifyArrivalsAndParallelExecution() {
        CloudSimPlus simulation = new CloudSimPlus();
        var broker = createBroker(simulation);
        var vms = createVms();
        broker.submitVmList(vms);
        List<TaskItem> tasks = new ArrayList<>();
        List<Integer> vmIndices = new ArrayList<>();
        // Two concurrent 100-second Medium tasks, then a third queued task.
        for (int count = 0; count < 3; count++) {
            tasks.add(new TaskItem(tasks.size(), 250_000, 2, 0, 0));
            vmIndices.add(4);
        }
        // Four concurrent 100-second Large tasks, then a fifth queued task.
        for (int count = 0; count < 5; count++) {
            tasks.add(new TaskItem(tasks.size(), 500_000, 2, 0, 0));
            vmIndices.add(8);
        }
        tasks.add(new TaskItem(tasks.size(), 100_000, 3, 1000, 0));
        vmIndices.add(0);
        int[] assignments = vmIndices.stream().mapToInt(Integer::intValue).toArray();
        broker.submitCloudletList(createCloudlets(tasks, assignments, vms));
        simulation.start();
        validateVmAllocation(vms);
        List<Cloudlet> finished = broker.getCloudletFinishedList();
        validateFinishedTasks(tasks, assignments, finished);
        var byId = new java.util.HashMap<Long, Cloudlet>();
        for (Cloudlet cloudlet : finished) byId.put(cloudlet.getId(), cloudlet);
        for (long id : List.of(0L, 1L, 3L, 4L, 5L, 6L)) {
            check(byId.get(id).getFinishTime() < 102, "Task should finish in the first parallel wave: " + id);
        }
        for (long id : List.of(2L, 7L)) {
            check(byId.get(id).getExecStartTime() >= 100 && byId.get(id).getFinishTime() < 203,
                    "Task must wait for a free PE: " + id);
        }
        check(byId.get(8L).getExecStartTime() >= 1000 && byId.get(8L).getFinishTime() < 1102,
                "Late task must wait until arrival, then execute");
        check(vms.stream().filter(vm -> vm.getHost().getId() >= 4 && vm.getHost().getId() <= 7).count() == 40, "40 VMs must map to DC2 hosts");
        expectFailure(IllegalStateException.class,
                () -> validateFinishedTasks(tasks, assignments, finished.subList(0, finished.size() - 1)));
        List<Cloudlet> duplicates = new ArrayList<>(finished);
        duplicates.set(duplicates.size() - 1, duplicates.get(0));
        expectFailure(IllegalStateException.class, () -> validateFinishedTasks(tasks, assignments, duplicates));
    }

    private static void verifySampleStandardDeviation() {
        List<SingleRunResult> runs = new ArrayList<>();
        for (int value = 1; value <= 3; value++) {
            runs.add(new SingleRunResult(value, 1, value, value, value, value, new double[0], new int[0]));
        }
        AggregatedResult aggregate = new AggregatedResult(1, runs);
        check(aggregate.meanMakespan == 2 && aggregate.stdMakespan == 1, "Std dev must use n-1");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void expectFailure(Class<? extends RuntimeException> expected, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException error) {
            if (expected.isInstance(error)) return;
            throw new AssertionError("Expected " + expected.getSimpleName() + ", got " + error, error);
        }
        throw new AssertionError("Expected " + expected.getSimpleName());
    }
}
