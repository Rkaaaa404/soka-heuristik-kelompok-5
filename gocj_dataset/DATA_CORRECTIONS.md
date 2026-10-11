# Koreksi data untuk benchmark

| File | Baris (1-based) | Nilai awal | Nilai sesudah koreksi | Dasar koreksi |
|---|---:|---|---|---|
| `GoCJ_Dataset_5000.csv` | 4986 | `83000,4,4987` | `83000,2,4987` | SLA 4 berada di luar domain 1–3. Nilai 2 diimputasi memakai kelas modal untuk panjang 83.000 MI pada file yang sama: 50 baris SLA 2, 38 baris SLA 3, dan 16 baris SLA 1 sebelum koreksi. |

Koreksi dilakukan pada 2026-10-11 atas permintaan pengguna. Nilai SLA asli tidak dapat dipastikan dari file lokal; nilai 2 merupakan imputasi yang terdokumentasi, bukan pemulihan label asli. Panjang task, arrival time, dan jumlah baris dipertahankan. Loader tetap menolak SLA di luar 1–3; tidak ada fallback atau normalisasi otomatis pada runtime.

SLA dipakai sebagai metadata dalam benchmark TOPSIS bi-objektif saat ini, sehingga koreksi label ini tidak mengubah fungsi keputusan penjadwalan. Bila eksperimen berikutnya memakai SLA sebagai prioritas atau kriteria, baris yang diimputasi perlu diperhitungkan dalam analisis.
