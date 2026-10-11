"""
Visualisasi Hasil Simulasi CloudSim Plus - Penjadwalan TOPSIS (SOKA Kelompok 5)
Script ini membaca hasil ekspor CSV dari TopsisCloudSim.java (docs/cloudsim_metrics.csv
dan docs/cloudsim_vm_allocation.csv) dan menghasilkan 3 grafik publikasi resolusi tinggi:
1. docs/cloudsim_evaluation_metrics.png          : 4 Metrik Evaluasi (Makespan, Cost, DI, Runtime) dengan Error Band (3 runs)
2. docs/cloudsim_datacenter_host_distribution.png: Distribusi Beban per Host pada Datacenter 1 (Host 0-3) dan DC 2 (Host 4-7)
3. docs/cloudsim_vm_workload_distribution.png    : Distribusi Beban Kerja Agregat Berdasarkan 3 Kelas VM (Small, Medium, Large)
"""

import os
import numpy as np
import pandas as pd
import matplotlib

matplotlib.use('Agg')  # Export files without requiring a desktop/Tk installation.
import matplotlib.pyplot as plt
from matplotlib.patches import Patch

# Path konfigurasi direktori
BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DOCS_DIR = os.path.join(BASE_DIR, 'docs')
METRICS_CSV = os.path.join(DOCS_DIR, 'cloudsim_metrics.csv')
VM_ALLOCATION_CSV = os.path.join(DOCS_DIR, 'cloudsim_vm_allocation.csv')


def load_metrics_data():
    if not os.path.exists(METRICS_CSV):
        raise FileNotFoundError(f"File metrik tidak ditemukan di {METRICS_CSV}. Jalankan TopsisCloudSim.java terlebih dahulu.")
    df = pd.read_csv(METRICS_CSV)
    print(f"[INFO] Memuat {len(df)} baris data metrik dari: {METRICS_CSV}")
    return df


def load_vm_allocation_data():
    if not os.path.exists(VM_ALLOCATION_CSV):
        raise FileNotFoundError(f"File alokasi VM tidak ditemukan di {VM_ALLOCATION_CSV}. Jalankan TopsisCloudSim.java terlebih dahulu.")
    df = pd.read_csv(VM_ALLOCATION_CSV)
    print(f"[INFO] Memuat data alokasi {len(df)} VM dari: {VM_ALLOCATION_CSV}")
    return df


def plot_evaluation_metrics(df_metrics):
    """
    Grafik 1: 4 Subplot Metrik Evaluasi (Makespan, Total Cost, DI, Runtime)
    dari skala 1.000 hingga 10.000 tasks dengan pita standar deviasi (error band) dari 3 kali run.
    """
    # Agregasi mean dan std dev per skala task
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

    plt.suptitle("Evaluasi Performa Penjadwalan TOPSIS Skala Masif (CloudSim Plus: 2 DC, 8 Host, 80 VM)",
                 fontsize=13, fontweight='bold', y=0.995)
    plt.tight_layout()

    out_path = os.path.join(DOCS_DIR, 'cloudsim_evaluation_metrics.png')
    plt.savefig(out_path, dpi=300)
    plt.close()
    print(f"[SUCCESS] Grafik evaluasi metrik tersimpan di: {out_path}")


def plot_datacenter_host_distribution(df_vm):
    """
    Grafik 2: Distribusi total task dan busy time per Physical Host
    pada Datacenter 1 (Host 0-3) dan Datacenter 2 (Host 4-7),
    menampilkan beban aktual tanpa mengasumsikan distribusi task merata.
    """
    host_summary = df_vm.groupby(['DatacenterId', 'HostId']).agg({
        'TaskCount': 'sum',
        'BusyTime_s': 'sum'
    }).reset_index()

    hosts = host_summary['HostId'].values
    tasks = host_summary['TaskCount'].values
    busy_times = host_summary['BusyTime_s'].values
    dcs = host_summary['DatacenterId'].values

    # Pewarnaan berdasar Datacenter
    colors = ['#1f77b4' if dc == 'DC_1' else '#ff7f0e' for dc in dcs]

    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(15, 6))

    # Subplot 1: Total Alokasi Task per Host
    bars1 = ax1.bar(hosts, tasks, color=colors, alpha=0.88, edgecolor='black', linewidth=0.8)
    ax1.set_title("Distribusi Jumlah Alokasi Task per Physical Host (10.000 Tasks)", fontsize=11, fontweight='bold')
    ax1.set_xlabel("Physical Host (Host 0-3: Datacenter 1 | Host 4-7: Datacenter 2)")
    ax1.set_ylabel("Total Task Dialokasikan")
    ax1.grid(True, linestyle='--', alpha=0.5, axis='y')
    ax1.axhline(y=10000 / 8, color='crimson', linestyle=':', linewidth=1.5, label='Target Rata-rata Ideal (1.250 task/host)')

    for bar in bars1:
        h = bar.get_height()
        ax1.text(bar.get_x() + bar.get_width() / 2.0, h + 25, f"{int(h):,}", ha='center', va='bottom', fontsize=9, fontweight='bold')
    ax1.legend(loc='lower right')

    # Subplot 2: Total Busy Time per Host
    bars2 = ax2.bar(hosts, busy_times, color=colors, alpha=0.88, edgecolor='black', linewidth=0.8)
    ax2.set_title("Akumulasi Durasi Task (Busy Time) per Physical Host", fontsize=11, fontweight='bold')
    ax2.set_xlabel("Physical Host (Host 0-3: Datacenter 1 | Host 4-7: Datacenter 2)")
    ax2.set_ylabel("Total Busy Time (detik komputasi)")
    ax2.grid(True, linestyle='--', alpha=0.5, axis='y')

    for bar in bars2:
        h = bar.get_height()
        ax2.text(bar.get_x() + bar.get_width() / 2.0, h + 300, f"{int(h):,}s", ha='center', va='bottom', fontsize=8)

    legend_elements = [
        Patch(facecolor='#1f77b4', edgecolor='black', label='Datacenter 1 (Host 0, 1, 2, 3 - 40 VM)'),
        Patch(facecolor='#ff7f0e', edgecolor='black', label='Datacenter 2 (Host 4, 5, 6, 7 - 40 VM)')
    ]
    fig.legend(handles=legend_elements, loc='upper center', bbox_to_anchor=(0.5, 1.02), ncol=2, frameon=True, fontsize=10)

    plt.suptitle("Distribusi Beban Aktual pada Pemetaan Deterministik CloudSim Plus (10.000 Tasks, Run 3)",
                 fontsize=12, fontweight='bold', y=1.06)
    plt.tight_layout()

    out_path = os.path.join(DOCS_DIR, 'cloudsim_datacenter_host_distribution.png')
    plt.savefig(out_path, dpi=300, bbox_inches='tight')
    plt.close()
    print(f"[SUCCESS] Grafik distribusi Datacenter & Host tersimpan di: {out_path}")


def plot_vm_workload_distribution(df_vm):
    """
    Grafik 3: Distribusi beban kerja agregat berdasarkan 3 kelas VM (Small, Medium, Large)
    di seluruh 80 VM pada simulasi skala 10.000 task.
    """
    # Agregasi per kelas VM
    type_summary = df_vm.groupby('VmType').agg({
        'TaskCount': ['sum', 'mean'],
        'BusyTime_s': ['sum', 'mean'],
        'VmId': 'count'
    }).loc[['Small', 'Medium', 'Large']]

    classes = ['Small\n(32 VM @ 1k MIPS)', 'Medium\n(32 VM @ 2.5k MIPS)', 'Large\n(16 VM @ 5k MIPS)']
    vm_colors = ['#2b5c8f', '#d95f02', '#2ca02c']

    fig, axes = plt.subplots(1, 3, figsize=(16, 5))

    # 1. Total Task per Kelas
    total_tasks = type_summary['TaskCount']['sum'].values
    bars1 = axes[0].bar(classes, total_tasks, color=vm_colors, alpha=0.88, edgecolor='black', linewidth=0.8)
    axes[0].set_title("Total Task Dialokasikan per Kelas VM", fontsize=11, fontweight='bold')
    axes[0].set_ylabel("Jumlah Task")
    axes[0].grid(True, linestyle='--', alpha=0.5, axis='y')
    for bar in bars1:
        h = bar.get_height()
        axes[0].text(bar.get_x() + bar.get_width() / 2.0, h + 50, f"{int(h):,} ({h/10000*100:.1f}%)",
                     ha='center', va='bottom', fontsize=9, fontweight='bold')

    # 2. Rata-rata Task per Individual VM
    mean_tasks = type_summary['TaskCount']['mean'].values
    bars2 = axes[1].bar(classes, mean_tasks, color=vm_colors, alpha=0.88, edgecolor='black', linewidth=0.8)
    axes[1].set_title("Rata-rata Task per Individual VM", fontsize=11, fontweight='bold')
    axes[1].set_ylabel("Rata-rata Task per VM")
    axes[1].grid(True, linestyle='--', alpha=0.5, axis='y')
    for bar in bars2:
        h = bar.get_height()
        axes[1].text(bar.get_x() + bar.get_width() / 2.0, h + 2, f"{h:.1f} task/VM",
                     ha='center', va='bottom', fontsize=9, fontweight='bold')

    # 3. Total Busy Time (detik) per Kelas VM
    total_busy = type_summary['BusyTime_s']['sum'].values
    bars3 = axes[2].bar(classes, total_busy, color=vm_colors, alpha=0.88, edgecolor='black', linewidth=0.8)
    axes[2].set_title("Akumulasi Durasi Task per Kelas VM", fontsize=11, fontweight='bold')
    axes[2].set_ylabel("Total Busy Time (detik)")
    axes[2].grid(True, linestyle='--', alpha=0.5, axis='y')
    for bar in bars3:
        h = bar.get_height()
        axes[2].text(bar.get_x() + bar.get_width() / 2.0, h + 1500, f"{int(h):,}s",
                     ha='center', va='bottom', fontsize=9, fontweight='bold')

    plt.suptitle("Analisis Pemanfaatan Kelas Virtual Machine Heterogen (80 VM, 10.000 Tasks GoCJ)",
                 fontsize=12, fontweight='bold', y=1.03)
    plt.tight_layout()

    out_path = os.path.join(DOCS_DIR, 'cloudsim_vm_workload_distribution.png')
    plt.savefig(out_path, dpi=300, bbox_inches='tight')
    plt.close()
    print(f"[SUCCESS] Grafik pemanfaatan kelas VM tersimpan di: {out_path}")


def main():
    print("=========================================================================")
    print("  VISUALISASI HASIL SIMULASI CLOUDSIM PLUS & PENJADWALAN TOPSIS (SOKA 5) ")
    print("  Dataset: GoCJ 1.000 s/d 10.000 Tasks (3x Repeated Runs)               ")
    print("  Arsitektur: 2 Datacenter, 8 Physical Hosts, 80 Heterogeneous VMs      ")
    print("=========================================================================\n")

    df_metrics = load_metrics_data()
    df_vm = load_vm_allocation_data()

    print("\n[PROSES] Membuat 3 grafik publikasi resolusi tinggi...")
    plot_evaluation_metrics(df_metrics)
    plot_datacenter_host_distribution(df_vm)
    plot_vm_workload_distribution(df_vm)

    print("\n[SELESAI] Seluruh 3 grafik telah berhasil dibuat dan disimpan di folder 'docs/'.")


if __name__ == "__main__":
    main()
