# KasirKita POS Android - Project Status

Last verified: 2026-09-28

## Project Overview

Android client untuk KasirKita POS dengan package utama `com.kasirkita.pos`.

Tech stack utama:

- Kotlin
- Jetpack Compose
- MVVM / Clean Architecture
- Hilt
- Retrofit + OkHttp
- Room
- DataStore Preferences
- Navigation Compose

Backend development API untuk workaround emulator lokal saat ini:

```text
http://127.0.0.1:3000/
adb reverse tcp:3000 tcp:3000
```

Backend tidak menggunakan global `/api/v1` prefix. Semua endpoint Retrofit menggunakan root path backend.
Konfigurasi loopback tersebut hanya workaround development dan bukan keputusan networking production.

## Current Architecture

- `core`: infrastruktur bersama, yaitu Room database/DAO/entity, DataStore session, konstanta network, dan Bearer `AuthInterceptor`.
- `data`: Retrofit API, request/response DTO, local data source, mapping, serta implementasi repository yang mengakses network, Room, atau state in-memory.
- `domain`: model domain, kontrak repository, dan use case. Layer ini menjadi batas antara data dan presentation.
- `presentation`: state, Hilt ViewModel, Compose screen, dan Navigation Compose untuk setiap fitur.
- `di`: Hilt module untuk database/DAO, Retrofit/API, dan binding implementasi repository.

Project masih berupa satu Android application module. Folder di atas adalah package/layer di dalam module `app`, bukan Gradle module terpisah.

## Completed Modules

### Authentication

- Auth V2 menggunakan login berbasis PIN melalui store resolve, user selection, dan PIN login.
- Endpoint autentikasi aktif adalah `POST /auth/v2/store/resolve`, `POST /auth/v2/pin/login`, `POST /auth/v2/refresh`, `POST /auth/v2/logout`, dan `GET /auth/v2/me`.
- `DeviceIdProvider` membuat dan mempertahankan device ID yang stabil untuk device session backend.
- `AuthSessionDataStore` menjadi satu-satunya penyimpanan session autentikasi. Data yang disimpan mencakup identitas user, tenant, role, outlet, access token, refresh token, expiry, dan device ID.
- `AuthV2TokenProvider` menjadi satu-satunya bearer token provider untuk `AuthInterceptor`.
- `RefreshTokenCoordinator` menjalankan refresh token rotation secara single-flight dan menyimpan token pair baru.
- `AuthAuthenticator` menangani HTTP 401, mengecualikan endpoint login, refresh, dan logout dari refresh loop, lalu mengulang request dengan access token baru ketika refresh berhasil.
- Startup navigation memulihkan session Auth V2, memeriksa expiry access token, mencoba refresh bila diperlukan, dan kembali ke Store Login jika session tidak ada atau refresh gagal.
- Logout merevoke current device session bila backend dapat dijangkau, lalu selalu membersihkan session lokal, selected outlet, dan cart tanpa menghapus offline transaction queue.

### Outlet

- `GET /outlets` melalui Retrofit.
- Daftar outlet, selection state, `OutletViewModel`, dan `OutletScreen` tersedia.
- Outlet terpilih disimpan pada `StateFlow` di singleton `OutletRepository`.

Catatan: selected outlet masih in-memory dan belum dipersist ke DataStore atau Room.

### Shift

- `GET /shifts/current`.
- `POST /shifts/open`.
- `POST /shifts/{id}/close`.
- HTTP 404 dari current shift diperlakukan sebagai `NoShift`, bukan error.
- Current shift disimpan pada `StateFlow` repository.
- `ShiftViewModel` dan `ShiftScreen` mendukung membuka dan menutup shift.
- Shift berstatus `OPEN` meneruskan flow ke Home.

### Products

- `GET /products`.
- Cache produk menggunakan Room table `products`.
- Repository menggunakan cache-first: Room dibaca lebih dahulu dan API dipanggil ketika cache kosong.
- Manual refresh mengambil API dan mengganti cache Room.
- Field backend `track_stock` dipetakan melalui `ProductResponse`, Room `ProductEntity`, dan domain `Product` sebagai `trackStock`.
- Product cache menyimpan `trackStock`; produk cache lama dimigrasikan dengan nilai default `true`.
- `ProductViewModel` dan `ProductScreen` tersedia.
- Setiap produk aktif dapat ditambahkan ke cart melalui `AddToCartUseCase`.
- ProductScreen menyediakan tombol **Buka Cart** yang terhubung ke route Cart.
- ProductScreen menampilkan **Stok dikelola** atau **Tanpa pelacakan stok** tanpa mengekspos cost.

### Cart

- Cart menggunakan singleton repository dan `MutableStateFlow`.
- Mendukung add/remove item, increase/decrease quantity, clear cart, subtotal, total item, dan total nominal.
- `CartViewModel` dan `CartScreen` tersedia.
- Seluruh nilai uang menggunakan `Long`.

Catatan: cart masih in-memory dan akan hilang jika process aplikasi mati.

### Checkout

- `POST /transactions` menggunakan payment method `CASH`.
- Checkout memvalidasi cart, selected outlet, current shift `OPEN`, outlet shift, dan jumlah pembayaran.
- Checkout tidak melakukan validasi stok lokal; backend tetap menjadi source of truth untuk enforcement berdasarkan `track_stock`.
- `client_transaction_id` UUID dibuat sekali di `CheckoutViewModel` dan dipakai kembali apabila terjadi transport failure.
- Cart dibersihkan setelah transaksi server berhasil.
- Checkout online yang berhasil membuka route receipt menggunakan server transaction ID.

### Receipt

- `GET /receipts/{transaction_id}`.
- DTO backend `store` dipetakan menjadi tenant pada domain, sementara `totals` dipetakan ke nilai subtotal, discount, tax, dan total.
- Repository, use case, `ReceiptViewModel`, dan `ReceiptScreen` tersedia.
- Receipt menampilkan tenant/store, outlet, kasir, customer jika ada, item, quantity, harga, subtotal, discount, tax, payment method, total, cash received, dan change.
- `ReceiptViewModel` membaca transaction ID dari Navigation `SavedStateHandle`.

### Offline Transaction Queue

- Transaksi offline dipersist dalam Room table `offline_transactions`.
- Tersedia status lokal `PENDING`, `SYNCED`, dan `FAILED`.
- `clientTransactionId` memiliki unique index dan juga digunakan sebagai local record ID.
- Request transaksi disimpan sebagai immutable `payloadJson`.
- Payload offline tetap memakai kontrak transaksi yang sama dan sengaja tidak menyertakan `track_stock`.
- Record menyimpan server transaction ID, last error, retry count, serta created/updated timestamp.
- DAO menyediakan insert, pending query, pending count flow, mark synced, mark failed, increment retry, retry failed, dan delete synced.

### Manual Transaction Sync

- `POST /sync/transactions`.
- Hanya transaksi `PENDING` yang dikirim, maksimal 100 per batch.
- Payload queue dideserialisasi kembali menjadi `CreateTransactionRequest` tanpa membuat UUID baru.
- Response dicocokkan berdasarkan `client_transaction_id`, bukan posisi array.
- Result `SYNCED` menyimpan server transaction ID dan tidak dikirim ulang.
- Result `FAILED` menyimpan error backend dan menaikkan retry count.
- Network, HTTP batch, atau empty-body failure mempertahankan transaksi sebagai `PENDING` dan menaikkan retry count.
- Repository menyediakan `retryFailedTransactions()` untuk mengubah record `FAILED` kembali menjadi `PENDING` lalu mencoba sync.
- Home menampilkan pending count, tombol **Sync Sekarang**, dan ringkasan synced/failed/pending.

### Checkout Offline Fallback

- Checkout hanya masuk offline queue ketika `POST /transactions` gagal karena transport/network `IOException`.
- HTTP/business error tidak otomatis masuk queue.
- Online request dan offline payload menggunakan `client_transaction_id` yang sama.
- Online dan offline queue menggunakan builder `CreateTransactionRequest` yang sama agar payload konsisten.
- Cart hanya dibersihkan setelah server sukses atau insert Room berhasil.
- Jika insert Room gagal, cart dipertahankan dan error ditampilkan.
- Setelah queue berhasil, Checkout menampilkan bahwa transaksi tersimpan untuk sinkronisasi.

## Database

Room database saat ini menggunakan version **3** dengan `exportSchema = false`.

Tables:

- `products`: cache data produk.
- `offline_transactions`: persistence queue transaksi offline.

Migration yang tersedia:

- `MIGRATION_1_2`: membuat table `offline_transactions` beserta unique index `index_offline_transactions_clientTransactionId`.
- `MIGRATION_2_3`: menambahkan kolom `products.trackStock` (`INTEGER NOT NULL DEFAULT 1`) agar cache lama tetap diperlakukan sebagai tracked.

Database builder mendaftarkan `MIGRATION_1_2` dan `MIGRATION_2_3` secara eksplisit. Tidak ada destructive migration.

## Backend API Used

Endpoint yang saat ini digunakan Android:

```text
POST /auth/v2/store/resolve
POST /auth/v2/pin/login
POST /auth/v2/refresh
POST /auth/v2/logout
GET  /auth/v2/me

GET  /outlets

GET  /shifts/current
POST /shifts/open
POST /shifts/{id}/close

GET  /products

POST /transactions

GET  /receipts/{transaction_id}

POST /sync/transactions
```

Semua endpoint bisnis terautentikasi menerima Bearer access token melalui `AuthInterceptor`. Store resolve dan PIN login berjalan tanpa session sebelumnya. Refresh menggunakan refresh token, sedangkan logout merevoke current device session.

## Important Technical Decisions

1. Semua monetary values menggunakan `Long`; `Double` dan `Float` tidak digunakan untuk uang.
2. `client_transaction_id` dibuat satu kali per checkout dan tidak berubah saat queue atau retry.
3. Backend menjadi source of truth untuk transaksi dan idempotency.
4. Offline queue hanya digunakan untuk transport/network uncertainty, bukan HTTP business errors.
5. Transaksi offline berstatus `SYNCED` tidak dikirim ulang.
6. Payload transaksi tidak diubah setelah tersimpan di queue.
7. Product menggunakan Room cache dengan strategi cache-first dan manual refresh.
8. Cart masih in-memory menggunakan `StateFlow`.
9. Selected outlet masih in-memory menggunakan `StateFlow`.
10. `AuthSessionDataStore` menjadi satu-satunya storage session autentikasi dan menyimpan access token serta refresh token Auth V2.
11. Workaround emulator lokal saat ini menggunakan `http://127.0.0.1:3000/` dengan `adb reverse`; ini bukan konfigurasi production permanen.
12. Manual sync memproses maksimal 100 record `PENDING` dan mencocokkan hasil menggunakan client transaction ID.
13. Backend menjadi satu-satunya source of truth untuk validasi/decrement stok berdasarkan `Product.track_stock`; Android tidak menolak checkout berdasarkan stok lokal.
14. `track_stock` tidak ditambahkan ke online maupun offline transaction payload karena backend menyelesaikan aturan tersebut dari product ID.
15. `AuthV2TokenProvider` menjadi satu-satunya sumber bearer token untuk request terautentikasi.
16. Access token yang expired dipulihkan melalui refresh token rotation single-flight; kegagalan refresh membersihkan session dan mengembalikan aplikasi ke Store Login.

## Current Navigation Flow

Startup flow:

```text
Session Auth V2 tidak tersedia       -> Store Login
Session Auth V2 valid                -> Outlet Selection
Access token expired                 -> Refresh token
Refresh berhasil                     -> Outlet Selection
Refresh gagal                        -> Clear session -> Store Login

Store Login -> User Selection -> PIN Login -> Outlet Selection -> Shift
                                                       |-- tidak ada shift OPEN -> tetap di Shift
                                                       `-- shift OPEN -> Home
```

Main flow:

```text
Home
 |-- Products -> Add Product -> Cart
 |-- Cart -> Checkout
 |             |-- online success -> Receipt/{transactionId}
 |             `-- network failure + Room success -> offline confirmation
 `-- Shift
```

Manual sync dijalankan dari Home. Offline transaction yang berhasil disinkronkan belum otomatis membuka Receipt.

Shift route membedakan initial gate dari akses manajemen melalui Home. Initial
gate hanya melanjutkan ke Home bila shift berstatus `OPEN` dan `outletId` cocok
dengan selected outlet. Akses Home -> Shift tetap berada di ShiftScreen agar
shift aktif dapat diperiksa atau ditutup tanpa redirect kembali ke Home.

## Verification

Verifikasi terakhir pada 2026-09-19:

```text
./gradlew testDebugUnitTest
BUILD SUCCESSFUL in 33s
32 actionable tasks: 16 executed, 16 up-to-date

./gradlew connectedDebugAndroidTest
BUILD SUCCESSFUL in 1m 25s
76 actionable tasks: 39 executed, 37 up-to-date

./gradlew build
BUILD SUCCESSFUL in 2m 15s
108 actionable tasks: 21 executed, 87 up-to-date
```

Audit Hilt Compose pada 2026-09-17 memastikan seluruh sembilan call site
`@HiltViewModel` menggunakan `hiltViewModel()`. `MainActivity` tetap memakai
`@AndroidEntryPoint`, sedangkan `KasirKitaApplication` tetap memakai
`@HiltAndroidApp`. Verifikasi `testDebugUnitTest` dan full build berhasil; APK
debug juga lulus cold-start smoke test di emulator tanpa fatal exception.

Offline sync repository test suite:

```text
9 tests
0 skipped
0 failures
0 errors
```

Cart repository test suite:

```text
1 test
0 skipped
0 failures
0 errors
```

Navigation guard test suite:

```text
4 tests
0 skipped
0 failures
0 errors
```

Product stock contract test suite:

```text
3 unit tests
2 Room instrumentation tests
0 skipped
0 failures
0 errors
```

Test yang tersedia memverifikasi queue menghasilkan `PENDING`, payload request lengkap dengan client transaction ID yang sama, kegagalan persistence, hasil `SYNCED`/`FAILED`, response matching walaupun urutannya berubah, retensi record saat network failure, batch maksimum 100, record synced tidak dikirim ulang, duplicate product menaikkan quantity tanpa mengubah field produk, initial/manage Shift navigation guard dan outlet matching, mapping `track_stock` true/false, payload checkout tanpa field stok, migration default `true`, serta persistence nilai `false` pada schema baru.

## E2E Readiness Audit

Audit code-level terakhir dilakukan pada 2026-09-17 sebelum implementasi WorkManager.

### Optional Stock Tracking E2E: PASS

- Product `track_stock=false` berhasil dipetakan Backend -> Retrofit -> Room -> Domain -> UI.
- Online checkout dengan untracked product berhasil tanpa validasi stok di Android.
- Offline checkout berhasil menyimpan transaksi ke local queue.
- Setelah backend kembali tersedia, queued transaction berhasil disinkronkan.
- Tidak terjadi error `INSUFFICIENT_STOCK` untuk product dengan `track_stock=false`.
- Backend tetap menjadi source of truth untuk stock enforcement.

- Product -> Cart: terhubung melalui `ProductViewModel`, `AddToCartUseCase`, singleton `CartRepository`, tombol add, dan route Cart.
- Cart -> Checkout: item, quantity +/-, remove, subtotal/total, dan Checkout tersedia; tombol Checkout disabled ketika cart kosong.
- Checkout online: satu UUID dibuat di `CheckoutViewModel`, diteruskan ke `POST /transactions`, cart dibersihkan setelah sukses, lalu navigation membuka Receipt.
- Checkout offline: hanya transport `IOException` yang memicu queue; request online dan payload Room memakai client transaction ID serta builder yang sama.
- Persistence safety: hasil insert Room diperiksa. Conflict hanya dianggap aman jika record dengan client ID tersebut sudah ada dan payload JSON identik.
- Business errors: `HttpException` dan error non-transport tetap ditampilkan dan tidak dimasukkan ke offline queue.
- Manual sync: hanya `PENDING`, maksimum 100, matching berdasarkan client transaction ID, serta update `SYNCED`/`FAILED` dan server transaction ID telah terhubung.
- Idempotency: tidak ada UUID generation pada repository/use case/sync path; retry mengirim payload tersimpan dengan ID asli.
- Stock: Android offline queue tidak melakukan local stock decrement; backend tetap source of truth.
- Optional stock tracking: DTO/cache/domain Android telah kompatibel, transaction/offline payload tetap hanya mengirim product ID dan quantity, dan runtime E2E berstatus **PASS**.
- Shift/outlet: Checkout menolak shift yang tidak `OPEN` atau memiliki outlet berbeda dari selected outlet.
- Navigation guard: OPEN shift hanya mengarahkan initial Shift gate ke Home; membuka Shift dari Home tidak menjalankan redirect tersebut kembali.
- Receipt: online success tetap membuka Receipt; hasil manual sync hanya menyimpan server transaction ID dan tidak melakukan navigation otomatis.
- Status readiness: seluruh jalur telah tersambung pada level code dan unit test. Pengujian dengan emulator serta backend/database nyata masih diperlukan sebelum background sync.
- WorkManager/automatic sync tetap **belum diimplementasikan**.

## Known Limitations / Technical Debt

- Belum ada WorkManager atau background automatic sync.
- Belum ada network connectivity observer.
- Cart masih in-memory dan tidak bertahan setelah process death.
- Selected outlet masih in-memory dan harus dipilih kembali setelah process recreation.
- Belum ada Transaction History pada Android.
- Belum ada Customer module pada Android.
- Belum ada Reports dashboard/UI pada Android.
- Belum ada printer atau receipt printing integration.
- Offline PIN verification belum tersedia.
- Logout hanya merevoke current device session; session user pada perangkat lain tidak ikut direvoke.
- Checkout menampilkan HTTP business error melalui `HttpException.message`; parsing error body backend menjadi pesan yang lebih spesifik belum tersedia.
- Retry untuk record `FAILED` tersedia pada repository, tetapi belum dihubungkan ke tombol atau queue-management UI.
- `deleteSynced()` tersedia pada DAO, tetapi belum ada retention/cleanup policy atau UI untuk record synced.
- Offline transaction yang selesai disinkronkan tidak otomatis membuka atau menyimpan receipt lokal.
- Room schema export masih dinonaktifkan; migration 2->3 sudah memiliki instrumentation test khusus.
- Product cache tidak difilter per tenant ketika dibaca; pergantian akun/tenant dapat membaca cache lama sebelum refresh.
- Offline queue tidak menyimpan tenant/user owner secara eksplisit; account switching perlu di-hardening sebelum digunakan pada perangkat bersama.
- Role guard sudah membatasi route manajemen dan reports untuk OWNER/ADMIN, sedangkan backend tetap menjadi authority final. Audit manual per role masih diperlukan untuk seluruh variasi UX bisnis.
- Jika current shift `OPEN` berasal dari outlet berbeda dengan outlet yang baru dipilih, ShiftScreen masih dapat meneruskan ke Home. Checkout tetap memblokir transaksi karena outlet mismatch, tetapi UX pemilihan outlet/shift perlu di-hardening.

## Next Milestones

1. End-to-end online/offline transaction testing.
2. WorkManager automatic sync.
3. Transaction History.
4. Customer Module.
5. Reports Dashboard.
6. Auth V2 follow-up hardening dan strategi offline PIN.
7. Printer/receipt printing.

## Rules For Future Development

- Update `PROJECT_STATUS.md` setelah milestone selesai.
- Jangan menandai fitur Completed sebelum build dan test berhasil.
- Jangan regenerate `client_transaction_id` ketika retry transaction.
- Jangan menggunakan destructive Room migration.
- Jangan mengubah backend contract tanpa kebutuhan dan koordinasi yang jelas.
- Reuse architecture, DTO, repository, use case, dan dependency injection yang sudah tersedia.
- Pertahankan `Long` untuk seluruh monetary values.
- Jalankan `./gradlew build` setelah perubahan signifikan.
