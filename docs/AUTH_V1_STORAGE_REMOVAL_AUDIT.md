# Auth V1 Storage Removal Audit

## Informasi Audit

| Field | Nilai |
| --- | --- |
| Project | KasirKita POS Android |
| Tanggal audit | 2026-09-27 |
| Status | Completed - Historical migration record |
| Baseline | Working tree setelah Auth V2 Phase 6.2.1 |
| Scope | `TokenDataStore`, legacy session adapter, Hilt binding, dan `UserSession` |
| Metode | Pencarian referensi statis dengan `rg` dan inspeksi langsung source, test, serta dokumentasi |
| Perubahan source code | Tidak ada |

Folder build, metadata Git, dan cache Gradle tidak disertakan dalam pencarian.

## Ringkasan

Auth V2 tidak lagi bergantung pada storage Auth V1. Bearer token aktif hanya berasal dari `AuthSessionDataStore`, dan logout Auth V2 tidak lagi memanggil `LegacySessionCleaner`.

Satu dependency runtime terhadap `TokenDataStore` masih tersisa pada jalur rollback Auth V1:

```text
LoginScreen
  -> LoginViewModel
     -> LoginUseCase
        -> AuthRepository
           -> AuthRepositoryImpl
              -> TokenDataStore.saveSession()
```

`LegacySessionReader`, `LegacySessionCleaner`, dan `TokenDataStoreLegacySessionReader` sudah tidak mempunyai consumer. Ketiganya masih terdaftar melalui `SessionModule`, tetapi tidak digunakan oleh startup, network, logout, test, atau fitur bisnis. File adapter dan module tersebut aman dihapus bersama.

`TokenDataStore` belum aman dihapus. `AuthRepositoryImpl` masih memerlukannya ketika route rollback Auth V1 digunakan. Normal startup tidak membuka Auth V1, tetapi destination `Screen.Login` dan seluruh login stack V1 masih dikompilasi dalam aplikasi.

## Verifikasi Jalur Auth V2 Aktif

Bearer token:

```text
AuthInterceptor
  -> AuthTokenProvider
     -> AuthV2TokenProvider
        -> AuthSessionDataStore.getAccessToken()
```

Tidak ada fallback ke `TokenDataStore` pada jalur ini.

Logout lokal:

```text
LogoutViewModel
  -> AuthSessionDataStore.clearSession()
  -> OutletRepository.clearSelectedOutlet()
  -> CartRepository.clearCart()
```

Tidak ada pemanggilan `LegacySessionCleaner` pada jalur ini.

## Klasifikasi

| Komponen | Kategori | Status penghapusan |
| --- | --- | --- |
| `TokenDataStore` | A | Belum aman |
| `UserSession` | A | Belum aman |
| `LegacySessionReader` | C | Aman bersama adapter dan module |
| `LegacySessionCleaner` | C | Aman bersama adapter dan module |
| `TokenDataStoreLegacySessionReader` | C | Aman bersama interface dan module |
| `SessionModule` | C | Aman bersama legacy adapter |
| Referensi dokumentasi | B | Bukan dependency runtime |
| Referensi test | B | Tidak ditemukan |

Kategori:

- A: masih dipakai oleh source yang masuk ke runtime application.
- B: hanya ditemukan pada test atau dokumentasi.
- C: tidak memiliki consumer dan aman dihapus sebagai satu perubahan terkoordinasi.

## A. Masih Dipakai Runtime

### `app/src/main/java/com/kasirkita/pos/core/datastore/TokenDataStore.kt`

Pemanggil langsung:

- `AuthRepositoryImpl` memanggil `saveSession()` setelah login Auth V1 berhasil.
- `TokenDataStoreLegacySessionReader` memanggil `getSession()` dan `clearSession()`, tetapi adapter ini sudah tidak memiliki consumer.

Status: belum aman dihapus.

Alasan:

`AuthRepositoryImpl` masih menerima `TokenDataStore` melalui constructor injection. Menghapus class ini sekarang membuat binding `AuthRepositoryImpl` gagal dikompilasi oleh Hilt. Implementasi `AuthTokenProvider` pada class ini tidak lagi menjadikannya bearer-token provider aktif karena `NetworkModule` secara eksplisit menyediakan `AuthV2TokenProvider` sebagai `AuthTokenProvider`.

Status setiap operasi:

| Operasi | Status |
| --- | --- |
| `saveSession()` | Masih dipanggil oleh login Auth V1 |
| `getToken()` | Tidak memiliki caller aktif setelah Phase 6.2.1 |
| `getSession()` | Hanya dipanggil adapter legacy yang tidak memiliki consumer |
| `clearSession()` | Hanya dipanggil adapter legacy yang tidak memiliki consumer |

Refactor yang diperlukan sebelum penghapusan:

- Hapus dependency `TokenDataStore` dari `AuthRepositoryImpl` dan tentukan bahwa rollback Auth V1 hanya menghasilkan `UserSession` in-memory, atau hapus seluruh login stack Auth V1 pada fase terpisah.
- Jangan menghubungkan kembali `TokenDataStore` ke `AuthTokenProvider` aktif.

### `app/src/main/java/com/kasirkita/pos/data/repository/AuthRepositoryImpl.kt`

Pemanggil:

- `RepositoryModule` mengikat implementasi ini ke `AuthRepository`.
- `LoginUseCase` memakai `AuthRepository` secara tidak langsung.
- `LoginViewModel` memanggil `LoginUseCase` saat route Auth V1 dipakai.

Status: file tidak aman dihapus selama Auth V1 dipertahankan untuk rollback.

Alasan:

Repository masih menjadi implementasi login Auth V1 dan merupakan satu-satunya consumer aktif `TokenDataStore`. Dependency storage pada constructor dan pemanggilan `tokenDataStore.saveSession(session)` adalah blocker langsung untuk penghapusan legacy storage.

### `app/src/main/java/com/kasirkita/pos/domain/model/UserSession.kt`

Pemanggil langsung:

- `TokenDataStore`
- `LegacySessionReader`
- `AuthRepositoryImpl`
- `AuthRepository`
- `LoginUseCase`
- `LoginState`
- `LoginScreen`
- `AppNavigation`

Status: `UserSession` belum aman dihapus.

Alasan:

Model ini masih membawa hasil login pada flow rollback Auth V1 dan menjadi payload `NavigationSession.AuthV1`. Penghapusan legacy storage tidak mengharuskan model ini ikut dihapus jika Auth V1 tetap tersedia tanpa persistence.

File ini juga mendefinisikan `UserRole`. `UserRole` dipakai oleh Auth V2, role guard, navigation, dan berbagai test. File `UserSession.kt` tidak boleh dihapus sebagai satu file sebelum `UserRole` dipindahkan ke file terpisah.

### `app/src/main/java/com/kasirkita/pos/domain/repository/AuthRepository.kt`

Pemanggil:

- `LoginUseCase`
- `AuthRepositoryImpl`
- `RepositoryModule`

Status: tidak aman dihapus selama rollback Auth V1 dipertahankan.

Alasan:

Contract login masih mengembalikan `Result<UserSession>`. File ini tidak mengakses storage secara langsung, tetapi menjaga `AuthRepositoryImpl` dan `UserSession` tetap berada pada runtime graph.

### `app/src/main/java/com/kasirkita/pos/domain/usecase/LoginUseCase.kt`

Pemanggil:

- `LoginViewModel`

Status: tidak aman dihapus selama rollback Auth V1 dipertahankan.

Alasan:

Use case meneruskan hasil `UserSession` dari repository ke presentation. Ia tidak mengakses storage secara langsung.

### `app/src/main/java/com/kasirkita/pos/presentation/auth/LoginState.kt`

Pemanggil:

- `LoginViewModel`
- `LoginScreen`

Status: tidak aman dihapus selama rollback Auth V1 dipertahankan.

Alasan:

State sukses membawa `UserSession` ke callback navigation Auth V1.

### `app/src/main/java/com/kasirkita/pos/presentation/auth/LoginScreen.kt`

Pemanggil:

- Destination `Screen.Login` di `AppNavigation`.

Status: tidak aman dihapus selama destination rollback Auth V1 dipertahankan.

Alasan:

Screen meneruskan `UserSession` melalui `onLoginSuccess`. Normal startup Auth V2 tidak membuka destination ini, tetapi destination masih terdaftar dalam runtime navigation graph.

### `app/src/main/java/com/kasirkita/pos/presentation/navigation/AppNavigation.kt`

Referensi terkait:

- Import `UserSession`.
- `NavigationSession.AuthV1` membawa `UserSession`.
- `onAuthV1Authenticated()` menerima `UserSession`.
- Destination `Screen.Login` menampilkan `LoginScreen`.

Status: file tidak aman dihapus. Blok Auth V1 dapat dihapus pada fase cleanup terpisah.

Alasan:

File ini juga mengelola seluruh navigation Auth V2 dan fitur bisnis. Startup hanya memilih Auth V2, tetapi callback rollback Auth V1 masih tersedia.

## B. Hanya Test atau Dokumentasi

### Unit test

Tidak ada referensi terhadap `TokenDataStore`, `LegacySessionReader`, `LegacySessionCleaner`, `TokenDataStoreLegacySessionReader`, `SessionModule`, atau `UserSession` di bawah `app/src/test` setelah Phase 6.2.1.

Kesimpulan: tidak ada test fixture legacy yang perlu dipindahkan sebelum adapter dan module dihapus. Test Auth V2 startup, refresh, dan logout sudah tidak bergantung pada tipe legacy tersebut.

### `docs/AUTH_V1_CLEANUP_AUDIT.md`

Pemanggil: tidak ada. File ini hanya dokumentasi.

Status: jangan dihapus sebagai bagian dari storage cleanup.

Alasan:

Dokumen menyimpan hasil audit sebelum Phase 6.2.1. Beberapa pernyataan di dalamnya sekarang bersifat historis, terutama fallback `CompositeAuthTokenProvider` dan dependency `LegacySessionCleaner` pada logout. Dokumen dapat diberi catatan superseded pada pekerjaan dokumentasi terpisah.

### `docs/PROJECT_STATUS.md`

Pemanggil: tidak ada. File ini hanya dokumentasi.

Status: jangan dihapus.

Alasan:

Dokumen menyebut `UserSession` dan `TokenDataStore` pada catatan implementasi Auth V1. Referensi tersebut tidak membentuk dependency runtime. Statusnya perlu diperbarui setelah legacy storage benar-benar dihapus.

## C. Aman Dihapus

### `app/src/main/java/com/kasirkita/pos/core/datastore/LegacySessionReader.kt`

Isi file:

- `LegacySessionReader`
- `LegacySessionCleaner`
- `TokenDataStoreLegacySessionReader`

Pemanggil saat ini:

- Tidak ada consumer untuk `LegacySessionReader`.
- Tidak ada consumer untuk `LegacySessionCleaner`.
- `TokenDataStoreLegacySessionReader` hanya disebut oleh binding di `SessionModule`.

Status: aman dihapus bersama `SessionModule.kt`.

Alasan:

Startup Auth V2 membaca `AuthSessionDataStore` secara langsung. Logout Auth V2 juga sudah membersihkan `AuthSessionDataStore` secara langsung. Adapter ini tidak lagi berada pada jalur startup, bearer token, logout, test, atau fitur bisnis.

### `app/src/main/java/com/kasirkita/pos/di/SessionModule.kt`

Pemanggil:

- Hilt menemukan module ini melalui `@InstallIn(SingletonComponent::class)`.
- Module mengikat `TokenDataStoreLegacySessionReader` ke dua interface legacy.
- Tidak ada injection site yang meminta kedua interface tersebut.

Status: aman dihapus bersama `LegacySessionReader.kt`.

Alasan:

Binding masih ikut dalam kompilasi production component, tetapi tidak memiliki dependent binding atau entry point. Menghapus module dan tipe yang diikat secara bersamaan tidak memutus consumer.

## Blocker Penghapusan `TokenDataStore`

Satu blocker source tersisa:

```text
AuthRepositoryImpl
  constructor -> TokenDataStore
  login() -> tokenDataStore.saveSession(session)
```

Selama dua referensi tersebut masih ada, `TokenDataStore.kt` tidak aman dihapus.

`UserSession` bukan blocker langsung untuk penghapusan storage. Model tersebut dapat tetap digunakan oleh rollback Auth V1 setelah repository tidak lagi melakukan persistence. Namun, `UserSession` belum aman dihapus sebagai model karena flow Auth V1 masih dikompilasi.

## Risiko Data Instalasi Lama

`TokenDataStore` memakai Preferences DataStore bernama `auth_session`. Menghapus class Kotlin tidak menghapus data tersebut dari instalasi yang sudah ada. File preferences legacy akan tetap berada di perangkat sebagai data yatim.

Jika tujuan cleanup juga mencakup penghapusan token lama dari perangkat, diperlukan satu kali cleanup yang menghapus isi `auth_session` sebelum kode akses legacy dihapus. Jika cleanup hanya bertujuan menghilangkan dependency runtime, data yatim tersebut tidak akan dibaca lagi tetapi tetap tersimpan.

## Urutan Cleanup yang Aman

1. Putuskan apakah rollback Auth V1 masih harus menyimpan session. Jika tidak, hapus dependency dan pemanggilan `TokenDataStore` dari `AuthRepositoryImpl`.
2. Hapus `SessionModule.kt` dan `LegacySessionReader.kt` dalam perubahan yang sama.
3. Pastikan tidak ada hasil pencarian untuk `TokenDataStore` selain definisi dan dokumentasi.
4. Jika token legacy harus dibersihkan dari perangkat, jalankan strategi cleanup satu kali sebelum menghapus `TokenDataStore.kt`.
5. Hapus `TokenDataStore.kt`.
6. Pertahankan `UserSession` selama Auth V1 masih tersedia. Pindahkan `UserRole` ke file sendiri sebelum file `UserSession.kt` dihapus pada fase berikutnya.
7. Jalankan `testDebugUnitTest`, full build, lint, dan pengujian emulator untuk startup, refresh, logout, serta authenticated API Auth V2.

## Kesimpulan

Phase 6.2.1 berhasil memutus Auth V2 dari legacy storage. Adapter `LegacySessionReader/Cleaner` dan `SessionModule` sekarang aman dihapus. `TokenDataStore` belum aman dihapus karena `AuthRepositoryImpl` masih menyimpan session Auth V1. Setelah dependency tersebut diputus, legacy storage dapat dihapus tanpa mengubah bearer token, startup, refresh, logout, atau fitur bisnis Auth V2.
