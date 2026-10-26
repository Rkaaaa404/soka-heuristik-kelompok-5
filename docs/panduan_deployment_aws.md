# Panduan Lengkap Deployment & Eksekusi Workload Riil TOPSIS di AWS

Dokumen ini merupakan panduan operasional komprehensif bagi anggota Kelompok 5 untuk mendeploy, mengonfigurasi, dan mengeksekusi workload riil berbasis algoritma **TOPSIS (Technique for Order Preference by Similarity to Ideal Solution)** pada infrastruktur cloud AWS yang telah disediakan.

---

## 1. Topologi Infrastruktur, Verdict, dan Batasan Bukti

### 1.1 Spesifikasi Topologi Cloud Lab
Berdasarkan inventaris pada `info.txt`, topologi infrastruktur memenuhi ketentuan minimum tugas (minimal **1 Datacenter, 2 Host, dan 2 VM per Host**):

- **Region AWS**: `ap-southeast-2` (Sydney)
- **Availability Zone (AZ)**: `ap-southeast-2a` (direpresentasikan sebagai 1 Datacenter Logis: `aws-sydney`)
- **Parent Hypervisor Hosts**: 2 unit instance AWS EC2 bertipe `m7i-flex.large` (Ubuntu 24.04 LTS):
  - **Host 1**: Instance ID `i-0ee2213be790b0b23` | IP Publik `13.239.241.220` | IP Privat `172.31.1.242`
  - **Host 2**: Instance ID `i-078a63e2bc5a95315` | IP Publik `52.63.114.7` | IP Privat `172.31.3.212`
- **Nested Virtual Machines (KVM/libvirt Guests)**: Masing-masing host menjalankan 2 VM heterogen:
  - **Host 1**:
    - `soka-vm-s1` (Tipe Small): 1 vCPU, 1 GiB RAM, slot komputasi 1, tarif model $0.05/jam.
    - `soka-vm-m1` (Tipe Medium): 2 vCPU, 2 GiB RAM, slot komputasi 2, tarif model $0.12/jam.
  - **Host 2**:
    - `soka-vm-s2` (Tipe Small): 1 vCPU, 1 GiB RAM, slot komputasi 1, tarif model $0.05/jam.
    - `soka-vm-m2` (Tipe Medium): 2 vCPU, 2 GiB RAM, slot komputasi 2, tarif model $0.12/jam.

Total kapasitas kluster eksekusi riil: **4 Virtual Machine heterogen, 6 vCPU tamu, 6 GiB RAM**.

### 1.2 Batasan Bukti Ilmiah & Karakteristik Infrastruktur
1. **Dukungan Resmi Nested Virtualization**:
   AWS secara resmi mendokumentasikan bahwa keluarga instance `m7i-flex` mendukung nested virtualization berbasis Intel VT-x/KVM:
   - Referensi resmi: [AWS EC2 Nested Virtualization Documentation](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/amazon-ec2-nested-virtualization.html).
2. **Definisi Datacenter & Anti-Affinity**:
   AWS mendefinisikan Availability Zone (AZ) sebagai satu atau beberapa pusat data fisik yang saling terhubung dengan latensi rendah, bukan jaminan tepat satu bangunan fisik:
   - Referensi resmi: [AWS Global Infrastructure: Regions & AZs](https://docs.aws.amazon.com/global-infrastructure/latest/regions/aws-regions-availability-zones.html).
   - Kedua host EC2 berada dalam satu AZ (`ap-southeast-2a`), sehingga diperlakukan sebagai satu datacenter logis. Jangan mengklaim kedua instance EC2 berada di server fisik terpisah (hardware anti-affinity) kecuali menggunakan AWS Dedicated Hosts.
3. **Overcommit CPU (3 vCPU Guest pada 2 vCPU Parent)**:
   Masing-masing parent EC2 `m7i-flex.large` memiliki 2 vCPU fisik/hypervisor AWS, sedangkan total alokasi guest adalah 3 vCPU (1 vCPU Small + 2 vCPU Medium). Rasio overcommit 1.5:1 ini wajar pada lingkungan KVM, namun dapat menimbulkan kontensi CPU saat kedua VM sibuk penuh. Algoritma penjadwalan menggunakan kalibrasi throughput throughput aktual untuk mengantisipasi hal ini.
4. **Kapasitas Disk Terbatas**:
   Disk guest VM berukuran ~4 GiB dan disk host ~10 GiB. Jangan menyimpan dataset besar, file ISO, atau build cache di dalam guest VM. Upload hanya artifact binary terkompresi (~300 KB).
5. **Mengapa 4 VM (Bukan 80 VM Seperti Simulasi CloudSim)**:
   Simulasi CloudSim sebelumnya mengasumsikan 80 VM sintetis pada 8 host server 24-core. Pada deployment cloud riil dengan anggaran mahasiswa, 4 VM adalah representasi nyata minimum yang dapat dibuktikan secara langsung. Jangan menduplikasi data CloudSim untuk laporan eksekusi riil.

---

## 2. Verifikasi Parent Host EC2

### 2.1 Melalui AWS Management Console (Tanpa CLI)
1. Buka [AWS Management Console](https://console.aws.amazon.com/ec2/).
2. Pastikan region di sudut kanan atas adalah **Sydney (`ap-southeast-2`)**.
3. Masuk ke menu **Instances** dan periksa kedua instance:
   - `i-0ee2213be790b0b23` dan `i-078a63e2bc5a95315`.
   - Pastikan status **Instance state = Running** dan **Status check = 2/2 checks passed**.
   - Catat alamat **Public IPv4 Address** terbaru (IP publik dapat berubah jika instance pernah di-stop/start).

### 2.2 Melalui AWS CLI (Opsional - Mode Read-Only)
Jika Anda memiliki kredensial AWS CLI yang valid di laptop:

```powershell
# PowerShell laptop: Cek status instance
aws ec2 describe-instances --region ap-southeast-2 `
  --instance-ids i-0ee2213be790b0b23 i-078a63e2bc5a95315 `
  --query "Reservations[].Instances[].{ID:InstanceId,Type:InstanceType,State:State.Name,PublicIP:PublicIpAddress,PrivateIP:PrivateIpAddress,AZ:Placement.AvailabilityZone}" `
  --output table
```

```powershell
# PowerShell laptop: Cek dukungan fitur processor m7i-flex.large
aws ec2 describe-instance-types --region ap-southeast-2 `
  --instance-types m7i-flex.large `
  --query "InstanceTypes[].ProcessorInfo.SupportedFeatures" `
  --output json
```

---

## 3. Inspeksi Hypervisor Host & Penemuan IP Guest (KVM/libvirt)

Lakukan SSH ke masing-masing host EC2 untuk memastikan KVM aktif dan memeriksa alamat IP guest yang sedang berjalan.

```bash
# Bash Ubuntu host: Verifikasi virtualisasi dan ketersediaan perangkat KVM
hostnamectl
lscpu | grep -E "Virtualization|Hypervisor|Model name"
ls -l /dev/kvm
```
*Hasil yang diharapkan: File `/dev/kvm` tersedia dengan permission `crw-rw----+`.*

```bash
# Bash Ubuntu host: Periksa status domain/VM KVM
sudo virsh -c qemu:///system list --all
```
*Hasil yang diharapkan: Menampilkan 2 domain dalam status `running` (misal `soka-vm-s1` dan `soka-vm-m1` di Host 1).*

```bash
# Bash Ubuntu host: Periksa konfigurasi spesifikasi VM
sudo virsh dominfo soka-vm-s1
sudo virsh dumpxml soka-vm-s1 | grep -E "domain type|vcpu|memory"
```

```bash
# Bash Ubuntu host: Temukan alamat IP DHCP lease aktual dari guest VM
sudo virsh net-dhcp-leases default
```
*Jika tabel lease kosong, periksa melalui interface domain:*
```bash
# Bash Ubuntu host: Temukan alamat IP alternatif via ARP / lease
sudo virsh domifaddr soka-vm-s1 --source lease
sudo virsh domifaddr soka-vm-m1 --source lease
```

> **Catatan Jaringan Penting**: Subnet `192.168.122.0/24` adalah jaringan NAT lokal libvirt bridge (`virbr0`) di masing-masing host EC2. Subnet ini independen dan tidak terhubung langsung antar Host 1 dan Host 2. Alamat IP guest di `info.txt` adalah catatan terakhir; selalu gunakan IP hasil temuan dari perintah di atas.

---

## 4. Konfigurasi SSH Laptop Teammate

Agar dapat mengakses host dan guest VM secara aman tanpa membuka port publik tambahan, gunakan konfigurasi OpenSSH di laptop Anda (`~/.ssh/config` pada Linux/macOS atau `C:\Users\<User>\.ssh\config` pada Windows).

Security Group AWS hanya mengizinkan port TCP 22 dari IP publik `/32` anggota tim. Port worker HTTP (8080) **tidak perlu dan tidak boleh** dibuka ke internet publik.

Simpan konfigurasi berikut pada file `~/.ssh/config` di laptop Anda:

```sshconfig
# Host 1 (Ganti 13.239.241.220 jika IP publik berubah setelah restart)
Host soka-host-1
    HostName 13.239.241.220
    User ubuntu
    IdentityFile ~/.ssh/soka-key.pem
    IdentitiesOnly yes
    StrictHostKeyChecking yes

# Host 2 (Ganti 52.63.114.7 jika IP publik berubah setelah restart)
Host soka-host-2
    HostName 52.63.114.7
    User ubuntu
    IdentityFile ~/.ssh/soka-key.pem
    IdentitiesOnly yes
    StrictHostKeyChecking yes

# Guest VMs Host 1 (Ganti IP jika berbeda pada virsh domifaddr)
Host soka-vm-s1
    HostName 192.168.122.130
    User ubuntu
    ProxyJump soka-host-1
    IdentityFile ~/.ssh/soka-guest-key.pem
    StrictHostKeyChecking yes

Host soka-vm-m1
    HostName 192.168.122.246
    User ubuntu
    ProxyJump soka-host-1
    IdentityFile ~/.ssh/soka-guest-key.pem
    StrictHostKeyChecking yes

# Guest VMs Host 2 (Ganti IP jika berbeda pada virsh domifaddr)
Host soka-vm-s2
    HostName 192.168.122.28
    User ubuntu
    ProxyJump soka-host-2
    IdentityFile ~/.ssh/soka-guest-key.pem
    StrictHostKeyChecking yes

Host soka-vm-m2
    HostName 192.168.122.54
    User ubuntu
    ProxyJump soka-host-2
    IdentityFile ~/.ssh/soka-guest-key.pem
    StrictHostKeyChecking yes
```

Uji konektivitas SSH dari laptop:
```powershell
# PowerShell laptop: Uji SSH ke Host
ssh soka-host-1 "uptime"
ssh soka-host-2 "uptime"

# PowerShell laptop: Uji SSH ke Guest VM via ProxyJump
ssh soka-vm-s1 "uptime"
```

---

## 5. Pembuatan Token Autentikasi Rahasia

Layanan worker dan controller berkomunikasi melalui REST API yang diamankan menggunakan HTTP Bearer Token. Jangan menggunakan token yang mudah ditebak atau mencatatnya di repository git.

Jalankan perintah berikut di PowerShell laptop untuk membuat token acak 32-byte (kompatibel dengan Windows PowerShell 5.1 & PowerShell Core 7+):

```powershell
# PowerShell laptop: Buat token acak kriptografis
$bytes = New-Object byte[] 32
(New-Object System.Security.Cryptography.RNGCryptoServiceProvider).GetBytes($bytes)
$token = -join ($bytes | ForEach-Object { '{0:x2}' -f $_ })
$env:SOKA_WORKER_TOKEN = $token
Write-Host "Token sesi berhasil dibuat dan disimpan di `$env:SOKA_WORKER_TOKEN:"
Write-Host $token
```

Simpan string token tersebut. Nilai token yang sama persis harus dimasukkan ke dalam file lingkungan `/etc/soka/worker.env` di ke-4 guest VM.

---

## 6. Build dan Instalasi Layanan Worker di Setiap Guest VM

### 6.1 Build Distribution Archive di Laptop
Dari root repository di laptop, jalankan build Gradle untuk mempaketkan aplikasi:

```powershell
# PowerShell laptop: Build distribution artifact
.\aws\gradlew.bat -p aws test installDist distTar
```
*File arsip yang dihasilkan berada di: `aws/build/distributions/cloudsim-topsis-1.0.0.tar` (ukuran ~300 KB).*

### 6.2 Upload Artifact ke Masing-Masing Guest VM
Gunakan SCP dengan alias ProxyJump yang telah dikonfigurasi:

```powershell
# PowerShell laptop: Upload tarball ke semua VM
scp aws/build/distributions/cloudsim-topsis-1.0.0.tar deploy/soka-worker.service soka-vm-s1:~/
scp aws/build/distributions/cloudsim-topsis-1.0.0.tar deploy/soka-worker.service soka-vm-m1:~/
scp aws/build/distributions/cloudsim-topsis-1.0.0.tar deploy/soka-worker.service soka-vm-s2:~/
scp aws/build/distributions/cloudsim-topsis-1.0.0.tar deploy/soka-worker.service soka-vm-m2:~/
```

### 6.3 Setup Worker di Dalam Guest VM
Masuk ke masing-masing VM via SSH dan lakukan instalasi berikut.

#### Langkah 1: Instalasi Java Runtime (JDK 17+)
```bash
# Bash Ubuntu guest: Update package & install Java JRE Headless
sudo apt-get update && sudo apt-get install -y openjdk-17-jre-headless

# Bash Ubuntu guest: Verifikasi modul jdk.httpserver tersedia
/usr/bin/java --list-modules | grep jdk.httpserver
```

#### Langkah 2: Buat User dan Ekstrak Aplikasi
```bash
# Bash Ubuntu guest: Buat unprivileged user soka
sudo useradd -r -s /usr/sbin/nologin -d /opt/soka soka

# Bash Ubuntu guest: Ekstrak arsip ke /opt/soka
sudo mkdir -p /opt/soka
sudo tar -xf ~/cloudsim-topsis-1.0.0.tar -C /opt/soka --strip-components=1
sudo chown -R soka:soka /opt/soka
sudo chmod -R 755 /opt/soka
rm ~/cloudsim-topsis-1.0.0.tar
```

#### Langkah 3: Konfigurasi File Lingkungan `/etc/soka/worker.env`
Buat direktori konfigurasi dan file lingkungan berhak akses ketat (`chmod 600`):

```bash
# Bash Ubuntu guest: Buat folder konfigurasi
sudo mkdir -p /etc/soka
```

Isi `/etc/soka/worker.env` sesuai identitas masing-masing VM:

**Untuk VM `soka-vm-s1` (Host 1, Small):**
```bash
sudo bash -c 'cat <<EOF > /etc/soka/worker.env
DATACENTER_ID=aws-sydney
HOST_ID=soka-host-1
VM_ID=soka-vm-s1
SLOTS=1
SOKA_WORKER_TOKEN=<MASUKKAN_TOKEN_DARI_LANGKAH_5>
EOF'
```

**Untuk VM `soka-vm-m1` (Host 1, Medium):**
```bash
sudo bash -c 'cat <<EOF > /etc/soka/worker.env
DATACENTER_ID=aws-sydney
HOST_ID=soka-host-1
VM_ID=soka-vm-m1
SLOTS=2
SOKA_WORKER_TOKEN=<MASUKKAN_TOKEN_DARI_LANGKAH_5>
EOF'
```

**Untuk VM `soka-vm-s2` (Host 2, Small):**
```bash
sudo bash -c 'cat <<EOF > /etc/soka/worker.env
DATACENTER_ID=aws-sydney
HOST_ID=soka-host-2
VM_ID=soka-vm-s2
SLOTS=1
SOKA_WORKER_TOKEN=<MASUKKAN_TOKEN_DARI_LANGKAH_5>
EOF'
```

**Untuk VM `soka-vm-m2` (Host 2, Medium):**
```bash
sudo bash -c 'cat <<EOF > /etc/soka/worker.env
DATACENTER_ID=aws-sydney
HOST_ID=soka-host-2
VM_ID=soka-vm-m2
SLOTS=2
SOKA_WORKER_TOKEN=<MASUKKAN_TOKEN_DARI_LANGKAH_5>
EOF'
```

Kunci hak akses file environment:
```bash
# Bash Ubuntu guest: Batasi akses hanya root dan soka
sudo chown soka:soka /etc/soka/worker.env
sudo chmod 600 /etc/soka/worker.env
```

#### Langkah 4: Pasang dan Jalankan Service systemd
```bash
# Bash Ubuntu guest: Salin service unit & aktifkan
sudo cp ~/soka-worker.service /etc/systemd/system/soka-worker.service
sudo systemctl daemon-reload
sudo systemctl enable --now soka-worker
```

#### Langkah 5: Verifikasi Status Worker & Health Check
```bash
# Bash Ubuntu guest: Cek status service
sudo systemctl status soka-worker

# Bash Ubuntu guest: Uji health check lokal terautentikasi
curl -i -H "Authorization: Bearer <MASUKKAN_TOKEN>" http://127.0.0.1:8080/health
```
*Respons yang diharapkan: HTTP 200 dengan payload JSON berisi `datacenterId`, `hostId`, `vmId`, `hostname`, `slots`, dan `busySlots: 0`.*

```bash
# Bash Ubuntu guest: Cek log worker
sudo journalctl -u soka-worker -n 20 --no-pager
```

> **Firewall Guest**: Jika UFW aktif di guest VM, pastikan port 8080 diizinkan dari bridge libvirt host (`192.168.122.1`):  
> `sudo ufw allow from 192.168.122.0/24 to any port 8080 proto tcp`

---

## 7. Pembukaan Tunnel Port Forwarding SSH

Karena worker mendengarkan di IP internal KVM pada masing-masing EC2, laptop operator menghubungkan port controller lokal ke worker melalui SSH Local Port Forwarding.

Buka **dua terminal PowerShell terpisah** di laptop Anda dan biarkan keduanya berjalan di latar depan:

### Terminal Port Forwarding 1 (Host 1):
```powershell
# PowerShell laptop (Terminal 1): Forward port VM Host 1
ssh -N -o ExitOnForwardFailure=yes -o ServerAliveInterval=30 `
  -L 127.0.0.1:18001:192.168.122.130:8080 `
  -L 127.0.0.1:18002:192.168.122.246:8080 `
  soka-host-1
```

### Terminal Port Forwarding 2 (Host 2):
```powershell
# PowerShell laptop (Terminal 2): Forward port VM Host 2
ssh -N -o ExitOnForwardFailure=yes -o ServerAliveInterval=30 `
  -L 127.0.0.1:18003:192.168.122.28:8080 `
  -L 127.0.0.1:18004:192.168.122.54:8080 `
  soka-host-2
```

*(Catatan: Ganti alamat IP `192.168.122.x` jika hasil temuan `virsh domifaddr` Anda berbeda).*

Setelah kedua terminal berjalan, port loopback laptop terpetakan secara transparan ke seluruh VM:
- `127.0.0.1:18001` -> `soka-vm-s1`
- `127.0.0.1:18002` -> `soka-vm-m1`
- `127.0.0.1:18003` -> `soka-vm-s2`
- `127.0.0.1:18004` -> `soka-vm-m2`

---

## 8. Eksekusi Workload Riil & Pengujian

Buka **terminal PowerShell ke-3** di laptop dari root folder repository.

### 8.1 Pastikan Variabel Token Terpasang di Sesi
```powershell
# PowerShell laptop: Pastikan token terdefinisi
$env:SOKA_WORKER_TOKEN = "<MASUKKAN_TOKEN_DARI_LANGKAH_5>"
```

### 8.2 Jalankan Preflight Check
Perintah ini memvalidasi identitas, ketersediaan, kapasitas slot, dan status antrean semua 4 worker sebelum eksekusi tugas dimulai:

```powershell
# PowerShell laptop: Uji preflight kluster
java -cp "aws/build/install/cloudsim-topsis/lib/*" com.soka.kelompok5.TopsisRealCloud preflight --config deploy/aws-workers.json
```

*Contoh output yang diharapkan:*
```text
=== STATUS PREFLIGHT WORKER AWS TOPSIS ===
Datacenter ID: aws-sydney | Region: ap-southeast-2 | AZ: ap-southeast-2a
+---------------+--------------+----------------------+-------+-------------+----------------------+
| Host ID       | VM ID        | Guest Hostname       | Slots | Hourly Rate | Endpoint             |
+---------------+--------------+----------------------+-------+-------------+----------------------+
| soka-host-1   | soka-vm-s1   | soka-vm-s1           | 1     | $0.05       | http://127.0.0.1:18001 |
| soka-host-1   | soka-vm-m1   | soka-vm-m1           | 2     | $0.12       | http://127.0.0.1:18002 |
| soka-host-2   | soka-vm-s2   | soka-vm-s2           | 1     | $0.05       | http://127.0.0.1:18003 |
| soka-host-2   | soka-vm-m2   | soka-vm-m2           | 2     | $0.12       | http://127.0.0.1:18004 |
+---------------+--------------+----------------------+-------+-------------+----------------------+

[PREFLIGHT] SUKSES: Semua 4 worker aktif, terautentikasi, dan siap menerima tugas.
```

### 8.3 Eksekusi Demo Cepat (20 Tugas dari GoCJ 1000)
Jalankan eksekusi 20 tugas pertama dari dataset resmi GoCJ untuk demonstrasi langsung:

```powershell
# PowerShell laptop: Jalankan 20 task ke kluster AWS
java -cp "aws/build/install/cloudsim-topsis/lib/*" com.soka.kelompok5.TopsisRealCloud run `
  --config deploy/aws-workers.json `
  --dataset gocj_dataset/GoCJ_Dataset_1000.csv `
  --limit 20 `
  --iterations-per-mi 10 `
  --arrival-scale 0.01 `
  --output results/aws-demo-20
```

*Penjelasan Parameter:*
- `--config deploy/aws-workers.json`: Path konfigurasi endpoint worker AWS.
- `--dataset gocj_dataset/GoCJ_Dataset_1000.csv`: Dataset resmi Google Cloud Jobs.
- `--limit 20`: Mengambil 20 task pertama secara deterministik.
- `--iterations-per-mi 10`: Mengonversi 1 MI (Million Instructions) menjadi 10 iterasi SHA-256 CPU bound deterministik.
- `--arrival-scale 0.01`: Mengompresi waktu kedatangan task GoCJ sebesar 100x agar eksekusi praktikum efisien tanpa menunggu jeda panjang.
- `--output results/aws-demo-20`: Direktori baru hasil eksekusi (wajib belum ada sebelumnya).

### 8.4 Penjelasan Format Output Hasil Eksekusi
Di dalam folder output (`results/aws-demo-20/`), dihasilkan 3 artefak utama:

1. **`real_tasks.csv`**:
   Rincian setiap task yang selesai dieksekusi di guest VM:
   - `TaskId`: ID unik task sesuai urutan file GoCJ (0-indexed).
   - `Length_MI`: Panjang task dalam Million Instructions dari dataset.
   - `SLA`: Tingkat prioritas task (1=Low, 2=Medium, 3=High).
   - `OriginalArrival_s`: Waktu kedatangan asli pada file CSV.
   - `Arrival_s`: Waktu kedatangan setelah penskalaan (`original * arrivalScale`).
   - `HostId` & `VmId`: Identitas host dan VM tempat task dieksekusi.
   - `Iterations`: Jumlah hashing SHA-256 aktual (`ceil(Length_MI * iterationsPerMi)`).
   - `Dispatch_s`: Waktu saat controller mengirimkan request ke worker (sejak epoch awal).
   - `Complete_s`: Waktu saat controller menerima respons sukses dari worker.
   - `Execution_s`: Durasi komputasi murni yang diukur langsung di dalam VM (nanosecond precision).
   - `Cpu_s`: Durasi CPU thread aktual yang digunakan di VM.
   - `Checksum`: Nilai heksadesimal 64 karakter dari SHA-256 berulang, membuktikan pekerjaan komputasi benar-benar dihitung.

2. **`real_vm_allocation.csv`**:
   Distribusi alokasi beban ke seluruh VM di kluster:
   - `DatacenterId`, `HostId`, `VmId`: Identitas infrastruktur.
   - `WorkerHostname`: Hostname riil yang dilaporkan sistem operasi guest VM.
   - `Slots`: Jumlah komputasi paralel maksimum VM.
   - `ModeledHourlyRate_USD`: Tarif per jam yang dimodelkan.
   - `MeasuredIterationsPerSecond`: Throughput komputasi riil hasil kalibrasi awal kluster.
   - `BusyTime_s`: Total durasi VM memproses tugas CPU.
   - `TaskCount`: Jumlah tugas yang dialokasikan ke VM tersebut oleh algoritma TOPSIS.

3. **`real_summary.json`**:
   Ringkasan metrik agregat:
   ```json
   {
     "status": "SUCCESS",
     "mode": "real",
     "dataset": "gocj_dataset/GoCJ_Dataset_1000.csv",
     "tasksRequested": 20,
     "tasksCompleted": 20,
     "iterationsPerMi": 10.0,
     "arrivalScale": 0.01,
     "makespanSeconds": 4.93,
     "modeledCostUsd": 0.34,
     "degreeOfImbalance": 0.83,
     "schedulerRuntimeMs": 3.95
   }
   ```
   - **Makespan**: Waktu penyelesaian seluruh workload (offset controller dari task awal hingga akhir).
   - **Degree of Imbalance (DI)**: Ketidakseimbangan beban kerja: $(Busy_{max} - Busy_{min}) / Busy_{mean}$.
   - **Modeled Cost**: Biaya model komputasi: $\sum \lceil Busy_j / 3600 \rceil \times Rate_j$.

---

## 9. Eksperimen Skala Penuh (1.000 s/d 10.000 Tasks)

Jika tim ingin menjalankan variasi skala lengkap (1.000 s/d 10.000 task dengan 3 repetisi seperti pada simulasi), gunakan skrip otomasi PowerShell berikut.

> **Peringatan**: Eksekusi skala riil memakan waktu komputasi fisik. Jalankan secara berurutan (sequential) agar tidak terjadi perebutan resource CPU pada worker.

```powershell
# PowerShell laptop: Script otomasi perulangan skala penuh
$token = $env:SOKA_WORKER_TOKEN
$scales = 1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000, 9000, 10000

foreach ($scale in $scales) {
    for ($run = 1; $run -le 3; $run++) {
        $dataset = "gocj_dataset/GoCJ_Dataset_$scale.csv"
        $outDir = "results/aws-full-exp/$scale/run-$run"

        Write-Host "`n>>> Menjalankan Skala $scale Tasks - Run $run ke $outDir..." -ForegroundColor Cyan

        java -cp "aws/build/install/cloudsim-topsis/lib/*" com.soka.kelompok5.TopsisRealCloud run `
            --config deploy/aws-workers.json `
            --dataset $dataset `
            --iterations-per-mi 10 `
            --arrival-scale 0.01 `
            --output $outDir

        if ($LASTEXITCODE -ne 0) {
            Write-Error "Eksperimen terhenti karena kegagalan pada Skala $scale Run $run!"
            exit 1
        }
    }
}
Write-Host "`nSELURUH 30 EKSPERIMEN REAL CLOUD BERHASIL SELESAI!" -ForegroundColor Green
```

---

## 10. Checklist Bukti untuk Presentasi & Laporan

Saat mengumpulkan laporan dan demo di hadapan dosen:
1. **Bukti AWS EC2**: Screenshot halaman AWS Console yang menunjukkan 2 instance `m7i-flex.large` di region Sydney (`ap-southeast-2a`).
2. **Bukti KVM Hypervisor**: Screenshot terminal terminal host yang menjalankan `virsh list --all` membuktikan adanya 2 guest KVM aktif di masing-masing host.
3. **Bukti Health & Preflight**: Log tabel preflight yang menampilkan 4 VM dengan hostname unik dari sistem operasi guest.
4. **Bukti Eksekusi Nyata**: Nilai kolom `Execution_s`, `Cpu_s`, dan `Checksum` SHA-256 pada file `real_tasks.csv`, serta log journalctl worker di guest VM:
   ```bash
   sudo journalctl -u soka-worker -n 30 --no-pager
   ```
5. **Bukti Metrik Terhitung**: File `real_summary.json` yang menunjukkan status `SUCCESS` dan metrik Makespan, Imbalance (DI), serta Modeled Cost.

---

## 11. Panduan Pemecahan Masalah (Troubleshooting)

### Q1: SSH ke EC2 timeout pada port 22
- **Penyebab**: Alamat IP publik laptop Anda berubah (misalnya akibat reconnect WiFi/ISP), sehingga Security Group AWS menolak koneksi.
- **Solusi**: Buka AWS Console -> EC2 -> Security Groups -> Edit Inbound Rules -> Perbarui aturan SSH (port 22) ke `My IP` (`<IP-Anda>/32`).

### Q2: Preflight melaporkan worker unreachable
- **Penyebab**: Tunnel SSH port forwarding belum berjalan atau salah satu terminal tunnel terputus.
- **Solusi**: Pastikan kedua terminal SSH port forwarding (Section 7) masih aktif dan tidak ada pesan error. Coba akses `curl http://127.0.0.1:18001/health` secara manual dari PowerShell.

### Q3: Preflight melaporkan HTTP 401 Unauthorized
- **Penyebab**: Nilai `$env:SOKA_WORKER_TOKEN` di laptop berbeda dengan `SOKA_WORKER_TOKEN` pada file `/etc/soka/worker.env` di VM.
- **Solusi**: Pastikan string token di laptop dan di ke-4 file `/etc/soka/worker.env` identik. Setelah mengedit `/etc/soka/worker.env`, jalankan `sudo systemctl restart soka-worker`.

### Q4: Worker mengembalikan HTTP 429 Too Many Requests
- **Penyebab**: Terjadi request konkuren yang melebihi jumlah slot komputasi VM (1 slot pada Small, 2 slot pada Medium).
- **Solusi**: Jangan menjalankan dua perintah `run` secara bersamaan. Controller mengelola antrean sesuai slot secara otomatis.

### Q5: Eksekusi gagal dengan pesan "Direktori output sudah ada"
- **Penyebab**: Standar integritas riset melarang menimpa (overwrite) data eksperimen sebelumnya.
- **Solusi**: Berikan nama direktori baru pada parameter `--output`, misalnya `results/aws-demo-20-v2` atau hapus folder lama jika memang tidak terpakai.

### Q6: Disk space habis di guest VM (`No space left on device`)
- **Penyebab**: File log atau file build tersimpan di guest VM yang berukuran 4 GiB.
- **Solusi**: Bersihkan log journalctl (`sudo journalctl --vacuum-size=50M`) dan hapus cache APT (`sudo apt-get clean`).

---

## 12. Prosedur Penghentian & Pembersihan (Teardown)

Setelah seluruh demonstrasi dan pengambilan data selesai:

1. **Hentikan Tunnel SSH di Laptop**:
   Tutup kedua jendela terminal PowerShell yang menjalankan port forwarding SSH (atau tekan `Ctrl + C`).
2. **Hentikan Layanan Worker di Guest VM**:
   ```bash
   # Bash Ubuntu guest: Matikan service
   sudo systemctl stop soka-worker
   ```
3. **Hentikan Instance EC2 di AWS Console (Penting untuk Menghemat Biaya)**:
   - Masuk ke [AWS Management Console](https://console.aws.amazon.com/ec2/).
   - Pilih kedua instance `i-0ee2213be790b0b23` dan `i-078a63e2bc5a95315`.
   - Pilih menu **Instance state -> Stop instance** (jangan pilih *Terminate* jika VM masih ingin digunakan kembali nanti).
   - **Perhatian Biaya**: Penyimpanan Amazon EBS (root volume) tetap dikenakan biaya sewa disk storage meskipun instance dalam kondisi *Stopped*. Selain itu, jika instance dinyalakan kembali, alamat IP publik IPv4 akan diperbarui oleh AWS sehingga konfigurasi `~/.ssh/config` harus disesuaikan dengan IP publik baru.
