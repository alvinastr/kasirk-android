# KasirKita POS Android - Project Status

Last verified: 2026-09-17

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

Backend development API:

```text
http://10.0.2.2:3000/
```

Backend tidak menggunakan global `/api/v1` prefix. Semua endpoint Retrofit menggunakan root path backend.

## Current Architecture

- `core`: infrastruktur bersama, yaitu Room database/DAO/entity, DataStore session, konstanta network, dan JWT `AuthInterceptor`.
- `data`: Retrofit API, request/response DTO, local data source, mapping, serta implementasi repository yang mengakses network, Room, atau state in-memory.
- `domain`: model domain, kontrak repository, dan use case. Layer ini menjadi batas antara data dan presentation.
- `presentation`: state, Hilt ViewModel, Compose screen, dan Navigation Compose untuk setiap fitur.
- `di`: Hilt module untuk database/DAO, Retrofit/API, dan binding implementasi repository.

Project masih berupa satu Android application module. Folder di atas adalah package/layer di dalam module `app`, bukan Gradle module terpisah.

## Completed Modules

### Authentication

- `POST /auth/login` menggunakan email, password, dan tenant ID.
- Access token dan payload JWT (`sub`, `tenant_id`, `role`) diproses menjadi `UserSession`.
- Session disimpan menggunakan DataStore Preferences: access token, tenant ID, user ID, dan role.
- `AuthInterceptor` menambahkan `Authorization: Bearer <token>` ketika token tersedia.
- `LoginViewModel` dan `LoginScreen` telah tersedia.
- Session/navigation guard memilih Login atau Outlet berdasarkan keberadaan access token.

Catatan: guard saat ini hanya memeriksa apakah token tersedia dan tidak kosong; expiry atau validitas JWT belum diperiksa.

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
- `ProductViewModel` dan `ProductScreen` tersedia.

### Cart

- Cart menggunakan singleton repository dan `MutableStateFlow`.
- Mendukung add/remove item, increase/decrease quantity, clear cart, subtotal, total item, dan total nominal.
- `CartViewModel` dan `CartScreen` tersedia.
- Seluruh nilai uang menggunakan `Long`.

Catatan: cart masih in-memory dan akan hilang jika process aplikasi mati.

### Checkout

- `POST /transactions` menggunakan payment method `CASH`.
- Checkout memvalidasi cart, selected outlet, current shift `OPEN`, outlet shift, dan jumlah pembayaran.
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

Room database saat ini menggunakan version **2** dengan `exportSchema = false`.

Tables:

- `products`: cache data produk.
- `offline_transactions`: persistence queue transaksi offline.

Migration yang tersedia:

- `MIGRATION_1_2`: membuat table `offline_transactions` beserta unique index `index_offline_transactions_clientTransactionId`.

Database builder mendaftarkan `MIGRATION_1_2` secara eksplisit. Tidak ada destructive migration.

## Backend API Used

Endpoint yang saat ini digunakan Android:

```text
POST /auth/login

GET  /outlets

GET  /shifts/current
POST /shifts/open
POST /shifts/{id}/close

GET  /products

POST /transactions

GET  /receipts/{transaction_id}

POST /sync/transactions
```

Semua endpoint terautentikasi menerima Bearer JWT melalui `AuthInterceptor`, kecuali login yang tetap berjalan tanpa header ketika token belum tersedia.

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
10. JWT session disimpan menggunakan DataStore Preferences.
11. Backend Android Emulator menggunakan `http://10.0.2.2:3000/`.
12. Manual sync memproses maksimal 100 record `PENDING` dan mencocokkan hasil menggunakan client transaction ID.

## Current Navigation Flow

Startup flow:

```text
Access token tidak tersedia -> Login
Access token tersedia       -> Outlet Selection

Login -> Outlet Selection -> Shift
                              |-- tidak ada shift OPEN -> tetap di Shift
                              `-- shift OPEN -> Home
```

Main flow:

```text
Home
 |-- Products
 |-- Cart -> Checkout
 |             |-- online success -> Receipt/{transactionId}
 |             `-- network failure + Room success -> offline confirmation
 `-- Shift
```

Manual sync dijalankan dari Home. Offline transaction yang berhasil disinkronkan belum otomatis membuka Receipt.

## Verification

Verifikasi terakhir pada 2026-09-17:

```text
./gradlew build
BUILD SUCCESSFUL in 22s
108 actionable tasks: 26 executed, 82 up-to-date
```

Offline sync repository test suite:

```text
7 tests
0 skipped
0 failures
0 errors
```

Test yang tersedia memverifikasi queue menghasilkan `PENDING`, client transaction ID tetap sama, hasil `SYNCED`/`FAILED`, retensi record saat network failure, batch maksimum 100, dan record synced tidak dikirim ulang.

## Known Limitations / Technical Debt

- Belum ada WorkManager atau background automatic sync.
- Belum ada network connectivity observer.
- Cart masih in-memory dan tidak bertahan setelah process death.
- Selected outlet masih in-memory dan harus dipilih kembali setelah process recreation.
- ProductScreen belum memiliki tombol/aksi untuk menambahkan produk ke cart. Cart API dan ViewModel sudah mendukung add product, tetapi belum terhubung dari UI produk.
- Belum ada Transaction History pada Android.
- Belum ada Customer module pada Android.
- Belum ada Reports dashboard/UI pada Android.
- Belum ada printer atau receipt printing integration.
- Belum ada global handling untuk HTTP 401, token expiry, refresh token, atau forced re-login.
- `TokenDataStore.clearSession()` tersedia, tetapi belum ada logout repository/use case/UI/navigation flow yang lengkap.
- Retry untuk record `FAILED` tersedia pada repository, tetapi belum dihubungkan ke tombol atau queue-management UI.
- `deleteSynced()` tersedia pada DAO, tetapi belum ada retention/cleanup policy atau UI untuk record synced.
- Offline transaction yang selesai disinkronkan tidak otomatis membuka atau menyimpan receipt lokal.
- Room schema export masih dinonaktifkan dan belum ada instrumentation migration test khusus.
- Product cache tidak difilter per tenant ketika dibaca; pergantian akun/tenant dapat membaca cache lama sebelum refresh.
- Offline queue tidak menyimpan tenant/user owner secara eksplisit; account switching perlu di-hardening sebelum digunakan pada perangkat bersama.
- Session guard hanya mengecek token tidak kosong, bukan signature atau expiry JWT.

## Next Milestones

1. End-to-end online/offline transaction testing.
2. WorkManager automatic sync.
3. Transaction History.
4. Customer Module.
5. Reports Dashboard.
6. Authentication/session hardening.
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
