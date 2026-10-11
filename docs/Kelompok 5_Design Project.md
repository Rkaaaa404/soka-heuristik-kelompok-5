# **Strategi Optimasi Komputasi Awan B Kelompok 5**

**Topik Proyek:** Penjadwalan Cloudlet Heterogen Menggunakan Metode Multi-Kriteria TOPSIS

# **1. Jenis Task / Workload & Dataset**

- **Jenis Workload:** *Independent and Non-Preemptive Batch Tasks* (*Bag-of-Tasks* atau *Meta-Tasks*).
  - Setiap task bersifat independen dan tidak memiliki ketergantungan *parent-child* maupun *barrier wait state*.
  - Task yang telah dieksekusi pada suatu *Virtual Machine* (VM) akan berjalan hingga selesai tanpa interupsi atau pemindahan (*migration*) ke VM lain.
  - Berbeda dengan *scientific workflow* berbentuk *Directed Acyclic Graph* (DAG), task pada proyek ini tidak perlu menunggu keluaran dari task lain. Oleh karena itu, simulasi difokuskan pada penjadwalan batch task independen menggunakan CloudSim Plus.

- **Dataset:**
  - Dataset yang digunakan adalah **Enhanced Google Cloud Jobs (GoCJ)**, yaitu benchmark yang diturunkan dari karakteristik workload Google cluster dan dipublikasikan melalui Mendeley Data (Hussain dan Aleem, MDPI *Data* 2018; Nawaz, Hussain, dan Khattak, Mendeley Data `r93dfjvvv6/5`). Pada hasil eksperimen, label workload ditulis sebagai **GoCJ**.
  - GoCJ asli hanya menyediakan ukuran job dalam satuan *Million Instructions* (MI), sedangkan Enhanced GoCJ menambahkan kelas *Service Level Agreement* (SLA) dan waktu kedatangan (*arrival time*). Proyek ini menggunakan format Enhanced GoCJ karena penjadwal memerlukan waktu kedatangan. Nilai SLA disimpan sebagai metadata dan belum digunakan sebagai kriteria TOPSIS.
  - Nilai SLA terdiri atas tiga kelas prioritas layanan, yaitu 1 untuk *Low Priority* (sekitar 18%), 2 untuk *Medium Priority* (sekitar 50%), dan 3 untuk *High Priority* (sekitar 32%). Kelas tersebut tidak menunjukkan hubungan ketergantungan antar-task.

- **Karakteristik Dataset:**
  - Jumlah task yang diuji terdiri atas **1.000, 2.000, 3.000, 4.000, 5.000, 6.000, 7.000, 8.000, 9.000, dan 10.000 task**.
  - Dataset 7.000 sampai dengan 10.000 task dibangkitkan menggunakan `GoCJGenerator.java`, yaitu adaptasi generator Enhanced GoCJ yang menerapkan *Monte Carlo Bootstrapping* dengan seed resmi `Original_Enhanced_Dataset.txt`.
  - Setiap berkas dataset terdiri atas tiga kolom numerik:
    1. **Task Length (MI):** panjang instruksi komputasi task.
    2. **SLA Priority Class (1, 2, atau 3):** tingkat prioritas layanan.
    3. **Arrival Time (detik):** waktu kedatangan task yang mengikuti proses Poisson nonhomogen dengan pola diurnal 24 jam.
  - Beban transfer I/O sintetis sebesar **50 MB sampai dengan 500 MB** dibangkitkan untuk setiap task pada setiap run. Nilai ini merupakan parameter tambahan simulasi dan bukan kolom pada dataset GoCJ. Bandwidth VM ditetapkan sebesar 1 Gbps atau 125 MB/s.

Setiap eksperimen membaca berkas `gocj_dataset/GoCJ_Dataset_<jumlah-task>.csv` sesuai skala pengujian. Berkas harus memuat tepat sejumlah task yang diminta, dengan panjang task bernilai positif dan *finite*, SLA bernilai 1 sampai dengan 3, serta *arrival time* bernilai nonnegatif dan *finite*. Task diurutkan berdasarkan *arrival time*, kemudian berdasarkan ID baris asli apabila memiliki waktu kedatangan yang sama. Kesalahan input akan menghentikan benchmark sebelum hasil diekspor; tidak terdapat penggantian otomatis dengan dataset berskala lain maupun task sintetis.

Satu label SLA yang tidak valid pada dataset 5.000 task telah dikoreksi sebelum benchmark. Nilai awal, nilai hasil imputasi, dan dasar koreksi dicatat dalam [catatan koreksi data](../gocj_dataset/DATA_CORRECTIONS.md).

Model I/O menggunakan satuan desimal, yaitu 1 MB sama dengan 1.000.000 byte. Durasi transfer I/O ditambahkan ke durasi CPU melalui panjang cloudlet efektif, tanpa memodelkan *bandwidth contention* maupun paket jaringan. *Arrival time* diterapkan sebagai *submission delay* cloudlet sejak awal simulasi, sedangkan seluruh workload dipetakan secara *offline* sebelum simulasi dimulai.

# **2. Desain Arsitektur Cloud (Spesifikasi Simulasi)**

Arsitektur cloud dirancang secara deterministik menggunakan **2 Datacenter fisik**, **8 Physical Host homogen**, dan **80 Virtual Machine heterogen**. Setiap host menampung 10 VM. Distribusi task pada masing-masing VM ditentukan oleh TOPSIS dan pola kedatangan task, sehingga pemetaan VM tidak menjamin distribusi task yang merata maupun seluruh host selalu berada dalam kondisi sibuk.

# **A. Hierarki Infrastruktur**

- **Datacenter:** 2 Datacenter fisik, yaitu Datacenter 1 dan Datacenter 2.
- **Physical Host:** 8 host fisik homogen. Setiap Datacenter menampung 4 host:
  - Datacenter 1 terdiri atas Host 0, Host 1, Host 2, dan Host 3.
  - Datacenter 2 terdiri atas Host 4, Host 5, Host 6, dan Host 7.
- **Virtual Machine:** 80 VM heterogen dengan alokasi 10 VM per host:
  - 32 Small VM;
  - 32 Medium VM; dan
  - 16 Large VM.
- **Kapasitas Agregat Cloud:** 160 vCPU, 320 GB RAM, dan tarif sewa total sebesar **$9.44 per jam**.

# **B. Tabel Spesifikasi Komponen Infrastruktur**

| Level | Komponen | Jumlah | Karakteristik / Spesifikasi Teknis |
|---|---|:---:|---|
| **Physical Host (Homogen)** | Host 0 sampai dengan Host 7 | 8 unit | **24 core CPU** @ 10.000 MIPS per host (total 192 core fisik), **64 GB RAM** (65.536 MB), **2 TB storage** (2.000.000 MB), **bandwidth 10 Gbps** (10.000 Mbps), dan `VmSchedulerSpaceShared` |
| **Virtual Machine (Heterogen)** | **Small VM** | 32 unit | **1 vCPU** (1.000 MIPS), **2 GB RAM** (2.048 MB), bandwidth 1 Gbps (125 MB/s), dan biaya sewa **$0.05 per jam** |
| | **Medium VM** | 32 unit | **2 vCPU** (2.500 MIPS), **4 GB RAM** (4.096 MB), bandwidth 1 Gbps (125 MB/s), dan biaya sewa **$0.12 per jam** |
| | **Large VM** | 16 unit | **4 vCPU** (5.000 MIPS), **8 GB RAM** (8.192 MB), bandwidth 1 Gbps (125 MB/s), dan biaya sewa **$0.25 per jam** |
| **Cloudlet / Workload** | GoCJ Batch Pool | 1.000 sampai dengan 10.000 task | Terdiri atas *task length*, SLA, dan *arrival time*, serta I/O sintetis sebesar 50 sampai dengan 500 MB per task |

# **C. Pemetaan Virtual Machine ke Physical Host dan Datacenter**

Pemetaan VM ke host ditetapkan secara eksplisit agar penempatan sumber daya dapat diamati dan seluruh host memperoleh alokasi VM yang sama.

| Datacenter | Physical Host | Rentang ID VM | Komposisi VM per Host | Total vCPU | Total RAM | Status Host |
|---|---|:---:|---|:---:|:---:|:---:|
| **Datacenter 1** | Host 0 | VM 00–09 | 4 Small (0–3), 4 Medium (4–7), 2 Large (8–9) | 20 vCPU | 40.960 MB | **Aktif** |
| **Datacenter 1** | Host 1 | VM 10–19 | 4 Small (10–13), 4 Medium (14–17), 2 Large (18–19) | 20 vCPU | 40.960 MB | **Aktif** |
| **Datacenter 1** | Host 2 | VM 20–29 | 4 Small (20–23), 4 Medium (24–27), 2 Large (28–29) | 20 vCPU | 40.960 MB | **Aktif** |
| **Datacenter 1** | Host 3 | VM 30–39 | 4 Small (30–33), 4 Medium (34–37), 2 Large (38–39) | 20 vCPU | 40.960 MB | **Aktif** |
| **Datacenter 2** | Host 4 | VM 40–49 | 4 Small (40–43), 4 Medium (44–47), 2 Large (48–49) | 20 vCPU | 40.960 MB | **Aktif** |
| **Datacenter 2** | Host 5 | VM 50–59 | 4 Small (50–53), 4 Medium (54–57), 2 Large (58–59) | 20 vCPU | 40.960 MB | **Aktif** |
| **Datacenter 2** | Host 6 | VM 60–69 | 4 Small (60–63), 4 Medium (64–67), 2 Large (68–69) | 20 vCPU | 40.960 MB | **Aktif** |
| **Datacenter 2** | Host 7 | VM 70–79 | 4 Small (70–73), 4 Medium (74–77), 2 Large (78–79) | 20 vCPU | 40.960 MB | **Aktif** |

Alokasi pada setiap host menggunakan 20 vCPU dari 24 core fisik atau sekitar 83,3% kapasitas CPU. Penggunaan RAM sebesar 40.960 MB dari 65.536 MB atau sekitar 62,5% kapasitas RAM. Dengan demikian, alokasi VM tidak melampaui kapasitas host.

# **3. Fungsi Objektif (Multi-Objective Optimization)**

Penelitian ini menerapkan optimasi dua objektif (*bi-objective optimization*) dengan bobot seimbang, yaitu $w_{\text{time}} = 0.5$ dan $w_{\text{cost}} = 0.5$.

# **A. Minimalisasi Makespan ($f_1$)**

Makespan merupakan waktu penyelesaian keseluruhan workload sampai task terakhir selesai dieksekusi pada seluruh 80 VM.

$$
\min f_1 = \max_{j \in \{0, \dots, 79\}} CT_j
$$

dengan $CT_j$ sebagai waktu selesai (*completion time*) pada VM ke-$j$.

# **B. Minimalisasi Total Biaya Eksekusi ($f_2$)**

Total biaya dihitung berdasarkan akumulasi durasi task pada setiap VM dengan pembulatan penggunaan per jam.

$$
\min f_2 = \sum_{j=0}^{79} \left(\left\lceil\frac{\text{BusyTime}_j}{3600}\right\rceil \times \text{HourlyRate}_j\right)
$$

$\text{BusyTime}_j$ merupakan jumlah durasi eksekusi seluruh task pada VM ke-$j$, termasuk tambahan durasi I/O. Pada VM dengan lebih dari satu core, durasi task yang dieksekusi secara paralel tetap dijumlahkan sehingga *BusyTime* dapat melebihi durasi aktif VM. Waktu idle tidak ditagihkan dalam model penelitian ini. Kriteria biaya TOPSIS pada tingkat task menggunakan biaya proporsional terhadap durasi, sedangkan metrik biaya akhir menggunakan pembulatan per jam sebagaimana ditunjukkan pada persamaan di atas.

# **4. Metrik Optimasi & Cloud Constraints**

# **A. Metrik Evaluasi**

1. **Makespan (detik):** waktu selesai task terakhir sejak awal simulasi, termasuk interval kedatangan dan antrean task.
2. **Total Execution Cost (USD):** biaya berdasarkan akumulasi *BusyTime* dan pembulatan per jam pada model penelitian.
3. **Degree of Imbalance (DI):** tingkat ketidakseimbangan beban kerja pada seluruh 80 VM, yang dihitung menggunakan persamaan berikut:

   $$
   \text{DI} = \frac{T_{\max} - T_{\min}}{T_{\text{avg}}}
   $$

   Nilai $T_j$ adalah $\text{BusyTime}_j$ untuk seluruh VM, termasuk VM yang tidak memperoleh task ($T_j = 0$). Waktu selesai tidak digunakan dalam perhitungan DI agar waktu idle akibat menunggu kedatangan tidak dianggap sebagai beban komputasi. Oleh karena itu, DI mengukur ketimpangan akumulasi durasi task dan bukan utilisasi yang dinormalisasi berdasarkan jumlah *processing element* (PE).
4. **Waktu Komputasi Algoritma (ms):** durasi yang diperlukan penjadwal TOPSIS untuk memetakan seluruh batch task.
5. **Pengamatan Variasi:** setiap skala workload dari 1.000 sampai dengan 10.000 task dijalankan sebanyak tiga kali menggunakan seed I/O sintetis yang berbeda. Hasil setiap skala disajikan dalam bentuk nilai rata-rata (*mean*) dan standar deviasi sampel (*sample standard deviation*) dengan `n - 1` atau `ddof=1`. Tiga kali pengulangan digunakan untuk mengamati variasi hasil dan tidak dimaksudkan sebagai bukti optimalitas global. CSV alokasi VM merepresentasikan skala 10.000 task pada run ketiga, sedangkan CSV metrik memuat seluruh 30 run.

# **B. Cloud Constraints**

- **Resource Capacity Constraint:** alokasi 20 vCPU dan 40 GB RAM dari 10 VM pada setiap host tidak boleh melebihi kapasitas host sebesar 24 core dan 64 GB RAM.
- **Non-Preemptive Constraint:** task yang sedang dieksekusi tidak dapat dihentikan sementara atau dipindahkan ke VM lain.
- **Mapping Constraint:** setiap task harus dialokasikan tepat ke satu VM tanpa task ganda maupun task yang terlewat.
- **Infrastructure Constraint:** seluruh 80 VM harus berhasil dibuat pada host dan Datacenter yang telah ditetapkan.

Sebelum metrik dihitung, program memeriksa ID task, status eksekusi, VM tujuan, dan waktu mulai setiap task. Kegagalan validasi maupun ekspor akan menghentikan program dan tidak menghasilkan pesan sukses.

# **5. Algoritma Optimasi yang Dipilih**

Algoritma yang digunakan adalah **Technique for Order Preference by Similarity to Ideal Solution (TOPSIS)**. Algoritma ini dipilih karena mampu mengevaluasi beberapa kriteria yang saling bertentangan, yaitu waktu penyelesaian dan biaya eksekusi, melalui proses normalisasi dan pembobotan yang sistematis.

Untuk setiap task $i$ dalam antrean, dibentuk matriks keputusan $X = [x_{jk}]_{80 \times 2}$. Kriteria pertama adalah prediksi *completion time*, sedangkan kriteria kedua adalah prediksi biaya eksekusi.

# **A. Pembentukan Matriks Keputusan**

Waktu eksekusi task $i$ pada VM $j$ dihitung dengan persamaan:

$$
\text{ExecTime}_{ij} = \frac{\text{Length}_i}{\text{MIPS}_j} + \frac{\text{IO\_Bytes}_i}{\text{Bandwidth}_j}
$$

Waktu tersedia VM ditentukan berdasarkan PE yang paling awal tersedia:

$$
\text{PredictedReadyTime}_j = \min_{p \in \text{PE}_j} \text{ReadyTime}_{jp}
$$

Elemen matriks keputusan dihitung sebagai berikut:

$$
x_{j1} = \max(\text{ArrivalTime}_i, \text{PredictedReadyTime}_j) + \text{ExecTime}_{ij}
$$

$$
x_{j2} = \text{ExecTime}_{ij} \times \frac{\text{HourlyRate}_j}{3600}
$$

# **B. Tahapan Perhitungan TOPSIS**

1. **Normalisasi Vektor:**

   $$
   r_{jk} = \frac{x_{jk}}{\sqrt{\sum_{p=0}^{79}x_{pk}^{2}}}, \qquad k \in \{1,2\}
   $$

2. **Pembobotan Matriks Ternormalisasi:**

   $$
   v_{jk} = w_k \cdot r_{jk}, \qquad w_1 = 0.5, \quad w_2 = 0.5
   $$

3. **Penentuan Solusi Ideal Positif dan Negatif:**

   Karena waktu dan biaya merupakan kriteria biaya (*cost criteria*), solusi ideal dirumuskan sebagai berikut:

   $$
   A^+ = \{\min_p v_{p1},\min_p v_{p2}\}, \qquad
   A^- = \{\max_p v_{p1},\max_p v_{p2}\}
   $$

4. **Perhitungan Jarak Euclidean:**

   $$
   d_j^+ = \sqrt{\sum_{k=1}^{2}(v_{jk}-A_k^+)^2}, \qquad
   d_j^- = \sqrt{\sum_{k=1}^{2}(v_{jk}-A_k^-)^2}
   $$

5. **Perhitungan Skor Kedekatan Relatif:**

   $$
   C_j^* = \frac{d_j^-}{d_j^+ + d_j^- + 10^{-9}}
   $$

   Task dialokasikan ke VM dengan skor $C_j^*$ tertinggi:

   $$
   \text{Chosen VM} = \arg\max_j C_j^*
   $$

# **C. Mekanisme Penjadwalan Task**

Setiap cloudlet menggunakan satu PE. Small VM, Medium VM, dan Large VM dapat menjalankan masing-masing 1, 2, dan 4 task secara paralel menggunakan `CloudletSchedulerSpaceShared`. Setelah VM dipilih, waktu tersedia PE yang paling awal akan diperbarui menjadi prediksi *completion time* task. Apabila terdapat lebih dari satu PE dengan waktu tersedia yang sama, PE dengan indeks terkecil dipilih.

Apabila beberapa VM memperoleh skor TOPSIS yang sama, VM dengan jumlah task teralokasi paling sedikit akan dipilih. Jika jumlah task juga sama, pemilihan dilakukan berdasarkan indeks VM terkecil. Aturan tersebut mencegah kecenderungan pemilihan VM berindeks awal ketika kedatangan task jarang, tanpa menambahkan kriteria maupun bobot TOPSIS baru.

Agar prediksi sesuai dengan representasi cloudlet, panjang efektif dibulatkan ke atas menjadi MI integer menggunakan persamaan berikut:

$$
\text{EffectiveLength}_i = \left\lceil \text{Length}_i + \frac{\text{IO\_Bytes}_i}{\text{Bandwidth}_j} \times \text{MIPS}_j \right\rceil
$$

Panjang efektif tersebut kemudian dibagi dengan MIPS VM untuk memperoleh durasi eksekusi. Prediksi tidak memasukkan *startup overhead* VM maupun resolusi event engine CloudSim.

# **Tools**

- **Simulasi Cloud:** **CloudSim Plus 8.0.0 (Java 17)** digunakan untuk memodelkan Datacenter, host, PE, VM, kebijakan alokasi VM, dan eksekusi cloudlet.
- **Analisis Data dan Visualisasi:** **Python 3.11, Pandas, dan Matplotlib** digunakan untuk membaca `docs/cloudsim_metrics.csv` serta `docs/cloudsim_vm_allocation.csv`, kemudian menghasilkan grafik hasil eksperimen.
- CloudSim Plus digunakan sebagai satu-satunya engine simulasi. Python hanya digunakan pada tahap analisis dan visualisasi hasil.