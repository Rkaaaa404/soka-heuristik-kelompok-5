# Analisis Komparatif Multi-Skenario Penjadwalan TOPSIS pada CloudSim Plus
**Evaluasi Skala Masif Workload Google Cloud Jobs (1.000 s/d 10.000 Tasks)**  
*Kelompok 5 — Sistem Operasi & Komputasi Awan (SOKA)*

---

## 1. Ringkasan Eksekutif

Laporan ini menyajikan evaluasi empiris komparatif antara dua skenario infrastruktur cloud heterogen menggunakan algoritma *Technique for Order Preference by Similarity to Ideal Solution* (TOPSIS) yang diimplementasikan di atas engine **CloudSim Plus**:
- **Scenario 1:** 2 Datacenter, 8 Physical Host homogen (4 Host/DC), 80 Virtual Machine heterogen (10 VM/Host: rasio 4 Small : 4 Medium : 2 Large).
- **Scenario 2:** 3 Datacenter, 9 Physical Host homogen (3 Host/DC), 27 Virtual Machine heterogen (3 VM/Host: rasio 1 Small : 1 Medium : 1 Large).

Pengujian dilakukan secara deterministik menggunakan dataset sintetis standar industri **Google Cloud Jobs (GoCJ)** dengan 10 variasi skala beban kerja (1.000 hingga 10.000 tasks). Setiap skala dieksekusi sebanyak **3 kali repeated runs** (total 30 runs per skenario, 60 runs keseluruhan) untuk menjamin signifikansi statistik dengan estimasi *Sample Mean* ($\mu$) dan *Sample Standard Deviation* ($s$, $ddof=1$).

Empat metrik evaluasi utama dianalisis secara menyeluruh:
1. **Makespan ($s$):** Waktu total penyelesaian seluruh task dalam workload.
2. **Total Execution Cost ($USD):** Biaya sewa VM berbasis *hourly billing tier* (pembulatan ke atas per jam komputasi aktif).
3. **Degree of Imbalance (DI):** Tingkat ketidakseimbangan beban kerja antar-VM.
4. **Algorithm Runtime ($ms$):** Efisiensi komputasi algoritma TOPSIS dalam menentukan pemetaan task ke VM.

---

## 2. Spesifikasi Topologi Infrastruktur

Tabel berikut merangkum arsitektur fisik dan logis kedua skenario yang diuji:

| Parameter Arsitektur | Scenario 1 (Baseline 80 VM) | Scenario 2 (Proposed 27 VM) | Perubahan ($\Delta$) |
| :--- | :--- | :--- | :--- |
| **Jumlah Datacenter** | 2 Physical Datacenter (DC 1, DC 2) | 3 Physical Datacenter (DC 1, DC 2, DC 3) | +1 DC (+50.0%) |
| **Jumlah Physical Host** | 8 Host (Host 0–3 di DC 1; Host 4–7 di DC 2) | 9 Host (Host 0–2 di DC 1; Host 3–5 di DC 2; Host 6–8 di DC 3) | +1 Host (+12.5%) |
| **Spesifikasi Per Host** | 24 PE @ 10.000 MIPS, 64 GB RAM, 2 TB Storage | 24 PE @ 10.000 MIPS, 64 GB RAM, 2 TB Storage | Identik (Homogen) |
| **VM per Physical Host** | 10 VM (4 Small, 4 Medium, 2 Large) | 3 VM (1 Small, 1 Medium, 1 Large) | -7 VM/Host (-70.0%) |
| **Total Virtual Machine** | **80 VM** (32 Small, 32 Medium, 16 Large) | **27 VM** (9 Small, 9 Medium, 9 Large) | -53 VM (-66.25%) |
| **Rasio Kelas VM** | 4 : 4 : 2 (40% Small, 40% Medium, 20% Large) | 1 : 1 : 1 (33.3% Small, 33.3% Medium, 33.3% Large) | Transisi ke rasio seimbang |
| **Total Kapasitas vCPU** | 160 vCPU (32×1 + 32×2 + 16×4) | 63 vCPU (9×1 + 9×2 + 9×4) | -97 vCPU (-60.6%) |
| **Total Kapasitas RAM** | 320 GB (327.680 MB) | 122,88 GB (125.808 MB) | -197,12 GB (-61.6%) |
| **Agregat Compute Power** | 512.000 MIPS | 234.000 MIPS | -278.000 MIPS (-54.3%) |
| **Base Hourly Cost Tier** | **$9.44 / jam** ($1.60 + $3.84 + $4.00) | **$3.78 / jam** ($0.45 + $1.08 + $2.25) | **-$5.66 / jam (-59.96%)** |

---

## 3. Hasil Pengujian Empiris & Tabel Komparasi

Data di bawah ini diperoleh langsung dari eksekusi 30 run simulasi CloudSim Plus per skenario, diagregasi per skala task ($N = 3$ runs, $Mean \pm Std$):

### 3.1 Tabel Komparasi Lengkap (1.000 s/d 10.000 Tasks)

| Tasks | Makespan S1 (s) | Makespan S2 (s) | $\Delta$ Makespan (%) | Cost S1 ($) | Cost S2 ($) | $\Delta$ Cost (%) | DI S1 | DI S2 | $\Delta$ DI (%) | Runtime S1 (ms) | Runtime S2 (ms) | Speedup ($\times$) |
| :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **1.000** | $1795.32 \pm 0.50$ | $1795.32 \pm 0.50$ | **0.00%** | $9.44 \pm 0.00$ | $3.78 \pm 0.00$ | **-59.96%** | $1.8791 \pm 0.1453$ | $1.5425 \pm 0.0491$ | **-17.91%** | $24.63 \pm 11.89$ | $21.16 \pm 17.42$ | $1.16\times$ |
| **2.000** | $3057.22 \pm 7.00$ | $3134.01 \pm 9.40$ | **+2.51%** | $9.44 \pm 0.00$ | $4.86 \pm 0.00$ | **-48.52%** | $1.6819 \pm 0.0824$ | $0.9832 \pm 0.0344$ | **-41.54%** | $35.25 \pm 8.21$ | $23.63 \pm 1.66$ | $1.49\times$ |
| **3.000** | $4090.30 \pm 1.30$ | $4250.49 \pm 5.12$ | **+3.92%** | $10.12 \pm 0.07$ | $8.64 \pm 0.00$ | **-14.62%** | $1.4266 \pm 0.0413$ | $0.7766 \pm 0.0087$ | **-45.56%** | $58.32 \pm 38.91$ | $30.45 \pm 6.63$ | $1.92\times$ |
| **4.000** | $4622.44 \pm 1.51$ | $4855.19 \pm 3.39$ | **+5.04%** | $13.28 \pm 0.00$ | $8.64 \pm 0.00$ | **-34.94%** | $1.4659 \pm 0.0223$ | $0.6897 \pm 0.0042$ | **-52.95%** | $106.10 \pm 25.20$ | $49.27 \pm 27.40$ | $2.15\times$ |
| **5.000** | $5188.17 \pm 1.32$ | $5411.17 \pm 4.60$ | **+4.30%** | $13.28 \pm 0.00$ | $10.89 \pm 0.00$ | **-18.00%** | $1.4410 \pm 0.0227$ | $0.6448 \pm 0.0013$ | **-55.26%** | $123.84 \pm 47.10$ | $31.34 \pm 18.36$ | **$3.95\times$** |
| **6.000** | $6200.55 \pm 1.41$ | $6550.41 \pm 43.49$ | **+5.64%** | $14.20 \pm 0.14$ | $12.80 \pm 0.52$ | **-9.86%** | $1.3660 \pm 0.0547$ | $0.6447 \pm 0.0024$ | **-52.81%** | $154.55 \pm 64.87$ | $39.30 \pm 1.47$ | **$3.93\times$** |
| **7.000** | $16328.73 \pm 1.78$ | $16328.73 \pm 1.79$ | **-0.00%** | $16.36 \pm 0.23$ | $12.69 \pm 0.00$ | **-22.43%** | $1.8084 \pm 0.0301$ | $2.0727 \pm 0.0164$ | **+14.61%** | $149.55 \pm 96.01$ | $44.88 \pm 9.52$ | $3.33\times$ |
| **8.000** | $18611.75 \pm 12.45$ | $18674.84 \pm 17.17$ | **+0.34%** | $18.72 \pm 0.00$ | $13.88 \pm 0.09$ | **-25.85%** | $1.8567 \pm 0.0981$ | $2.0723 \pm 0.0273$ | **+11.61%** | $215.29 \pm 120.07$ | $62.81 \pm 14.53$ | $3.43\times$ |
| **9.000** | $20988.47 \pm 1.53$ | $20988.47 \pm 1.53$ | **0.00%** | $18.76 \pm 0.07$ | $15.34 \pm 0.07$ | **-18.23%** | $1.7687 \pm 0.0599$ | $2.0886 \pm 0.0337$ | **+18.08%** | $169.36 \pm 42.61$ | $92.59 \pm 49.65$ | $1.83\times$ |
| **10.000**| $23102.88 \pm 0.21$ | $23102.89 \pm 0.22$ | **+0.00%** | $20.16 \pm 0.00$ | $16.89 \pm 0.16$ | **-16.22%** | $1.7536 \pm 0.0548$ | $2.0240 \pm 0.0311$ | **+15.42%** | $204.36 \pm 107.25$ | $81.04 \pm 5.63$ | **$2.52\times$** |

---

## 4. Analisis & Interpretasi Teknis Mendalam

### 4.1 Dinamika Makespan: Kapasitas Komputasi vs Waktu Kedatangan Task (Arrival Bound)

Salah satu temuan paling menonjol dari komparasi ini adalah: **Meskipun Scenario 2 memotong kapasitas vCPU sebesar 60.6% (dari 160 menjadi 63 vCPU) dan aggregate MIPS sebesar 54.3% (dari 512k menjadi 234k MIPS), Makespan hanya meningkat maksimal +5.64% pada rentang skala 2k–6k, dan praktis IDENTIK ($\Delta \approx 0.00\%$) pada skala 1k dan 7k–10k.**

Fenomena ini dapat dijelaskan melalui teori antrean (*queuing theory*) dan karakteristik dataset GoCJ:
1. **Skala Padat (Dense Arrivals, 2.000 – 6.000 Tasks):**
   - Pada skala 2k hingga 6k, laju kedatangan task $\lambda$ melebihi laju layanan instan $\mu$ dari 27 VM. Akibatnya, antrean lokal pada PE terbentuk sementara.
   - Peningkatan makespan sebesar $+2.51\%$ (2k), $+3.92\%$ (3k), $+5.04\%$ (4k), $+4.30\%$ (5k), dan $+5.64\%$ (6k) merefleksikan *residual queuing delay*. Pada 6.000 task, makespan S2 bertambah 349.86 detik (dari 6.200 s ke 6.550 s). Selisih ini tergolong sangat moderat jika dibandingkan dengan reduksi kapasitas infrastruktur sebesar 54.3%.
2. **Skala Tersebar (Poisson-Stretched Arrivals, 7.000 – 10.000 Tasks):**
   - Pada skala 7.000 s/d 10.000 tasks, dataset GoCJ memiliki rentang waktu kedatangan (*arrival timestamp*) yang membentang hingga 23.100 detik ($\approx 6,42$ jam).
   - Di sini sistem beroperasi dalam rezim **Arrival-Bound**: laju rata-rata kedatangan task lebih rendah dari kapasitas pemrosesan agregat 27 VM ($\lambda_{avg} < \mu_{system}$).
   - Karena task terakhir tiba pada $t \approx 23.102$ detik dan langsung dieksekusi oleh core yang sedang bebas, sisa waktu eksekusi task tersebut ($\Delta t_{exec}$) sangat kecil ($\approx 0,5 - 0,8$ detik). Akibatnya, makespan pada kedua skenario dibatasi oleh *arrival horizon*, sehingga nilai Makespan pada skala 7k, 9k, dan 10k menjadi identik secara matematis:
     $$\text{Makespan} \approx \max_{i} (t_{arrive, i} + \frac{MI_i}{MIPS_{vm}})$$
   - Hal ini membuktikan bahwa penambahan 53 VM tambahan pada Scenario 1 (menjadi 80 VM) pada workload jangka panjang menghasilkan *overprovisioning* yang tidak memberikan percepatan makespan sama sekali.

---

### 4.2 Efisiensi Finansial & Dinamika Billing Tier Per Jam

Model biaya dalam CloudSim mengadopsi model komersial *Google Cloud Platform* dan *AWS EC2*, di mana penyewaan VM dihitung per jam penuh (*hourly billing blocks*):

$$\text{Cost}_{vm} = \lceil \frac{\text{BusyTime}_{vm}}{3600} \rceil \times \text{HourlyRate}_{vm}$$

1. **Efisiensi Skala Rendah (1.000 Tasks):**
   - Pada 1.000 tasks, Makespan kedua skenario adalah 1.795,32 s ($< 1$ jam / 3.600 s).
   - Seluruh VM pada kedua skenario hanya ditagihkan untuk 1 jam pertama.
   - Pada Scenario 1, seluruh 80 VM aktif ditagihkan base rate agregat = **$9.44**.
   - Pada Scenario 2, seluruh 27 VM aktif ditagihkan base rate agregat = **$3.78**.
   - **Hasil: Scenario 2 menghasilkan penghematan biaya langsung sebesar 59.96% tanpa ada degradasi makespan sama sekali!**
2. **Transisi Ambang Batas Jam (Scales 2.000 – 6.000 Tasks):**
   - Pada 2.000 tasks, makespan S2 mencapai 3.134 s, namun sebagian Medium VM yang memproses beban lebih tinggi melewati batas 3.600 s ke jam kedua, menaikkan biaya S2 menjadi $4.86. Meski demikian, biaya S2 tetap 48.52% lebih murah daripada S1 ($9.44).
   - Pada 4.000 tasks, S1 mengalami lonjakan biaya menjadi $13.28 karena sejumlah besar VM melewati 3.600 s ke jam kedua. Sementara S2 baru mencapai $8.64 (penghematan 34.94%).
3. **Skala Besar (7.000 – 10.000 Tasks):**
   - Pada 10.000 tasks, total biaya S1 adalah $20.16, sedangkan S2 hanya $16.89 (penghematan 16.22%).
   - Scenario 2 terbukti lebih unggul dalam *cost-to-workload ratio* di seluruh 10 skala pengujian.

---

### 4.3 Keseimbangan Beban (Degree of Imbalance / DI)

Degree of Imbalance mengukur dispersi durasi kerja relatif terhadap beban rata-rata sistem:

$$DI = \frac{T_{max} - T_{min}}{T_{avg}}$$

Perbandingan DI antara Scenario 1 dan Scenario 2 memperlihatkan dua fase yang sangat menarik:

```
Skala 1k - 6k Tasks:  DI S2 jauh lebih RENDAH (0.64 vs 1.36)  --> S2 LEBIH SEIMBANG (-55%)
Skala 7k - 10k Tasks: DI S2 sedikit lebih TINGGI (2.07 vs 1.80)  --> S1 LEBIH SEIMBANG (+15%)
```

#### Mengapa DI Scenario 2 jauh lebih baik pada Skala 2k–6k?
- Pada Scenario 1 (rasio 4:4:2), terdapat 32 Medium VM. Algoritma TOPSIS dengan bobot seimbang ($\alpha = 0.5, \beta = 0.5$) sangat memprioritaskan Medium VM karena menawarkan rasio MIPS-per-dolar terbaik ($2.500 \text{ MIPS} / \$0.12 \approx 20.833$ MIPS/$), dibandingkan Small ($20.000$ MIPS/$) dan Large ($20.000$ MIPS/$). Akibatnya, pada Scenario 1, Medium VM dibanjiri task sementara Small dan Large VM relatif menganggur, menghasilkan $DI \approx 1.36 - 1.68$.
- Pada Scenario 2 (rasio 1:1:1), hanya terdapat 9 Medium VM. Ketika task datang, 9 Medium VM tersebut langsung terisi penuh. Mekanisme TOPSIS yang memperhitungkan waktu ketersediaan core:
  $$\hat{T}_{finish} = T_{avail} + \frac{MI_{task}}{MIPS_{vm}}$$
  segera mendeteksi antrean pada Medium VM dan secara dinamis mengalihkan task baru ke Small VM dan Large VM. Hal ini memaksa distribusi task menyebar rata ke ketiga kelas VM, menurunkan DI secara drastis hingga **$0.6447$** (penurunan ketidakseimbangan sebesar $55.26\%$).

#### Mengapa DI Scenario 2 meningkat pada Skala 7k–10k?
- Pada skala 7k–10k, total durasi kedatangan mencapai 6.4 jam. Dengan hanya 9 Large VM yang biaya per jamnya mahal ($0.25), TOPSIS tetap berupaya meminimalkan biaya sehingga Large VM hanya dialokasikan 15–16 task per VM ($\approx 868$ s busy time).
- Sementara itu, 9 Medium VM memikul beban utama dengan rata-rata 828 task per VM ($\approx 38.412$ s busy time) dan Small VM memikul 267 task per VM ($\approx 18.375$ s busy time).
- Karena rentang $(T_{max} - T_{min})$ pada 27 VM melebar secara proporsional terhadap $T_{avg}$, nilai DI S2 naik ke kisaran 2.02 – 2.08, dibandingkan S1 yang memiliki 80 VM untuk membagi beban ekor tersebut.

---

### 4.4 Kompleksitas & Efisiensi Algoritma TOPSIS (Runtime Speedup)

Secara teoritis, algoritma TOPSIS mengevaluasi setiap task $t \in T$ terhadap seluruh kandidat VM $v \in V$:
- Matriks keputusan berdimensi $M \times C$, di mana $M = |V|$ (jumlah VM) dan $C = 2$ (kriteria: Waktu dan Biaya).
- Normalisasi vektor, pembobotan, penentuan solusi ideal positif ($A^+$) dan negatif ($A^-$), serta perhitungan jarak Euclidean berorde $\mathcal{O}(M \times C)$.
- Total kompleksitas untuk menjadwalkan $N$ task adalah:
  $$\mathcal{O}(N \times M)$$

Dengan memangkas jumlah VM dari $M_1 = 80$ ke $M_2 = 27$, rasio teoritis kompleksitas adalah:
$$\text{Theoretical Reduction Factor} = \frac{80}{27} \approx 2.96\times$$

**Verifikasi Empiris:**
- Pada skala 5.000 tasks: Runtime S1 = $123.84$ ms, Runtime S2 = $31.34$ ms $\rightarrow$ **Speedup $3.95\times$** (reduksi waktu komputasi -74.69%).
- Pada skala 6.000 tasks: Runtime S1 = $154.55$ ms, Runtime S2 = $39.30$ ms $\rightarrow$ **Speedup $3.93\times$** (reduksi waktu komputasi -74.57%).
- Pada skala 10.000 tasks: Runtime S1 = $204.36$ ms, Runtime S2 = $81.04$ ms $\rightarrow$ **Speedup $2.52\times$** (reduksi waktu komputasi -60.34%).

Pengurangan kandidat alternatif VM secara signifikan meringankan *overhead* penjadwal cloud, memungkinkan TOPSIS mengambil keputusan alokasi secara *real-time* bahkan pada skala masif.

---

### 4.5 Evaluasi Keseimbangan Beban Datacenter & Physical Host (Menjawab Feedback Dosen)

Pada pengujian sebelumnya, dosen penguji menanyakan apakah pemetaan deterministik CloudSim Plus benar-benar mendistribusikan beban secara merata ke seluruh Datacenter dan Physical Host yang didefinisikan, atau hanya terpusat pada host awal.

Hasil eksekusi Scenario 2 pada skala 10.000 tasks membuktikan pemerataan beban yang sempurna:

#### Distribusi per Physical Host (Scenario 2 - 10.000 Tasks):
| Datacenter | Host ID | VM Ditampung | Total Task Dialokasikan | Deviasi dari Ideal (1.111,1 task) | Total Busy Time (s) |
| :---: | :---: | :---: | :---: | :---: | :---: |
| **Datacenter 1** | Host 0 | VM 0 (S), VM 1 (M), VM 2 (L) | **1.111 task** | -0.11 task (-0.01%) | 58.774 s |
| **Datacenter 1** | Host 1 | VM 3 (S), VM 4 (M), VM 5 (L) | **1.108 task** | -3.11 task (-0.28%) | 56.375 s |
| **Datacenter 1** | Host 2 | VM 6 (S), VM 7 (M), VM 8 (L) | **1.116 task** | +4.89 task (+0.44%) | 56.706 s |
| **Datacenter 2** | Host 3 | VM 9 (S), VM 10 (M), VM 11 (L) | **1.111 task** | -0.11 task (-0.01%) | 56.891 s |
| **Datacenter 2** | Host 4 | VM 12 (S), VM 13 (M), VM 14 (L)| **1.111 task** | -0.11 task (-0.01%) | 59.442 s |
| **Datacenter 2** | Host 5 | VM 15 (S), VM 16 (M), VM 17 (L)| **1.112 task** | +0.89 task (+0.08%) | 55.561 s |
| **Datacenter 3** | Host 6 | VM 18 (S), VM 19 (M), VM 20 (L)| **1.111 task** | -0.11 task (-0.01%) | 57.964 s |
| **Datacenter 3** | Host 7 | VM 21 (S), VM 22 (M), VM 23 (L)| **1.111 task** | -0.11 task (-0.01%) | 58.373 s |
| **Datacenter 3** | Host 8 | VM 24 (S), VM 25 (M), VM 26 (L)| **1.109 task** | -2.11 task (-0.19%) | 58.825 s |

#### Distribusi per Datacenter (Scenario 2 - 10.000 Tasks):
- **Datacenter 1 (Host 0, 1, 2):** 3.335 task (**33.35%**) | 171.855 detik komputasi
- **Datacenter 2 (Host 3, 4, 5):** 3.334 task (**33.34%**) | 171.894 detik komputasi
- **Datacenter 3 (Host 6, 7, 8):** 3.331 task (**33.31%**) | 175.162 detik komputasi
- **Total:** 10.000 task (**100.00%**)

**Kesimpulan Distribusi:** Deviasi beban antar ketiga Datacenter kurang dari $0.04\%$, dan deviasi antar-host kurang dari $0.45\%$. Terbukti secara empiris bahwa algoritma TOPSIS dan pemetaan deterministik CloudSim Plus mendayagunakan seluruh infrastruktur secara proporsional dan tidak ada host yang idle atau mengalami kelaparan beban (*starvation*).

---

## 5. Kesimpulan & Rekomendasi Arsitektural

1. **Efisiensi Finansial Tinggi:** Scenario 2 (27 VM) mengungguli Scenario 1 dalam hal efisiensi biaya pada seluruh skala pengujian (penghematan antara $16.2\%$ hingga $60.0\%$), menjadikannya pilihan paling rasional untuk penyedia layanan cloud yang memprioritaskan biaya operasional (*OPEX*).
2. **Kompensasi Makespan yang Sangat Kecil:** Penurunan kapasitas vCPU hingga $60.6\%$ pada Scenario 2 hanya menyebabkan penambahan makespan sebesar maksimal $5.6\%$ pada skala padat, dan $0.0\%$ pada skala besar yang bersifat *arrival-bound*.
3. **Peningkatan Keseimbangan Beban Menengah:** Rasio 1:1:1 pada Scenario 2 berhasil menekan Degree of Imbalance sebesar $41\% - 55\%$ pada rentang 2k–6k tasks karena memicu saturasi sehat pada Medium VM dan mengaktifkan utilitas Small VM secara optimal.
4. **Kecepatan Komputasi Penjadwal:** Scenario 2 memberikan *speedup* algoritma sebesar $2.5\times - 3.95\times$, memperkuat kelayakan algoritma TOPSIS untuk diimplementasikan pada lingkungan produksi dengan beban kerja dinamis berkecepatan tinggi.

---
*Dokumentasi ini disusun secara otomatis berdasarkan artefak eksekusi simulasi CloudSim Plus pada folder `docs/scenario1/` dan `docs/scenario2/`.*
