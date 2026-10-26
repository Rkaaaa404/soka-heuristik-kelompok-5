# Penjelasan Demo 3: Eksekusi TOPSIS pada AWS

Dokumen ini menjelaskan kode dan alur demo yang menjalankan workload TOPSIS pada worker di AWS. Penjelasan ini ditujukan untuk membantu anggota kelompok memahami apa yang terjadi saat build, preflight, penjadwalan, eksekusi, dan pembacaan hasil.

## Gambaran umum

Program terdiri dari dua bagian utama:

1. **Controller** berjalan di laptop operator. Controller membaca konfigurasi dan dataset, mengecek worker, mengukur kecepatan setiap VM, menentukan penempatan task dengan TOPSIS, lalu mengirim task melalui HTTP.
2. **Worker** berjalan sebagai service Java pada setiap VM guest. Worker menerima permintaan, menjalankan perhitungan CPU SHA-256, kemudian mengembalikan hasil dan pengukuran waktu.

Di infrastruktur demo, terdapat dua EC2 host. Masing-masing menjalankan dua VM KVM, jadi controller berkomunikasi dengan empat worker. Worker hanya dapat dijangkau lewat SSH tunnel dari laptop; port 8080 tidak perlu dibuka untuk akses publik.

```text
Laptop operator
  └─ TopsisRealCloud (controller)
       ├─ SSH tunnel → EC2 host 1 → VM s1:8080
       ├─ SSH tunnel → EC2 host 1 → VM m1:8080
       ├─ SSH tunnel → EC2 host 2 → VM s2:8080
       └─ SSH tunnel → EC2 host 2 → VM m2:8080

Setiap VM menjalankan TopsisWorker sebagai service systemd.
```

## Peta kode

| File | Tanggung jawab |
|---|---|
| [`TopsisRealCloud.java`](../aws/src/main/java/com/soka/kelompok5/TopsisRealCloud.java) | CLI controller: memuat konfigurasi, preflight, kalibrasi, scheduling, dispatch, validasi respons, dan menulis hasil. |
| [`TopsisScheduler.java`](../aws/src/main/java/com/soka/kelompok5/TopsisScheduler.java) | Algoritma penempatan task multi-kriteria TOPSIS. |
| [`TopsisWorker.java`](../aws/src/main/java/com/soka/kelompok5/TopsisWorker.java) | HTTP service worker yang menyediakan endpoint `/health` dan `/execute`. |
| [`CpuWorkload.java`](../aws/src/main/java/com/soka/kelompok5/CpuWorkload.java) | Beban CPU deterministik berupa hashing SHA-256 berulang dan checksum. |
| [`GocjWorkload.java`](../aws/src/main/java/com/soka/kelompok5/GocjWorkload.java) | Membaca serta memvalidasi baris dataset GoCJ menjadi task. |
| [`aws-workers.json`](../deploy/aws-workers.json) | Identitas host/VM, alamat endpoint lokal hasil tunnel, kapasitas slot, dan tarif model. |
| [`soka-worker.service`](../deploy/soka-worker.service) | Unit systemd untuk menjalankan worker otomatis pada VM. |

## Implementasi real-world AWS ada di mana?

Implementasi workload real AWS berada terutama di folder **`aws/`**. Kode ini berbeda dari program simulasi CloudSim: program AWS benar-benar mengirim request ke service worker yang berjalan di VM guest dan menerima hasil komputasi dari VM tersebut.

Bagian implementasinya terbagi seperti berikut:

| Bagian | Lokasi | Berjalan di | Peran dalam eksekusi real |
|---|---|---|---|
| Controller/orchestrator | [`TopsisRealCloud.java`](../aws/src/main/java/com/soka/kelompok5/TopsisRealCloud.java) | Laptop operator | Membaca dataset dan konfigurasi worker, melakukan preflight serta kalibrasi, memanggil scheduler, mengirim task melalui HTTP, memeriksa respons, dan menyimpan hasil. |
| Penjadwal TOPSIS | [`TopsisScheduler.java`](../aws/src/main/java/com/soka/kelompok5/TopsisScheduler.java) | Laptop operator, di dalam proses controller | Memilih VM tujuan untuk setiap task berdasarkan estimasi waktu selesai dan biaya model. |
| HTTP worker | [`TopsisWorker.java`](../aws/src/main/java/com/soka/kelompok5/TopsisWorker.java) | Masing-masing VM guest AWS | Menyediakan endpoint `/health` dan `/execute`; menghitung task yang diterima dan mengembalikan data hasil. |
| Komputasi task | [`CpuWorkload.java`](../aws/src/main/java/com/soka/kelompok5/CpuWorkload.java) | Masing-masing VM guest AWS | Melakukan hashing SHA-256 berulang sebagai beban CPU terukur, lalu menghasilkan checksum. |
| Pembaca workload | [`GocjWorkload.java`](../aws/src/main/java/com/soka/kelompok5/GocjWorkload.java) | Laptop operator, di dalam proses controller | Membaca baris dataset GoCJ dan membentuk daftar task yang akan dikirim ke worker. |
| Daftar endpoint dan kapasitas | [`aws-workers.json`](../deploy/aws-workers.json) | Dibaca oleh controller di laptop | Memetakan setiap VM ke endpoint tunnel lokal, identitas host/VM, slot, serta tarif model. |
| Cara worker dijalankan | [`soka-worker.service`](../deploy/soka-worker.service) | systemd pada masing-masing VM guest | Memulai Java worker saat service diaktifkan, dengan konfigurasi identitas dan token dari environment file VM. |
| Prosedur provisioning dan koneksi | [`panduan_deployment_aws.md`](./panduan_deployment_aws.md) dan [`info.txt`](../info.txt) | Panduan/inventaris | Menjelaskan EC2 host, guest KVM, SSH, tunnel, pemasangan Java, token, build, dan perintah eksekusi. |

Jadi, **kode penjadwalan dan pengendaliannya ada di laptop**, sedangkan **pekerjaan CPU task dilakukan oleh kode worker di VM AWS**. Laptop tetap menjadi controller selama demo; ia bukan tempat komputasi task utama. SSH tunnel hanya menyediakan jalur privat agar controller dapat mengakses HTTP worker pada jaringan guest.

Secara praktis, titik masuk real workload adalah class `TopsisRealCloud` di source set `aws/src/main/java`. Gradle mengemas aplikasi dari folder `aws/` menjadi distribution. Artifact tersebut dipasang pada VM untuk menjalankan `TopsisWorker`; controller lokal menggunakan classpath hasil build untuk menjalankan `TopsisRealCloud`. Konfigurasi endpoint yang dibaca controller ada di folder `deploy/`.

Dengan demikian, folder `aws/` berisi **implementasi program real workload**, sedangkan folder `deploy/` berisi **konfigurasi runtime dan service worker**. File panduan menerangkan cara menghubungkan keduanya ke infrastruktur EC2/KVM yang sudah tersedia. Membuat atau menyalakan EC2 bukan bagian yang dilakukan otomatis oleh class Java ini.

## Konsep yang perlu dipahami

- **Datacenter, host, VM**: `datacenterId` menamai kelompok lokasi logis; `hostId` membedakan EC2; `vmId` membedakan VM worker pada host tersebut.
- **Slot**: jumlah task CPU yang boleh diproses bersamaan oleh worker. VM small menggunakan satu slot, VM medium dua slot. Slot membatasi konkurensi aplikasi, bukan menambah kapasitas CPU fisik.
- **Arrival time**: waktu relatif kapan task seharusnya dikirim sejak awal workload. `arrivalScale` mengalikan nilai ini; angka lebih kecil memadatkan kedatangan task.
- **Length MI**: panjang workload dari CSV digunakan sebagai ukuran relatif. Untuk eksekusi riil, program mengubahnya menjadi jumlah iterasi hashing, yaitu `ceil(lengthMI × iterationsPerMi)`. Itu bukan interpretasi literal bahwa satu iterasi sama dengan satu instruksi mesin atau satu juta instruksi.
- **Tarif**: `modeledHourlyRateUsd` adalah tarif model untuk menghitung kriteria biaya, bukan tagihan AWS aktual. `modeledCostUsd` pada ringkasan juga merupakan estimasi berdasarkan tarif tersebut.

## Alur program dari awal sampai akhir

### 1. Worker dinyalakan di tiap VM

Service systemd menjalankan `TopsisWorker` dengan identitas datacenter, host, VM, jumlah slot, bind address, dan port. Rahasia `SOKA_WORKER_TOKEN` dibaca dari environment file di VM.

Worker membuka dua endpoint:

- `GET /health`: mengembalikan identitas worker, hostname guest, jumlah slot, dan slot yang sedang sibuk.
- `POST /execute`: menerima JSON berisi `taskId` dan `iterations`, menjalankan beban CPU, lalu mengembalikan identitas, waktu, dan checksum.

Keduanya memerlukan header `Authorization: Bearer <token>`. Token yang tidak cocok menghasilkan HTTP 401. Jika semua slot sedang dipakai, `/execute` menjawab HTTP 429.

### 2. SSH tunnel menghubungkan laptop ke jaringan VM

VM memakai alamat privat jaringan NAT libvirt yang tidak dapat diakses langsung dari internet. Dua tunnel SSH meneruskan port lokal laptop `18001–18004` ke port `8080` pada empat VM. Karena itu endpoint di file konfigurasi memakai `127.0.0.1`, contohnya `http://127.0.0.1:18001`.

Jalankan tunnel di dua terminal dan biarkan terminal tersebut tetap aktif selama preflight dan workload berjalan. Bila alamat IP guest berubah, tujuan tunnel harus disesuaikan dengan IP yang ditemukan dari host menggunakan `virsh domifaddr`.

### 3. Controller membaca konfigurasi dan token

`TopsisRealCloud` adalah entry point command line. Perintah yang tersedia adalah `preflight` dan `run`. Controller membaca token dari environment laptop (`SOKA_WORKER_TOKEN`) dan memuat JSON konfigurasi.

`loadConfig` memeriksa bahwa identitas lokasi tersedia, endpoint unik dan menggunakan HTTP/HTTPS, slot serta tarif valid, ada minimal dua host, dan tiap host memiliki minimal dua worker. Ini mencegah konfigurasi yang salah dipakai tanpa terdeteksi.

### 4. Preflight memastikan semua worker siap

Untuk setiap endpoint, controller mengirim `GET /health` dengan bearer token. Controller memvalidasi HTTP 200 dan mencocokkan `datacenterId`, `hostId`, `vmId`, jumlah slot, serta memastikan `busySlots` nol.

Perintah terpisah untuk menjalankan pemeriksaan ini:

```powershell
java -cp "aws/build/install/cloudsim-topsis/lib/*" com.soka.kelompok5.TopsisRealCloud preflight --config deploy/aws-workers.json
```

Jika gagal, periksa service worker, tunnel, IP guest, konfigurasi identitas/slot, dan token. Preflight yang sukses berarti semua worker terjangkau dan siap pada saat pemeriksaan.

### 5. Dataset diproses menjadi task

Pada perintah `run`, `GocjWorkload.loadCpu` membaca setiap baris CSV yang terdiri dari tiga kolom: panjang task, level SLA (1–3), dan waktu kedatangan. Nilai divalidasi, ID task dibuat berdasarkan urutan baris mulai dari 0, lalu data diurutkan berdasarkan waktu kedatangan dan ID.

`--limit 20` membatasi workload ke 20 baris pertama dari daftar yang telah dimuat/diurutkan. `--iterations-per-mi` menentukan banyaknya hashing berdasarkan panjang task. `--arrival-scale` mengalikan waktu kedatangan. Sebelum memulai, program memvalidasi argumen dan menolak direktori output yang sudah ada agar hasil lama tidak tertimpa.

### 6. Controller mengukur throughput masing-masing worker

Sebelum scheduling, controller mengirim beban kalibrasi dengan jumlah iterasi tetap (`200.000`) ke worker dan mengukur iterasi per detik. Pengukuran beberapa sampel diringkas sebagai median, sehingga scheduler memakai kecepatan VM yang terukur pada kondisi saat itu, bukan hanya mengandalkan label small/medium.

Kalibrasi ini sendiri memakai CPU worker. Jangan menjalankan beberapa eksperimen bersamaan, karena dapat memengaruhi hasil pengukuran dan performa.

### 7. TOPSIS memilih worker untuk tiap task

`TopsisScheduler.schedule` memproses task berdasarkan arrival time. Untuk setiap task dan setiap VM, ia memperkirakan:

1. **Waktu selesai**: waktu mulai paling awal yang tersedia pada salah satu slot VM, ditambah durasi perkiraan task (`iterations / measuredIterationsPerSecond`).
2. **Biaya model**: durasi perkiraan dikali tarif per jam dan dibagi 3600.

Setiap VM menjadi alternatif; waktu selesai dan biaya menjadi kriteria yang keduanya diminimalkan. Nilai dinormalisasi, diberi bobot, dibandingkan dengan solusi ideal terbaik/terburuk, lalu dihitung skor kedekatan relatif. Task ditempatkan pada VM dengan skor terbaik. Jika skor sama, scheduler memilih VM yang saat itu memiliki lebih sedikit task teralokasi.

Untuk demo, pemanggilan scheduler memberi bobot waktu `0.5` dan biaya `0.5`. Task yang sudah dijadwalkan juga memperbarui waktu tersedia slot virtual yang paling cepat siap, sehingga scheduler memperhitungkan antrean serta kapasitas slot.

### 8. Task dikirim ke worker sesuai waktu kedatangan

Controller membuat executor lokal per worker dengan jumlah thread sama dengan kapasitas slot worker. Task dikirim ke endpoint `/execute` yang dipilih scheduler, setelah menunggu sampai scaled arrival time-nya. Request memuat ID dan jumlah iterasi. Worker memvalidasi token dan request, mengambil satu permit slot, menjalankan SHA-256, lalu mengembalikan respons.

`CpuWorkload` berulang kali menghitung SHA-256 atas digest sebelumnya. Ini menghasilkan pekerjaan CPU deterministik. Worker merekam `executionSeconds` (durasi elapsed) dan bila JVM mendukungnya, `cpuSeconds` (waktu CPU thread). Checksum 64 karakter digunakan sebagai bukti bahwa perhitungan selesai dan hasilnya diterima.

Controller mengecek ulang bahwa task ID, iterasi, identitas lokasi, dan checksum cocok. Jika request gagal atau respons tidak valid, proses dihentikan dan file hasil parsial ditulis dengan status `FAILED`.

### 9. Hasil ditulis ke direktori output

Jika semua task sukses, controller menulis tiga file:

- `real_tasks.csv`: detail per task, worker tujuan, arrival/dispatch/completion, durasi eksekusi, CPU time, checksum.
- `real_vm_allocation.csv`: kapasitas, tarif model, throughput terukur, akumulasi waktu komputasi, dan jumlah task per VM.
- `real_summary.json`: status, ukuran workload, makespan, biaya model, degree of imbalance, dan waktu scheduler.

`makespanSeconds` pada implementasi ini adalah waktu completion maksimum yang diukur controller sejak epoch workload. `degreeOfImbalance` dihitung dari total `executionSeconds` per VM sebagai `(busy maksimum − busy minimum) / busy rata-rata`. VM yang tidak mendapat task memiliki busy time nol. Biaya model dihitung per VM dengan pembulatan jam ke atas: `ceil(busyTime / 3600) × modeledHourlyRateUsd`. `schedulerRuntimeMs` hanya waktu untuk proses penjadwalan, bukan waktu eksekusi task.

## Cara menjalankan demo

Pastikan build sudah dibuat dan worker sudah aktif pada keempat VM. Ikuti petunjuk koneksi/tunnel dan pemasangan worker pada [`panduan_deployment_aws.md`](./panduan_deployment_aws.md).

1. Buka dua terminal PowerShell untuk SSH tunnel (masing-masing satu host).
2. Di terminal controller, atur token yang sama dengan token pada keempat VM:

   ```powershell
   $env:SOKA_WORKER_TOKEN = "<token-rahasia>"
   ```

3. Jalankan preflight:

   ```powershell
   java -cp "aws/build/install/cloudsim-topsis/lib/*" com.soka.kelompok5.TopsisRealCloud preflight --config deploy/aws-workers.json
   ```

4. Jalankan demo 20 task. Nama output harus belum pernah ada:

   ```powershell
   java -cp "aws/build/install/cloudsim-topsis/lib/*" com.soka.kelompok5.TopsisRealCloud run `
     --config deploy/aws-workers.json `
     --dataset gocj_dataset/GoCJ_Dataset_1000.csv `
     --limit 20 `
     --iterations-per-mi 10 `
     --arrival-scale 0.01 `
     --output results/aws-demo-20
   ```

5. Buka `real_summary.json` untuk ringkasan, lalu cocokkan penempatan task di `real_tasks.csv` dengan agregat per VM di `real_vm_allocation.csv`.

Build distribusi worker dijalankan dari root project:

```powershell
.\aws\gradlew.bat -p aws test installDist distTar
```

Perintah build di atas juga menjalankan pemeriksaan project yang terikat pada task Gradle `test`. Output distribusi dipasang pada masing-masing VM sesuai panduan deployment.

## Cara menjelaskan demo secara lisan

> “Controller di laptop lebih dulu mengecek empat worker AWS dengan endpoint health. Setelah semua siap, controller membaca task GoCJ dan mengukur throughput nyata tiap VM. TOPSIS lalu memilih VM berdasarkan prediksi waktu selesai dan biaya model. Controller mengirim task melalui SSH tunnel, setiap VM menjalankan hashing SHA-256 dan mengembalikan waktu serta checksum. Terakhir, controller menyimpan detail task, distribusi VM, dan metrik agregat ke file hasil.”

Hal penting saat presentasi:

- Perhitungan CPU berlangsung pada VM guest AWS; controller mengatur dan mengukur orkestrasi dari laptop.
- Akses worker diarahkan melalui SSH tunnel. Port 8080 worker tidak diekspos ke publik.
- `SOKA_WORKER_TOKEN` adalah rahasia; jangan masukkan nilai token ke repository atau screenshot presentasi.
- Biaya yang dicetak adalah **biaya model berdasarkan rate konfigurasi**, bukan jumlah tagihan AWS.
- Jumlah iterasi SHA-256 adalah beban kerja sintetis yang diturunkan dari panjang task dataset.
- Keberhasilan preflight menunjukkan worker siap dijangkau saat itu; hasil workload baru membuktikan task selesai dieksekusi.
