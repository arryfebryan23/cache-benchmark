# Runbook — Menjalankan Lagi di VM Benchmark

Catatan operasional untuk menyalakan kembali `cache-benchmark` di VM RHEL 9
setelah jeda. Ditulis dari kondisi deployment 30 September 2026.

Ada dua skenario dan keduanya sangat berbeda. Tentukan dulu yang mana.

```
Hostname VM masih sama seperti terakhir kali?
        |
        +-- YA    --> Skenario A. Aplikasi masih terpasang, tinggal jalan.
        |
        +-- TIDAK --> Skenario B. Lab dibuat ulang, deploy dari nol.
```

Lab server biasanya **dihancurkan**, bukan sekadar dimatikan. Kalau hostname
berubah, seluruh isi VM hilang dan Skenario A tidak berlaku.

---

## Skenario A — VM yang sama hidup lagi

### Yang bertahan di VM

| Item | Lokasi | Catatan |
|---|---|---|
| Source + JAR | `~/cache-benchmark/` | JAR 26 MB, siap pakai |
| Cache Maven | `~/.m2/` | 43 MB, build ulang jadi cepat |
| JDK 17 | rpm `java-17-openjdk-headless` | bawaan image, bukan hasil instalasi kita |
| Maven 3.6.3 | rpm `maven` | bawaan image |
| Konfigurasi + kredensial | `~/cache-benchmark/config/benchmark.yaml` | mode 600 |
| SSH deploy key | `~/.ssh/authorized_keys` | entri `cache-benchmark-deploy` |
| Hasil benchmark | `~/cache-benchmark/results/` | **ambil sebelum lab expire** |

### Yang TIDAK bertahan

**Isi keyspace Redis.** Ini yang paling mudah bikin salah. Key `benchmark:*`
hasil preload sesi sebelumnya sudah hilang saat dicek ulang — `DBSIZE` kembali
`0`. Jadi jangan pernah pakai `--preload false` dengan asumsi datanya masih ada.

Kalau tetap dicoba, benchmark berhenti sendiri:

```
PRELOAD_VALIDATION_FAILED: key benchmark:0 is missing.
```

Itu memang perilaku yang diinginkan. Tanpa pengaman itu, GET terhadap keyspace
kosong tidak menghasilkan error — ia mengembalikan null dengan sangat cepat dan
hasilnya justru terlihat bagus sekali.

### Langkah

```bash
# 1. Masuk
ssh -i ~/.ssh/id_labserver cloud_user@<hostname>.mylabserver.com

# 2. Pastikan lingkungan masih waras
cd ~/cache-benchmark
java -jar target/cache-benchmark.jar --version     # harus: cache-benchmark 1.0.0

# 3. Cek endpoint backend belum berubah, lalu cek reachability
sed -n "/^redis:/,/^hazelcast:/p" config/benchmark.yaml | grep -E "host|port|username"
REDIS_HOST=<ip-redis> REDIS_PORT=<port> ./scripts/preflight.sh

# 4. Run pendek untuk memastikan tersambung
./scripts/run.sh --operation GET --keys 5000 --threads 4 --warmup 2 --duration 5
```

Kalau langkah 4 keluar `Result Status : VALID`, semuanya siap.

Tidak perlu menyentuh `mvn` sama sekali di Skenario A. JAR-nya sudah ada.

---

## Skenario B — VM lab baru

Hostname baru, isi kosong, tapi image-nya sama: RHEL 9 dengan JDK 17 dan Maven
sudah terpasang dari sananya. Jalankan dari mesin Windows, di Git Bash.

```bash
cd /d/vibe-coding/dummy-aplikasi-redis-hazlecast
H="cloud_user@<hostname-baru>.mylabserver.com"

# 1. Pasang deploy key (sekali saja, akan minta password VM)
ssh-copy-id -i ~/.ssh/id_labserver.pub $H

# 2. Kirim source sesuai commit terakhir
git archive --format=tar --prefix=cache-benchmark/ HEAD | gzip > /tmp/cb.tar.gz
scp -i ~/.ssh/id_labserver /tmp/cb.tar.gz $H:/tmp/

# 3. Ekstrak dan build
ssh -i ~/.ssh/id_labserver $H "
  rm -rf ~/cache-benchmark
  tar xzf /tmp/cb.tar.gz -C ~
  cd ~/cache-benchmark
  ./scripts/build.sh
"
```

Build memakan ~40 detik ditambah unduhan dependency sekitar 43 MB, sekali saja.

Pakai `git archive`, bukan `scp -r` direktori kerja. Archive mengambil isi
commit apa adanya, sehingga script `.sh` tetap LF dan tetap executable. Menyalin
direktori kerja dari Windows berisiko membawa CRLF dan kehilangan bit
executable.

### Kalau ssh-copy-id tidak jalan dari Git Bash

Pakai Posh-SSH di PowerShell (modul sudah terpasang di mesin ini):

```powershell
Import-Module Posh-SSH
$cred = Get-Credential cloud_user
$s = New-SSHSession -ComputerName "<hostname>.mylabserver.com" -Credential $cred -AcceptKey
$pub = (Get-Content "$env:USERPROFILE\.ssh\id_labserver.pub" -Raw).Trim()
Invoke-SSHCommand -SessionId $s.SessionId -Command @"
mkdir -p ~/.ssh && chmod 700 ~/.ssh
grep -qF '$pub' ~/.ssh/authorized_keys 2>/dev/null || echo '$pub' >> ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys
"@
Remove-SSHSession -SessionId $s.SessionId
```

### Isi ulang konfigurasi

`config/benchmark.yaml` di git berisi `null` dan `127.0.0.1` — kredensial
sengaja tidak ikut di-commit. Isi di VM:

```bash
cd ~/cache-benchmark
vi config/benchmark.yaml      # redis.host, redis.port, redis.username, redis.password
chmod 600 config/benchmark.yaml
```

Atau lewati file dan berikan lewat flag di tiap run:

```bash
./scripts/run.sh --redis-host <ip> --redis-port <port> \
                 --redis-username <user> --redis-password '<password>'
```

Kutip tunggal pada password itu penting kalau mengandung tanda seru. Tanpa itu
bash menafsirkannya sebagai history expansion dan password yang terkirim salah.

---

## Cheat sheet

```bash
cd ~/cache-benchmark

# run tunggal
./scripts/run.sh --operation GET --threads 16 --payload 1024 --duration 60

# campuran GET+SET: 20 % SET / 80 % GET
./scripts/run.sh --operation MIXED --set-percent 20 --threads 16 --duration 60

# override endpoint tanpa menyentuh config
./scripts/run.sh --redis-host 10.0.0.5 --redis-port 6379 --operation SET

# semua opsi
java -jar target/cache-benchmark.jar --help

# cek kesiapan
REDIS_HOST=<ip> REDIS_PORT=<port> HZ_HOST=<ip> ./scripts/preflight.sh

# matrix kecil, aman untuk lab berumur pendek
TARGETS="redis" OPERATIONS="GET" PAYLOADS="1024" THREADS="1 2 4 8 16 32 64" \
REPEATS=3 KEYS=100000 WARMUP=10 DURATION=30 ./scripts/run-matrix.sh

# matrix MIXED di beberapa rasio SET
TARGETS="redis" OPERATIONS="MIXED" SET_PERCENTS="10 20 50" PAYLOADS="1024" \
THREADS="8 16 32" REPEATS=3 KEYS=100000 WARMUP=10 DURATION=30 ./scripts/run-matrix.sh
```

MIXED melakukan preload seperti GET, jadi aturan `--preload false` di atas
berlaku juga untuknya.

### Run panjang wajib pakai nohup

VM ini tidak punya `tmux` maupun `screen`. Matrix penuh butuh sekitar 14 jam dan
akan mati begitu koneksi SSH putus. Lepaskan dari terminal:

```bash
cd ~/cache-benchmark
nohup ./scripts/run-matrix.sh > ~/matrix.log 2>&1 &
echo $!                       # catat PID-nya

tail -f ~/matrix.log          # pantau
ps -p <pid> && echo running || echo finished
```

### Ambil hasil sebelum lab mati

```bash
# dari mesin Windows
scp -i ~/.ssh/id_labserver -r \
  cloud_user@<hostname>.mylabserver.com:~/cache-benchmark/results \
  /d/vibe-coding/dummy-aplikasi-redis-hazlecast/results-<tanggal>
```

`results/summary.csv` berisi satu baris per run dan itulah yang dipakai untuk
menyusun tabel perbandingan.

---

## Hal yang mudah bikin tersandung

**Redis ada di IP privat.** `172.31.x.x` adalah alamat internal AWS, hanya bisa
dijangkau dari dalam VPC yang sama, tidak dari laptop. Semua pengujian koneksi
harus dijalankan dari VM benchmark. Kalau lab dibuat ulang, IP Redis kemungkinan
besar ikut berubah.

**Redis kemarin menerima koneksi tanpa autentikasi.** Server itu melayani PING
dan GET/SET tanpa AUTH sama sekali; `superadmin` hanya ACL user tambahan. Run
yang lupa mencantumkan kredensial tetap berhasil, jadi salah konfigurasi tidak
akan terasa.

**Password ACL butuh username.** `AUTH <password>` saja berarti login sebagai
user `default`, dan server yang punya ACL user bernama menolaknya dengan
`WRONGPASS`. Selalu sertakan `--redis-username` atau isi `redis.username`.

**Disk tinggal sekitar 1,8 GB.** Cukup untuk matrix penuh karena hasil dan log
hanya puluhan KB, tapi jangan menaruh file besar lain di sana.

**Hazelcast butuh cluster name yang persis sama** dengan konfigurasi member.
Kalau salah, client gagal setelah timeout, bukan menggantung. Default aplikasi
`dev`, sama dengan default Hazelcast.

**SELinux Enforcing.** Tidak mengganggu; aplikasi hanya membuka koneksi keluar
dan menulis di home directory.

**`sudo` minta password.** Tidak diperlukan untuk menjalankan benchmark.

---

## Troubleshooting

| Gejala | Penyebab | Solusi |
|---|---|---|
| `bad interpreter: /usr/bin/env bash^M` | script ter-checkout sebagai CRLF | kirim ulang lewat `git archive`, atau `sed -i "s/\r$//" scripts/*.sh` |
| `Permission denied` saat `./scripts/*.sh` | bit executable hilang | `chmod +x scripts/*.sh` |
| `PRELOAD_VALIDATION_FAILED: key benchmark:0 is missing` | `--preload false` padahal keyspace kosong | hapus flag itu, biarkan preload berjalan |
| `PRELOAD_VALIDATION_FAILED: ... holds 1024 bytes but the configured payload is 100 bytes` | keyspace diisi dengan payload ukuran lain | preload ulang dengan `--payload` yang sesuai |
| `WRONGPASS invalid username-password pair` | AUTH tanpa username di server ber-ACL | tambahkan `--redis-username` |
| `Unable to connect to any cluster` | cluster name Hazelcast salah, atau member mati | cocokkan `--hz-cluster` dengan konfigurasi member |
| `Benchmark failed: RedisConnectionException` | host/port salah, atau kredensial salah | jalankan `preflight.sh` dulu, baru periksa kredensial |
| exit code 2 | error rate melewati ambang, status `INVALID` | hasil mentah tetap tertulis; periksa blok `errors` di JSON |
| Throughput jauh di bawah harapan | generator yang mentok, bukan backend | lihat `cpu.processCpuLoadAvg` di JSON; mendekati 1.00 berarti VM ini batasnya |

### Arti exit code

```
0 = benchmark selesai, hasil VALID
1 = konfigurasi salah, backend tidak tersambung, atau preload gagal
2 = benchmark selesai tapi hasil INVALID (error rate di atas ambang)
```

---

## Status terakhir, 30 September 2026

| | |
|---|---|
| VM | `ed6d8e88ca1c.mylabserver.com` — RHEL 9.8, 4 vCPU, 15 GiB — **sudah expire** |
| Redis | `172.31.36.225:12000`, Redis 8.6.2 standalone, ACL user `superadmin` — terverifikasi |
| Hazelcast | belum ada endpoint |
| Commit ter-deploy | `de63e97` |
| Smoke run GET | 8 thread, 1 KB, 10 k key, 10 detik → 10.453 ops/sec, p99 2,886 ms, VALID |
| Smoke run SET | 8 thread, 1 KB, 10 k key, 10 detik → 8.733 ops/sec, p99 3,680 ms, VALID |

Angka di atas masih dibatasi latensi jaringan antar-VM, bukan kapasitas Redis.
Dengan p50 0,6 ms dan 8 thread closed-loop, langit-langit teoretisnya sekitar
13 k ops/sec dan kita sudah menyentuh 10,4 k. Naikkan jumlah thread untuk
melihat di mana kurvanya mulai datar.

Perbandingan Redis vs Hazelcast belum bisa dibuat sampai endpoint Hazelcast
tersedia.

### Catatan: VM sudah hilang

Beberapa menit setelah smoke run terakhir, hostname VM berhenti resolve dan IP
terakhirnya tidak lagi menerima koneksi. Lab-nya sudah dihancurkan.

Artinya, saat Anda kembali nanti, **Skenario B yang berlaku**, bukan A. Isi
`~/cache-benchmark` di VM lama sudah tidak ada, termasuk sembilan file hasil
smoke run yang belum sempat diunduh. Yang tersimpan tinggal apa yang ada di repo
git ini.

Konsekuensi praktisnya: unduh `results/` segera setelah run selesai, jangan
ditunda sampai sesi berikutnya.

---

## Update, 2 Oktober 2026

Catatan di atas ternyata keliru. `ed6d8e88ca1c.mylabserver.com` aktif lagi
dengan isi utuh, termasuk `results/`, jadi waktu itu kemungkinan lab hanya
dimatikan sementara, bukan dihancurkan. Tetap periksa hostname dulu sebelum
memilih Skenario A atau B.

| | |
|---|---|
| Ter-deploy | operation `MIXED` (`--set-percent`), dikirim dari working tree, belum di-commit |
| Konfigurasi | `config/benchmark.yaml` di VM dipertahankan; versi baru dari repo disimpan sebagai `config/benchmark.yaml.example` |
| `summary.csv` | baris lama 18 kolom dilengkapi menjadi 23 kolom (5 kolom MIXED kosong); aslinya di `results/summary.csv.bak-18col` |
| Smoke run MIXED | Redis, 20 % SET, 8 thread, 1 KB, 10 k key, 10 detik → 9.638 ops/sec (GET 7.716 + SET 1.921), p99 2,339 ms, VALID |
