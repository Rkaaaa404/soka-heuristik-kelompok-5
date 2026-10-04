# 📘 Panduan & Modul Pencerdasan Tim — Kelompok 5
**Mata Kuliah:** Strategi Optimasi Komputasi Awan (SOKA)  
**Topik Proyek:** Penjadwalan Cloudlet Heterogen Menggunakan Multi-Criteria Decision Making (TOPSIS)  
**Tujuan Dokumen:** Referensi bersama seluruh anggota tim untuk persiapan Demo 1, pemahaman teori, eksekusi kode, dan menghadapi tanya-jawab dosen.

---

## 📑 Daftar Isi
1. [Elevator Pitch (Penjelasan Singkat 60 Detik)](#1-elevator-pitch-penjelasan-singkat-60-detik)
2. [Checklist Kebutuhan Demo 1 Dosen](#2-checklist-kebutuhan-demo-1-dosen)
3. [Kesesuaian Proposal vs Setup Kode](#3-kesesuaian-proposal-vs-setup-kode)
4. [Membedah Dataset: GoCJ & Workload Sintetis](#4-membedah-dataset-gocj--workload-sintetis)
5. [Cara Kerja Algoritma TOPSIS (Langkah demi Langkah)](#5-cara-kerja-algoritma-topsis-langkah-demi-langkah)
6. [Arsitektur Kode: Java (CloudSim Plus) vs Python (SimPy)](#6-arsitektur-kode-java-cloudsim-plus-vs-python-simpy)
7. [Penjelasan 4 Metrik Evaluasi](#7-penjelasan-4-metrik-evaluasi)
8. [Panduan Menjalankan Simulasi (Hands-On)](#8-panduan-menjalankan-simulasi-hands-on)
9. [Bocoran Pertanyaan Dosen & Cara Menjawabnya (FAQ)](#9-bocoran-pertanyaan-dosen--cara-menjawabnya-faq)
10. [Analisis Mendalam Hasil Simulasi CloudSim Plus](#10-analisis-mendalam-hasil-simulasi-cloudsim-plus)

---

## 1. Elevator Pitch (Penjelasan Singkat 60 Detik)
> *"Proyek ini memecahkan masalah **penjadwalan task/cloudlet** pada arsitektur cloud computing yang **heterogen** (kapasitas VM berbeda-beda). Kami menggunakan pendekatan **Bi-Objective Optimization**, yaitu mencari trade-off optimal antara **meminimalkan Makespan** (waktu selesai total) dan **meminimalkan Biaya Sewa VM (Cost)** secara simultan.*  
>  
> *Algoritma yang kami gunakan adalah **TOPSIS** (Technique for Order of Preference by Similarity to Ideal Solution), sebuah metode Multi-Criteria Decision Making yang memilih VM terbaik berdasarkan jarak terdekat ke solusi ideal positif dan terjauh dari solusi ideal negatif.*  
>  
> *Sistem diuji menggunakan **dataset riil Google Cloud Jobs (GoCJ)** dan dataset sintetis (Uniform & Normal) pada skala 100 hingga 1.000 task, serta divalidasi silang menggunakan dua platform simulasi: **CloudSim Plus (Java)** dan **SimPy (Python)**."*

---

## 2. Checklist Kebutuhan Demo 1 Dosen
Berdasarkan `docs/task-question.txt`, berikut poin yang wajib ditunjukkan saat demo beserta letak berkasnya:

| Poin Demo | Apa yang Harus Disampaikan / Ditunjukkan | Letak Berkas di Proyek |
|---|---|---|
| **1. Penjelasan Algoritma** | Jelaskan logika TOPSIS, matriks keputusan ($10 \times 2$), vektor normalisasi, dan pemilihan alternatif terbaik. | Dokumen ini (Bab 5) & `docs/Kelompok 5_Design Project.md` |
| **2. Menjalankan Simulasi** | Jalankan simulasi langsung di terminal (Java Gradle) atau sel Jupyter Notebook. Tunjukkan grafik hasil. | `java/` (`gradle run`), `python/kelompok_5_TOPSIS.ipynb`, `docs/*.png` |
| **3. Implementasi Kode** | Buka editor, tunjukkan baris fungsi algoritma TOPSIS yang menghitung skor dan melakukan assignment ke VM. | `TopsisCloudSim.java` (baris 287–370) & `kelompok_5_TOPSIS.ipynb` (sel 3) |
| **4. Kesesuaian Proposal** | Tunjukkan bahwa 1 Datacenter, 4 Physical Host, 10 Heterogeneous VM, dan batch 100–1.000 task di kode persis sama dengan proposal. | Dokumen ini (Bab 3) & `TopsisCloudSim.java` (baris 40–75, 250–280) |

---

## 3. Kesesuaian Proposal vs Setup Kode

Dosen sering menguji: *"Apakah parameter simulasi kalian sesuai dengan yang ditulis di proposal bab 2?"*  
Jawabannya: **Ya, 100% konsisten.**

### A. Infrastruktur Fisik (Datacenter & Host)
- **1 Datacenter Utama**
- **4 Physical Host:**
  - **Host 1 & 2:** 8 Core CPU @ 10.000 MIPS, RAM 32 GB, Storage 1 TB, Bandwidth 10 Gbps.
  - **Host 3 & 4:** 16 Core CPU @ 20.000 MIPS, RAM 64 GB, Storage 2 TB, Bandwidth 10 Gbps.
  - *Di Java:* Diimplementasikan pada metode `createDatacenter()` (`TopsisCloudSim.java`).

### B. Virtual Machine (10 Unit Heterogen)
Terdapat 3 kelas VM yang merepresentasikan heterogenitas cloud:
| ID VM | Tipe | CPU | MIPS | RAM | Bandwidth | Biaya Sewa |
|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| **VM 0, 1, 2, 3** | Small | 1 vCPU | 1.000 MIPS | 2 GB | 1 Gbps (125 MB/s) | **$0.05 / jam** |
| **VM 4, 5, 6, 7** | Medium | 2 vCPU | 2.500 MIPS | 4 GB | 1 Gbps (125 MB/s) | **$0.12 / jam** |
| **VM 8, 9** | Large | 4 vCPU | 5.000 MIPS | 8 GB | 1 Gbps (125 MB/s) | **$0.25 / jam** |

### C. Batasan Sistem (*Constraints*)
1. **Non-Preemptive:** Task yang sedang berjalan di suatu VM tidak boleh dipotong/diinterupsi sampai selesai (`CloudletSchedulerSpaceShared` di Java dan `simpy.Resource(capacity=1)` di Python).
2. **Resource Capacity:** Total kebutuhan RAM dan core VM tidak melebihi kapasitas host fisik.
3. **Mapping Constraint:** Setiap task tepat dialokasikan ke 1 VM (tidak ada task ganda atau terlewat).

---

## 4. Membedah Dataset: GoCJ & Workload Sintetis

### A. Apa itu Dataset GoCJ?
Folder `gocj_dataset/` berisi dataset trace riil bernama **Enhanced GoCJ (Google Cloud Jobs)** yang diunduh dari repositori akademik **Mendeley Data** (peneliti: Altaf Hussain & Muhammad Aleem, diterbitkan di jurnal MDPI *Data*). Dataset ini merupakan hasil ekstraksi beban kerja nyata di cluster server Google.

### B. Mengapa ada file `GoCJ_Dataset_1000.csv` sampai `6000.csv`?
- Angka `1000` s/d `6000` menandakan **jumlah task (baris data)** pada file tersebut.
- Peneliti aslinya membuat file bertingkat ini sebagai tolok ukur pengujian skalabilitas (1.000 task hingga 6.000 task).
- **Di proyek kita:** Sesuai proposal, skenario yang diuji adalah **100, 200, 500, dan 1.000 task**. Program kita membaca file `GoCJ_Dataset_1000.csv` dan mengambil $N$ baris pertama sesuai batch uji. File 2000–6000 adalah data cadangan untuk pengujian skala masif jika diperlukan nanti.

### C. Maksud 3 Kolom pada File CSV
Setiap baris CSV memiliki 3 kolom angka tanpa header:
Contoh: `83000, 2, 3`

1. **Kolom 1: Task Length / Job Size (Satuan: MI — *Million Instructions*)**
   - Beban instruksi CPU yang harus diproses. Nilainya pada GoCJ berkisar antara **15.000 s/d 900.000 MI**.
   - Menentukan waktu komputasi: $\text{Exec Time (detik)} = \frac{\text{Length (MI)}}{\text{VM MIPS}}$.
2. **Kolom 2: SLA Priority Level (1, 2, atau 3)**
   - Kelas prioritas *Service Level Agreement*:
     - `1`: *Low Priority* (Batch task biasa).
     - `2`: *Medium Priority*.
     - `3`: *High Priority* (Task interaktif / urgent).
3. **Kolom 3: Arrival Time (Satuan: Detik)**
   - Waktu kedatangan task ke sistem datacenter (misal: detik ke-1, ke-2, ke-3, dst.), merefleksikan proses kedatangan dinamis (*Poisson arrival*).

### D. Dataset Sintetis (Uniform & Normal)
Selain GoCJ, sistem menguji dua workload sintetis untuk melihat performa algoritma di bawah distribusi statistik yang terkontrol:
- **Uniform Distribution:** Task Length acak seragam antara **1.000 – 50.000 MI**, ukuran I/O acak **50 – 500 MB**.
- **Normal Distribution (Gaussian):** Task Length berpusat pada rata-rata **25.500 MI** ($\sigma = 8.000$), I/O berpusat pada **275 MB** ($\sigma = 75$).

---

## 5. Cara Kerja Algoritma TOPSIS (Langkah demi Langkah)

TOPSIS adalah metode pendukung keputusan multikriteria. Konsep intinya: **alternatif terbaik adalah yang memiliki jarak terpendek dari solusi ideal positif ($A^+$) dan jarak terjauh dari solusi ideal negatif ($A^-$).**

Untuk setiap task yang masuk, TOPSIS mengevaluasi seluruh alternatif (10 VM) melalui 6 langkah:

```
[Task Masuk] 
     │
     ▼
1. Bentuk Decision Matrix (10 VM × 2 Kriteria: Completion Time & Cost)
     │
     ▼
2. Normalisasi Vektor (Menghilangkan satuan detik vs dollar)
     │
     ▼
3. Pembobotan Matriks (w_time = 0.5, w_cost = 0.5)
     │
     ▼
4. Tentukan Solusi Ideal:
   • A+ (Ideal Positif) = Nilai MINIMUM (Waktu tercepat, Biaya termurah)
   • A- (Ideal Negatif) = Nilai MAKSIMUM (Waktu terlama, Biaya termahal)
     │
     ▼
5. Hitung Jarak Euclidean (d+ dan d-)
     │
     ▼
6. Hitung Skor Kedekatan Relatif: Ci = d- / (d+ + d-)
     │
     ▼
[Pilih VM dengan Skor Ci Tertinggi]
```

### Penjelasan Detail Rumus:
1. **Decision Matrix ($M$):**
   Untuk setiap VM $j$ ($j = 0 \dots 9$):
   - $\text{ExecTime}_j = \frac{\text{Length}}{\text{MIPS}_j} + \frac{\text{IO\_Bytes}}{\text{Bandwidth}_j}$
   - $\text{CompletionTime}_j = \text{ReadyTime}_j + \text{ExecTime}_j$
   - $\text{Cost}_j = \text{ExecTime}_j \times \text{TarifPerDetik}_j$
2. **Normalisasi Vektor:**
   $$r_{j, k} = \frac{x_{j, k}}{\sqrt{\sum_{i=0}^{9} x_{i, k}^2}}$$
3. **Matriks Terbobot ($V$):**
   $$v_{j, 1} = r_{j, 1} \times 0.5 \quad (\text{Waktu}), \qquad v_{j, 2} = r_{j, 2} \times 0.5 \quad (\text{Biaya})$$
4. **Solusi Ideal Positif ($A^+$) & Negatif ($A^-$):**
   *Perhatikan:* Karena waktu dan biaya adalah **cost criteria** (semakin kecil semakin bagus):
   - $A^+ = [\min(v_{*, 1}), \min(v_{*, 2})]$ *(Waktu tercepat & Biaya termurah)*
   - $A^- = [\max(v_{*, 1}), \max(v_{*, 2})]$ *(Waktu terlama & Biaya termahal)*
5. **Jarak Euclidean:**
   $$d_j^+ = \sqrt{(v_{j,1} - A_1^+)^2 + (v_{j,2} - A_2^+)^2}$$
   $$d_j^- = \sqrt{(v_{j,1} - A_1^-)^2 + (v_{j,2} - A_2^-)^2}$$
6. **Skor Kedekatan Relatif ($C_j^*$):**
   $$C_j^* = \frac{d_j^-}{d_j^+ + d_j^-}$$
   Pilih VM dengan $C_j^*$ terbesar!

---

## 6. Arsitektur Kode: Java (CloudSim Plus) vs Python (SimPy)

Proyek ini memiliki keunggulan karena mengimplementasikan algoritma pada dua platform sekaligus:

| Aspek | Java (`TopsisCloudSim.java`) | Python (`kelompok_5_TOPSIS.ipynb`) |
|---|---|---|
| **Engine** | CloudSim Plus 8.0.0 (Framework standar riset cloud) | SimPy (Discrete-event simulation engine) |
| **Kelebihan** | Memodelkan hardware fisik secara ketat (Datacenter, Host, PE, VM Broker). | Sangat cepat untuk prototyping, analisis data interaktif, dan plotting Matplotlib. |
| **Letak Algoritma** | Method `scheduleTasksTopsis()` (baris 287–370) | Fungsi `simulate_simpy_topsis()` (cell 3) |
| **Output** | CSV (`docs/cloudsim_metrics.csv` & `cloudsim_vm_allocation_1000.csv`) | Grafik visualisasi interaktif & DataFrame ringkasan |

---

## 7. Penjelasan 4 Metrik Evaluasi

1. **Makespan (detik):**
   - Total waktu yang dihabiskan sejak simulasi dimulai hingga task paling terakhir selesai di seluruh VM.
   - *Target:* Semakin kecil semakin bagus.
2. **Total Execution Cost (USD):**
   - Total biaya sewa seluruh VM. Dihitung berdasarkan durasi aktif (*busy time*) masing-masing VM dengan aturan pembulatan ke atas per jam pemakaian:
     $$\text{Total Cost} = \sum_{j=1}^{10} \lceil \frac{\text{BusyTime}_j}{3600} \rceil \times \text{HourlyRate}_j$$
3. **Degree of Imbalance (DI):**
   - Mengukur seberapa merata beban terdistribusi antar VM:
     $$\text{DI} = \frac{T_{\max} - T_{\min}}{T_{\text{avg}}}$$
   - Jika DI mendekati 0, artinya pembagian kerja sangat seimbang (tidak ada VM yang menganggur sementara VM lain *overloaded*).
   - *Hasil kita:* Pada 1.000 task GoCJ, nilai DI turun hingga **0.0349** (sangat merata).
4. **Waktu Komputasi Algoritma / Runtime (ms):**
   - Waktu komputasi yang dibutuhkan oleh algoritma TOPSIS untuk memetakan seluruh task.
   - *Hasil kita:* Untuk 1.000 task hanya membutuhkan waktu **$\approx 40 - 55$ milidetik**, membuktikan TOPSIS sangat ringan dan cocok untuk penjadwalan *real-time*.

---

## 8. Panduan Menjalankan Simulasi (Hands-On)

### Opsi A: Menjalankan Simulasi Java (CloudSim Plus)
Pastikan berada di root proyek `task-4/`:
```bash
# Masuk ke direktori java
cd java

# Jalankan via Gradle (Linux/macOS)
./gradlew run

# Atau jika di Windows (CMD/PowerShell)
gradlew.bat run
```
*Hasil:* Program akan mengeksekusi simulasi untuk 100, 200, 500, dan 1.000 task pada 3 jenis workload, menampilkan tabel di terminal, dan memperbarui file `docs/cloudsim_metrics.csv`.

### Opsi B: Menjalankan Visualisasi Grafik Python
```bash
# Dari root task-4
python python/visualize_cloudsim.py
```
*Hasil:* Menghasilkan 3 gambar grafik beresolusi tinggi di folder `docs/`:
- `cloudsim_evaluation_metrics.png` (4 metrik evaluasi)
- `cloudsim_vm_distribution.png` (distribusi beban per VM)
- `comparison_simpy_vs_cloudsim.png` (perbandingan SimPy vs CloudSim)

### Opsi C: Menjalankan Jupyter Notebook
Buka `python/kelompok_5_TOPSIS.ipynb` di VS Code atau Jupyter Lab, lalu pilih **Run All Cells**.

---

## 9. Bocoran Pertanyaan Dosen & Cara Menjawabnya (FAQ)

### ❓ Q1: "Mengapa kalian memilih TOPSIS dibanding algoritma klasik seperti FCFS atau Round Robin?"
> **Jawaban:**  
> *"Algoritma klasik seperti FCFS atau Round Robin bersifat single-objective atau buta terhadap kapasitas mesin. Di lingkungan cloud yang heterogen, Round Robin akan membagi beban sama rata tanpa mempedulikan bahwa Small VM hanya 1.000 MIPS sedangkan Large VM 5.000 MIPS, sehingga Small VM akan menjadi bottleneck.  
> Sebaliknya, TOPSIS adalah metode Multi-Criteria Decision Making yang mempertimbangkan trade-off antara waktu penyelesaian dan biaya sewa secara simultan untuk setiap task, sehingga didapatkan alokasi yang cerdas dan efisien."*

---

### ❓ Q2: "Mengapa bobot $w_{\text{time}} = 0.5$ dan $w_{\text{cost}} = 0.5$?"
> **Jawaban:**  
> *"Bobot 0.5 : 0.5 dipilih untuk memberikan kompromi seimbang (fair trade-off) antara performa kecepatan (QoS pengguna) dan efisiensi biaya (anggaran penyedia cloud). Bobot ini bersifat fleksibel; jika skenario mengutamakan kecepatan (urgent workload), bobot waktu bisa dinaikkan menjadi 0.7 atau 0.8."*

---

### ❓ Q3: "Bagaimana algoritma kalian mencegah penumpukan antrean pada VM yang cepat?"
> **Jawaban:**  
> *"Dalam algoritma TOPSIS kami, kriteria waktu yang dihitung bukan hanya durasi eksekusi murni, melainkan **Completion Time**:  
> $$\text{Completion Time} = \text{Predicted Ready Time} + \text{Execution Time}$$  
> Setiap kali suatu VM dipilih, `Predicted Ready Time` VM tersebut akan bertambah. Ketika Large VM sudah memiliki antrean panjang, nilai Completion Time-nya akan membesar, sehingga pada task berikutnya skor TOPSIS Large VM akan turun dan algoritma otomatis mengalihkan task ke Medium atau Small VM yang sedang bebas."*

---

### ❓ Q4: "Di proposal ada Salp Swarm Algorithm (SSA), kenapa di kode ini yang jalan baru TOPSIS?"
> **Jawaban:**  
> *"Sesuai dengan target **Demo 1** pada lembar tugas, fokus tahapan ini adalah mendemonstrasikan algoritma heuristik terpilih (TOPSIS), membuktikan fungsionalitas simulasi, serta memvalidasi kesesuaian infrastruktur dengan proposal.  
> Algoritma metaheuristik SSA direncanakan untuk tahap **Demo 2 / Laporan Akhir** sebagai pembanding performa lanjutan terhadap TOPSIS."*

---

### ❓ Q5: "Apa bedanya hasil antara CloudSim Plus (Java) dan SimPy (Python)?"
> **Jawaban:**  
> *"Kedua simulasi memberikan tren performa yang sangat konsisten. Perbedaan minor hanya terletak pada overhead internal engine: CloudSim Plus menyertakan overhead model jaringan (transfer I/O bandwidth datacenter dan alokasi PE host), sedangkan SimPy berfokus pada discrete-event queueing murni. Validasi silang kedua engine ini membuktikan bahwa algoritma TOPSIS kami bersifat deterministik dan reliabel di platform mana pun."*

---

## 10. Analisis Mendalam Hasil Simulasi CloudSim Plus

Bagian ini adalah **kunci analisis ilmiah** untuk menjawab pertanyaan dosen saat melihat angka-angka hasil simulasi dan grafik.

### A. Tabel Data Hasil Eksekusi (`docs/cloudsim_metrics.csv`)

| Workload | Tasks | Makespan (detik) | Total Cost ($) | Degree of Imbalance (DI) | Runtime (ms) |
|---|:---:|:---:|:---:|:---:|:---:|
| **GoCJ (Real Trace)** | 100 | 609,16 | $1,18 | 0,2907 | 0,46 ms |
| | 200 | 1.064,23 | $1,18 | 0,0681 | 1,90 ms |
| | 500 | 2.904,33 | $1,18 | 0,0708 | 5,18 ms |
| | **1000** | **5.752,88** | **$2,36** | **0,0414** | **2,63 ms** |
| **Synthetic Uniform** | 100 | 159,80 | $1,18 | 0,2574 | 0,54 ms |
| | 200 | 315,23 | $1,18 | 0,2854 | 1,75 ms |
| | 500 | 761,26 | $1,18 | 0,2362 | 1,72 ms |
| | **1000** | **1.490,10** | **$1,18** | **0,2202** | **3,09 ms** |
| **Synthetic Normal** | 100 | 160,97 | $1,18 | 0,1924 | 0,86 ms |
| | 200 | 310,76 | $1,18 | 0,2093 | 1,82 ms |
| | 500 | 771,65 | $1,18 | 0,2299 | 1,57 ms |
| | **1000** | **1.539,64** | **$1,18** | **0,2499** | **2,37 ms** |

---

### B. 4 Temuan Utama & Analisis Fenomena Data

#### 1. Mengapa Makespan GoCJ ~3,8× Lebih Lama dari Sintetis?
- **Fakta Data:** Pada 1.000 task, Makespan GoCJ mencapai **5.752 detik ($\approx$ 95,8 menit)**, sedangkan Sintetis Uniform hanya **1.490 detik ($\approx$ 24,8 menit)**.
- **Penyebab Teknis:**
  - Pada dataset sintetis, panjang task dibatasi secara artifisial antara **1.000 – 50.000 MI** (rata-rata $\approx 25.500$ MI).
  - Pada dataset riil Google Cloud Jobs (`GoCJ`), task length rata-rata adalah **$\approx 130.000$ MI** dengan beberapa task besar (*heavy compute*) mencapai hingga **900.000 MI**.
- **Kesimpulan Akademis:** Trace riil industri cloud memiliki variabilitas dan *heavy-tailed distribution* yang jauh lebih ekstrem dibanding distribusi statistik sintetis acak. Ini membuktikan bahwa algoritma TOPSIS berhasil diuji pada skenario stres beban kerja nyata.

#### 2. Mengapa Total Cost Tetap $1,18 pada 100–500 Task, Lalu Melonjak ke $2,36 pada 1.000 Task GoCJ?
- **Fakta Data:** Cost konstan di $1,18 untuk hampir semua batch, namun melompat tepat 2× lipat menjadi $2,36 khusus pada 1.000 task GoCJ.
- **Rasionalisasi Model Biaya (Hourly Billing):**
  - Total tarif sewa seluruh 10 VM per 1 jam adalah:
    $$\text{Tarif} = (4 \times \$0,05) + (4 \times \$0,12) + (2 \times \$0,25) = \$0,20 + \$0,48 + \$0,50 = \$1,18 / \text{jam}$$
  - Sistem cloud publik (seperti AWS EC2 / GCP) membulatkan tagihan ke atas per jam pemakaian ($\lceil \text{durasi} / 3600 \rceil$).
  - Untuk 100, 200, dan 500 task (serta seluruh batch sintetis), Makespan $< 3.600$ detik (kurang dari 1 jam). Semua VM aktif dalam rentang jam pertama, sehingga tagihannya tepat **$1,18**.
  - Khusus pada 1.000 task GoCJ, Makespan mencapai **5.752 detik ($\approx 1,6$ jam)**. Karena telah melewati batas 1 jam (3.600 detik) dan masuk ke jam kedua, tagihan seluruh VM dibulatkan menjadi 2 jam sewa:
    $$\text{Cost} = 2 \text{ jam} \times \$1,18 = \$2,36$$

#### 3. Fenomena Degree of Imbalance (DI): Mengapa Semakin Banyak Task, DI Semakin Seimbang?
- **Fakta Data:** Pada workload GoCJ, nilai DI justru turun drastis seiring bertambahnya beban:
  $$100 \text{ tasks: } 0,2907 \longrightarrow 200 \text{ tasks: } 0,0681 \longrightarrow 1.000 \text{ tasks: } \mathbf{0,0414}$$
- **Penyebab Teknis:**
  - Pada batch kecil (100 task), jika sebuah task raksasa (misal 900.000 MI) jatuh ke salah satu VM, waktu eksekusi task tunggal tersebut mendominasi durasi VM tersebut sehingga rasio selisih $(T_{\max} - T_{\min})$ masih terasa.
  - Pada batch besar (1.000 task), mekanisme pembaruan `predictedReadyTime` pada TOPSIS bekerja secara sempurna. Setiap kali satu VM menerima task besar, skor TOPSIS-nya turun untuk task berikutnya, sehingga puluhan task berikutnya dialihkan ke VM lain.
  - Hasilnya, pada 1.000 task, waktu sibuk (*busy time*) kesepuluh VM berkumpul sangat rapat di rentang **5.509 detik hingga 5.738 detik** (hanya selisih $\approx 4\%$). Ini membuktikan TOPSIS mencegah *resource starvation* dan *idle resources*.

#### 4. Analisis Distribusi Alokasi Beban per VM (`docs/cloudsim_vm_allocation_1000.csv`)
Pada pengujian 1.000 task GoCJ:
- **Small VM (VM 0–3, 1.000 MIPS):** Menerima 79–88 task per VM, Busy Time $\approx 5.614 - 5.674$ detik.
- **Medium VM (VM 4–7, 2.500 MIPS):** Menerima 118–130 task per VM, Busy Time $\approx 5.632 - 5.738$ detik.
- **Large VM (VM 8–9, 5.000 MIPS):** Menerima 82–85 task per VM, Busy Time $\approx 5.509 - 5.650$ detik.
- **Insight Menarik:**
  - Medium VM menerima **jumlah task terbanyak (118–130 task)** karena Medium VM memiliki *rasio efisiensi biaya-kecepatan paling manis* ($2.500 \text{ MIPS}$ dengan tarif hanya $\$0,12$/jam dibanding Large VM $\$0,25$/jam).
  - Large VM menerima task-task dengan instruksi yang lebih besar (panjang) sehingga meski jumlah task-nya lebih sedikit (82–85 task), durasi komputasinya tetap seimbang dengan VM lainnya.

#### 5. Efisiensi Algoritma (Runtime < 6 milidetik)
- Runtime komputasi TOPSIS untuk memetakan 1.000 task hanya membutuhkan waktu **$\approx 2,6 - 5,2$ milidetik**.
- Kompleksitas waktu algoritma adalah $\mathcal{O}(N \times M)$ dengan $N = \text{tasks}$ dan $M = 10 \text{ VMs}$. Operasi matriks berukuran $10 \times 2$ dapat diproses secara instan dalam memori tanpa menimbulkan jeda *scheduling delay*.
