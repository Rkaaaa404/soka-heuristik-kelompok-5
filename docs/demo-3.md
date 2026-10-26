# Checklist demo TOPSIS AWS

Konfigurasi worker sudah terpasang di keempat guest. **Tidak perlu build atau memasang ulang** selama instance tetap menyala. Besok, siapkan token dan buka tunnel, lalu jalankan preflight dan workload.

## 1. Buka PowerShell utama

Dari folder repository:

```powershell
Set-Location 'D:\ITS\5 SOKA\soka-heuristik-kelompok-5'
```

Pastikan token yang sama dengan token di keempat worker tersedia di sesi PowerShell ini:

```powershell
$env:SOKA_WORKER_TOKEN = '<token-rahasia-yang-sama>'
```

Token tidak disimpan permanen dalam variabel PowerShell. Simpan dengan aman untuk demo dan jangan masukkan ke repository atau kirim lewat chat.

## 2. Buka dua tunnel

Buka dua jendela PowerShell terpisah dan biarkan keduanya berjalan selama demo.

**Jendela 1 — Host 1:**

```powershell
ssh -N -o ExitOnForwardFailure=yes -o ServerAliveInterval=30 `
  -L 127.0.0.1:18001:192.168.122.130:8080 `
  -L 127.0.0.1:18002:192.168.122.246:8080 `
  soka-host-1
```

**Jendela 2 — Host 2:**

```powershell
ssh -N -o ExitOnForwardFailure=yes -o ServerAliveInterval=30 `
  -L 127.0.0.1:18003:192.168.122.28:8080 `
  -L 127.0.0.1:18004:192.168.122.54:8080 `
  soka-host-2
```

Jangan tutup kedua jendela itu sampai demo selesai.

## 3. Jalankan preflight

Di PowerShell utama:

```powershell
java -cp "aws/build/install/cloudsim-topsis/lib/*" com.soka.kelompok5.TopsisRealCloud preflight --config deploy/aws-workers.json
```

Pastikan keluar tulisan:

```text
[PREFLIGHT] SUKSES: Semua 4 worker aktif, terautentikasi, dan siap menerima tugas.
```

Jika preflight gagal, pastikan kedua tunnel masih berjalan dan tokennya benar.

## 4. Jalankan demo 20 task

Folder `results/aws-demo-20` sudah dipakai pada demo sebelumnya, jadi gunakan nama baru, misalnya:

```powershell
java -cp "aws/build/install/cloudsim-topsis/lib/*" com.soka.kelompok5.TopsisRealCloud run `
  --config deploy/aws-workers.json `
  --dataset gocj_dataset/GoCJ_Dataset_1000.csv `
  --limit 20 `
  --iterations-per-mi 10 `
  --arrival-scale 0.01 `
  --output results/aws-demo-20-besok
```

Tunggu sampai muncul:

```text
[CONTROLLER] SUKSES: Semua 20 task selesai dieksekusi.
```

Hasil demo tersimpan di folder output tersebut:

- `real_summary.json` — status dan metrik ringkasan
- `real_tasks.csv` — detail eksekusi setiap task
- `real_vm_allocation.csv` — pembagian tugas per VM

Demo sebelumnya berhasil menyelesaikan **20/20 task** dengan makespan sekitar **10,51 detik**.

## 5. Setelah demo

Tutup tunnel dengan `Ctrl+C` di kedua jendela PowerShell. Karena EC2 tetap menyala, worker tetap berjalan.