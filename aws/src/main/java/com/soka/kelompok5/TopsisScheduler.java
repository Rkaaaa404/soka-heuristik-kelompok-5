package com.soka.kelompok5;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Multi-criteria TOPSIS scheduler with per-PE queue tracking.
 * Minimizes both predicted completion time and modeled execution cost.
 */
public final class TopsisScheduler {
    private static final double SECONDS_PER_HOUR = 3600.0;
    private static final double EPSILON = 1e-9;

    private TopsisScheduler() {}

    public record Resource(int slots, double hourlyRate) {
        public Resource {
            if (slots <= 0) {
                throw new IllegalArgumentException("Slot resource harus positif: " + slots);
            }
            if (!Double.isFinite(hourlyRate) || hourlyRate < 0) {
                throw new IllegalArgumentException("Tarif per jam resource harus nonnegatif dan finite: " + hourlyRate);
            }
        }
    }

    @FunctionalInterface
    public interface DurationEstimator {
        double seconds(GocjWorkload.TaskItem task, int resourceIndex);
    }

    public static int[] schedule(List<GocjWorkload.TaskItem> tasks,
                                 List<Resource> resources,
                                 DurationEstimator estimator,
                                 double timeWeight,
                                 double costWeight) {
        if (tasks == null) throw new IllegalArgumentException("Daftar task tidak boleh null");
        if (resources == null) throw new IllegalArgumentException("Daftar resource tidak boleh null");
        if (estimator == null) throw new IllegalArgumentException("Estimator durasi tidak boleh null");
        if (!Double.isFinite(timeWeight) || timeWeight < 0
                || !Double.isFinite(costWeight) || costWeight < 0
                || (timeWeight + costWeight <= 0)) {
            throw new IllegalArgumentException("Bobot TOPSIS harus bernilai nonnegatif dan finite dengan jumlah > 0");
        }
        if (tasks.isEmpty()) {
            return new int[0];
        }
        if (resources.isEmpty()) {
            throw new IllegalArgumentException("Daftar resource tidak boleh kosong untuk task yang tidak kosong");
        }

        int numResources = resources.size();
        double[][] peReadyTimes = new double[numResources][];
        int[] assignedTaskCounts = new int[numResources];
        for (int i = 0; i < numResources; i++) {
            peReadyTimes[i] = new double[resources.get(i).slots()];
        }

        List<Integer> taskOrder = new ArrayList<>(tasks.size());
        for (int index = 0; index < tasks.size(); index++) taskOrder.add(index);
        taskOrder.sort(Comparator.comparingDouble((Integer idx) -> tasks.get(idx).arrivalTime)
                .thenComparingInt(idx -> tasks.get(idx).id));

        int[] assignments = new int[tasks.size()];

        for (int tIdx : taskOrder) {
            GocjWorkload.TaskItem task = tasks.get(tIdx);
            double[][] matrix = new double[numResources][2];

            for (int j = 0; j < numResources; j++) {
                Resource res = resources.get(j);
                double execTime = estimator.seconds(task, j);
                if (!Double.isFinite(execTime) || execTime <= 0) {
                    throw new IllegalArgumentException("Estimasi durasi harus positif dan finite: " + execTime);
                }
                int earliestPeIndex = earliestPe(peReadyTimes[j]);
                double compTime = Math.max(task.arrivalTime, peReadyTimes[j][earliestPeIndex]) + execTime;
                double cost = execTime * (res.hourlyRate() / SECONDS_PER_HOUR);

                matrix[j][0] = compTime;
                matrix[j][1] = cost;
            }

            // Normalisasi Vektor
            double sumSqTime = 0.0;
            double sumSqCost = 0.0;
            for (int j = 0; j < numResources; j++) {
                sumSqTime += matrix[j][0] * matrix[j][0];
                sumSqCost += matrix[j][1] * matrix[j][1];
            }
            double normTime = Math.sqrt(sumSqTime);
            double normCost = Math.sqrt(sumSqCost);

            double[][] weighted = new double[numResources][2];
            for (int j = 0; j < numResources; j++) {
                weighted[j][0] = (normTime > 0 ? (matrix[j][0] / normTime) : 0.0) * timeWeight;
                weighted[j][1] = (normCost > 0 ? (matrix[j][1] / normCost) : 0.0) * costWeight;
            }

            // Solusi Ideal Positif (A+) dan Solusi Ideal Negatif (A-) [Kriteria biaya/waktu: minimum terbaik]
            double aPlusTime = Double.MAX_VALUE;
            double aPlusCost = Double.MAX_VALUE;
            double aMinusTime = -Double.MAX_VALUE;
            double aMinusCost = -Double.MAX_VALUE;

            for (int j = 0; j < numResources; j++) {
                if (weighted[j][0] < aPlusTime) aPlusTime = weighted[j][0];
                if (weighted[j][1] < aPlusCost) aPlusCost = weighted[j][1];

                if (weighted[j][0] > aMinusTime) aMinusTime = weighted[j][0];
                if (weighted[j][1] > aMinusCost) aMinusCost = weighted[j][1];
            }

            // Jarak Euclidean & Skor Kedekatan Relatif
            double maxScore = -1.0;
            int bestResourceIndex = 0;

            for (int j = 0; j < numResources; j++) {
                double diffPlusTime = weighted[j][0] - aPlusTime;
                double diffPlusCost = weighted[j][1] - aPlusCost;
                double dPlus = Math.sqrt(diffPlusTime * diffPlusTime + diffPlusCost * diffPlusCost);

                double diffMinusTime = weighted[j][0] - aMinusTime;
                double diffMinusCost = weighted[j][1] - aMinusCost;
                double dMinus = Math.sqrt(diffMinusTime * diffMinusTime + diffMinusCost * diffMinusCost);

                double score = dMinus / (dPlus + dMinus + EPSILON);

                if (score > maxScore || (score == maxScore
                        && assignedTaskCounts[j] < assignedTaskCounts[bestResourceIndex])) {
                    maxScore = score;
                    bestResourceIndex = j;
                }
            }

            assignments[tIdx] = bestResourceIndex;
            assignedTaskCounts[bestResourceIndex]++;

            int peIndex = earliestPe(peReadyTimes[bestResourceIndex]);
            peReadyTimes[bestResourceIndex][peIndex] = matrix[bestResourceIndex][0];
        }

        return assignments;
    }

    private static int earliestPe(double[] readyTimes) {
        int earliest = 0;
        for (int index = 1; index < readyTimes.length; index++) {
            if (readyTimes[index] < readyTimes[earliest]) {
                earliest = index;
            }
        }
        return earliest;
    }
}
