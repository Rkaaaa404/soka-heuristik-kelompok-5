# Rekapitulasi, Pembahasan Hasil Simulasi, dan Lesson Learned
**Infrastruktur:** 2 Datacenter Fisik, 8 Physical Host Homogen, 80 Virtual Machine Heterogen  
**Workload:** Enhanced Google Cloud Jobs (GoCJ) Skala 1.000 s/d 10.000 Tasks (3x Repeated Runs)  

---

## 1. Rekapitulasi Hasil Simulasi

Data mentah individual trial (30 run) tersimpan pada `docs/cloudsim_metrics.csv`, sedangkan data agregat ringkas tersimpan pada `docs/cloudsim_metrics_summary.csv`.

### A. Tabel Ringkasan Metrik Agregat ($Mean \pm Std\ Dev$ Sampel)
Tabel berikut merangkum hasil pengujian 10 variasi skala task (1.000 s/d 10.000 task), masing-masing dijalankan sebanyak 3 kali (*sample standard deviation* dihitung dengan formula $n-1$):

| Skala Tasks | Makespan (detik) | Total Cost (USD) | Degree of Imbalance (DI) | Runtime Algoritma (ms) |
|:---:|:---:|:---:|:---:|:---:|
| **1.000** | $1.795{,}32 \pm 0{,}50$ | $9{,}44 \pm 0{,}00$ | $1{,}8791 \pm 0{,}1453$ | $20{,}99 \pm 4{,}91$ |
| **2.000** | $3.057{,}22 \pm 7{,}00$ | $9{,}44 \pm 0{,}00$ | $1{,}6819 \pm 0{,}0824$ | $45{,}61 \pm 18{,}03$ |
| **3.000** | $4.090{,}30 \pm 1{,}30$ | $10{,}12 \pm 0{,}07$ | $1{,}4266 \pm 0{,}0413$ | $34{,}72 \pm 19{,}04$ |
| **4.000** | $4.622{,}44 \pm 1{,}51$ | $13{,}28 \pm 0{,}00$ | $1{,}4659 \pm 0{,}0223$ | $28{,}93 \pm 0{,}93$ |
| **5.000** | $5.188{,}17 \pm 1{,}32$ | $13{,}28 \pm 0{,}00$ | $1{,}4410 \pm 0{,}0228$ | $34{,}48 \pm 0{,}50$ |
| **6.000** | $6.200{,}55 \pm 1{,}41$ | $14{,}20 \pm 0{,}14$ | $1{,}3660 \pm 0{,}0547$ | $41{,}06 \pm 0{,}55$ |
| **7.000** | $16.328{,}73 \pm 1{,}78$ | $16{,}36 \pm 0{,}23$ | $1{,}8084 \pm 0{,}0301$ | $61{,}89 \pm 7{,}30$ |
| **8.000** | $18.611{,}75 \pm 12{,}45$ | $18{,}72 \pm 0{,}00$ | $1{,}8567 \pm 0{,}0981$ | $61{,}28 \pm 5{,}08$ |
| **9.000** | $20.988{,}47 \pm 1{,}53$ | $18{,}76 \pm 0{,}07$ | $1{,}7687 \pm 0{,}0599$ | $73{,}37 \pm 9{,}69$ |
| **10.000** | $23.102{,}88 \pm 0{,}21$ | $20{,}16 \pm 0{,}00$ | $1{,}7536 \pm 0{,}0548$ | $87{,}22 \pm 6{,}64$ |
### B. Distribusi Beban Infrastruktur pada Skala Puncak (10.000 Tasks)
Berdasarkan berkas alokasi `docs/cloudsim_vm_allocation.csv`:

#### 1. Distribusi per Datacenter dan Physical Host
| Datacenter | Physical Host | Total VM Dialokasikan | Jumlah Task yang Diproses | Total Busy Time (detik) | Persentase Task |
|:---:|:---:|:---:|:---:|:---:|:---:|
| **Datacenter 1** | Host 0 | 10 VM (VM 00–09) | 1.253 task | 64.221,35 s | 12,53% |
| **Datacenter 1** | Host 1 | 10 VM (VM 10–19) | 1.252 task | 65.565,14 s | 12,52% |
| **Datacenter 1** | Host 2 | 10 VM (VM 20–29) | 1.252 task | 67.114,78 s | 12,52% |
| **Datacenter 1** | Host 3 | 10 VM (VM 30–39) | 1.252 task | 68.716,60 s | 12,52% |
| **Datacenter 2** | Host 4 | 10 VM (VM 40–49) | 1.252 task | 65.678,89 s | 12,52% |
| **Datacenter 2** | Host 5 | 10 VM (VM 50–59) | 1.249 task | 65.447,27 s | 12,49% |
| **Datacenter 2** | Host 6 | 10 VM (VM 60–69) | 1.246 task | 67.379,98 s | 12,46% |
| **Datacenter 2** | Host 7 | 10 VM (VM 70–79) | 1.244 task | 66.216,23 s | 12,44% |
| **Total / Rata-rata** | **8 Host** | **80 VM** | **10.000 task** | **530.340,24 s** | **100,00%** |

*Catatan Validasi:* Tidak ada satu pun host yang menganggur. Setiap host menanggung rata-rata $\approx 1.250$ task dengan deviasi beban antar-host yang sangat kecil ($< 0{,}7\%$).

#### 2. Distribusi per Kelas Virtual Machine
| Kelas VM | Spesifikasi Teknis | Tarif Sewa / Jam | Jumlah VM | Total Task Diproses | Rata-rata Task / VM | Total Busy Time (s) | Biaya Total Sewa |
|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| **Small VM** | 1 vCPU @ 1.000 MIPS, 2 GB RAM | $0.05 / jam | 32 unit | 2.613 task | 81,66 task | 184.768,10 s | $3,20 |
| **Medium VM** | 2 vCPU @ 2.500 MIPS, 4 GB RAM | $0.12 / jam | 32 unit | 7.258 task | 226,81 task | 337.965,22 s | $12,96 |
| **Large VM** | 4 vCPU @ 5.000 MIPS, 8 GB RAM | $0.25 / jam | 16 unit | 129 task | 8,06 task | 7.606,92 s | $4,00 |
| **Total** | **160 vCPU, 320 GB RAM** | **$9.44 / jam** | **80 unit** | **10.000 task** | — | **530.340,24 s** | **$20,16** |

---

## 2. Pembahasan Hasil Simulasi & Analisis Fenomena Teknis

### A. Analisis Pertumbuhan Makespan dan Fenomena Lonjakan pada 7.000 Tasks
Salah satu temuan paling menonjol pada grafik evaluasi adalah pola kurva Makespan:
- Pada skala **1.000 s/d 6.000 tasks**, Makespan naik secara bertahap dari **1.795,32 s** menjadi **6.200,55 s**.
- Namun pada skala **7.000 tasks**, Makespan melonjak tajam ke **16.328,73 s** (kenaikan $> 163\%$), dan berlanjut ke **18.611,75 s** (8k), **20.988,47 s** (9k), serta **23.102,88 s** (10k).

```
Makespan (detik)
25000 ┼                                                     ● (23.103s)
20000 ┼                                         ● (20.988s)
      │                             ● (18.612s)
15000 ┼                 ● (16.329s) [Lonjakan akibat rentang kedatangan Poisson]
      │
 5000 ┼ ● (1.795s) ─── ● (6.201s)
    0 ┼───┴───────┴───────┴───────┴───────┴───────┴───────┴───────┴───────┴───
         1k      2k      4k      6k      7k      8k      9k     10k  (Tasks)
```

#### Mengapa Lonjakan Ini Terjadi?
Lonjakan ini **BUKAN** disebabkan oleh kelemahan algoritma TOPSIS atau kelebihan beban (*bottleneck*) pada infrastruktur VM. Akar penyebabnya terbukti secara matematis dari karakteristik dataset **Enhanced GoCJ**:
1. **Perbedaan Rentang Waktu Kedatangan (Arrival Time Span):**
   - Pada dataset 1.000 s/d 6.000 tasks (dataset asli GoCJ), rentang waktu kedatangan task di-generate dalam durasi yang lebih padat (task terakhir pada 6.000 task tiba pada detik $t = 5.998$).
   - Pada dataset 7.000 s/d 10.000 tasks (yang dibangkitkan menggunakan `GoCJGenerator.java` dengan generator Poisson non-homogen Enhanced GoCJ berbasis parameter empiris cluster Google dengan siklus diurnal 24 jam), laju kedatangan dasar adalah $\lambda \approx 0{,}62$ jobs/detik.
   - Akibatnya, pada dataset 7.000 task, **task terakhir baru tiba pada detik $t = 16.085$**!
2. **Definisi Makespan pada Sistem Non-Preemptive:**
   Makespan dihitung dari awal simulasi ($t = 0$) hingga task terakhir selesai dieksekusi:
   $$\text{Makespan} = \max_{j} (\text{CompletionTime}_j) \ge \max_{i} (\text{ArrivalTime}_i)$$
   Karena task terakhir baru tiba pada detik ke-16.085, maka secara matematis Makespan **tidak mungkin lebih kecil dari 16.085 detik**.
3. **Efisiensi Klaster VM yang Nyata:**
   Bila dihitung durasi eksekusi setelah task terakhir tiba:
   $$\Delta t = \text{Makespan} - \max(\text{ArrivalTime}) = 16.328{,}73 - 16.085{,}00 = 243{,}73\text{ detik}$$
   Artinya, infrastruktur cloud hanya membutuhkan waktu **$\approx 243$ detik** untuk menyelesaikan seluruh sisa antrean task setelah task terakhir masuk. Klaster VM sebenarnya memproses beban dengan sangat cepat, namun Makespan terikat oleh *timeline* kedatangan task.

---

### B. Analisis Total Biaya Eksekusi ($) dan Karakteristik Hourly Billing
Model penagihan biaya menggunakan kebijakan sewa berbasis jam (*hourly billing*):
$$\text{Total Cost} = \sum_{j=0}^{79} \left( \left\lceil \frac{\text{BusyTime}_j}{3600} \right\rceil \times \text{HourlyRate}_j \right)$$

#### Temuan Pola Biaya:
1. **Biaya Flat $9.44 pada Skala 1.000 dan 2.000 Tasks:**  
   Nilai $9.44 adalah **tarif dasar agregat per jam untuk seluruh 80 VM** jika seluruh VM memiliki waktu aktif $> 0$ detik:
   $$\text{Rate}_{\text{total}} = (32 \times \$0.05) + (32 \times \$0.12) + (16 \times \$0.25) = \$1.60 + \$3.84 + \$4.00 = \$9.44$$
   Pada skala 1.000 dan 2.000 tasks, setiap VM berhasil menerima task dengan akumulasi BusyTime di bawah 3.600 detik ($\le 1$ jam). Fungsi pembulatan ke atas ($\lceil \text{BusyTime}/3600 \rceil$) membulatkan seluruh VM ke tepat 1 jam penagihan, sehingga menghasilkan angka persis $9.44.
2. **Kenaikan Bertahap Menuju $20.16 pada 10.000 Tasks:**  
   - Pada 10.000 tasks, Small VM memiliki rata-rata BusyTime 5.774 s ($\lceil 5774/3600 \rceil = 2$ jam) $\rightarrow 32 \times 2 \times \$0.05 = \$3.20$.
   - Large VM memiliki rata-rata BusyTime 475 s ($\lceil 475/3600 \rceil = 1$ jam) $\rightarrow 16 \times 1 \times \$0.25 = \$4.00$.
   - Medium VM memiliki rata-rata BusyTime 10.561 s (antara 3 s/d 4 jam) $\rightarrow \text{Total} = \$12.96$.
   - Total keseluruhan: $\$3.20 + \$4.00 + \$12.96 = \$20.16$.

---

### C. Analisis Preferensi TOPSIS: Mengapa Medium VM Menyerap 72,6% Task?
Pada pengujian 10.000 tasks, alokasi beban antar-kelas VM sangat kontras:
- **Medium VM:** 7.258 task (72,58%)
- **Small VM:** 2.613 task (26,13%)
- **Large VM:** 129 task (1,29%)

#### Mengapa TOPSIS Sangat Condong ke Medium VM?
Jawabannya terletak pada **analisis efisiensi biaya per satuan komputasi (*cost per MIPS*)**:
- **Small VM:** Biaya sewa $\$0.05/\text{jam}$ untuk 1.000 MIPS $\rightarrow$ Biaya per 1 juta instruksi:
  $$\frac{\$0.05}{3600 \times 1000} \approx \$1{,}388 \times 10^{-8}/\text{MI}$$
- **Large VM:** Biaya sewa $\$0.25/\text{jam}$ untuk 5.000 MIPS $\rightarrow$ Biaya per 1 juta instruksi:
  $$\frac{\$0.25}{3600 \times 5000} \approx \$1{,}388 \times 10^{-8}/\text{MI}$$
- **Medium VM:** Biaya sewa $\$0.12/\text{jam}$ untuk 2.500 MIPS $\rightarrow$ Biaya per 1 juta instruksi:
  $$\frac{\$0.12}{3600 \times 2500} \approx \$1{,}333 \times 10^{-8}/\text{MI}$$

**Medium VM adalah kelas VM yang paling murah per satuan instruksi komputasi ($1{,}333 \times 10^{-8}$ vs $1{,}388 \times 10^{-8}$)**.  
Selain itu, Medium VM memiliki 2 vCPU yang memungkinkan penanganan 2 task secara paralel tanpa antrean panjang. Large VM memiliki biaya per jam yang relatif tinggi ($0.25), sehingga kriteria biaya pada TOPSIS memberikan penalti skor yang lebih berat pada Large VM ketika antrean pada Medium VM masih relatif longgar.

---

### D. Analisis Degree of Imbalance (DI)
Nilai Degree of Imbalance berkisar antara **1,36 hingga 1,88**:
$$\text{DI} = \frac{T_{\max} - T_{\min}}{T_{\text{avg}}}$$
- Pada sistem komputasi awan homogen, target DI adalah mendekati 0.
- Namun pada sistem **heterogen bertingkat** (kapasitas MIPS bervariasi dari 1.000 hingga 5.000 MIPS dan jumlah core 1 hingga 4 PE), nilai DI sebesar 1,36–1,88 adalah hasil yang sangat wajar.
- Perhitungan DI menggunakan BusyTime absolut (akumulasi detik). Karena Medium VM memiliki kapasitas komputasi agregat yang lebih dominan dan tarif paling hemat, VM tersebut secara alami menyerap akumulasi durasi eksekusi lebih tinggi ($T_{\max} \approx 12.000$ s) dibanding Large VM yang hanya aktif sebentar ($T_{\min} \approx 380$ s).

---

### E. Analisis Waktu Komputasi Algoritma TOPSIS (Runtime)
- Runtime algoritma berkisar dari **20,99 ms (1.000 task)** hingga **87,22 ms (10.000 task)**.
- Pertumbuhan runtime bersifat mendekati linear dengan kompleksitas teoritis $\mathcal{O}(N \times M)$ di mana $N$ adalah jumlah task dan $M = 80$ VM.
- Waktu komputasi di bawah 100 milidetik membuktikan bahwa **TOPSIS sangat efisien dan layak diterapkan untuk sistem penjadwalan batch berskala besar (*real-time scheduler*)**, berbeda dengan algoritma metaheuristik (seperti Algoritma Genetika atau Particle Swarm Optimization) yang membutuhkan waktu komputasi beberapa detik hingga menit untuk konvergensi populasi.

---

### F. Evaluasi Terhadap Catatan Dosen (Eliminasi Host Idle)
Pada catatan evaluasi Demo 1 (`notes.txt`), dosen menemukan bahwa alokasi bersifat blackbox dan Host 0 serta Host 1 menganggur.  
Melalui perbaikan arsitektur berupa 2 Datacenter, 8 Host homogen, serta pemetaan deterministik `((int) vm.getId() % 40) / 10`:
- **Datacenter 1** memproses 5.009 task.
- **Datacenter 2** memproses 4.991 task.
- Setiap host dari Host 0 hingga Host 7 memproses tepat **1.244 hingga 1.253 task**.
- Tidak ada satu pun host yang menganggur. Semua 8 physical host berkontribusi aktif secara proporsional.

---

## 3. Lesson Learned & Temuan Utama Pasca Simulasi

Dari seluruh tahapan perancangan, eksperimen multi-skala, running berulang, dan analisis data, berikut poin-poin pelajaran penting (*lesson learned*):

### 1. Memahami Perilaku Bawaan Simulator Cloud (CloudSim Plus)
* **Temuan:** Kebijakan alokasi VM default (`VmAllocationPolicySimple`) bersifat *first-fit greedy*. Jika kapasitas host fisik lebih besar dari kebutuhan satu VM, simulator akan menjejalkan seluruh VM ke host pertama yang muat hingga host tersebut penuh. Inilah penyebab Host 2 dan 3 penuh sementara Host 0 dan 1 menganggur pada Demo 1.
* **Lesson Learned:** Jangan memperlakukan simulator sebagai kotak hitam (*blackbox*). Alokasi sumber daya fisik ke virtual harus dikendalikan secara eksplisit via custom policy atau selector function agar arsitektur infrastruktur sesuai dengan proposal desain.

### 2. Membedakan Karakteristik Workload: Independen vs Dependen & Makna SLA
* **Temuan:** Di awal proyek sempat timbul keraguan apakah Google Cloud Jobs bersifat dependen atau independen, dan apakah kolom SLA menunjukkan relasi antar-task.
* **Lesson Learned:** Berdasarkan studi literatur benchmark GoCJ (Hussain & Aleem, 2018), GoCJ adalah *Bag-of-Tasks / Meta-Tasks* independen (tidak memiliki relasi parent-child seperti DAG pada Pegasus/WorkflowSim). Kolom SLA adalah *Service Level Agreement* (Prioritas Layanan: 1=Low, 2=Medium, 3=High). Memahami dataset secara mendalam sangat krusial sebelum menentukan algoritma optimasi.

### 3. Keterkaitan Waktu Kedatangan (*Arrival Time*) terhadap Metrik Makespan
* **Temuan:** Lonjakan makespan pada 7.000 task bukan karena degradasi algoritma, melainkan karena rentang kedatangan task pada trace Poisson berlangsung hingga jam ke-4,4 (16.085 detik).
* **Lesson Learned:** Ketika mengevaluasi makespan pada sistem dengan kedatangan dinamis (*arrival intervals*), makespan tidak bisa diartikan murni sebagai kecepatan komputasi klaster, melainkan penjumlahan dari *arrival span* dan sisa waktu antrean (*queue draining time*). Metrik pendukung seperti *response time* dan *waiting time* dapat melengkapi analisis makespan.

### 4. Dampak Struktur Tarif (*Pricing Model*) terhadap Keputusan Multi-Objektif
* **Temuan:** Karena Medium VM memiliki rasio tarif terhadap MIPS paling efisien ($0.12 untuk 2.500 MIPS = $1,333e-8 / MI), TOPSIS secara cerdas mengarahkan 72,6% beban kerja ke Medium VM untuk meminimalkan kriteria biaya.
* **Lesson Learned:** TOPSIS bekerja sangat sensitif terhadap rasio biaya per performa. Jika penyedia cloud ingin mendistribusikan beban lebih merata ke VM Large, struktur tarif harus disesuaikan atau bobot kriteria ($w_{\text{time}}$ dan $w_{\text{cost}}$) harus dikalibrasi secara dinamis sesuai SLA task.

### 5. Efek Kuantisasi Model Penagihan Berbasis Jam (*Hourly Billing*)
* **Temuan:** Penagihan per jam dengan fungsi pembulatan $\lceil \text{BusyTime}/3600 \rceil$ menciptakan *staircase effect*. Jika VM bekerja selama 3.601 detik, pengguna ditagih 2 jam penuh, sama seperti jika VM bekerja selama 7.199 detik.
* **Lesson Learned:** Pada skala kecil (1.000 dan 2.000 task), biaya bersifat datar ($9.44) karena seluruh VM masih berada pada batas 1 jam pertama. Model penagihan per detik (*per-second billing*) yang diterapkan oleh penyedia cloud modern (seperti AWS dan GCP) akan memberikan diferensiasi biaya yang lebih halus (*granular*).

### 6. Nilai Praktis dari Pengujian Berulang (*Repeated Runs*)
* **Temuan:** Variasi transfer I/O sintetis (50–500 MB) menghasilkan standar deviasi makespan yang sangat kecil ($\le 12$ detik pada makespan $\sim 18.000$ detik, variasi $< 0{,}1\%$).
* **Lesson Learned:** Pengujian multi-run membuktikan bahwa penjadwalan TOPSIS sangat stabil dan tangguh terhadap fluktuasi ukuran I/O. Standar deviasi yang kecil memberikan bukti validitas ilmiah yang kuat saat demo di hadapan dosen penguji.

---

## 4. Ringkasan untuk Presentasi / Demo Ulang

| Pertanyaan Kunci Dosen | Jawaban Berbasis Bukti Simulasi |
|---|---|
| **Apakah format `cloudsim_metrics.csv` sudah sesuai?** | Ya, file tersebut menyimpan seluruh 30 individual run untuk membuktikan pengujian 3x repeated runs secara konsisten, sementara rekap $Mean \pm Std$ disajikan pada `cloudsim_metrics_summary.csv` dan laporan ini. |
| **Mengapa Makespan melonjak di 7.000 tasks?** | Karena waktu kedatangan task terakhir pada trace Poisson Enhanced GoCJ baru tiba di detik 16.085. Sisa eksekusi hanya butuh $\approx 243$ detik setelah task terakhir masuk. |
| **Apakah ada Host yang menganggur?** | Tidak ada. Seluruh 8 Host pada 2 Datacenter memproses beban kerja merata antara 1.244 hingga 1.253 task per host. |
| **Mengapa biaya sewa awal $9.44?** | Karena $9.44 adalah tarif akumulasi 1 jam pertama untuk 80 VM. Pada skala 1k–2k, semua VM terpakai di bawah 3.600 detik. |
| **Berapa lama TOPSIS memproses 10.000 tasks?** | Hanya 87,22 milidetik, membuktikan efisiensi penjadwalan berkecepatan tinggi. |
