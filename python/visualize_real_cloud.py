"""Generate charts from the real AWS workload output files."""

import argparse
import json
import math
import os

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import pandas as pd


TASK_COLUMNS = {
    "TaskId", "Arrival_s", "Complete_s", "Execution_s", "HostId", "VmId"
}
VM_COLUMNS = {
    "HostId", "VmId", "Slots", "ModeledHourlyRate_USD",
    "MeasuredIterationsPerSecond", "BusyTime_s", "TaskCount"
}


def parse_args():
    parser = argparse.ArgumentParser(
        description="Generate visualizations for a real AWS TOPSIS execution."
    )
    parser.add_argument("--data-dir", required=True,
                        help="Directory containing real_tasks.csv, real_vm_allocation.csv, and real_summary.json")
    parser.add_argument("--out-dir", default=None,
                        help="Chart output directory (default: same as --data-dir)")
    parser.add_argument("--scenario-title", default=None,
                        help="Optional title suffix, for example 'AWS demo - 20 tasks'")
    return parser.parse_args()


def load_outputs(data_dir):
    task_path = os.path.join(data_dir, "real_tasks.csv")
    vm_path = os.path.join(data_dir, "real_vm_allocation.csv")
    summary_path = os.path.join(data_dir, "real_summary.json")

    for path in (task_path, vm_path, summary_path):
        if not os.path.isfile(path):
            raise FileNotFoundError(f"Required AWS result file not found: {path}")

    tasks = pd.read_csv(task_path)
    vms = pd.read_csv(vm_path)
    with open(summary_path, encoding="utf-8") as summary_file:
        summary = json.load(summary_file)

    missing_task_columns = TASK_COLUMNS.difference(tasks.columns)
    missing_vm_columns = VM_COLUMNS.difference(vms.columns)
    if missing_task_columns:
        raise ValueError(f"{task_path} is missing columns: {sorted(missing_task_columns)}")
    if missing_vm_columns:
        raise ValueError(f"{vm_path} is missing columns: {sorted(missing_vm_columns)}")
    if summary.get("mode") != "real":
        raise ValueError(f"{summary_path} is not a real workload result (mode={summary.get('mode')!r})")
    if summary.get("status") != "SUCCESS":
        raise ValueError(
            f"{summary_path} does not describe a successful run "
            f"(status={summary.get('status')!r})"
        )
    if tasks.empty or vms.empty:
        raise ValueError("AWS result CSV files must contain at least one data row.")

    return tasks, vms, summary


def plot_evaluation_metrics(tasks, vms, summary, out_dir, scenario_title):
    fig, axes = plt.subplots(2, 2, figsize=(14, 9))
    title = "Evaluasi Workload Riil AWS TOPSIS"
    if scenario_title:
        title += f" ({scenario_title})"
    fig.suptitle(title, fontsize=14, fontweight="bold")

    ordered = tasks.sort_values("TaskId")
    axes[0, 0].plot(
        ordered["TaskId"], ordered["Execution_s"],
        marker="o", color="#1f77b4", linewidth=1.8
    )
    axes[0, 0].set_title("Durasi Eksekusi per Task")
    axes[0, 0].set_xlabel("Task ID")
    axes[0, 0].set_ylabel("Execution (detik)")
    axes[0, 0].grid(True, linestyle="--", alpha=0.5)

    axes[0, 1].scatter(
        ordered["Arrival_s"], ordered["Complete_s"],
        c=ordered["TaskId"], cmap="viridis", s=28
    )
    axes[0, 1].plot(
        [0, max(ordered["Complete_s"].max(), 1)],
        [0, max(ordered["Complete_s"].max(), 1)],
        linestyle=":", color="gray"
    )
    axes[0, 1].set_title("Kedatangan dan Penyelesaian Task")
    axes[0, 1].set_xlabel("Arrival (detik)")
    axes[0, 1].set_ylabel("Complete (detik)")
    axes[0, 1].grid(True, linestyle="--", alpha=0.5)

    vm_costs = vms.assign(
        ModeledCost_USD=(
            (vms["BusyTime_s"] / 3600).apply(math.ceil)
            * vms["ModeledHourlyRate_USD"]
        )
    )
    axes[1, 0].bar(
        vm_costs["VmId"], vm_costs["ModeledCost_USD"],
        color="#2ca02c", edgecolor="black", linewidth=0.6
    )
    axes[1, 0].set_title("Biaya Model per VM")
    axes[1, 0].set_xlabel("Virtual Machine")
    axes[1, 0].set_ylabel("Biaya model (USD)")
    axes[1, 0].tick_params(axis="x", rotation=25)
    axes[1, 0].grid(True, linestyle="--", alpha=0.5, axis="y")

    axes[1, 1].axis("off")
    summary_lines = [
        ("Tasks selesai", f"{int(summary['tasksCompleted']):,} / {int(summary['tasksRequested']):,}"),
        ("Makespan", f"{float(summary['makespanSeconds']):.4f} s"),
        ("Total biaya model", f"${float(summary['modeledCostUsd']):.4f}"),
        ("Degree of Imbalance", f"{float(summary['degreeOfImbalance']):.6f}"),
        ("Runtime scheduler", f"{float(summary['schedulerRuntimeMs']):.4f} ms"),
    ]
    axes[1, 1].set_title("Ringkasan Metrik Eksekusi")
    table = axes[1, 1].table(
        cellText=[[label, value] for label, value in summary_lines],
        colLabels=["Metrik", "Nilai"],
        cellLoc="left",
        colLoc="left",
        loc="center",
        colWidths=[0.58, 0.4],
    )
    table.auto_set_font_size(False)
    table.set_fontsize(10)
    table.scale(1, 1.8)

    fig.tight_layout(rect=(0, 0, 1, 0.95))
    path = os.path.join(out_dir, "real_evaluation_metrics.png")
    fig.savefig(path, dpi=300, bbox_inches="tight")
    plt.close(fig)
    print(f"[SUCCESS] Grafik evaluasi metrik tersimpan di: {path}")


def plot_host_distribution(vms, out_dir, scenario_title):
    host_summary = vms.groupby("HostId", sort=False).agg(
        TaskCount=("TaskCount", "sum"),
        BusyTime_s=("BusyTime_s", "sum"),
    )
    fig, axes = plt.subplots(1, 2, figsize=(13, 5))
    hosts = host_summary.index
    axes[0].bar(hosts, host_summary["TaskCount"], color="#1f77b4", edgecolor="black")
    axes[0].set_title("Task Dialokasikan per Host")
    axes[0].set_ylabel("Jumlah task")
    axes[0].grid(True, linestyle="--", alpha=0.5, axis="y")
    axes[1].bar(hosts, host_summary["BusyTime_s"], color="#ff7f0e", edgecolor="black")
    axes[1].set_title("Akumulasi Busy Time per Host")
    axes[1].set_ylabel("Busy time (detik)")
    axes[1].grid(True, linestyle="--", alpha=0.5, axis="y")
    title = "Distribusi Beban Workload AWS per Host"
    if scenario_title:
        title += f" ({scenario_title})"
    fig.suptitle(title, fontsize=13, fontweight="bold")
    fig.tight_layout()
    path = os.path.join(out_dir, "real_datacenter_host_distribution.png")
    fig.savefig(path, dpi=300, bbox_inches="tight")
    plt.close(fig)
    print(f"[SUCCESS] Grafik distribusi host tersimpan di: {path}")


def plot_vm_distribution(vms, out_dir, scenario_title):
    ordered = vms.sort_values(["HostId", "VmId"])
    labels = ordered["VmId"]
    fig, axes = plt.subplots(1, 3, figsize=(16, 5))
    charts = [
        ("TaskCount", "Jumlah Task per VM", "Jumlah task", "#2b5c8f"),
        ("BusyTime_s", "Busy Time per VM", "Busy time (detik)", "#d95f02"),
        ("MeasuredIterationsPerSecond", "Throughput Kalibrasi per VM",
         "Iterasi per detik", "#2ca02c"),
    ]
    for axis, (column, title, ylabel, color) in zip(axes, charts):
        values = pd.to_numeric(ordered[column], errors="coerce").fillna(0)
        axis.bar(labels, values, color=color, edgecolor="black", linewidth=0.6)
        axis.set_title(title)
        axis.set_ylabel(ylabel)
        axis.tick_params(axis="x", rotation=30)
        axis.grid(True, linestyle="--", alpha=0.5, axis="y")

    title = f"Analisis Pemanfaatan VM AWS ({int(ordered['TaskCount'].sum()):,} Tasks)"
    if scenario_title:
        title += f" - {scenario_title}"
    fig.suptitle(title, fontsize=13, fontweight="bold")
    fig.tight_layout()
    path = os.path.join(out_dir, "real_vm_workload_distribution.png")
    fig.savefig(path, dpi=300, bbox_inches="tight")
    plt.close(fig)
    print(f"[SUCCESS] Grafik distribusi workload VM tersimpan di: {path}")


def main():
    args = parse_args()
    data_dir = os.path.abspath(args.data_dir)
    out_dir = os.path.abspath(args.out_dir if args.out_dir else data_dir)
    os.makedirs(out_dir, exist_ok=True)

    tasks, vms, summary = load_outputs(data_dir)
    plot_evaluation_metrics(tasks, vms, summary, out_dir, args.scenario_title)
    plot_host_distribution(vms, out_dir, args.scenario_title)
    plot_vm_distribution(vms, out_dir, args.scenario_title)
    print(f"\n[SELESAI] Grafik hasil workload AWS tersimpan di '{out_dir}'.")


if __name__ == "__main__":
    main()
