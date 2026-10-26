"""
Perbandingan Kinerja Multi-Skenario CloudSim Plus (TOPSIS Scheduling)
Membandingkan:
- Scenario 1: 2 Datacenter, 8 Physical Host, 80 VM (160 vCPU, 512k MIPS, Base Rate $9.44/jam)
- Scenario 2: 3 Datacenter, 9 Physical Host, 27 VM (63 vCPU, 234k MIPS, Base Rate $3.78/jam)
Dataset: GoCJ 1.000 s/d 10.000 Tasks (3x Repeated Runs per Skala)
"""

import os
import argparse
import pandas as pd
import matplotlib

matplotlib.use('Agg')
import matplotlib.pyplot as plt

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_S1_DIR = os.path.join(BASE_DIR, 'docs', 'scenario1')
DEFAULT_S2_DIR = os.path.join(BASE_DIR, 'docs', 'scenario2')
DEFAULT_OUT_FILE = os.path.join(BASE_DIR, 'docs', 'scenario_comparison_metrics.png')


def parse_args():
    parser = argparse.ArgumentParser(description="Komparasi Multi-Skenario Simulasi CloudSim Plus")
    parser.add_argument("--scenario1-dir", default=DEFAULT_S1_DIR,
                        help="Direktori metrik Scenario 1 (default: docs/scenario1)")
    parser.add_argument("--scenario2-dir", default=DEFAULT_S2_DIR,
                        help="Direktori metrik Scenario 2 (default: docs/scenario2)")
    parser.add_argument("--out-file", default=DEFAULT_OUT_FILE,
                        help="File output gambar komparasi (default: docs/scenario_comparison_metrics.png)")
    parser.add_argument("--label1", default="Scenario 1 (2 DC, 8 Host, 80 VM)",
                        help="Label legenda Skenario 1")
    parser.add_argument("--label2", default="Scenario 2 (3 DC, 9 Host, 27 VM)",
                        help="Label legenda Skenario 2")
    return parser.parse_args()


def load_aggregated(csv_dir):
    csv_path = os.path.join(csv_dir, 'cloudsim_metrics.csv')
    if not os.path.exists(csv_path):
        raise FileNotFoundError(f"File metrik tidak ditemukan: {csv_path}")
    df = pd.read_csv(csv_path)
    agg = df.groupby('Tasks').agg({
        'Makespan_s': ['mean', 'std'],
        'TotalCost_USD': ['mean', 'std'],
        'DI': ['mean', 'std'],
        'Runtime_ms': ['mean', 'std']
    }).reset_index()
    return agg


def plot_comparison(agg1, agg2, out_path, label1, label2):
    tasks = agg1['Tasks'].values

    # Metric arrays Scenario 1
    m1_mean, m1_std = agg1['Makespan_s']['mean'].values, agg1['Makespan_s']['std'].fillna(0).values
    c1_mean, c1_std = agg1['TotalCost_USD']['mean'].values, agg1['TotalCost_USD']['std'].fillna(0).values
    di1_mean, di1_std = agg1['DI']['mean'].values, agg1['DI']['std'].fillna(0).values
    rt1_mean, rt1_std = agg1['Runtime_ms']['mean'].values, agg1['Runtime_ms']['std'].fillna(0).values

    # Metric arrays Scenario 2
    m2_mean, m2_std = agg2['Makespan_s']['mean'].values, agg2['Makespan_s']['std'].fillna(0).values
    c2_mean, c2_std = agg2['TotalCost_USD']['mean'].values, agg2['TotalCost_USD']['std'].fillna(0).values
    di2_mean, di2_std = agg2['DI']['mean'].values, agg2['DI']['std'].fillna(0).values
    rt2_mean, rt2_std = agg2['Runtime_ms']['mean'].values, agg2['Runtime_ms']['std'].fillna(0).values

    color_s1 = '#1f77b4'  # Biru
    color_s2 = '#d95f02'  # Oranye kemerahan

    fig, axes = plt.subplots(2, 2, figsize=(15, 11))

    # ─────────────────────────────────────────────────────────────
    # Subplot 1: Makespan Comparison
    # ─────────────────────────────────────────────────────────────
    ax1 = axes[0, 0]
    ax1.plot(tasks, m1_mean, marker='o', color=color_s1, linewidth=2.2, label=f'{label1}')
    ax1.fill_between(tasks, m1_mean - m1_std, m1_mean + m1_std, color=color_s1, alpha=0.18)
    ax1.plot(tasks, m2_mean, marker='s', color=color_s2, linewidth=2.2, linestyle='--', label=f'{label2}')
    ax1.fill_between(tasks, m2_mean - m2_std, m2_mean + m2_std, color=color_s2, alpha=0.18)

    ax1.set_title("Komparasi Makespan (Waktu Selesai Total)", fontsize=11, fontweight='bold')
    ax1.set_xlabel("Jumlah Task GoCJ")
    ax1.set_ylabel("Makespan (detik)")
    ax1.set_xticks(tasks)
    ax1.grid(True, linestyle='--', alpha=0.5)
    ax1.legend(loc='upper left', frameon=True)

    # ─────────────────────────────────────────────────────────────
    # Subplot 2: Total Cost Comparison
    # ─────────────────────────────────────────────────────────────
    ax2 = axes[0, 1]
    ax2.plot(tasks, c1_mean, marker='o', color=color_s1, linewidth=2.2, label=f'{label1}')
    ax2.fill_between(tasks, c1_mean - c1_std, c1_mean + c1_std, color=color_s1, alpha=0.18)
    ax2.plot(tasks, c2_mean, marker='s', color=color_s2, linewidth=2.2, linestyle='--', label=f'{label2}')
    ax2.fill_between(tasks, c2_mean - c2_std, c2_mean + c2_std, color=color_s2, alpha=0.18)

    ax2.set_title("Komparasi Total Biaya Komputasi (Hourly Billing Tier)", fontsize=11, fontweight='bold')
    ax2.set_xlabel("Jumlah Task GoCJ")
    ax2.set_ylabel("Total Biaya ($)")
    ax2.set_xticks(tasks)
    ax2.grid(True, linestyle='--', alpha=0.5)
    ax2.legend(loc='upper left', frameon=True)

    # ─────────────────────────────────────────────────────────────
    # Subplot 3: Degree of Imbalance (DI) Comparison
    # ─────────────────────────────────────────────────────────────
    ax3 = axes[1, 0]
    ax3.plot(tasks, di1_mean, marker='o', color=color_s1, linewidth=2.2, label=f'{label1}')
    ax3.fill_between(tasks, di1_mean - di1_std, di1_mean + di1_std, color=color_s1, alpha=0.18)
    ax3.plot(tasks, di2_mean, marker='s', color=color_s2, linewidth=2.2, linestyle='--', label=f'{label2}')
    ax3.fill_between(tasks, di2_mean - di2_std, di2_mean + di2_std, color=color_s2, alpha=0.18)

    ax3.set_title("Komparasi Degree of Imbalance / DI (Keseimbangan Beban)", fontsize=11, fontweight='bold')
    ax3.set_xlabel("Jumlah Task GoCJ")
    ax3.set_ylabel("Degree of Imbalance (DI)")
    ax3.set_xticks(tasks)
    ax3.grid(True, linestyle='--', alpha=0.5)
    ax3.legend(loc='upper right', frameon=True)

    # ─────────────────────────────────────────────────────────────
    # Subplot 4: Algorithm Runtime Comparison
    # ─────────────────────────────────────────────────────────────
    ax4 = axes[1, 1]
    ax4.plot(tasks, rt1_mean, marker='o', color=color_s1, linewidth=2.2, label=f'{label1}')
    ax4.fill_between(tasks, rt1_mean - rt1_std, rt1_mean + rt1_std, color=color_s1, alpha=0.18)
    ax4.plot(tasks, rt2_mean, marker='s', color=color_s2, linewidth=2.2, linestyle='--', label=f'{label2}')
    ax4.fill_between(tasks, rt2_mean - rt2_std, rt2_mean + rt2_std, color=color_s2, alpha=0.18)

    ax4.set_title("Komparasi Efisiensi Algoritma TOPSIS (Waktu Eksekusi)", fontsize=11, fontweight='bold')
    ax4.set_xlabel("Jumlah Task GoCJ")
    ax4.set_ylabel("Waktu Komputasi Algoritma (ms)")
    ax4.set_xticks(tasks)
    ax4.grid(True, linestyle='--', alpha=0.5)
    ax4.legend(loc='upper left', frameon=True)

    plt.suptitle("Evaluasi Komparatif Kinerja Multi-Skenario TOPSIS pada CloudSim Plus\n"
                 "Scenario 1 (2 DC, 8 Host, 80 VM) vs Scenario 2 (3 DC, 9 Host, 27 VM)",
                 fontsize=13, fontweight='bold', y=0.995)
    plt.tight_layout()

    os.makedirs(os.path.dirname(os.path.abspath(out_path)), exist_ok=True)
    plt.savefig(out_path, dpi=300)
    plt.close()
    print(f"[SUCCESS] Grafik komparasi multi-skenario tersimpan di: {out_path}")


def main():
    args = parse_args()
    print("=========================================================================")
    print("  KOMPARASI MULTI-SKENARIO SIMULASI CLOUDSIM PLUS & PENJADWALAN TOPSIS   ")
    print(f"  Skenario 1 : {args.scenario1_dir} ({args.label1})")
    print(f"  Skenario 2 : {args.scenario2_dir} ({args.label2})")
    print(f"  Output     : {args.out_file}")
    print("=========================================================================\n")

    agg1 = load_aggregated(args.scenario1_dir)
    agg2 = load_aggregated(args.scenario2_dir)
    plot_comparison(agg1, agg2, args.out_file, args.label1, args.label2)
    print("\n[SELESAI] Komparasi selesai dibuat.")


if __name__ == "__main__":
    main()
