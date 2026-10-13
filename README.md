# Zack Game Space

Aplikasi Android native (Java) dengan UI neon biru-ungu dan fitur **auto tap** yang memakai
`AccessibilityService` + `dispatchGesture()`. Dilengkapi Game Library (daftar aplikasi, favorit,
pencarian, peluncur game) dan panel overlay melayang berisi crosshair target.

| Item | Nilai |
|---|---|
| Package / namespace | `com.zack.gamespace` |
| Bahasa | Java 17 (sintaks Java 8, tanpa Kotlin) |
| minSdk / targetSdk / compileSdk | 26 / 34 / 34 |
| Android Gradle Plugin | 8.5.2 |
| Gradle (wrapper) | 8.7 |
| Dependency | `androidx.appcompat:appcompat:1.6.1` |

## Build lewat GitHub Actions (tanpa Android Studio)

1. Buat repository baru di GitHub, lalu unggah isi folder project ini (bukan ZIP-nya, tapi isinya).
   Pastikan folder `.github/workflows/` ikut terunggah.
2. Push ke branch `main` (atau `master`). Workflow **Build APK** berjalan otomatis;
   bisa juga dijalankan manual lewat tab **Actions → Build APK → Run workflow**.
3. Setelah selesai, buka run tersebut dan unduh artifact **ZackGameSpace-debug-apk**.
   Isinya `app-debug.apk` yang sudah ditandatangani dengan debug key dan bisa langsung dipasang.

## Build lokal

Butuh JDK 17 dan Android SDK (platform 34).

```bash
./gradlew assembleDebug
# hasil: app/build/outputs/apk/debug/app-debug.apk
```

## Cara pakai di perangkat

1. Pasang APK, buka **Zack Game Space**, tekan **Aktifkan Layanan Aksesibilitas**, lalu aktifkan
   **Zack Auto Tap**.
2. **Android 13+ dan APK di luar Play Store:** bila opsi layanan abu-abu, buka
   *Info Aplikasi → Zack Game Space → menu ⋮ → Izinkan setelan terbatas*, lalu ulangi langkah 1.
3. Tekan **Tampilkan Panel Overlay**. Muncul crosshair dan panel kecil yang bisa digeser.
4. Buka game, geser crosshair ke tombol yang ingin ditap, lalu tekan **MULAI** di panel. **STOP** untuk berhenti.
5. Interval, durasi sentuhan, batas jumlah tap, dan jitter acak diatur dari layar utama.

## Cara kerja singkat

- `AutoTapService` mengirim tap lewat `dispatchGesture()` (butuh `canPerformGestures="true"` pada
  konfigurasi layanan). Tap berikutnya dijadwalkan setelah tap sebelumnya selesai; berhenti otomatis bila
  gesture gagal 5 kali berturut-turut (misalnya layar mati).
- Overlay memakai `TYPE_ACCESSIBILITY_OVERLAY`, jadi **tidak butuh izin "tampil di atas aplikasi lain"**.
  Saat auto tap berjalan, crosshair dibuat tidak bisa disentuh agar tap menembus ke game.
- Layanan **tidak membaca isi layar** (tidak meminta `canRetrieveWindowContent`) dan aplikasi tidak
  meminta izin internet.

## Catatan

- Sebagian game online melarang atau mendeteksi input otomatis. Pemakaian di game kompetitif/online bisa
  melanggar aturan game dan berisiko akun diblokir. Gunakan dengan bijak.
- APK debug cocok untuk pemakaian pribadi. Untuk rilis ke Play Store, tambahkan signing config release sendiri
  dan simpan keystore di GitHub Secrets (jangan di-commit).

## Struktur

```
app/src/main/java/com/zack/gamespace/
  MainActivity.java         layar utama: status, pengaturan, aksi
  GameLibraryActivity.java  daftar aplikasi, favorit, pencarian, peluncur
  AutoTapService.java       AccessibilityService + mesin tap
  OverlayController.java    crosshair + panel kontrol melayang
  TapSettings.java          penyimpanan pengaturan (SharedPreferences)
  ScreenUtil.java           ukuran layar fisik
app/src/main/res/           layout, drawable neon, strings, tema, konfigurasi aksesibilitas
.github/workflows/build-apk.yml
```
