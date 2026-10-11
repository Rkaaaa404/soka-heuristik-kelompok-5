# **Strategi Optimasi Komputasi Awan (SOKA) — Kelompok 5**
# **Dokumen Desain Proyek: Penjadwalan Cloudlet Heterogen Menggunakan Multi-Kriteria TOPSIS**

---

## **1. Jenis Task / Workload & Dataset**

### **A. Jenis Workload**
- **Independent and Non-Preemptive Batch Tasks (Bag-of-Tasks / Meta-Tasks):**
  - Setiap task berdiri sendiri dan bersifat independen, tanpa dependensi alur kerja (tidak ada ketergantungan *parent-child* atau *barrier wait state*).
  - Sekali task dieksekusi pada suatu Virtual Machine (VM), proses berlangsung secara non-preemptive hingga tuntas tanpa interupsi atau pemindahan (*migration*) ke mesin lain.
- **Klarifikasi Ilmiah: Independent Tasks vs Dependent Tasks (Scientific Workflows):**
  - **Dataset GoCJ adalah Independent Tasks:** Setiap baris pada dataset merepresentasikan pekerjaan komputasi mandiri yang dapat langsung dijadwalkan ke VM mana pun yang tersedia.
  - **Kolom SLA adalah Tingkat Prioritas Layanan (Service Level Agreement):** Nilai SLA {1, 2, 3} merujuk ke kelas prioritas layanan pelanggan (1 = *Low Priority* ~18%, 2 = *Medium Priority* ~50%, 3 = *High Priority* ~32%), **BUKAN relasi ketergantungan antar-task**.
  - **Perbandingan dengan Dependent Tasks (DAG Workflows):** Task dependen dimodelkan sebagai *Directed Acyclic Graph* (DAG) dengan format XML/DAX (seperti pada *Pegasus Scientific Workflows*: Montage, CyberShake, Epigenomics), di mana task anak (*child*) wajib menunggu output data dari task induk (*parent*). Penjadwalan DAG memerlukan algoritma workflow khusus (seperti HEFT pada *WorkflowSim*). Sementara itu, fokus proyek ini adalah penjadwalan multi-kriteria batch task independen pada platform standar *CloudSim Plus*.

### **B. Dataset Resmi Google Cloud Jobs (GoCJ)**
- Menggunakan dataset riil hasil ekstraksi beban kerja komputasi klaster produksi Google: **Enhanced GoCJ (Google Cloud Jobs)** dari repositori akademik resmi *Mendeley Data* (Hussain & Aleem, MDPI *Data* 2018; Nawaz, Hussain, & Khattak, Mendeley Data `r93dfjvvv6/5`).
- **Skala Workload:**
  - Pengujian dilakukan secara berjenjang dari skala moderat hingga masif: **1.000, 2.000, 3.000, 4.000, 5.000, 6.000, 7.000, 8.000, 9.000, dan 10.000 tasks**.
  - Dataset skala 7.000 s/d 10.000 tasks dibangkitkan menggunakan generator resmi pencipta GoCJ (`GoCJGenerator.java`) dengan algoritma *Monte Carlo Bootstrapping* berbasis file distribusi kumulatif Google (`Original_DataSet.txt`).
- **Format 3 Kolom Dataset GoCJ:**
  1. **Kolom 1 — Task Length (MI — *Million Instructions*):** Panjang instruksi komputasi yang bervariasi antara **15.000 MI hingga 900.000 MI** (rata-rata $\approx 130.000$ MI).
  2. **Kolom 2 — SLA Priority Class (1, 2, 3):** Tingkat prioritas layanan.
  3. **Kolom 3 — Arrival Time (Detik):** Waktu kedatangan task mengikuti proses Poisson non-homogen dengan pola diurnal 24 jam.
  4. **Ukuran File I/O (Simulasi Data Transfer):** Setiap task memiliki beban transfer data I/O antara **50 MB – 500 MB** (jaringan 1 Gbps / 125 MB/s).

**Kontrak pembacaan workload:** Program wajib membaca file `gocj_dataset/GoCJ_Dataset_<jumlah-task>.csv` yang sesuai dengan skala pengujian. Tidak ada fallback ke dataset skala lain maupun pembangkitan task sintetis. File wajib berisi tepat sejumlah task yang diminta, dengan tiga kolom numerik: panjang positif dan finite, SLA 1–3, serta arrival time nonnegatif dan finite. Kesalahan input menghentikan benchmark sebelum ekspor hasil. Task diurutkan menurut arrival time, lalu ID baris asli untuk kedatangan bersamaan. SLA disimpan sebagai metadata; TOPSIS bi-objektif ini belum menerapkan kebijakan prioritas SLA.

Satu label SLA invalid pada dataset 5.000 task dikoreksi secara eksplisit sebelum benchmark. Nilai awal, nilai imputasi, dan dasar koreksinya tercatat pada [catatan koreksi data](../gocj_dataset/DATA_CORRECTIONS.md).

**Model I/O:** MB transfer memakai satuan desimal (`1 MB = 1.000.000 byte`). Durasi I/O ditambahkan ke durasi CPU melalui panjang cloudlet efektif, tanpa contention bandwidth atau simulasi paket jaringan. RAM dan storage infrastruktur tetap mengikuti angka kapasitas pada tabel. Arrival time diterapkan sebagai submission delay cloudlet sejak awal simulasi; seluruh workload dipetakan offline sebelum simulasi dimulai.

---

## **2. Desain Arsitektur Cloud (Spesifikasi Simulasi)**

Arsitektur cloud dirancang secara deterministik dan transparan menggunakan **2 Datacenter Fisik**, **8 Physical Host Homogen**, dan **80 Virtual Machine Heterogen**. Setiap host menampung VM, tetapi jumlah task yang dieksekusi pada masing-masing host ditentukan TOPSIS dan pola kedatangan; pemetaan VM tidak menjamin distribusi task merata atau host selalu sibuk.

### **A. Hierarki Infrastruktur**
- **Datacenter:** 2 Datacenter Fisik (Datacenter 1 dan Datacenter 2).
- **Physical Host:** 8 Host fisik dengan spesifikasi perangkat keras identik (**Homogen**), masing-masing Datacenter menampung tepat 4 Host fisik:
  - Datacenter 1: Host 0, Host 1, Host 2, Host 3.
  - Datacenter 2: Host 4, Host 5, Host 6, Host 7.
- **Virtual Machine (VM):** 80 unit VM heterogen, dengan alokasi **10 VM per Host** secara deterministik:
  - Total 32 Small VM (VM 0-3, 10-13, 20-23, 30-33, 40-43, 50-53, 60-63, 70-73).
  - Total 32 Medium VM (VM 4-7, 14-17, 24-27, 34-37, 44-47, 54-57, 64-67, 74-77).
  - Total 16 Large VM (VM 8-9, 18-19, 28-29, 38-39, 48-49, 58-59, 68-69, 78-79).
  - Total Kapasitas Agregat Cloud: **160 vCPU**, **320 GB RAM**, tarif sewa **$9.44 per jam**.

### **B. Tabel Spesifikasi Komponen Infrastruktur**

| Level | Komponen | Jumlah | Karakteristik / Spesifikasi Teknis |
|---|---|:---:|---|
| **Physical Host (Homogen)** | Host 0 s/d Host 7 | 8 Unit | **24 Core CPU** @ 10.000 MIPS (Total 192 Core fisik)<br>**64 GB RAM** (65.536 MB), **2 TB Storage** (2.000.000 MB)<br>**Bandwidth 10 Gbps** (10.000 Mbps)<br>Scheduler: `VmSchedulerSpaceShared` (Dedicated core) |
| **Virtual Machine (Heterogen)** | **Small VM** | 32 Unit | **1 vCPU** (1.000 MIPS), **2 GB RAM** (2.048 MB), BW 1 Gbps (125 MB/s)<br>Biaya Sewa: **$0.05 / jam** |
| | **Medium VM** | 32 Unit | **2 vCPU** (2.500 MIPS), **4 GB RAM** (4.096 MB), BW 1 Gbps (125 MB/s)<br>Biaya Sewa: **$0.12 / jam** |
| | **Large VM** | 16 Unit | **4 vCPU** (5.000 MIPS), **8 GB RAM** (8.192 MB), BW 1 Gbps (125 MB/s)<br>Biaya Sewa: **$0.25 / jam** |
| **Cloudlet / Workload** | GoCJ Batch Pool | 1.000 s/d 10.000 | Panjang: 15.000 – 900.000 MI<br>Transfer I/O: 50 – 500 MB per task |

### **C. Tabel Pemetaan Deterministik VM ke Physical Host & Datacenter**

Untuk membuat penempatan VM transparan dan memastikan seluruh host menampung VM, pemetaan VM ke Host diatur secara eksplisit:

| Datacenter | Physical Host | Range ID VM | Komposisi VM per Host (10 VM) | Total vCPU | Total RAM | Status Host |
|---|---|:---:|---|:---:|:---:|:---:|
| **Datacenter 1** | Host 0 | VM 00 – 09 | 4 Small (0-3), 4 Medium (4-7), 2 Large (8-9) | 20 vCPU | 40.960 MB | **Aktif** |
| **Datacenter 1** | Host 1 | VM 10 – 19 | 4 Small (10-13), 4 Medium (14-17), 2 Large (18-19) | 20 vCPU | 40.960 MB | **Aktif** |
| **Datacenter 1** | Host 2 | VM 20 – 29 | 4 Small (20-23), 4 Medium (24-27), 2 Large (28-29) | 20 vCPU | 40.960 MB | **Aktif** |
| **Datacenter 1** | Host 3 | VM 30 – 39 | 4 Small (30-33), 4 Medium (34-37), 2 Large (38-39) | 20 vCPU | 40.960 MB | **Aktif** |
| **Datacenter 2** | Host 4 | VM 40 – 49 | 4 Small (40-43), 4 Medium (44-47), 2 Large (48-49) | 20 vCPU | 40.960 MB | **Aktif** |
| **Datacenter 2** | Host 5 | VM 50 – 59 | 4 Small (50-53), 4 Medium (54-57), 2 Large (58-59) | 20 vCPU | 40.960 MB | **Aktif** |
| **Datacenter 2** | Host 6 | VM 60 – 69 | 4 Small (60-63), 4 Medium (64-67), 2 Large (68-69) | 20 vCPU | 40.960 MB | **Aktif** |
| **Datacenter 2** | Host 7 | VM 70 – 79 | 4 Small (70-73), 4 Medium (74-77), 2 Large (78-79) | 20 vCPU | 40.960 MB | **Aktif** |

*Rasio Konsumsi Sumber Daya Host:*
- Alokasi vCPU per Host = 20 vCPU $\le$ 24 physical core (Aman, utilisasi 83.3%).
- Alokasi RAM per Host = 40.960 MB $\le$ 65.536 MB (Aman, utilisasi 62.5%).

---

## **3. Fungsi Objektif & Formulasi Matematis TOPSIS**

Sistem menerapkan optimasi multi-objektif (*Bi-Objective Optimization*) dengan bobot seimbang $w_{\text{time}} = 0.5$ dan $w_{\text{cost}} = 0.5$.

### **A. Minimalisasi Makespan ($f_1$)**
Makespan adalah waktu selesai keseluruhan hingga task terakhir tuntas dieksekusi di seluruh 80 VM:
$$\min f_1 = \max_{j \in \{0, \dots, 79\}} CT_j$$
di mana $CT_j$ adalah waktu selesai (*Completion Time*) pada VM ke-$j$.

### **B. Minimalisasi Total Biaya Eksekusi / Cost ($f_2$)**
Total biaya dihitung menggunakan model penelitian berbasis akumulasi durasi task dengan pembulatan per jam:
$$\min f_2 = \sum_{j=0}^{79} \left( \left\lceil \frac{\text{BusyTime}_j}{3600} \right\rceil \times \text{HourlyRate}_j \right)$$

`BusyTime_j` adalah jumlah durasi eksekusi seluruh task pada VM ke-j, termasuk tambahan durasi I/O. Pada VM multi-core, task yang paralel tetap dijumlahkan sehingga BusyTime dapat melebihi durasi VM aktif. Waktu idle tidak ditagihkan dalam model ini; formula tersebut bukan klaim kesetaraan dengan tagihan aktual penyedia cloud. Kriteria biaya TOPSIS per task tetap memakai biaya proporsional durasi, sedangkan metrik akhir memakai pembulatan per jam di atas.

### **C. Formulasi Lengkap Algoritma Multi-Kriteria TOPSIS**
Untuk setiap task $i$ dalam antrean, dibentuk matriks keputusan $X = [x_{jk}]_{80 \times 2}$ di mana kriteria 1 adalah Completion Time dan kriteria 2 adalah Execution Cost:

1. **Perhitungan Elemen Matriks Keputusan:**
   $$\text{ExecTime}_{jk} = \frac{\text{Length}_i}{\text{MIPS}_j} + \frac{\text{IO\_Bytes}_i}{\text{Bandwidth}_j}$$
   $$\text{PredictedReadyTime}_j = \min_{p \in \text{PE}_j} \text{ReadyTime}_{jp}$$
   $$x_{j, 1} = \max(\text{ArrivalTime}_i, \text{PredictedReadyTime}_j) + \text{ExecTime}_{jk}, \qquad x_{j, 2} = \text{ExecTime}_{jk} \times \frac{\text{HourlyRate}_j}{3600}$$

   Setiap cloudlet memakai satu PE. Small, Medium, dan Large dapat menjalankan masing-masing 1, 2, dan 4 task secara paralel dengan `CloudletSchedulerSpaceShared`. Setelah VM dipilih, waktu tersedia PE yang paling awal diperbarui menjadi completion time prediksi task. Tie antar-PE memakai indeks terkecil. Jika skor TOPSIS beberapa VM tepat sama, pilih VM dengan jumlah task teralokasi paling sedikit, lalu indeks terkecil; skor yang lebih rendah tidak dipilih demi pemerataan. Aturan ini mencegah kecenderungan memilih VM berindeks awal saat kedatangan jarang, tanpa menambahkan kriteria atau bobot TOPSIS baru. Untuk menyamakan prediksi dengan representasi cloudlet, panjang efektif dibulatkan ke atas ke MI integer: `ceil(Length + IO_Bytes / Bandwidth × MIPS)`, lalu dibagi MIPS. Prediksi tidak memasukkan overhead startup VM dan resolusi event engine CloudSim.
2. **Normalisasi Vektor (Vector Normalization):**
   $$r_{jk} = \frac{x_{jk}}{\sqrt{\sum_{p=0}^{79} x_{pk}^2}}, \quad k \in \{1, 2\}$$
3. **Pembobotan Matriks Ternormalisasi:**
   $$v_{jk} = w_k \cdot r_{jk}, \quad \text{dengan } w_1 = 0.5, \; w_2 = 0.5$$
4. **Solusi Ideal Positif ($A^+$) dan Negatif ($A^-$):**
   Karena waktu dan biaya merupakan kriteria biaya (*cost criteria*):
   $$A^+ = \{\min_p v_{p1}, \; \min_p v_{p2}\}, \qquad A^- = \{\max_p v_{p1}, \; \max_p v_{p2}\}$$
5. **Jarak Euclidean ke Solusi Ideal:**
   $$d_j^+ = \sqrt{\sum_{k=1}^{2} (v_{jk} - A_k^+)^2}, \qquad d_j^- = \sqrt{\sum_{k=1}^{2} (v_{jk} - A_k^-)^2}$$
6. **Skor Kedekatan Relatif ($C_j^*$):**
   $$C_j^* = \frac{d_j^-}{d_j^+ + d_j^- + 10^{-9}}$$
   Task dialokasikan ke VM dengan skor $C_j^*$ tertinggi: $\text{Chosen VM} = \arg\max_j C_j^*$.

---

## **4. Metrik Evaluasi & Batasan Sistem (*Constraints*)**

### **A. Metrik Evaluasi yang Diukur**
1. **Makespan (Detik):** Timestamp selesai task terakhir sejak awal simulasi, termasuk interval kedatangan dan antrean task.
2. **Total Execution Cost (USD):** Biaya menurut akumulasi BusyTime dan pembulatan per jam pada model penelitian.
3. **Degree of Imbalance (DI):** Mengukur seimbang/tidaknya beban kerja di seluruh 80 VM:
   $$\text{DI} = \frac{T_{\max} - T_{\min}}{T_{\text{avg}}}$$
   Di sini `T_j = BusyTime_j` untuk seluruh 80 VM, termasuk VM tanpa task (`T_j = 0`). Timestamp selesai tidak dipakai untuk DI agar waktu idle menunggu kedatangan tidak dianggap sebagai beban komputasi. Metrik ini mengukur ketimpangan akumulasi durasi task, bukan utilisasi yang dinormalisasi terhadap jumlah PE.
4. **Waktu Komputasi Algoritma (Runtime, ms):** Durasi eksekusi penjadwal TOPSIS untuk memetakan seluruh batch task.
5. **Uji Konsistensi Statistik (3x Repeated Runs):** Setiap skala task (1k s/d 10k) dieksekusi sebanyak 3 kali pengulangan dengan variasi seed I/O transfer untuk mengukur nilai rata-rata (*Mean*) dan standar deviasi (*Std Dev*).
   Standar deviasi memakai rumus sampel (`n - 1`, `ddof=1`) secara konsisten pada Java dan Python. CSV alokasi VM merepresentasikan skala 10.000 task pada run ketiga; CSV metrik memuat seluruh 30 run.

### **B. Batasan Sistem (*Cloud Constraints*)**
- **Resource Capacity Constraint:** Total vCPU (20 core) dan RAM (40 GB) dari 10 VM per host tidak boleh melebihi kapasitas host (24 core, 64 GB).
- **Non-Preemptive Constraint:** Task yang sedang berjalan tidak dapat dipotong atau dialihkan ke VM lain.
- **Mapping Constraint:** Setiap task harus dialokasikan ke tepat satu VM tanpa ada task ganda atau terlewat.
  Program memeriksa ID, status sukses, VM tujuan, dan waktu mulai setiap task sebelum menghitung metrik. Seluruh 80 VM juga harus berhasil dibuat pada host dan datacenter yang ditetapkan. Kegagalan validasi atau ekspor menghentikan program dengan error, tanpa pesan sukses.

---

## **5. Perangkat & Lingkungan Implementasi (*Tools*)**

- **Simulasi Cloud (Engine Utama):** **CloudSim Plus 8.0.0 (Java 17)** — Framework simulasi standar industri dan riset komputasi awan yang memodelkan entitas fisik (Datacenter, Host, PE, Network, dan VM Allocation Policy) secara presisi.
- **Analisis Data & Visualisasi:** **Python 3.11 (Pandas & Matplotlib)** — Membaca data hasil ekspor simulasi CloudSim Plus (`docs/cloudsim_metrics.csv` dan `docs/cloudsim_vm_allocation.csv`) serta menghasilkan grafik visualisasi publikasi resolusi tinggi.
- **Pembersihan SimPy:** SimPy dihapus sepenuhnya untuk menjaga fokus tunggal pada CloudSim Plus sebagai simulator resmi standar de facto.
