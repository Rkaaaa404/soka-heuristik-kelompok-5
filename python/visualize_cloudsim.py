"""
Visualisasi Hasil Simulasi CloudSim Plus & Evaluasi Penjadwalan TOPSIS (SOKA 5)
Mendukung eksekusi multi-skenario secara dinamis:
- Skala 1.000 s/d 10.000 tasks (10 variasi skala, 3 repeated runs)
- Konfigurasi arbitrary Datacenter, Physical Host, dan VM Heterogen
- Ekspor visualisasi ke target direktori (CLI arguments: --data-dir, --out-dir, --scenario-title)
"""

import os
import sys
import argparse
import numpy as np
import pandas as pd
import matplotlib

matplotlib.use('Agg')  # Export files without requiring a desktop/Tk installation.
import matplotlib.pyplot as plt
from matplotlib.patches import Patch

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_DOCS_DIR = os.path.join(BASE_DIR, 'docs')


def parse_args():
    parser = argparse.ArgumentParser(description="Visualisasi Hasil Simulasi CloudSim Plus & TOPSIS")
    parser.add_argument("--data-dir", default=DEFAULT_DOCS_DIR,
                        help="Direktori input CSV (default: docs/)")
    parser.add_argument("--out-dir", default=None,
                        help="Direktori output grafik (default: sama dengan data-dir)")
    parser.add_argument("--scenario-title", default=None,
                        help="Keterangan judul skenario (misal: 'Scenario 1 (2 DC, 8 Host, 80 VM)')")
    return parser.parse_args()


def load_metrics_data(data_dir):
    csv_path = os.path.join(data_dir, 'cloudsim_metrics.csv')
    if not os.path.exists(csv_path):
        raise FileNotFoundError(f"File metrik tidak ditemukan: {csv_path}")
    df = pd.read_csv(csv_path)
    return df


def load_vm_allocation_data(data_dir):
    csv_path = os.path.join(data_dir, 'cloudsim_vm_allocation.csv')
    if not os.path.exists(csv_path):
        raise FileNotFoundError(f"File alokasi VM tidak ditemukan: {csv_path}")
    df = pd.read_csv(csv_path)
    return df


def export_summary_csv_if_missing(df_metrics, out_dir):
    summary_path = os.path.join(out_dir, 'cloudsim_metrics_summary.csv')
    if not os.path.exists(summary_path):
        agg = df_metrics.groupby('Tasks').agg({
            'Makespan_s': ['mean', 'std'],
            'TotalCost_USD': ['mean', 'std'],
            'DI': ['mean', 'std'],
            'Runtime_ms': ['mean', 'std']
        }).reset_index()
        summary_df = pd.DataFrame({
            'Tasks': agg['Tasks'],
            'Makespan_Mean': agg['Makespan_s']['mean'],
            'Makespan_Std': agg['Makespan_s']['std'].fillna(0),
            'TotalCost_Mean': agg['TotalCost_USD']['mean'],
            'TotalCost_Std': agg['TotalCost_USD']['std'].fillna(0),
            'DI_Mean': agg['DI']['mean'],
            'DI_Std': agg['DI']['std'].fillna(0),
            'Runtime_Mean': agg['Runtime_ms']['mean'],
            'Runtime_Std': agg['Runtime_ms']['std'].fillna(0)
        })
        summary_df.to_csv(summary_path, index=False)
        print(f"[EXPORT] Ringkasan metrik dibuat otomatis di: {summary_path}")


def plot_evaluation_metrics(df_metrics, out_dir, scenario_title=None, dc_count=None, host_count=None, vm_count=None):
    """
    Grafik 1: 4 Subplot Metrik Evaluasi (Makespan, Total Cost, DI, Runtime)
    dari skala 1.000 hingga 10.000 tasks dengan error band (mean +- std dev).
    """
    agg = df_metrics.groupby('Tasks').agg({
        'Makespan_s': ['mean', 'std'],
        'TotalCost_USD': ['mean', 'std'],
        'DI': ['mean', 'std'],
        'Runtime_ms': ['mean', 'std']
    }).reset_index()

    tasks = agg['Tasks'].values
    m_mean = agg['Makespan_s']['mean'].values
    m_std = agg['Makespan_s']['std'].fillna(0).values

    c_mean = agg['TotalCost_USD']['mean'].values
    c_std = agg['TotalCost_USD']['std'].fillna(0).values

    di_mean = agg['DI']['mean'].values
    di_std = agg['DI']['std'].fillna(0).values

    rt_mean = agg['Runtime_ms']['mean'].values
    rt_std = agg['Runtime_ms']['std'].fillna(0).values

    fig, axes = plt.subplots(2, 2, figsize=(14, 10))

    # Subplot 1: Makespan vs Jumlah Task
    ax1 = axes[0, 0]
    ax1.plot(tasks, m_mean, marker='o', color='#1f77b4', linewidth=2.4, label='Mean Makespan (s)')
    ax1.fill_between(tasks, m_mean - m_std, m_mean + m_std, color='#1f77b4', alpha=0.22, label='\u00b11 Std Dev Band')
    ax1.set_title("Makespan vs Jumlah Task (Skala Masif 1k - 10k)", fontsize=11, fontweight='bold')
    ax1.set_xlabel("Jumlah Task GoCJ")
    ax1.set_ylabel("Makespan (detik)")
    ax1.set_xticks(tasks)
    ax1.grid(True, linestyle='--', alpha=0.5)
    ax1.legend(loc='upper left')

    # Subplot 2: Total Execution Cost vs Jumlah Task
    ax2 = axes[0, 1]
    ax2.plot(tasks, c_mean, marker='s', color='#2ca02c', linewidth=2.4, label='Mean Total Cost ($)')
    ax2.fill_between(tasks, c_mean - c_std, c_mean + c_std, color='#2ca02c', alpha=0.22, label='\u00b11 Std Dev Band')
    ax2.set_title("Total Biaya Sewa VM vs Jumlah Task (Hourly Billing Tier)", fontsize=11, fontweight='bold')
    ax2.set_xlabel("Jumlah Task GoCJ")
    ax2.set_ylabel("Total Biaya ($)")
    ax2.set_xticks(tasks)
    ax2.grid(True, linestyle='--', alpha=0.5)
    ax2.legend(loc='upper left')

    # Subplot 3: Degree of Imbalance (DI) vs Jumlah Task
    ax3 = axes[1, 0]
    ax3.plot(tasks, di_mean, marker='^', color='#d62728', linewidth=2.4, label='Mean Degree of Imbalance (DI)')
    ax3.fill_between(tasks, di_mean - di_std, di_mean + di_std, color='#d62728', alpha=0.22, label='\u00b11 Std Dev Band')
    ax3.set_title("Degree of Imbalance (DI) vs Jumlah Task (Load Balance)", fontsize=11, fontweight='bold')
    ax3.set_xlabel("Jumlah Task GoCJ")
    ax3.set_ylabel("DI (Keseimbangan Beban)")
    ax3.set_xticks(tasks)
    ax3.grid(True, linestyle='--', alpha=0.5)
    ax3.legend(loc='lower right')

    # Subplot 4: Waktu Komputasi Algoritma TOPSIS
    ax4 = axes[1, 1]
    ax4.plot(tasks, rt_mean, marker='D', color='#9467bd', linewidth=2.4, label='Mean TOPSIS Runtime (ms)')
    ax4.fill_between(tasks, rt_mean - rt_std, rt_mean + rt_std, color='#9467bd', alpha=0.22, label='\u00b11 Std Dev Band')
    ax4.set_title("Efisiensi Runtime Algoritma TOPSIS vs Skala Task", fontsize=11, fontweight='bold')
    ax4.set_xlabel("Jumlah Task GoCJ")
    ax4.set_ylabel("Waktu Komputasi (ms)")
    ax4.set_xticks(tasks)
    ax4.grid(True, linestyle='--', alpha=0.5)
    ax4.legend(loc='upper left')

    if scenario_title:
        title_str = f"Evaluasi Performa Penjadwalan TOPSIS Skala Masif ({scenario_title})"
    elif dc_count is not None and host_count is not None and vm_count is not None:
        title_str = f"Evaluasi Performa Penjadwalan TOPSIS Skala Masif (CloudSim Plus: {dc_count} DC, {host_count} Host, {vm_count} VM)"
    else:
        title_str = "Evaluasi Performa Penjadwalan TOPSIS Skala Masif (CloudSim Plus)"

    plt.suptitle(title_str, fontsize=13, fontweight='bold', y=0.995)
    plt.tight_layout()

    out_path = os.path.join(out_dir, 'cloudsim_evaluation_metrics.png')
    plt.savefig(out_path, dpi=300)
    plt.close()
    print(f"[SUCCESS] Grafik evaluasi metrik tersimpan di: {out_path}")


def plot_datacenter_host_distribution(df_vm, out_dir, scenario_title=None):
    """
    Grafik 2: Distribusi total task dan busy time per Physical Host
    mendukung konfigurasi arbitrary Datacenter dan Host secara dinamis.
    """
    host_summary = df_vm.groupby(['DatacenterId', 'HostId']).agg({
        'TaskCount': 'sum',
        'BusyTime_s': 'sum'
    }).reset_index()

    hosts = host_summary['HostId'].values
    tasks = host_summary['TaskCount'].values
    busy_times = host_summary['BusyTime_s'].values
    dcs = host_summary['DatacenterId'].values
    unique_dcs = sorted(host_summary['DatacenterId'].unique(), key=lambda x: int(x.split('_')[1]))

    # Dynamic color mapping for Datacenters
    palette = ['#1f77b4', '#ff7f0e', '#2ca02c', '#d62728', '#9467bd', '#8c564b', '#e377c2', '#7f7f7f']
    dc_color_map = {dc: palette[i % len(palette)] for i, dc in enumerate(unique_dcs)}
    colors = [dc_color_map[dc] for dc in dcs]

    total_tasks = int(tasks.sum())
    total_hosts = len(hosts)
    ideal_tasks_per_host = total_tasks / total_hosts

    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(15, 6))

    # Subplot 1: Total Alokasi Task per Host
    bars1 = ax1.bar(hosts, tasks, color=colors, alpha=0.88, edgecolor='black', linewidth=0.8)
    ax1.set_title(f"Distribusi Jumlah Alokasi Task per Physical Host ({total_tasks:,} Tasks)", fontsize=11, fontweight='bold')

    # Construct descriptive x-label
    dc_desc_parts = []
    for dc in unique_dcs:
        dc_num = dc.split('_')[1]
        dc_hosts = host_summary[host_summary['DatacenterId'] == dc]['HostId'].tolist()
        dc_desc_parts.append(f"Host {min(dc_hosts)}-{max(dc_hosts)}: DC {dc_num}")
    ax1.set_xlabel(f"Physical Host ({' | '.join(dc_desc_parts)})")
    ax1.set_ylabel("Total Task Dialokasikan")
    ax1.set_xticks(hosts)
    ax1.grid(True, linestyle='--', alpha=0.5, axis='y')
    ax1.axhline(y=ideal_tasks_per_host, color='crimson', linestyle=':', linewidth=1.5,
                label=f'Target Rata-rata Ideal ({ideal_tasks_per_host:,.0f} task/host)')

    for bar in bars1:
        h = bar.get_height()
        ax1.text(bar.get_x() + bar.get_width() / 2.0, h + (total_tasks * 0.0025),
                 f"{int(h):,}", ha='center', va='bottom', fontsize=9, fontweight='bold')
    ax1.legend(loc='lower right')

    # Subplot 2: Total Busy Time per Host
    bars2 = ax2.bar(hosts, busy_times, color=colors, alpha=0.88, edgecolor='black', linewidth=0.8)
    ax2.set_title("Akumulasi Durasi Task (Busy Time) per Physical Host", fontsize=11, fontweight='bold')
    ax2.set_xlabel(f"Physical Host ({' | '.join(dc_desc_parts)})")
    ax2.set_ylabel("Total Busy Time (detik komputasi)")
    ax2.set_xticks(hosts)
    ax2.grid(True, linestyle='--', alpha=0.5, axis='y')

    max_busy = max(busy_times) if len(busy_times) > 0 else 1000
    for bar in bars2:
        h = bar.get_height()
        ax2.text(bar.get_x() + bar.get_width() / 2.0, h + (max_busy * 0.01),
                 f"{int(h):,}s", ha='center', va='bottom', fontsize=8)

    # Dynamic Legend
    legend_elements = []
    for dc in unique_dcs:
        dc_num = dc.split('_')[1]
        dc_hosts = host_summary[host_summary['DatacenterId'] == dc]['HostId'].tolist()
        dc_vms = len(df_vm[df_vm['DatacenterId'] == dc])
        hosts_str = ", ".join(str(h) for h in dc_hosts)
        label_text = f"Datacenter {dc_num} (Host {hosts_str} - {dc_vms} VM)"
        legend_elements.append(Patch(facecolor=dc_color_map[dc], edgecolor='black', label=label_text))

    fig.legend(handles=legend_elements, loc='upper center', bbox_to_anchor=(0.5, 1.02),
               ncol=min(len(unique_dcs), 3), frameon=True, fontsize=10)

    suptitle_text = f"Distribusi Beban Aktual pada Pemetaan Deterministik CloudSim Plus ({total_tasks:,} Tasks)"
    if scenario_title:
        suptitle_text += f" - {scenario_title}"
    plt.suptitle(suptitle_text, fontsize=12, fontweight='bold', y=1.06)
    plt.tight_layout()

    out_path = os.path.join(out_dir, 'cloudsim_datacenter_host_distribution.png')
    plt.savefig(out_path, dpi=300, bbox_inches='tight')
    plt.close()
    print(f"[SUCCESS] Grafik distribusi Datacenter & Host tersimpan di: {out_path}")


def plot_vm_workload_distribution(df_vm, out_dir, scenario_title=None):
    """
    Grafik 3: Distribusi beban kerja agregat berdasarkan kelas VM (Small, Medium, Large)
    secara dinamis menghitung jumlah VM per tipe.
    """
    type_order = [t for t in ['Small', 'Medium', 'Large'] if t in df_vm['VmType'].values]
    if not type_order:
        type_order = sorted(df_vm['VmType'].unique())

    type_summary = df_vm.groupby('VmType').agg({
        'TaskCount': ['sum', 'mean'],
        'BusyTime_s': ['sum', 'mean'],
        'VmId': 'count',
        'Mips': 'first'
    }).loc[type_order]

    vm_counts = {t: int(type_summary.loc[t, ('VmId', 'count')]) for t in type_order}
    mips_values = {t: float(type_summary.loc[t, ('Mips', 'first')]) for t in type_order}

    classes = [f"{t}\n({vm_counts[t]} VM @ {mips_values[t]/1000:g}k MIPS)" for t in type_order]
    default_colors = ['#2b5c8f', '#d95f02', '#2ca02c', '#9467bd']
    vm_colors = default_colors[:len(type_order)]

    fig, axes = plt.subplots(1, 3, figsize=(16, 5))
    total_tasks = df_vm['TaskCount'].sum()

    # 1. Total Task per Kelas
    total_tasks_per_type = type_summary['TaskCount']['sum'].values
    bars1 = axes[0].bar(classes, total_tasks_per_type, color=vm_colors, alpha=0.88, edgecolor='black', linewidth=0.8)
    axes[0].set_title("Total Task Dialokasikan per Kelas VM", fontsize=11, fontweight='bold')
    axes[0].set_ylabel("Jumlah Task")
    axes[0].grid(True, linestyle='--', alpha=0.5, axis='y')
    for bar in bars1:
        h = bar.get_height()
        pct = (h / total_tasks * 100) if total_tasks > 0 else 0
        axes[0].text(bar.get_x() + bar.get_width() / 2.0, h + (total_tasks * 0.005),
                     f"{int(h):,} ({pct:.1f}%)", ha='center', va='bottom', fontsize=9, fontweight='bold')

    # 2. Rata-rata Task per Individual VM
    mean_tasks = type_summary['TaskCount']['mean'].values
    bars2 = axes[1].bar(classes, mean_tasks, color=vm_colors, alpha=0.88, edgecolor='black', linewidth=0.8)
    axes[1].set_title("Rata-rata Task per Individual VM", fontsize=11, fontweight='bold')
    axes[1].set_ylabel("Rata-rata Task per VM")
    axes[1].grid(True, linestyle='--', alpha=0.5, axis='y')
    max_mean = max(mean_tasks) if len(mean_tasks) > 0 else 100
    for bar in bars2:
        h = bar.get_height()
        axes[1].text(bar.get_x() + bar.get_width() / 2.0, h + (max_mean * 0.015),
                     f"{h:.1f} task/VM", ha='center', va='bottom', fontsize=9, fontweight='bold')

    # 3. Total Busy Time (detik) per Kelas VM
    total_busy = type_summary['BusyTime_s']['sum'].values
    bars3 = axes[2].bar(classes, total_busy, color=vm_colors, alpha=0.88, edgecolor='black', linewidth=0.8)
    axes[2].set_title("Akumulasi Durasi Task per Kelas VM", fontsize=11, fontweight='bold')
    axes[2].set_ylabel("Total Busy Time (detik)")
    axes[2].grid(True, linestyle='--', alpha=0.5, axis='y')
    max_busy = max(total_busy) if len(total_busy) > 0 else 1000
    for bar in bars3:
        h = bar.get_height()
        axes[2].text(bar.get_x() + bar.get_width() / 2.0, h + (max_busy * 0.015),
                     f"{int(h):,}s", ha='center', va='bottom', fontsize=9, fontweight='bold')

    suptitle_text = f"Analisis Pemanfaatan Kelas Virtual Machine Heterogen ({len(df_vm)} VM, {int(total_tasks):,} Tasks GoCJ)"
    if scenario_title:
        suptitle_text += f" - {scenario_title}"
    plt.suptitle(suptitle_text, fontsize=12, fontweight='bold', y=1.03)
    plt.tight_layout()

    out_path = os.path.join(out_dir, 'cloudsim_vm_workload_distribution.png')
    plt.savefig(out_path, dpi=300, bbox_inches='tight')
    plt.close()
    print(f"[SUCCESS] Grafik pemanfaatan kelas VM tersimpan di: {out_path}")


def main():
    args = parse_args()
    data_dir = os.path.abspath(args.data_dir)
    out_dir = os.path.abspath(args.out_dir if args.out_dir else data_dir)
    os.makedirs(out_dir, exist_ok=True)

    print("=========================================================================")
    print("  VISUALISASI HASIL SIMULASI CLOUDSIM PLUS & PENJADWALAN TOPSIS (SOKA 5) ")
    print(f"  Direktori Data  : {data_dir}")
    print(f"  Direktori Output: {out_dir}")
    if args.scenario_title:
        print(f"  Label Skenario  : {args.scenario_title}")
    print("=========================================================================\n")

    df_metrics = load_metrics_data(data_dir)
    df_vm = load_vm_allocation_data(data_dir)

    export_summary_csv_if_missing(df_metrics, out_dir)

    dc_count = df_vm['DatacenterId'].nunique()
    host_count = df_vm['HostId'].nunique()
    vm_count = len(df_vm)

    plot_evaluation_metrics(df_metrics, out_dir, args.scenario_title, dc_count, host_count, vm_count)
    plot_datacenter_host_distribution(df_vm, out_dir, args.scenario_title)
    plot_vm_workload_distribution(df_vm, out_dir, args.scenario_title)

    print(f"\n[SELESAI] Seluruh 3 grafik berhasil disimpan di '{out_dir}'.")


if __name__ == "__main__":
    main()
