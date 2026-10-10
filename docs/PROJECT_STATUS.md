# KasirKita POS Android - Project Status

Last verified: 2026-10-08 (M16 Held Orders complete after focused/full unit tests, production release build, and physical-device QA)

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

#### Phase 3: Fresh Auth NavHost Race Fix (commit 5fdd394)

- `SessionState.Authenticated` menambahkan flag `requiresOperationalSetup: Boolean`.
- Fresh Auth V2 login: `onAuthV2Authenticated(session)` → `Authenticated(session, requiresOperationalSetup = true)` → `startupRouteFor` → Outlet.
- Cold-start/restored session: `checkSession()` → `Authenticated(session, requiresOperationalSetup = false)` → `startupRouteFor` → Home.
- Parent NavHost startDestination recomposition race dihilangkan; fresh login tidak lagi melewati Outlet selection.

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
- Tersedia status lokal `PENDING`, `SYNCED`, `FAILED`, dan recovery/action-required untuk business rejection.
- `clientTransactionId` memiliki unique index dan juga digunakan sebagai local record ID.
- Request transaksi disimpan sebagai immutable `payloadJson`.
- Payload offline tetap memakai kontrak transaksi yang sama dan sengaja tidak menyertakan `track_stock`.
- Record menyimpan server transaction ID, last error, retry count, serta created/updated timestamp.
- DAO menyediakan insert, pending query, pending count flow, mark synced, mark failed, increment retry, retry failed, dan delete synced.
- Backend business rejection saat sync, misalnya stok tidak cukup, tidak dibuang diam-diam; transaksi masuk flow Offline Recovery.

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

### M16 — Held Orders

Status: **CLOSED — Held Orders workflow complete on 2026-10-08 after automated verification and physical-device QA.**

Implementation commit: `d477cc6f05c5d01858242efffc4e3d973c4b8834` (`fix(android): repair held orders navigation and state sync`).

#### Implementation Summary

- Embedded POS Cart can navigate to Held Orders without leaving stale destination-scoped cart state behind.
- Held Orders list parses backend summary responses that omit detail-only `items`; detail responses still map item/modifier rows for open/restore.
- Save, list, open/restore, and cancel workflows are wired through the network-only Held Order API and shared cart state.
- HTTP 401 and 403 are classified safely as authentication/access errors instead of generic unknown failures.
- Product, Cart, and Held Orders destinations share one `CartViewModel`, so Held Orders badge state synchronizes immediately after save and cancel.

#### Verification

```text
./gradlew :app:testDebugUnitTest --tests '*HeldOrder*' --rerun-tasks
PASS — 131 tests, 0 failures, 0 errors, 0 skipped

./gradlew :app:testDebugUnitTest --rerun-tasks
PASS — 643 tests, 0 failures, 0 errors, 0 skipped

./gradlew :app:assembleRelease -Pkasirkita.releaseApiBaseUrl=https://api.bekasirk.tech/
PASS

Physical-device QA
PASS — navigation, save, list, open/restore, cancel, and badge synchronization
```

No backend source, production database, Room schema, offline transaction queue, printer behavior, signing configuration, APK/build output, or credentials were changed for this documentation closure. Receipt Template Settings / Receipt V2 remains planned follow-up work and is not marked implemented.

### Phase 3 Offline Reliability Closure

Phase 3 dinyatakan selesai pada 2026-10-01 berdasarkan unit/build verification dan manual device regression pada Android emulator.

Important Phase 3 commits/milestones:

- `fff89ee feat: enable offline product catalog` — Room-backed product cache untuk penggunaan offline.
- `326a237 feat: persist offline operational context` — outlet/shift operational context dipersist lokal.
- `3a22de0 fix: harden operational context persistence boundaries` — persistence boundaries diperketat.
- `344fd19 fix: restore offline operational context from session state` — cold-start restore dari session state.
- `aebe874 fix: await operational context persistence writes` — write persistence outlet/shift ditunggu sebelum navigation.
- `2d9fbdd fix: await operational context persistence completion` — completion-event semantics untuk persistence sebelum route lanjut.
- `75387be fix: recover operational setup navigation` — Shift null-outlet recovery dan operational setup navigation diperbaiki.
- `5fdd394 fix: distinguish fresh auth operational setup` — fresh Auth V2 dibedakan dari restored session untuk mencegah NavHost race ke Home.

Manual device regression evidence:

1. Fresh Auth V2 login berhasil menampilkan Outlet selection, lalu Outlet Utama → Shift → Home.
2. Setelah outlet/shift dikonfirmasi, DataStore berisi `auth_v2_session.preferences_pb` dan non-empty `operational_context.preferences_pb`.
3. Process death via `adb shell am force-stop com.kasirkita.pos` memulihkan session, Outlet Utama, dan shift `OPEN` tanpa `pm clear`.
4. Offline cold start setelah persistence memulihkan session, outlet, active shift, opening cash `Rp500.000`, dan status `OPEN`.
5. Product catalog tersedia offline setelah Products pernah berhasil dibuka online dan cache Room terisi.
6. Offline checkout menyimpan transaksi lokal, menampilkan "Transaksi tersimpan untuk sinkronisasi", membuat stable client transaction ID, mengosongkan cart setelah persistence aman, dan Home menampilkan pending sync count.
7. WorkManager automatic sync sukses tanpa tombol manual setelah internet kembali; transaction history menampilkan server transaction ID, status `COMPLETED`, cash payment, dan totals benar.
8. Business failure path saat stock backend tidak cukup menghasilkan Offline Recovery: "Transaksi ditolak" dan "Stok tidak mencukupi saat transaksi disinkronkan." Retry/delete tersedia. Record tidak dibuang diam-diam.

Intentional behavior / limitations retained:

- Product cache membutuhkan minimal satu successful online product load sebelum offline pertama pada fresh installation/data state.
- Offline tracked products dengan unknown stock boleh dijual lokal; backend tetap authoritative dan dapat reject saat sync.
- Backend business rejection saat sync harus tetap terlihat di Offline Recovery, bukan dihapus diam-diam.
- Receipt/struk UX setelah offline checkout bukan blocker Phase 3. Server-backed transaction detail tersedia setelah successful sync; receipt UX dapat ditinjau terpisah.
- Cart tetap in-memory dan bukan bagian Phase 3 persistence guarantee.
- Offline PIN verification belum tersedia.

## Database

Room database saat ini menggunakan version **4** dengan `exportSchema = false`.

Tables:

- `products`: cache data produk, keyed per tenant.
- `offline_transactions`: persistence queue transaksi offline, scoped per tenant/user/client transaction.

Migration yang tersedia:

- `MIGRATION_1_2`: membuat table `offline_transactions` beserta unique index `index_offline_transactions_clientTransactionId`.
- `MIGRATION_2_3`: menambahkan kolom `products.trackStock` (`INTEGER NOT NULL DEFAULT 1`) agar cache lama tetap diperlakukan sebagai tracked.
- `MIGRATION_3_4`: memindahkan `products` ke primary key `(tenantId, id)` dan memindahkan `offline_transactions` ke primary key `(tenantId, userId, clientTransactionId)` dengan index status/createdAt per account.

Database builder mendaftarkan `MIGRATION_1_2`, `MIGRATION_2_3`, dan `MIGRATION_3_4` secara eksplisit. Tidak ada destructive migration.

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
Session Auth V2 tidak tersedia       -> Auth V2 Graph (Store Login)
Session Auth V2 valid                -> Outlet Selection (cold-start returning user)
Access token expired                 -> Refresh token
Refresh berhasil                     -> Outlet Selection
Refresh gagal                        -> Clear session -> Auth V2 Graph

Store Login -> User Selection -> PIN Login -> Outlet Selection -> Shift
                                                       |-- tidak ada shift OPEN -> tetap di Shift
                                                       `-- shift OPEN -> Home

Fresh Auth V2 login (new in Phase 3):
Auth V2 Graph -> User Selection -> PIN Login -> Outlet Selection (requiresOperationalSetup=true)
                                                  `-- Outlet Selection -> Shift -> Home
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
- Status readiness: seluruh jalur telah tersambung pada level code, unit test, dan manual device regression.
- WorkManager/automatic sync telah diimplementasikan dan diverifikasi pada success path serta backend business-rejection path.

## Phase 4 — Security Hardening

Status: **CLOSED — automated security hardening and manual Android emulator regression complete.**

### Automated Regression: PASS

Executed verification:

```text
./gradlew testDebugUnitTest
./gradlew testDebugUnitTest --tests '*Auth*' --tests '*Session*' --tests '*Token*' --tests '*Refresh*' --tests '*Operational*' --tests '*Offline*' --tests '*Work*' --tests '*Product*'
./gradlew assembleDebug assembleRelease -Pkasirkita.releaseApiBaseUrl=https://example.com/
./gradlew build -Pkasirkita.releaseApiBaseUrl=https://example.com/
git diff --check
```

Expected negative release-config tests:

```text
./gradlew assembleRelease -Pkasirkita.releaseApiBaseUrl=http://example.com/
# rejected: Release API URL must use HTTPS

./gradlew assembleRelease -Pkasirkita.releaseApiBaseUrl=malformed://bad
# rejected: Release API URL must end with '/'
```

### Manual Device Regression: PASS

Android emulator regression completed successfully with existing app data preserved. Online cashier authentication succeeded; Outlet Utama, open shift, and opening cash Rp500.000 restored after force-stop. Products loaded online and cached products remained usable offline. Offline cash transaction queued with UI message "Transaksi tersimpan untuk sinkronisasi"; pending sync changed 0 → 1. After connectivity returned, WorkManager synchronized automatically without manual sync; pending sync changed 1 → 0 and synchronized Rp5.000 transaction appeared in history. Logout returned to authentication/store-entry flow; force-stop and relaunch remained logged out.

An existing unrelated "Perlu tindakan: 1" recovery item predated testing and remained separate from this regression. No `pm clear`, app-data clear, database clear, or uninstall was used; force-stop only. Destructive cloud-backup/device-transfer restore testing was **not** performed and remains post-MVP validation.

### 4A Network Security

- Debug builds keep development HTTP support for emulator/local endpoints through debug source-set network security config.
- Release builds require HTTPS-only API base URL.
- Release build-time URL validation rejects HTTP and malformed base URLs.
- Release merged manifest references `@xml/network_security_config` and does not enable cleartext traffic.

### 4B Secure Token Storage

- `access_token` and `refresh_token` persist encrypted via Android Keystore.
- Encryption uses AES-256-GCM with fresh IV per encrypted value.
- Stored encrypted format remains versioned: `v1:<base64-iv>:<base64-ciphertext>`.
- Legacy plaintext token pairs migrate to encrypted storage, then legacy keys are removed.
- Partial/mixed/corrupt/unsupported token states fail closed.
- `clearSession` removes encrypted and legacy tokens while preserving stable plaintext `device_id`.
- Stale refresh-token and account replacement protections remain intact.
- `CancellationException` is rethrown, and `sessionFlow` cannot enter repeated decrypt-crash loop.

### 4C Backup Protection

- `backup_rules.xml` protects legacy full-backup behavior.
- `data_extraction_rules.xml` protects Android 12+ cloud backup and device transfer.
- DataStore directory `files/datastore/` is excluded, covering `auth_v2_session`, `operational_context`, and `device_id` stored through DataStore.
- Room database files are excluded: `kasirkita.db`, `kasirkita.db-wal`, and `kasirkita.db-shm`.
- Merged debug and release manifests reference both backup rule resources.

### 4D Logging & Privacy

- Debug network logging is `HttpLoggingInterceptor.Level.BASIC`.
- Release network logging is `HttpLoggingInterceptor.Level.NONE`.
- `Authorization` header remains redacted.
- No `Level.BODY`, `Level.HEADERS`, `Log.*`, `println`, or `printStackTrace` remain in `app/src/main`.
- No intentional logging of tokens, PIN/passwords, request/response payloads, or crypto material found.

### 4E Release Security Audit

- Release build is non-debuggable.
- Release cleartext traffic remains disabled.
- Backup/data extraction rules remain active.
- Exported components reviewed and justified:
  - `MainActivity`: launcher activity.
  - `SystemJobService`: WorkManager, protected by `android.permission.BIND_JOB_SERVICE`.
  - `DiagnosticsReceiver`: AndroidX diagnostic receiver, protected by `android.permission.DUMP`.
  - `ProfileInstallReceiver`: AndroidX profile installer receiver, protected by `android.permission.DUMP`.
- Permissions reviewed and justified: `INTERNET`, `WAKE_LOCK`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`, and app signature dynamic-receiver permission.
- No unnecessary dangerous permissions found.
- No signing secrets committed.
- Minification/resource shrinking intentionally deferred post-MVP to avoid R8/Gson/Retrofit/Hilt/Room release risk before full integration coverage.

### Manual Phase 4 Device Regression Checklist

Do not use `pm clear`, uninstall, clear cache, clear database, or destructive backup/restore commands. Force-stop is allowed.

1. Existing/fresh authentication succeeds.
2. Outlet selection succeeds.
3. Shift restores after force-stop.
4. Products load online.
5. Online API call succeeds.
6. Token/session continuity survives app relaunch and normal navigation.
7. Cached products remain available offline.
8. Offline cash transaction can be created.
9. Pending sync visibility appears for offline transaction.
10. Connectivity restoration detected after returning online.
11. WorkManager automatic sync runs.
12. Synced transaction appears in history.
13. Logout returns to login screen.
14. Relaunch after logout remains logged out.
15. Phase 4B check: normal restore/refresh/logout works without printing token values or token decryption errors in Logcat.

## Known Limitations / Technical Debt

- Destructive cloud-backup/device-transfer restore testing was not performed; backup rule exclusion logic verified in code/merged manifest but not with actual Google account backup/transfer on device.
- Minification/resource shrinking remains disabled for MVP; enable post-MVP after integration testing and explicit R8 keep-rule review for Gson `@SerializedName`, Room, Hilt, Retrofit, WorkManager, and Compose.
- Production signing configuration is required before Play Store distribution; release builds remain unsigned until local `keystore.properties` and production keystore are provided.
- Network connectivity observer khusus belum ada; WorkManager memakai `NetworkType.CONNECTED` dan app lifecycle/session observer untuk enqueue automatic sync.
- Cart masih in-memory dan tidak bertahan setelah process death.
- Selected outlet runtime state masih `StateFlow`; operational context persistence memulihkan outlet/shift melalui DataStore pada cold start, dan Phase 5I.1 memastikan outlet hanya aktif/persisted bila `outlet.tenantId == current authenticated session.tenantId`.
- Offline PIN verification belum tersedia.
- Logout hanya merevoke current device session; session user pada perangkat lain tidak ikut direvoke.
- Checkout menampilkan HTTP business error melalui `HttpException.message`; parsing error body backend menjadi pesan yang lebih spesifik belum tersedia.
- `deleteSynced()` tersedia pada DAO, tetapi belum ada retention/cleanup policy atau UI untuk record synced.
- Offline transaction yang selesai disinkronkan tidak otomatis membuka atau menyimpan receipt lokal.
- Room schema export masih dinonaktifkan; migration 3->4 dan account scoping perlu dipertahankan dalam regression coverage.
- Role guard sudah membatasi route manajemen dan reports untuk OWNER/ADMIN, sedangkan backend tetap menjadi authority final. Audit manual per role masih diperlukan untuk seluruh variasi UX bisnis.
- Jika current shift `OPEN` berasal dari outlet berbeda dengan outlet yang baru dipilih, ShiftScreen masih dapat meneruskan ke Home. Checkout tetap memblokir transaksi karena outlet mismatch, tetapi UX pemilihan outlet/shift perlu di-hardening.
- Receipt/struk UX pasca offline checkout belum menjadi UI terpisah; server-backed transaction detail tersedia setelah sync sukses.

## Phase 5B — UI Foundation / Design System

Status: **CLOSED — POS design foundation complete, manual visual smoke test passed.**

### Implementation Summary

Established KasirKita POS visual foundation with consistent color roles, typography hierarchy, spacing tokens, and reusable Compose components. Implementation completed 2026-10-02.

#### Color System

- **Semantic color roles** for light and dark modes
- Primary action green (#006C4C light, #77D9A7 dark)
- Surface, background, outline variants for hierarchy
- Error, success, warning containers with accessible contrast
- Dynamic color disabled by default to preserve KasirKita semantic palette

#### Typography Hierarchy

- **Material 3 complete scale** (headline, title, body, label)
- **POS semantic extensions**: screenTitle, sectionTitle, body, supporting, label, price
- Bold price emphasis for monetary hierarchy
- Consistent line heights and letter spacing

#### Spacing Tokens

- `KasirSpacing` object with 7 size steps (4dp–32dp)
- Screen padding (24dp), item gap (8dp), section gap (16dp)
- Large touch targets: 48dp button minimum height
- 12dp corner radius for modern soft UI

#### Reusable Components

Created in `ui/components/KasirComponents.kt`:

- **KasirPrimaryButton / KasirSecondaryButton** — action buttons with loading state
- **KasirTextField** — consistent input styling with prefix, error, supporting text
- **KasirCard** — surface container with border, padding, gap-spaced content
- **KasirTopBar** — centered title bar with optional nav/actions
- **PriceText / PriceDisplay** — monetary emphasis (bold titleLarge)
- **StatusBadge** — compact status indicator (4 tones: Neutral, Success, Warning, Error)
- **KasirLoadingState / KasirEmptyState / KasirErrorState** — shared presentation states

All components use Material 3 primitives without new dependencies.

#### Files Created

```
app/src/main/java/com/kasirkita/pos/ui/components/KasirComponents.kt    268 lines
app/src/main/java/com/kasirkita/pos/ui/theme/Spacing.kt                  23 lines
```

#### Files Modified

```
app/src/main/java/com/kasirkita/pos/ui/theme/Color.kt      43 additions, 11 deletions
app/src/main/java/com/kasirkita/pos/ui/theme/Theme.kt      55 additions, 41 deletions
app/src/main/java/com/kasirkita/pos/ui/theme/Type.kt       98 additions, 41 deletions
```

Total: 154 insertions, 52 deletions (net +102 lines).

#### Scope Verification

No business logic, navigation, ViewModel, repository, domain, data, or core changes. All modifications confined to `ui/theme` and new `ui/components` package. Existing screens (Auth, Home, Product, Cart, Checkout, Receipt, Outlet, Shift) continue using Material 3 primitives directly; new components available for adoption in Phase 5C.

Theme changes visible app-wide via MaterialTheme colors/typography:
- Primary action color changed purple → green
- Surface/background colors shifted to Kasir semantic palette
- Typography now uses complete Material 3 scale with POS semantic extensions

#### Verification

```text
./gradlew testDebugUnitTest
BUILD SUCCESSFUL in 34s
33 actionable tasks: 16 executed, 17 up-to-date

./gradlew assembleDebug
BUILD SUCCESSFUL in 7s
42 actionable tasks: 5 executed, 37 up-to-date

./gradlew assembleRelease -Pkasirkita.releaseApiBaseUrl=https://example.com/
BUILD SUCCESSFUL in 34s
53 actionable tasks: 15 executed, 38 up-to-date

Manual visual smoke test: PASS
```

No Mobile MCP required for this milestone. Device validation performed manually.

#### Visual Direction

- Modern Indonesian POS aesthetic
- Clean, professional, cashier-first
- High readability, large touch targets
- Strong price/action hierarchy
- Restrained color use
- Majoo-inspired UX (not 1:1 copy)

Design system intentionally simple and Compose-native. No over-engineered abstractions.

## Phase 5C — Home Screen Polish

Status: **COMPLETE — cashier-first Home redesign verified manually on emulator.**

Baseline Phase 5B commit: `a43726b` (`feat: establish POS UI design foundation`).

### Implementation Summary

- Cashier-first Home hierarchy implemented with prominent **Buka Kasir** action.
- Real authenticated user, selected outlet, and current shift context displayed from existing navigation/repository state; no fake or placeholder operational data added.
- Shift opening cash shown with Indonesian Rupiah formatting.
- Shift `openedAt` ISO timestamp formatted for Indonesian POS display: time for today, date and time for earlier shifts.
- Existing sync/offline state, navigation callbacks, and logout action retained; Home only reorganizes their presentation.

### Verification

```text
./gradlew testDebugUnitTest
PASS

./gradlew assembleDebug
PASS

./gradlew assembleRelease -Pkasirkita.releaseApiBaseUrl=https://example.com/
PASS

Manual emulator visual verification
PASS
```

Mobile MCP not used. Device validation performed manually.

### Scope Verification

Phase 5C changes confined to Home presentation, presentation wiring, shared UI component import, and milestone documentation:

- `presentation/home/HomeScreen.kt`
- `presentation/navigation/AppNavigation.kt`
- `ui/components/KasirComponents.kt`
- `docs/PROJECT_STATUS.md`

No business/domain/repository/data/core behavior changed. Auth/session behavior, tenant/account isolation, outlet/shift persistence, offline/sync behavior, transaction/payment/stock behavior, and existing navigation destinations remain unchanged.

## Phase 5D — Product / Kasir Screen Polish

Status: **COMPLETE — cashier-first Product/Kasir screen verified manually on Small_Phone emulator.**

### Implementation Summary

- Product/Kasir screen now uses a cashier-first workspace layout with local product discovery and persistent cart access.
- Local presentation-side search supports product name and SKU with case-insensitive matching from the already loaded product list.
- Product cards present content consistently in this order: product name, SKU, Rupiah price, stock state, and add-to-cart action.
- Stock presentation respects optional stock tracking:
  - tracked products show stock count and availability state such as **Tersedia** or **Habis**;
  - untracked products show **Stok tidak dikelola** / **Tanpa pelacakan** and are not marked out of stock based on numeric stock.
- Persistent live cart summary shows current cart quantity and total value, and provides **Lihat Keranjang** access.
- Empty cart action is disabled using the normal design-system disabled button state, and becomes enabled immediately when cart contains at least one item.
- Category filtering deferred because category names/list are not currently exposed to Product/Kasir presentation state; only product `categoryId` exists.

### Verification

```text
./gradlew testDebugUnitTest
PASS

./gradlew assembleDebug
PASS

./gradlew assembleRelease -Pkasirkita.releaseApiBaseUrl=https://example.com/
PASS

Manual Small_Phone emulator validation
PASS
```

Mobile MCP not used. Device validation performed manually.

### Scope Verification

Phase 5D changes are presentation-only and confined to:

- `presentation/product/ProductScreen.kt`
- `docs/PROJECT_STATUS.md`

No ViewModel, repository, data, domain, cart business logic, stock business logic, offline/sync behavior, authentication/session behavior, API, or database changes were made.

## Phase 5E — Cart Screen Polish

Status: **COMPLETE — cashier-first Cart review screen verified manually on Small_Phone emulator.**

### Implementation Summary

- Cart screen now uses card-based cart item presentation for a clearer cashier order review.
- Each cart item emphasizes product name, unit price, quantity, subtotal, remove action, and concise stock presentation.
- Quantity controls now render as clear stepper buttons with large touch targets while preserving existing increment/decrement behavior.
- Remove-item action remains visible and keeps the existing remove semantics.
- Optional-stock-aware presentation preserves tracked stock display and avoids misleading numeric stock constraints for untracked products.
- Subtotal and order total are visually emphasized using the Phase 5B monetary display components.
- Sticky Checkout summary remains accessible at the bottom of the screen without overlapping the cart list.
- Empty cart state now uses the Phase 5B design-system empty state with clear guidance.

### Verification

```text
./gradlew testDebugUnitTest
PASS

./gradlew assembleDebug
PASS

./gradlew assembleRelease -Pkasirkita.releaseApiBaseUrl=https://example.com/
PASS

Manual Small_Phone emulator validation
PASS

Checkout flow non-regression
PASS
```

Mobile MCP not used. Device validation performed manually.

### Scope Verification

Phase 5E changes are presentation-only and confined to:

- `presentation/cart/CartScreen.kt`
- `docs/PROJECT_STATUS.md`

No ViewModel, repository, domain, data, cart business logic, stock business logic, pricing calculation, checkout/payment logic, offline/sync behavior, authentication/session behavior, API, or database changes were made.

## Phase 5F — Checkout + Receipt Screen Polish

Status: **CLOSED — cashier-first Checkout and Receipt flow verified manually on Small_Phone emulator.**

### Implementation Summary

- Checkout screen now uses a clear payment-completion hierarchy with `KasirTopBar`, order summary, cart item rows, payment method section, cash input section, and bottom completion CTA.
- Payment/cash/change presentation is explicit: `Uang diterima`, insufficient-cash `Kurang`, and successful `Kembalian` use Indonesian customer-facing labels and the Phase 5B price components.
- Existing CASH payment behavior remains unchanged; the checkout and shift summaries present it as "Tunai" alongside QRIS and EDC.
- Receipt screen now presents transaction completion with a clear `Transaksi Berhasil` header, store/outlet/cashier context, readable item rows, totals, payment details, and bottom `Transaksi Baru` CTA.
- Transaction ID remains available but visually secondary in the receipt footer.
- Offline/pending-sync presentation is preserved; Checkout still shows queued transaction state without changing queue/sync logic.
- No new payment/business features were added.

### Verification

```text
./gradlew testDebugUnitTest
PASS

./gradlew assembleDebug
PASS

./gradlew assembleRelease -Pkasirkita.releaseApiBaseUrl=https://example.com/
PASS

Manual Small_Phone emulator validation
PASS
```

Mobile MCP not used. Device validation performed manually.

### Manual Small_Phone Validation Summary

- Checkout renders correctly with clear `Pembayaran` header, readable order summary, CASH method, `Uang diterima`, correct `Kembalian`, and usable `Selesaikan Transaksi` CTA.
- Receipt completes successfully with clear `Transaksi Berhasil`, store/outlet/cashier context, readable `Kopi Susu` item details, subtotal/total/payment/change correctness, secondary transaction ID, visible `Transaksi Baru` CTA, and verified scroll behavior.
- No visible clipping, horizontal overflow, or Small_Phone blocker found.

### Scope Verification

Phase 5F changes are presentation-only and confined to:

- `presentation/checkout/CheckoutScreen.kt`
- `presentation/receipt/ReceiptScreen.kt`
- `docs/PROJECT_STATUS.md`

No ViewModel, repository, domain, data, transaction calculation, payment calculation, offline queue/sync, idempotency, stock behavior, authentication/session, API, or database/schema changes were made.

## Phase 5G — Auth + Outlet + Shift Polish

Status: **CLOSED — cashier-first auth/outlet/shift entry flow verified manually on Small_Phone emulator.**

### Implementation Summary

- Store Login, User Selection, PIN Login, Outlet Selection, and Shift Setup presentation polished with Phase 5B design system.
- Store Login now uses KasirKita POS identity, clear store-code explanation card, persistent field label, helpful guidance, and no API/tenant/device exposure.
- User Selection now uses tappable user cards with name emphasis, role secondary, empty state support, and back-to-store fallback.
- PIN Login now uses clear user identity context card, 6-digit masked input with IME submission, and strong "Masuk" CTA.
- Outlet Selection now uses tappable outlet cards with name emphasis, address secondary, and consistent loading/error/empty states.
- Shift Setup now uses clear shift status (StatusBadge), emphasized opening cash (PriceText), "Shift belum dibuka" no-shift state, "Buka Shift" CTA, and active-shift context.
- windowInsetsPadding(safeDrawing) applied to auth/outlet screens for notch/gesture area compatibility.
- All 5 screens use KasirCard, KasirPrimaryButton, KasirTextField/OutlinedTextField with KeyboardActions, KasirLoadingState, KasirEmptyState, KasirErrorState for consistency.
- Indonesian cashier-facing labels preserved: "Kode toko", "Masuk", "Pilih outlet", "PIN 6 digit", "Shift belum dibuka", "Buka Shift".

### Verification

```text
./gradlew testDebugUnitTest
PASS

./gradlew assembleDebug
PASS

./gradlew assembleRelease -Pkasirkita.releaseApiBaseUrl=https://example.com/
PASS

git diff --check
clean

Manual Small_Phone emulator validation
PASS
```

Mobile MCP not used. Device validation performed manually.

### Manual Small_Phone Validation Summary

- Store Login renders correctly with clear KasirKita POS identity, store-code explanation, and guidance.
- User Selection renders correctly with tappable user cards and clear hierarchy.
- PIN Login renders correctly with user context and masked 6-digit input.
- Outlet Selection renders correctly with tappable outlet cards.
- Existing authenticated Home state restores correctly after force-stop.
- Outlet Utama remains restored.
- Active shift remains restored with opening cash Rp500.000.
- No visible overflow, clipping, broken CTA, or navigation blocker found.

### Scope Verification

Phase 5G changes are presentation-only and confined to:

- `presentation/authv2/StoreLoginScreen.kt`
- `presentation/authv2/UserSelectionScreen.kt`
- `presentation/authv2/PinLoginScreen.kt`
- `presentation/outlet/OutletScreen.kt`
- `presentation/shift/ShiftScreen.kt`
- `docs/PROJECT_STATUS.md`

Total: 398 insertions, 340 deletions.

No ViewModel, state model, repository, domain, data, AuthV2Navigation, authentication/session logic, tenant isolation, encrypted token storage, outlet/shift persistence logic, transaction/payment/stock logic, offline queue/sync, API, or database/schema changes were made.

## Phase 5H — Secondary Screen Polish

Status: **CLOSED — secondary operational screens polished and verified manually on Small_Phone.**

### Implementation Summary

- Transaction History, Transaction Detail, Reports, and Offline Recovery presentation were aligned with the Phase 5B design system.
- Transaction History now uses readable card-based rows, better price hierarchy, design-system loading/empty/error states, and removes raw outlet UUIDs from history rows.
- Phase 5H.1 cleanup removed the misleading `Pembayaran: Lihat detail` fallback; payment is shown only when the existing transaction payment method is available.
- Transaction Detail now uses clearer grouped sections for summary, items, totals, and payment while preserving transaction values and calculations exactly.
- Phase 5H.1 cleanup makes long transaction, outlet, and product IDs visually secondary and compact so they do not wrap badly on Small_Phone.
- Reports presentation uses design-system loading/error states, spacing, and stacked metrics. Reports remains role-gated for Cashier; RBAC was not changed to inspect it.
- Offline Recovery presentation uses design-system status badges and actions while preserving existing retry/delete/acknowledge semantics.
- Mobile MCP was not used. Device validation was performed manually.
- Phase 5I has not started.

### Verification

```text
./gradlew testDebugUnitTest
PASS

./gradlew assembleDebug
PASS

./gradlew assembleRelease -Pkasirkita.releaseApiBaseUrl=https://example.com/
PASS

git diff --check
clean

Manual Small_Phone validation
PASS
```

### Manual Small_Phone Validation Summary

- Transaction History is readable and scannable.
- Raw outlet UUID is no longer shown in history rows.
- No misleading `Pembayaran: Lihat detail` fallback remains.
- Transaction Detail long transaction/outlet/product IDs are secondary and no longer wrap badly.
- Detail screen scroll works.
- Payment values verified: method CASH, subtotal/total Rp36.000, paid Rp40.000, change Rp4.000.
- Offline Recovery validated PASS.
- Reports remains role-gated for Cashier; no RBAC or permission change was made to test it.
- No visible clipping, overflow, broken CTA, or blocker found.

### Scope Verification

Phase 5H changes are presentation-only and confined to:

- `presentation/transaction/TransactionHistoryScreen.kt`
- `presentation/transaction/TransactionDetailScreen.kt`
- `presentation/reports/ReportsScreen.kt`
- `presentation/offline/OfflineRecoveryScreen.kt`
- `docs/PROJECT_STATUS.md`

No ViewModel, state model, navigation, repository, domain, data, transaction/payment/offline behavior, business logic, API, or database/schema changes were made.

## Phase 5I — Final Release Candidate Audit

Status: **CLOSED — KasirKita MVP Release Candidate READY after automated verification and complete manual Small_Phone RC regression.**

### Release Candidate Declaration

Current Android build is declared as the **KasirKita MVP Release Candidate** after Phase 5I final RC audit and manual Small_Phone regression passed.

Important Phase 5I.1 security fix:

- `bad0978e714a7ead13dbbd4a95918f67b7cb08fb` (`fix: enforce outlet tenant isolation`) — `OutletRepositoryImpl.selectOutlet()` now fails closed unless `outlet.tenantId == current authenticated session.tenantId` before operational context persistence, selected-outlet update, or navigation success.

### Automated Verification: PASS

```text
./gradlew testDebugUnitTest
PASS

./gradlew assembleDebug
PASS

./gradlew assembleRelease -Pkasirkita.releaseApiBaseUrl=https://example.com/
PASS

git diff --check
clean
```

### Manual Small_Phone RC Regression: PASS

Manual emulator validation completed without Mobile MCP.

- Core online transaction flow: PASS — Cashier authenticated successfully, Outlet Utama selected/restored, active shift available, product catalog loaded, product search worked, cart quantity/add/remove worked, CASH checkout worked, receipt/success screen worked, transaction history and detail showed correct transaction/payment data, and no clipping/overflow/broken CTA/navigation blocker was observed.
- Operational persistence after force-stop: PASS — `adb shell am force-stop com.kasirkita.pos` preserved authenticated session, Outlet Utama, active shift, and operational context.
- Offline checkout: PASS — cached product catalog remained usable offline, checkout succeeded, UI displayed "Transaksi tersimpan untuk sinkronisasi", and pending synchronization became 1.
- Automatic synchronization: PASS — internet restore triggered automatic sync, pending synchronization returned 1 -> 0, synced transaction appeared in Transaction History, and no action-required item remained.
- Transaction history/detail: PASS — synced transaction visible, history remained readable/scannable, and detail/payment presentation remained correct.
- Logout persistence: PASS — logout completed, force-stop + relaunch remained logged out, and store-code login screen was shown.
- Cashier role-boundary: PASS — Cashier can access Produk, Cart, Shift, Masalah Sync, and Riwayat Transaksi; Laporan is disabled; no admin-only Product Management or Stock Adjustment access exposed. RBAC was not changed for testing.

Safety constraints respected: no `pm clear`, no uninstall, no app data clear, no cache clear, no database clear. Force-stop only.

### Phase 5 — UI/UX Polish

Status: **CLOSED — MVP cashier UI polish complete through final RC regression.**

Phase 5 delivered POS design foundation and cashier-first polish across Home, Product/Kasir, Cart, Checkout, Receipt, Auth, Outlet, Shift, Transaction History, Transaction Detail, Reports, and Offline Recovery. Phase 5I final audit closed the MVP RC after automated verification and complete manual Small_Phone regression passed.

### Post-MVP Notes Preserved

- Destructive backup/device-transfer validation remains post-MVP.
- Minification/resource shrinking remains deferred post-MVP pending R8/Gson/Retrofit/Hilt/Room/WorkManager/Compose keep-rule review and integration coverage.
- Production signing / Play Store distribution readiness remains required before store distribution.

## Phase 6A — Android Release Packaging & Signing Preparation

Status: **CLOSED — local signed APK verification PASS. Production signing READY.**

|- RC baseline remains `v0.1.0-rc1` at commit `bb9e7245f031124239e87e41477b3a2eedbdea01`.
|- Release signing reads credentials only from local, git-ignored `keystore.properties`.
|- `keystore.properties.example` documents required property names without credentials.
|- Production `.jks` / `.keystore` files and local signing property files are ignored by Git.
|- Invalid or incomplete local signing configuration fails during Gradle configuration with a clear error.
|- Without local signing configuration, `assembleRelease` remains a safe CI verification path and produces `app-release-unsigned.apk`.
|- Existing release API URL HTTPS validation remains active. Debug signing/configuration remains unchanged.
|- **Production signing key has been created locally**, stored outside repository at `/Users/alvin/kasirkita-release.jks`.
|- **`keystore.properties` now exists locally and is git‑ignored; its contents are never committed, printed, or modified by agent.**
|- **Local signed APK verification build completed: `assembleRelease` produced a signed APK.**
|- **apksigner verification PASS:** v2 signing true, 1 signer, RSA 2048-bit.

### Phase 6A Verification

```text
./gradlew testDebugUnitTest
PASS

./gradlew assembleDebug
PASS

# Unsigned CI verification path (keystore.properties absent)
./gradlew assembleRelease -Pkasirkita.releaseApiBaseUrl=https://example.com/
PASS — app-release-unsigned.apk produced

# Local signing verification (keystore.properties present)
./gradlew assembleRelease -Pkasirkita.releaseApiBaseUrl=https://example.com/
PASS — signed APK produced

app/build/outputs/apk/release/app-release.apk
present, signed, ~10M

# APK signature verification
apksigner verify -v app/build/outputs/apk/release/app-release.apk
Verified using v2 scheme (v1 scheme none, v3 scheme none, v4 scheme none)
SIGNATURE BLOCK VERIFIED

Signing details:
- v2 signing: true
- Number of signers: 1
- RSA 2048-bit
- Certificate SHA-256: f1b37763c7da2cf7d3e03c6cfe7b630887901acf08dd8e115063a00e872b193b

Signed APK SHA-256: e993365eda02735a09e62396d34205a08cfc7157f299a207ec09e03c4ca42fc0

git diff --check
clean
```

### Build Verification Notice

The signed verification APK was built with `-Pkasirkita.releaseApiBaseUrl=https://example.com/`. `https://example.com/` is **only a documentation placeholder domain** and is NOT a reachable service. This APK is **NOT production-distributable**; its API base URL still points to the placeholder domain. Before any production distribution the real backend HTTPS URL must replace the placeholder, and a production-distributable release build must be generated from that configuration.

## M18B — Record-only EDC Android Support

Status: **CLOSED — M18B implemented, pushed, installed, and physical QA PASS.**

- Checkout supports `CASH`, `QRIS`, and record-only `EDC`. EDC sends only `method: "EDC"`; it has no tender, change, provider, card, bank, gateway, or webhook data.
- EDC guidance requires the cashier to confirm success on the external machine before completing checkout. It uses the existing completion action and never opens the cash drawer.
- Supported V2 offline payloads persist and replay EDC method-only payments. Legacy RC2 remains CASH-only. Existing CASH and QRIS flows remain unchanged.
- Shift summaries expose separate Tunai, QRIS, and EDC totals. Older responses without `edc` map that bucket to zero; total sales and non-reconciling fields are unchanged.
- Receipts, transaction details, history, manual/automatic print, and reprint preserve `Metode: EDC` without tender or change fields.
- Android commit: `41bb2ad897d4ea2b3a0b08d8e52b3ab670cc0d24`.
- Production release: `https://api.bekasirk.tech/`, package `com.kasirkita.pos`, version `1.0` (versionCode `1`). Signed APK SHA-256: `5dcca7a5c5f6791d5c64a9fec80979ea852825e0842c86576eb546b8d3d0de98`; APK Signature Scheme v2 with exactly one signer; certificate SHA-256 `f1b37763c7da2cf7d3e03c6cfe7b630887901acf08dd8e115063a00e872b193b`.
- Online and offline EDC QA PASS: no tender or change input, no cash-drawer pulse, checkout success summary, auto/manual/history reprint, `Metode: EDC` without `Diterima` or `Kembalian`, separate shift EDC total, V2 queue persistence/replay, pending count returned to zero, exactly-once sync, and no duplicate transaction, payment, or print.
- Focused M18B 231/231, full unit 798/798, Kotlin compilation, `lintDebug`, debug/release builds, and diff checks passed. No card or other sensitive payment data is collected or stored. CASH and QRIS regression behavior remains preserved.
- Backend M18A is deployed and production EDC is available. M18 overall is CLOSED; no unrelated future milestone is marked complete.

## M19A — Adaptive Navigation Foundation

Status: **IMPLEMENTED — automated verification and tablet physical QA PASS; phone NavigationBar physical QA remains pending.**

- OWNER and ADMIN top-level destinations are Beranda, Kasir, Transaksi, Laporan, and Lainnya. CASHIER receives Beranda, Kasir, Transaksi, and Lainnya; inaccessible administrative destinations are not exposed.
- Phone uses one role-aware Material 3 `NavigationBar`; tablet/wide layouts use one app-wide `NavigationRail` at the established applicable 840dp boundary. The wide Kasir workspace preserves the product catalog and embedded cart, with no ProductScreen-owned duplicate rail.
- Shift entry is explicit and type-safe: `GATE` is used for startup, outlet, and shift-opening flows; `MANAGE` is used from Lainnya. Strict parsing rejects malformed modes without falling back to GATE.
- Home is the authenticated top-level anchor. First-shift and Home-absent navigation normalize to Home → Kasir or Home → the selected destination, while logout and session invalidation clear protected navigation history.
- Products, Cart, and Held Orders continue to share the single root-scoped `CartViewModel`; no global Held Orders badge or duplicate state owner was introduced.
- Home and Lainnya use bounded, operational layouts. Light and dark themes use semantic surfaces and KasirKita green for selected navigation and primary actions; M19A adds no dashboard metrics, charts, product media, or decorative content.

### Verification

- Focused UI/navigation policy tests: **55 passed**.
- Full `:app:testDebugUnitTest`: **816 passed**, with zero failures, errors, or skipped tests.
- `lintDebug`, debug build, and signed release build passed. The release package remains `com.kasirkita.pos`, the API URL remains `https://api.bekasirk.tech/`, and the signer certificate SHA-256 remains `f1b37763c7da2cf7d3e03c6cfe7b630887901acf08dd8e115063a00e872b193b`.

### Physical QA

- Tablet QA on RR2N50016ZZ (1200×2000, 213 dpi; approximately 901dp portrait) passed in system light and dark modes for Home, Kasir, Lainnya, selected green NavigationRail state, bounded layouts, Shift management, top-level/back behavior, repeated destination selection, cart persistence, focused-route chrome hiding, and logout protection.
- Physical phone NavigationBar QA was not performed; automated policy coverage includes widths 839dp, 840dp, and 841dp. Phone NavigationBar behavior remains a device-QA risk.

### Scope boundaries

- M19B Home Dashboard remains a separate future milestone; no sales metrics, charts, or fabricated totals are marked complete here.
- Customer Management, Staff/User Management, and Product Media are not implemented by M19A and remain future feature milestones.
- No backend, API, Room/DataStore, payment, offline queue, printer, receipt, signing, package, or version behavior was changed.

## M19B — Operational Home Dashboard

Status: **CLOSED — operational dashboard implemented, automated verification passed, and tablet physical QA passed.**

### Dashboard behavior

- OWNER and ADMIN Home displays authenticated identity, outlet and shift context, daily sales total, daily transaction count, current-shift transaction count, separate Tunai, QRIS, and EDC totals, authoritative held-order total, and synchronization status.
- CASHIER Home never requests or displays tenant-level daily sales. CASHIER receives current-shift summary, held-order, and synchronization state only.
- Daily sales uses the existing reports API and use case. It is not calculated from local transaction lists.
- Shift values use the active shift summary. Held-order count uses pagination `meta.total`, never the first-page list size.
- The root `CartViewModel` remains the sole cart and held-order owner. Held-order state distinguishes `NotLoaded`, `Loading`, `Available`, `Stale`, and `Error`.

### State and isolation

- Daily-sales and shift-summary groups use independent jobs, retries, request tokens, response identity validation, and cancellation handling.
- Home operational context includes tenant, authenticated user, role, outlet, active shift, and cashier-session identity.
- Held-order refreshes coalesce for the same context. Mandatory mutation refreshes invalidate older work, and authoritative results publish items and `meta.total` together.
- Tenant, user, outlet, shift, cashier-session, and logout transitions clear state and reject late responses. Lifecycle resume uses normal same-context coalescing rather than forced replacement.

### Verification

- Full `:app:testDebugUnitTest`: **841 passed**, 0 failures, 0 errors, 0 skipped.
- Focused Home, Cart, held-order repository, presentation-policy, and M19A navigation tests passed.
- `compileDebugKotlin`, `compileDebugUnitTestKotlin`, `lintDebug`, and `assembleDebug` passed.
- Signed release build with `https://api.bekasirk.tech/` passed. Package remains `com.kasirkita.pos`; certificate SHA-256 remains `f1b37763c7da2cf7d3e03c6cfe7b630887901acf08dd8e115063a00e872b193b`.
- `git diff --check` passed.

### Physical QA

Tablet `RR2N50016ZZ` QA passed:

- Adaptive tablet NavigationRail remained functional.
- Dark mode passed on Home, Kasir, Lainnya, and Order Tersimpan. Light mode passed on Home, Kasir, and Lainnya.
- No mixed white/dark root canvas remained, and semantic green selected navigation remained visible.
- Dashboard loaded daily sales and current-shift values. Held-order authoritative count changed `0 → 1 → 0`; the created order appeared and cancellation returned the list to empty. Kasir badge returned to `Order Tersimpan (0)`.
- Offline Home showed explicit unavailable or stale states with retry actions. Held-order offline presentation showed the last known value as unsynchronized. Returning online restored daily, shift, and held-order data.
- No crash or blocked navigation was observed.

Phone NavigationBar physical QA, phone portrait/landscape QA, TalkBack QA, and large-font QA were not performed. No production deployment, backend change, new API, or database migration was made.

M19A navigation, Shift `GATE`/`MANAGE` behavior, root `CartViewModel` ownership, CASH/QRIS/EDC payments, offline checkout, printer behavior, and receipt behavior remain unchanged. Customer Management, Staff/User Management, and Product Media are not implemented.

## Next Milestones

1. Receipt Template Settings / Receipt V2.
2. Auth V2 follow-up hardening dan strategi offline PIN.
3. Customer Module.
4. Reports Dashboard.

## Rules For Future Development

- Update `PROJECT_STATUS.md` setelah milestone selesai.
- Jangan menandai fitur Completed sebelum build dan test berhasil.
- Jangan regenerate `client_transaction_id` ketika retry transaction.
- Jangan menggunakan destructive Room migration.
- Jangan mengubah backend contract tanpa kebutuhan dan koordinasi yang jelas.
- Reuse architecture, DTO, repository, use case, dan dependency injection yang sudah tersedia.
- Pertahankan `Long` untuk seluruh monetary values.
- Jalankan `./gradlew build` setelah perubahan signifikan.
