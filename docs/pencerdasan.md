# 📘 Panduan & Modul Pencerdasan Tim — Kelompok 5
**Mata Kuliah:** Strategi Optimasi Komputasi Awan (SOKA)  
**Topik Proyek:** Penjadwalan Cloudlet Heterogen Menggunakan Multi-Criteria Decision Making (TOPSIS)  
**Arsitektur:** 2 Datacenter, 8 Physical Host Homogen, 80 Virtual Machine Heterogen  
**Workload:** Google Cloud Jobs (GoCJ) Skala Masif 1.000 s/d 10.000 Tasks (3x Repeated Runs)  
**Tujuan Dokumen:** Referensi bersama seluruh anggota tim untuk persiapan Demo Ulang, penguasaan teori arsitektur, pemahaman kode, dan panduan menjawab pertanyaan evaluasi dosen.

---

## 📑 Daftar Isi
1. [Elevator Pitch (Penjelasan Singkat 60 Detik)](#1-elevator-pitch-penjelasan-singkat-60-detik)
2. [Checklist Evaluasi Demo Dosen & Solusi Revisi](#2-checklist-evaluasi-demo-dosen--solusi-revisi)
3. [Arsitektur Cloud: 2 Datacenter, 8 Host, 80 VM (Deterministik)](#3-arsitektur-cloud-2-datacenter-8-host-80-vm-deterministik)
4. [Karakteristik Ilmiah Dataset GoCJ (Independent vs Dependent)](#4-karakteristik-ilmiah-dataset-gocj-independent-vs-dependent)
5. [Cara Kerja Matematis Algoritma TOPSIS (Langkah demi Langkah)](#5-cara-kerja-matematis-algoritma-topsis-langkah-demi-langkah)
6. [Penjelasan 4 Metrik Evaluasi & Metodologi Multi-Run](#6-penjelasan-4-metrik-evaluasi--metodologi-multi-run)
7. [Panduan Eksekusi Simulasi & Visualisasi (Hands-On)](#7-panduan-eksekusi-simulasi--visualisasi-hands-on)
8. [FAQ Tanya-Jawab Demo Dosen (6 Pertanyaan Kunci Evaluasi)](#8-faq-tanya-jawab-demo-dosen-6-pertanyaan-kunci-evaluasi)
9. [Analisis Mendalam Hasil Simulasi Skala Masif 1k s/d 10k Tasks](#9-analisis-mendalam-hasil-simulasi-skala-masif-1k-sd-10k-tasks)

---

## 1. Elevator Pitch (Penjelasan Singkat 60 Detik)
> *"Proyek ini memecahkan masalah **penjadwalan batch task** pada infrastruktur komputasi awan yang terdistribusi dan **heterogen**. Kami mengimplementasikan arsitektur berskala besar pada **CloudSim Plus 8.0.0** yang terdiri dari **2 Datacenter Fisik**, **8 Physical Host homogen** (masing-masing 24 core CPU @ 10.000 MIPS dan 64 GB RAM), serta **80 Virtual Machine heterogen** (10 VM per Host: 4 Small, 4 Medium, 2 Large) dengan total kapasitas **160 vCPU** dan **320 GB RAM**.*
>
> *Untuk menghapus kelemahan alokasi blackbox sebelumnya, kami menerapkan **pemetaan deterministik transparan** di mana Host 0–3 menampung VM 0–39 di Datacenter 1, dan Host 4–7 menampung VM 40–79 di Datacenter 2. Seluruh host terbukti aktif memproses beban kerja tanpa ada host yang menganggur.*
>
> *Penjadwalan dioptimasi menggunakan algoritma multi-kriteria **TOPSIS** dengan bobot seimbang ($w_{\text{time}} = 0.5, w_{\text{cost}} = 0.5$) untuk meminimalkan Makespan dan Total Biaya Sewa secara simultan. Kami menguji performa sistem menggunakan trace riil **Google Cloud Jobs (GoCJ)** dari skala **1.000 hingga 10.000 tasks** yang dieksekusi masing-masing **3 kali repeated runs** untuk menjamin konsistensi statistik."*

---

## 2. Checklist Evaluasi Demo Dosen & Solusi Revisi

Berdasarkan lembar catatan evaluasi Demo 1 (`docs/notes.txt`), berikut rangkuman kekurangan dan perbaikan total yang telah diimplementasikan:

| Poin Evaluasi Dosen | Masalah Saat Demo 1 | Solusi & Status Revisi pada Demo Ulang | Bukti di Kode / Dokumen |
|---|---|---|---|
| **1. Pemetaan VM ke Host** | Tidak memahami alokasi VM ke Host; mengira VM standalone. | Membangun hierarki penuh: Datacenter $\rightarrow$ Host $\rightarrow$ VM. Dibuat 2 Datacenter dan 8 Host dengan alokasi tepat 10 VM/host. | `TopsisCloudSim.java` baris 260–335 & Tabel Arsitektur |
| **2. Alokasi Host Timpang (Blackbox)** | Seluruh VM menumpuk di Host 2 dan 3; Host 0 dan 1 kosong/menganggur. | Mengimplementasikan custom `hostSelector` pada `VmAllocationPolicySimple` dan broker Datacenter mapper. Tidak ada host menganggur (~1.250 task/host). | `cloudsim_vm_allocation.csv` & `cloudsim_datacenter_host_distribution.png` |
| **3. Tertukar Konsep Homogen vs Heterogen** | Menyebut spesifikasi disamakan sebagai heterogen. | Klarifikasi tegas: **Host bersifat Homogen** (spesifikasi fisik sama), **VM bersifat Heterogen** (3 kelas: Small, Medium, Large). | Dokumen ini (Bab 3 & 8) dan `docs/Kelompok 5_Design Project.md` |
| **4. Karakteristik Dataset (Independent vs DAG)** | Ragu-ragu dan mengira kolom SLA adalah dependensi task. | Menjelaskan secara ilmiah bahwa GoCJ adalah **Independent Tasks** (Bag-of-Tasks). Kolom SLA adalah **Service Level Agreement** (Prioritas: 1=Low, 2=Medium, 3=High). | Dokumen ini (Bab 4) & `GoCJGenerator.java` |
| **5. Cakupan Pengujian Minim** | Pengujian hanya pada 1.000 task dan dijalankan 1 kali saja. | Memperluas pengujian dari **1.000 hingga 10.000 tasks** (10 skala ukuran), masing-masing dijalankan **3 kali pengulangan (total 30 run)** dengan analisis Mean $\pm$ Std Dev. | `cloudsim_metrics.csv` & `cloudsim_evaluation_metrics.png` |
| **6. Perbandingan SimPy Membingungkan** | SimPy membingungkan evaluasi dan tidak fokus. | **Menghapus SimPy sepenuhnya**; fokus 100% pada CloudSim Plus (Java) sebagai engine simulasi dan Python murni untuk visualisasi data. | `python/visualize_cloudsim.py` & `kelompok_5_TOPSIS.ipynb` |

---

## 3. Arsitektur Cloud: 2 Datacenter, 8 Host, 80 VM (Deterministik)

### A. Definisi Mutlak: Homogen vs Heterogen
- **Homogen (Spesifikasi Sama):**
  - **8 Physical Host:** Seluruh 8 host fisik memiliki spesifikasi perangkat keras identik: **24 Core CPU @ 10.000 MIPS**, **64 GB RAM**, **2 TB Storage**, **10 Gbps Bandwidth**.
  - Menggunakan `VmSchedulerSpaceShared`: Setiap core dialokasikan secara eksklusif (dedicated) untuk vCPU VM tanpa preemption.
- **Heterogen (Spesifikasi Berbeda):**
  - **80 Virtual Machine:** Terbagi menjadi 3 kelas komputasi yang berbeda kapasitas dan tarif sewanya:
    - **Small (32 unit):** 1 vCPU (1.000 MIPS), 2 GB RAM, $0.05/jam.
    - **Medium (32 unit):** 2 vCPU (2.500 MIPS), 4 GB RAM, $0.12/jam.
    - **Large (16 unit):** 4 vCPU (5.000 MIPS), 8 GB RAM, $0.25/jam.

### B. Tabel Pemetaan Deterministik Alokasi VM
Setiap Host fisik menampung tepat **10 VM Heterogen** (4 Small, 4 Medium, 2 Large):
- Total vCPU per Host = $(4 \times 1) + (4 \times 2) + (2 \times 4) = 20$ vCPU $\le$ 24 physical core (Aman).
- Total RAM per Host = $(4 \times 2) + (4 \times 4) + (2 \times 8) = 40$ GB $\le$ 64 GB physical RAM (Aman).

```
[Infrastruktur Cloud Kelompok 5]
├── Datacenter 1 (DC 0) ── Menampung VM 0 s/d 39 (40 VM)
│   ├── Host 0 ── VM 00-03 (Small), VM 04-07 (Medium), VM 08-09 (Large)
│   ├── Host 1 ── VM 10-13 (Small), VM 14-17 (Medium), VM 18-19 (Large)
│   ├── Host 2 ── VM 20-23 (Small), VM 24-27 (Medium), VM 28-29 (Large)
│   └── Host 3 ── VM 30-33 (Small), VM 34-37 (Medium), VM 38-39 (Large)
└── Datacenter 2 (DC 1) ── Menampung VM 40 s/d 79 (40 VM)
    ├── Host 4 ── VM 40-43 (Small), VM 44-47 (Medium), VM 48-49 (Large)
    ├── Host 5 ── VM 50-53 (Small), VM 54-57 (Medium), VM 58-59 (Large)
    ├── Host 6 ── VM 60-63 (Small), VM 64-67 (Medium), VM 68-69 (Large)
    └── Host 7 ── VM 70-73 (Small), VM 74-77 (Medium), VM 78-79 (Large)
```

### C. Implementasi Kode Pemetaan pada CloudSim Plus
1. **Pemetaan Datacenter pada Broker:**
   ```java
   broker.setDatacenterMapper((lastDc, vm) -> (vm.getId() < 40) ? dc1 : dc2);
   ```
2. **Pemetaan Host Deterministik pada Kebijakan Alokasi Host:**
   ```java
   BiFunction<VmAllocationPolicy, Vm, Optional<Host>> dc1HostSelector = (policy, vm) -> {
       int localHostIdx = ((int) vm.getId() % 40) / 10;
       return Optional.of(policy.getHostList().get(localHostIdx));
   };
   BiFunction<VmAllocationPolicy, Vm, Optional<Host>> dc2HostSelector = (policy, vm) -> {
       int localHostIdx = ((int) (vm.getId() - 40) % 40) / 10;
       return Optional.of(policy.getHostList().get(localHostIdx));
   };
   ```
Mekanisme ini menjamin alokasi 100% transparan, terprediksi, dan terdokumentasi tanpa host yang menganggur.

---

## 4. Karakteristik Ilmiah Dataset GoCJ (Independent vs Dependent)

### A. Independent Tasks (Bag-of-Tasks / Meta-Tasks)
- Dataset **Google Cloud Jobs (GoCJ)** adalah sekumpulan task independen yang diekstraksi dari trace klaster Google nyata (Hussain & Aleem, MDPI 2018; Mendeley Data `b7bp6xhrcd/1` & `r93dfjvvv6/5`).
- Setiap task merepresentasikan job komputasi mandiri. Tidak ada *data dependency*, tidak ada graph precedence, dan tidak ada task yang harus menunggu task lain selesai sebelum mulai dieksekusi.

### B. Arti Kolom SLA (Bukan Task Dependency!)
Setiap file CSV GoCJ memiliki 3 kolom angka tanpa header:
Contoh baris: `83000, 2, 3`
1. **Kolom 1: Task Length / Job Size (Satuan: MI — Million Instructions)**  
   Panjang beban komputasi CPU (15.000 s/d 900.000 MI).
2. **Kolom 2: SLA Class (Level of Priority: 1, 2, atau 3)**  
   Merujuk ke tingkat jaminan layanan (*Service Level Agreement*):
   - `1`: *Low Priority* (~18% populasi) — Best-effort batch computing.
   - `2`: *Medium Priority* (~50% populasi) — Layanan standar.
   - `3`: *High Priority* (~32% populasi) — Task mission-critical / latency-sensitive.  
   **Ini adalah level prioritas antrean, BUKAN relasi dependensi!**
3. **Kolom 3: Arrival Time (Satuan: Detik)**  
   Waktu kedatangan task ke broker cloud mengikuti proses Poisson non-homogen.

### C. Perbandingan Ilmiah dengan Dependent Tasks (Scientific Workflows)
- **Dependent Tasks:** Dimodelkan sebagai **Directed Acyclic Graph (DAG)** berformat XML/DAX (seperti *Pegasus Scientific Workflows*: Montage astronomi, CyberShake gempa bumi, Epigenomics genomika).
- Task dependen memiliki relasi orang tua-anak (*parent-child*), memerlukan workflow engine khusus (misal algoritma HEFT pada *WorkflowSim*).
- Penjadwalan TOPSIS pada proyek Kelompok 5 berfokus pada **multi-criteria independent batch scheduling** pada simulator standar industri *CloudSim Plus*.

---

## 5. Cara Kerja Matematis Algoritma TOPSIS (Langkah demi Langkah)

TOPSIS (*Technique for Order Preference by Similarity to Ideal Solution*) mengevaluasi alternatif Virtual Machine ($m = 80$ VM) terhadap 2 kriteria objektif ($n = 2$: Completion Time dan Execution Cost).

```
[Task Masuk dari GoCJ]
         │
         ▼
1. Bentuk Decision Matrix X (80 VM × 2 Kriteria: Completion Time & Cost)
         │
         ▼
2. Normalisasi Vektor r_ij = x_ij / sqrt(sum(x_kj^2))
         │
         ▼
3. Pembobotan v_ij = w_j * r_ij (w_time = 0.5, w_cost = 0.5)
         │
         ▼
4. Tentukan Solusi Ideal:
   • A+ (Ideal Positif) = MINIMUM (Waktu tercepat, Biaya termurah)
   • A- (Ideal Negatif) = MAKSIMUM (Waktu terlama, Biaya termahal)
         │
         ▼
5. Hitung Jarak Euclidean d+ dan d- ke Solusi Ideal
         │
         ▼
6. Hitung Skor Kedekatan Relatif: C_i* = d- / (d+ + d-)
         │
         ▼
[Alokasikan Task ke VM dengan Skor C_i* Tertinggi]
         │
         ▼
[Perbarui Waktu Tersedia PE pada VM Terpilih]
```

### Rumus Matematis:
1. **Decision Matrix:**
   $$\text{ExecTime}_j = \frac{\text{Length}}{\text{MIPS}_j} + \frac{\text{IO\_Bytes}}{\text{Bandwidth}_j}$$
   $$x_{j, 1} = \max(\text{ArrivalTime}_i, \text{PredictedReadyTime}_j) + \text{ExecTime}_j, \qquad x_{j, 2} = \text{ExecTime}_j \times \frac{\text{HourlyRate}_j}{3600}$$
2. **Normalisasi Vektor:**
   $$r_{jk} = \frac{x_{jk}}{\sqrt{\sum_{p=0}^{79} x_{pk}^2}}$$
3. **Solusi Ideal Positif ($A^+$) dan Solusi Ideal Negatif ($A^-$):**
   Karena waktu dan biaya adalah *cost criteria* (semakin kecil nilainya, semakin baik):
   $$A^+ = \{\min_p v_{p1}, \; \min_p v_{p2}\}, \qquad A^- = \{\max_p v_{p1}, \; \max_p v_{p2}\}$$
4. **Jarak Euclidean:**
   $$d_j^+ = \sqrt{(v_{j1} - A_1^+)^2 + (v_{j2} - A_2^+)^2}, \qquad d_j^- = \sqrt{(v_{j1} - A_1^-)^2 + (v_{j2} - A_2^-)^2}$$
5. **Skor Kedekatan Relatif:**
   $$C_j^* = \frac{d_j^-}{d_j^+ + d_j^- + 10^{-9}}$$

---

## 6. Penjelasan 4 Metrik Evaluasi & Metodologi Multi-Run

1. **Makespan (detik):** Total waktu selesai hingga task terakhir selesai di seluruh 80 VM ($\max_j CT_j$).
2. **Total Execution Cost (USD):** Total biaya sewa seluruh VM menggunakan aturan penagihan per jam (*hourly billing*):
   $$\text{Total Cost} = \sum_{j=0}^{79} \lceil \frac{\text{BusyTime}_j}{3600} \rceil \times \text{HourlyRate}_j$$
   BusyTime menjumlahkan durasi seluruh task termasuk I/O pada setiap VM; task paralel tetap dijumlahkan. Model ini tidak memakai makespan untuk menghitung biaya dan bukan tagihan aktual penyedia cloud. Tarif agregat satu jam jika semua VM memiliki BusyTime positif adalah **$9.44**.
3. **Degree of Imbalance (DI):** Mengukur seberapa merata beban terdistribusi antar VM:
   $$\text{DI} = \frac{T_{\max} - T_{\min}}{T_{\text{avg}}}$$
   `T_j` adalah BusyTime VM, termasuk nilai nol pada VM tanpa task. Makespan memasukkan waktu tunggu kedatangan; DI tidak memakai timestamp selesai.
4. **Runtime Algoritma (ms):** Waktu komputasi yang dihabiskan algoritma TOPSIS untuk memetakan seluruh task.
5. **Metodologi 3x Repeated Runs:** Setiap skala task (1.000 s/d 10.000) diuji sebanyak 3 kali dengan seed variasi transfer I/O berbeda (`seed = 42 + (scale * 10) + run`), lalu dihitung nilai **Mean $\pm$ Std Dev sampel** (`ddof=1`).

---

## 7. Panduan Eksekusi Simulasi & Visualisasi (Hands-On)

CSV setiap skala wajib tersedia dan valid; tidak ada fallback. Koreksi SLA 4 menjadi 2 pada dataset 5.000 task dicatat sebagai imputasi pada [catatan koreksi data](../gocj_dataset/DATA_CORRECTIONS.md). Jalankan `gradlew.bat check` untuk memeriksa regresi sebelum benchmark.

### Langkah 1: Eksekusi Generator GoCJ (Jika Ingin Membangkitkan Dataset Baru)
```bash
# Dari root proyek task-4
java -cp java/build/classes/java/main com.soka.kelompok5.GoCJGenerator
```

### Langkah 2: Menjalankan Simulasi CloudSim Plus (30 Runs)
```bash
# Masuk ke folder java dan jalankan via Gradle
cd java
gradlew.bat run
```
*Hasil:* Mengeksekusi 10 variasi skala (1k s/d 10k task) $\times$ 3 runs, mencetak tabel arsitektur pemetaan VM, tabel ringkasan statistik (Mean $\pm$ Std Dev), dan mengekspor 2 file CSV ke folder `docs/`:
- `docs/cloudsim_metrics.csv` (30 baris data metrik individual run).
- `docs/cloudsim_vm_allocation.csv` (alokasi dan busy time 80 VM pada 10.000 task).

### Langkah 3: Menjalankan Visualisasi Python (3 Gambar Publikasi)
```bash
# Dari root task-4
python python/visualize_cloudsim.py
```
*Hasil:* Menghasilkan 3 gambar PNG beresolusi tinggi (300 DPI) di folder `docs/`:
1. `docs/cloudsim_evaluation_metrics.png` (4 subplot metrik dengan error band 3 runs).
2. `docs/cloudsim_datacenter_host_distribution.png` (distribusi beban per Host pada DC 1 dan DC 2).
3. `docs/cloudsim_vm_workload_distribution.png` (distribusi beban pada kelas Small, Medium, Large).

### Langkah 4: Menjalankan Jupyter Notebook
Buka `python/kelompok_5_TOPSIS.ipynb` di VS Code / Jupyter Lab dan klik **Run All Cells**.

---

## 8. FAQ Tanya-Jawab Demo Dosen (6 Pertanyaan Kunci Evaluasi)

### ❓ Q1: "Mengapa alokasi kalian pada Demo 1 sebelumnya timpang dan menumpuk di Host 2 dan 3, lalu bagaimana arsitektur 2 Datacenter sekarang menjamin pemetaan terstruktur?"
> **Jawaban:**  
> *"Pada Demo 1, alokasi VM ke host menggunakan kebijakan default CloudSim Plus (`VmAllocationPolicySimple`) yang mencari host pertama yang muat secara first-fit. Karena Host 2 dan 3 memiliki kapasitas core dan RAM yang lebih besar (16 core, 64 GB), kebijakan default langsung menempatkan seluruh 10 VM di sana hingga penuh, sehingga Host 0 dan 1 dibiarkan menganggur tanpa kita sadari.*  
>  
> *Pada arsitektur baru ini, kami merestrukturisasi sistem menjadi **2 Datacenter Fisik** dan mengimplementasikan **pemetaan deterministik eksplisit**:*  
> *1. Broker Datacenter Mapper membagi VM secara merata: VM 0–39 ke Datacenter 1 dan VM 40–79 ke Datacenter 2.*  
> *2. Di setiap Datacenter, kami memasang **custom hostSelector** pada `VmAllocationPolicySimple` dengan rumus `((int) vm.getId() % 40) / 10`. Rumus ini secara matematis mengarahkan tepat 10 VM ke masing-masing host lokal (Host 0 menerima VM 0–9, Host 1 menerima VM 10–19, dst).*  
> *Hasilnya pada skala 10.000 task, seluruh 8 host terbukti memproses beban kerja merata antara **1.243 hingga 1.252 task per host** tanpa ada host yang menganggur sama sekali."*

---

### ❓ Q2: "Apa perbedaan konsep homogen dan heterogen pada sistem cloud kalian?"
> **Jawaban:**  
> *"Perbedaannya terletak pada keseragaman spesifikasi teknis komponen:*  
> *- **Homogen:** Komponen memiliki konfigurasi hardware yang identik. Di sistem kami, seluruh **8 Physical Host bersifat Homogen**, yaitu sama-sama memiliki 24 Core CPU @ 10.000 MIPS, 64 GB RAM, 2 TB Storage, dan 10 Gbps Bandwidth.*  
> *- **Heterogen:** Komponen memiliki konfigurasi kapasitas dan karakteristik performa yang berbeda. Di sistem kami, **80 Virtual Machine bersifat Heterogen**, terbagi menjadi 3 kelas: Small (1 vCPU, 1.000 MIPS), Medium (2 vCPU, 2.500 MIPS), dan Large (4 vCPU, 5.000 MIPS) dengan tarif sewa yang berbeda.*  
> *Heterogenitas inilah yang menjadi objek uji bagi algoritma optimasi TOPSIS agar mampu membagi beban secara adil dan efisien."*

---

### ❓ Q3: "Apakah task pada Google Cloud Jobs itu independen atau dependen, dan apa maksud kolom SLA di dataset?"
> **Jawaban:**  
> *"Berdasarkan publikasi resmi penciptanya (Altaf Hussain & Muhammad Aleem, MDPI Data 2018; Mendeley Data), **dataset GoCJ adalah Independent Tasks (Bag-of-Tasks / Meta-Tasks)**. Setiap task berdiri sendiri dan dapat langsung dieksekusi tanpa graph precedence atau barrier wait state dari task lain.*  
>  
> *Mengenai **kolom SLA**, itu adalah singkatan dari **Service Level Agreement (Tingkat Prioritas Layanan)**, BUKAN task dependency! Nilai 1 berarti Low Priority (~18%), nilai 2 berarti Medium Priority (~50%), dan nilai 3 berarti High Priority (~32%).*  
>  
> *Task dependen hanya ada pada **Scientific Workflows** yang dimodelkan sebagai Directed Acyclic Graph (DAG) berformat XML/DAX (seperti Pegasus Workflows Montage atau CyberShake), yang membutuhkan scheduler workflow khusus seperti HEFT pada WorkflowSim. TOPSIS kami berfokus pada penjadwalan multi-kriteria independent batch tasks."*

---

### ❓ Q4: "Mengapa kalian tidak lagi menggunakan SimPy dan hanya fokus pada CloudSim Plus?"
> **Jawaban:**  
> *"Pada Demo 1, perbandingan dengan SimPy membingungkan evaluasi karena SimPy hanyalah discrete-event queueing engine generik tanpa pemodelan fisik cloud yang presisi.  
>  
> Sebaliknya, **CloudSim Plus** adalah simulator standar industri dan de facto riset akademis cloud computing yang memodelkan entitas fisik secara ketat: hierarki Datacenter, Physical Host, Processing Elements (PE), alokasi vCPU, bandwidth jaringan, serta VmAllocationPolicy.  
>  
> Oleh karena itu, kami menghapus SimPy sepenuhnya untuk menjaga fokus tunggal pada CloudSim Plus (Java) sebagai engine simulasi utama, sedangkan Python kami gunakan secara proporsional sebagai alat analisis statistik (Pandas) dan visualisasi grafik (Matplotlib)."*

---

### ❓ Q5: "Bagaimana cara membaca dan menginterpretasikan hasil pengujian 1.000 s/d 10.000 tasks dengan 3 kali run?"
> **Jawaban:**  
> Hasil terbaru menerapkan arrival time dan antrean per PE. Rata-rata makespan meningkat dari 1.795,32 detik pada 1.000 task menjadi 23.118,20 detik pada 10.000 task. Perubahan antar-skala juga dipengaruhi pola kedatangan dataset, sehingga tidak disimpulkan sebagai hubungan linear terhadap jumlah task saja.
>
> Biaya dihitung dari akumulasi durasi task per VM dengan pembulatan per jam, bukan dari makespan. Pada 10.000 task rata-ratanya $23,20. DI memakai BusyTime dan tidak dinormalisasi terhadap jumlah PE. Runtime bergantung pada mesin dan kondisi JVM; tiga repeated runs tidak membuktikan kesiapan sistem real-time atau optimalitas global.

---

### ❓ Q6: "Tunjukkan di baris kode mana alokasi VM ke Host dan Datacenter dilakukan!"
> **Jawaban (Tunjukkan Langsung di Editor):**  
> Buka `java/src/main/java/com/soka/kelompok5/TopsisCloudSim.java` dan cari nama method agar referensi tetap berlaku setelah refactor:
>
> - `buildVmSpecs()`: komposisi 4 Small, 4 Medium, 2 Large pada setiap host.
> - `createHostList()`: spesifikasi dan scheduler host fisik.
> - `createBroker()`: host selector berdasarkan `spec.hostId % HOSTS_PER_DATACENTER` dan datacenter mapper berdasarkan `spec.dcId`.
> - `scheduleTasksTopsis()`: prediksi per PE dan arrival time. Saat skor TOPSIS tepat sama, VM dengan jumlah task paling sedikit dipilih, lalu indeks terkecil.
> - `validateVmAllocation()` dan `validateFinishedTasks()`: pemeriksaan penempatan VM serta setiap task selesai tepat sekali.
>
> Saat benchmark dijalankan, tabel arsitektur dicetak dari konfigurasi VM.

---

## 9. Analisis Mendalam Hasil Simulasi Skala Masif 1k s/d 10k Tasks

### A. Tabel Data Rangkuman Statistik (`docs/cloudsim_metrics.csv`)
Hasil terakhir dari 3 repeated runs per skala; standar deviasi sampel (`ddof=1`).

| Tasks | Makespan (s) | Total Cost (USD) | DI (BusyTime) | Runtime TOPSIS (ms) |
|---:|---:|---:|---:|---:|
| 1000 | 1795.32 ± 0.50 | 9.44 ± 0.00 | 1.8791 ± 0.1453 | 27.36 ± 13.83 |
| 2000 | 3057.22 ± 7.00 | 9.44 ± 0.00 | 1.6819 ± 0.0824 | 37.45 ± 6.93 |
| 3000 | 4090.30 ± 1.30 | 10.12 ± 0.07 | 1.4266 ± 0.0413 | 31.10 ± 8.98 |
| 4000 | 4622.44 ± 1.51 | 13.28 ± 0.00 | 1.4659 ± 0.0223 | 33.48 ± 0.35 |
| 5000 | 5188.17 ± 1.32 | 13.28 ± 0.00 | 1.4410 ± 0.0228 | 36.97 ± 1.94 |
| 6000 | 6200.55 ± 1.41 | 14.20 ± 0.14 | 1.3660 ± 0.0547 | 56.10 ± 3.93 |
| 7000 | 16403.96 ± 0.78 | 17.57 ± 0.17 | 1.8876 ± 0.0490 | 53.61 ± 6.02 |
| 8000 | 18789.69 ± 1.08 | 20.78 ± 0.28 | 2.0017 ± 0.0641 | 69.51 ± 2.86 |
| 9000 | 21023.49 ± 0.47 | 22.36 ± 0.18 | 1.9103 ± 0.0320 | 72.63 ± 11.55 |
| 10000 | 23118.20 ± 0.69 | 23.20 ± 0.18 | 1.8765 ± 0.0228 | 85.60 ± 18.93 |

### B. Distribusi Beban per Physical Host (10.000 Tasks, Run 3)

| Datacenter | Host | Jumlah Task | Akumulasi Durasi Task (s) |
|---|---|---:|---:|
| DC_1 | Host_0 | 1252 | 75122.78 |
| DC_1 | Host_1 | 1252 | 78291.67 |
| DC_1 | Host_2 | 1252 | 78525.54 |
| DC_1 | Host_3 | 1252 | 76170.34 |
| DC_2 | Host_4 | 1252 | 78101.11 |
| DC_2 | Host_5 | 1249 | 79025.51 |
| DC_2 | Host_6 | 1248 | 82995.34 |
| DC_2 | Host_7 | 1243 | 78947.91 |

Seluruh delapan host menerima task pada run ini, sebanyak 1.243–1.252 task per host. Penempatan VM deterministik memastikan setiap host menampung VM; pemerataan task juga dibantu aturan skor seri TOPSIS. Hasil ini tidak menjamin host selalu sibuk atau beban durasi identik.

I/O dimodelkan sebagai tambahan durasi CPU tanpa contention jaringan (MB desimal). Prediksi TOPSIS tidak memasukkan overhead startup VM dan resolusi event simulator. SLA disimpan sebagai metadata, tanpa kebijakan prioritas SLA.
