# **Strategi Optimasi Komputasi Awan B Kelompok 5** 
# **1. Jenis Task / Workload & Dataset** 

- **Jenis Workload:** Independent and Non-Preemptive Batch Tasks. Setiap task berdiri sendiri (tidak memiliki dependensi grafik/DAG seperti scientific workflow) dan sekali dieksekusi pada VM tidak dapat diinterupsi hingga selesai. 

- **Dataset:** 

   - Menggunakan dataset standar simulasi cloud berbasis distribusi instruksi acak (Uniform / Normal distribution) atau trace GoCJ (Google Cloud Jobs) / HPC2N Workload Trace. 

   - **Karakteristik Dataset:** 

      - **Jumlah Task:** Skenario pengujian bertahap dengan 100, 200, 500, dan 1.000 Cloudlet. 

      - **Panjang Task:** Bervariasi antara 1.000 sampai 50.000 MI untuk merefleksikan heterogenitas beban kerja. 

      - **Ukuran File I/O:** 50 – 500 MB 

# **2. Desain Arsitektur Cloud (Spesifikasi Simulasi)** 

Arsitektur dirancang bersifat heterogen (kapasitas mesin berbeda-beda) agar algoritma optimasi benar-benar teruji dalam mendistribusikan beban secara adil dan efisien. 

# **A. Hierarki Infrastruktur** 

- **Datacenter:** 1 Datacenter utama 

- **Physical Host:** 4 Host fisik dengan alokasi resource bersama. 

- **Virtual Machine (VM):** 10 unit VM dengan 3 kelas spesifikasi (heterogen). 

# ● **Task / Cloudlet:** 100 – 1.000 task. 

# **B. Tabel Spesifikasi Komponen** 

|**Level**|**Kompone**<br>**n**|**Jumla**<br>**h**|**Karakteristik / Spesifikasi**|
|---|---|---|---|
|H Fiik|Host 1 & 2|2 unit|8 Core CPU @ 10.000 MIPS,<br>RAM 32 GB, Storage 1 TB,<br>Bandwidth 10 Gbps|
|ost s|Host 3 & 4|2 unit|16 Core CPU @ 20.000 MIPS,<br>RAM 64 GB, Storage 2 TB,<br>Bandwidth 10 Gbps|
||Small VM|4 unit|1 vCPU (1.000 MIPS), RAM 2<br>GB, Biaya: $0.05 / jam|
|Virtual<br>Machine|Medium<br>VM|4 unit|2 vCPU (2.500 MIPS), RAM 4<br>GB, Biaya: $0.12 / jam|
||Large VM|2 unit|4 vCPU (5.000 MIPS), RAM 8<br>GB, Biaya: $0.25 / jam|
|Cloudlet|Workload<br>Pool|100 –<br>1.000|Heterogen (1.000 – 50.000 MI),<br>ukuran I/O: 50 – 500 MB|



# **3. Fungsi Objektif (Multi-Objective Optimization)** 

Menggunakan pendekatan Bi-Objective dengan meminimalkan waktu dan biaya secara simultan. 

# **A. Minimalisasi Makespan (f₁)** 

Makespan adalah total waktu yang dibutuhkan hingga task terakhir selesai dieksekusi di seluruh VM: 



<!-- Start of picture text -->
minf; =<br><!-- End of picture text -->

_Keterangan: CTj_ adalah waktu selesai ( _Completion Time_ ) pada VM ke- _j_ . 

# **B. Minimalisasi Total Biaya Eksekusi / Cost (f₂)** 

Total biaya sewa VM berdasarkan durasi pemakaian dan tarif per jam masing-masing VM: 



<!-- Start of picture text -->
min;  fo =2»» (TseCT, x CostPerHour;<br><!-- End of picture text -->

# **C. Formulasi Fitness Function Gabungan (Weighted Sum Model)** 

Untuk memudahkan implementasi (tanpa perlu Pareto Frontier yang rumit), gunakan skalarisasi berbobot: 



<!-- Start of picture text -->
Hote .(_2<br>Fitness = w, - ( ——22<br>(sa + we Costmax<br><!-- End of picture text -->

Di mana _w1_ = 0.5 dan _w2_ = 0.5 (atau disesuaikan prioritas), serta _w1_ + _w2_ = 1. 

# **4. Metrik Optimasi & Cloud Constraints** 

- **A. Metrik Optimasi yang Diukur** 

   1. **Makespan (Detik):** Kecepatan penyelesaian seluruh kumpulan task. 

   2. **Total Execution Cost (USD):** Efisiensi pengeluaran komputasi. 

   3. **Degree of Imbalance (DI):** Mengukur seberapa merata beban terdistribusi antar VM: 



<!-- Start of picture text -->
DI= Tmax ~ Trin<br>Tavg<br><!-- End of picture text -->

_(Semakin kecil nilainya, semakin seimbang pemanfaatan VM)._ 

4. **Waktu Konvergensi / Waktu Komputasi Algoritma:** Waktu yang dibutuhkan algoritma untuk menghasilkan solusi optimal. 

# **B. Constraints** 

- **Resource Capacity Constraint:** Total alokasi RAM dan MIPS pada satu host tidak boleh melebihi kapasitas fisik host. 

- **Non-Preemptive Constraint:** Task yang sedang berjalan di satu VM tidak dapat dipotong atau dipindahkan ke VM lain. 

- **Deadline Constraint (Opsional/Skenario Khusus):** Makespan total tidak boleh melebihi batas waktu maksimal Dmax. 

- **Mapping Constraint:** Setiap task harus dialokasikan ke tepat satu VM (tidak ada task yang terduplikasi atau tertinggal). 

# **5. Algoritma Optimasi yang Dipilih** 

Untuk menjaga pengerjaan tetap mudah namun memenuhi kriteria akademis, bandingkan 1 algoritma heuristik dan 1 algoritma metaheuristik: 

# 1. **Algoritma Heuristik: TOPSIS-Based Task Scheduling** 

- _Cara kerja:_ Menghitung skor kedekatan relatif setiap task terhadap solusi ideal positif (waktu eksekusi tercepat dan biaya terendah) serta solusi ideal negatif (waktu eksekusi terlama dan biaya tertinggi) menggunakan metode pembuat keputusan kriteria majemuk (MCDM). Task dengan skor kedekatan tertinggi diprioritaskan untuk dialokasikan terlebih dahulu. 

- _Alasan dipilih:_ Mampu mempertimbangkan banyak parameter sekaligus (seperti makespan, biaya, dan resource utilization) secara seimbang. 

# 2. **Algoritma Metaheuristik: Salp Swarm Algorithm (SSA)** 

- _Cara kerja:_ Mensimulasikan perilaku kawanan salp (biota laut) yang membentuk rantai saat bergerak dan mencari makanan. Pemimpin rantai memandu arah pencarian posisi task-to-VM terbaik, sementara pengikut (follower) memperbarui posisinya mengikuti pemimpin untuk meminimalkan fungsi fitness. 

- _Alasan dipilih:_ Memiliki mekanisme eksplorasi dan eksploitasi ruang pencarian yang sangat seimbang sehingga terhindar dari jebakan local optima, serta memiliki parameter pengaturan yang sedikit. 

# **Tools:** 

- CloudSim / CloudSim Plus (Java) 

- Python (SimPy / Custom Script) 

