# Auth V1 Cleanup Audit

## Document Information

| Field | Value |
| --- | --- |
| Project | KasirKita POS Android |
| Audit date | 2026-09-27 |
| Status | Completed - Historical migration record |
| Scope | Auth V1 references in application code, unit tests, Hilt modules, network infrastructure, and project documentation |
| Method | Static reference audit with `rg` and direct source inspection |
| Code changes | None |

Generated build output and Git metadata were excluded from the search.

## Executive Summary

Auth V2 is already the normal startup authentication flow. Auth V1 no longer participates in startup session resolution, but its UI route, domain and data stack, Hilt bindings, local session storage, token fallback, and rollback callback remain in the project.

The Auth V1 login stack can be removed as one coordinated change. `TokenDataStore` and `LegacySessionReader.kt` cannot be deleted first because Auth V2 infrastructure still has two dependencies on them:

1. `CompositeAuthTokenProvider` falls back to the Auth V1 token when no Auth V2 access token exists.
2. `LogoutViewModel` clears the Auth V1 store through `LegacySessionCleaner` during Auth V2 logout.

No normal navigation callback currently opens `Screen.Login`. The route remains registered in the global navigation graph for explicit rollback.

## Dependency Map

```text
Screen.Login
  -> LoginScreen
     -> LoginViewModel
        -> LoginUseCase
           -> AuthRepository
              -> AuthRepositoryImpl
                 -> AuthApi
                    -> POST /auth/login
                 -> LoginRequest
                 -> LoginResponse
                 -> TokenDataStore

AuthInterceptor
  -> AuthTokenProvider
     -> CompositeAuthTokenProvider
        -> AuthSessionDataStore
        -> TokenDataStore

LogoutViewModel
  -> LegacySessionCleaner
     -> TokenDataStoreLegacySessionReader
        -> TokenDataStore
```

## Classification Summary

Category meanings:

- A: still used by Auth V2 or shared infrastructure. Do not delete directly.
- B: belongs only to Auth V1 and can be removed as part of one atomic Auth V1 cleanup.
- C: shared file or coupling point that must be edited before the Auth V1 files can be deleted.

| Component | Category | Current deletion verdict |
| --- | --- | --- |
| `LoginScreen` | B | Safe in coordinated cleanup |
| `LoginViewModel` | B | Safe in coordinated cleanup |
| `LoginState` | B | Safe in coordinated cleanup |
| `LoginUseCase` | B | Safe in coordinated cleanup |
| `AuthRepository` | B | Safe in coordinated cleanup |
| `AuthRepositoryImpl` | B | Safe in coordinated cleanup |
| `AuthApi` | B | Safe in coordinated cleanup |
| `LoginRequest` | B | Safe in coordinated cleanup |
| `LoginResponse` | B | Safe in coordinated cleanup |
| `POST /auth/login` | B | Safe after `AuthApi` removal |
| `TokenDataStore` | A | Not safe yet |
| `LegacySessionReader` | C | Split or remove legacy cleanup coupling first |
| `NavigationSession.AuthV1` | C | Remove from shared navigation file |
| `Screen.Login` | C | Remove from shared route file |
| `onAuthV1Authenticated` | C | Remove from shared navigation ViewModel |

## A. Still Used by Auth V2 or Shared Infrastructure

### `app/src/main/java/com/kasirkita/pos/core/datastore/TokenDataStore.kt`

Called by:

- `AuthRepositoryImpl`, which writes an Auth V1 session after `POST /auth/login`.
- `CompositeAuthTokenProvider`, which returns the V1 token when `AuthSessionDataStore` has no V2 token.
- `TokenDataStoreLegacySessionReader`, which reads and clears the legacy session.

Safe to delete: No.

Reason:

`TokenDataStore` is still in the active OkHttp bearer-token path through `CompositeAuthTokenProvider`. It is also part of Auth V2 logout cleanup through `LegacySessionCleaner`. Deleting it now would break Hilt construction for the network client and `LogoutViewModel`.

Required before deletion:

- Make the active `AuthTokenProvider` read only from `AuthSessionDataStore`.
- Remove legacy-session cleanup from `LogoutViewModel` after confirming no V1 session can affect authentication or authorization.
- Remove the legacy adapter and its Hilt bindings.

## B. Auth V1 Only and Safe to Remove

The files in this section form one dependency chain. They are safe to remove together with the Hilt and navigation references listed in section C. Removing an individual file before its caller will cause compilation failure.

### `app/src/main/java/com/kasirkita/pos/presentation/auth/LoginScreen.kt`

Called by:

- The `Screen.Login` destination in `AppNavigation.kt`.

Safe to delete: Yes, in coordinated cleanup.

Reason:

The screen implements the email, password, and tenant ID form used only by Auth V1. Auth V2 uses `StoreLoginScreen`, `UserSelectionScreen`, and `PinLoginScreen` instead.

### `app/src/main/java/com/kasirkita/pos/presentation/auth/LoginViewModel.kt`

Called by:

- `LoginScreen` through `hiltViewModel()`.

Safe to delete: Yes, together with `LoginScreen`.

Reason:

The ViewModel accepts Auth V1 credentials and calls only `LoginUseCase`. No Auth V2 screen or business feature injects it.

### `app/src/main/java/com/kasirkita/pos/presentation/auth/LoginState.kt`

Called by:

- `LoginViewModel` as its exposed state.
- `LoginScreen` for rendering idle, loading, success, and error states.

Safe to delete: Yes, together with the Auth V1 screen and ViewModel.

Reason:

The state carries `UserSession`, which is the Auth V1 session model. Auth V2 uses `AuthV2State` and `AuthSession`.

### `app/src/main/java/com/kasirkita/pos/domain/usecase/LoginUseCase.kt`

Called by:

- `LoginViewModel`.

Safe to delete: Yes, together with `LoginViewModel`.

Reason:

The use case only forwards email, password, and tenant ID to `AuthRepository.login()`. Auth V2 uses `ResolveStoreUseCase` and `PinLoginUseCase`.

### `app/src/main/java/com/kasirkita/pos/domain/repository/AuthRepository.kt`

Called by:

- `LoginUseCase`.
- `AuthRepositoryImpl` as its implemented contract.
- `RepositoryModule` as a Hilt binding type.

Safe to delete: Yes, after removing its Hilt binding and Auth V1 use case.

Reason:

The interface exposes only the Auth V1 email and password login contract. Auth V2 uses the separate `AuthV2Repository` interface.

### `app/src/main/java/com/kasirkita/pos/data/repository/AuthRepositoryImpl.kt`

Called by:

- Hilt through `RepositoryModule.bindAuthRepository()`.

Dependencies:

- `AuthApi`
- `LoginRequest`
- `TokenDataStore`
- `Gson`
- `UserSession`

Safe to delete: Yes, after removing `bindAuthRepository()`.

Reason:

The implementation calls only the Auth V1 endpoint, decodes the V1 JWT payload, and writes a V1 session. Auth V2 uses `AuthV2RepositoryImpl`, `AuthSessionDataStore`, and its own token models.

### `app/src/main/java/com/kasirkita/pos/data/api/AuthApi.kt`

Called by:

- `AuthRepositoryImpl`.
- `NetworkModule.provideAuthApi()` for Hilt provisioning.

Safe to delete: Yes, after removing its provider and repository implementation.

Reason:

The interface contains only `POST /auth/login`. `AuthV2Api` is separate and does not depend on it.

### `app/src/main/java/com/kasirkita/pos/data/model/LoginRequest.kt`

Called by:

- `AuthRepositoryImpl`, which constructs the request.
- `AuthApi`, which accepts it as the request body.

Safe to delete: Yes, together with `AuthApi` and `AuthRepositoryImpl`.

Reason:

The email, password, and tenant ID payload belongs only to the Auth V1 backend contract. `PinLoginRequest` is a separate Auth V2 model and must not be removed.

### `app/src/main/java/com/kasirkita/pos/data/model/LoginResponse.kt`

Called by:

- `AuthApi` as the return body for `POST /auth/login`.

Safe to delete: Yes, together with `AuthApi`.

Reason:

The model contains only the Auth V1 `access_token` response. Auth V2 uses `AuthTokenResponse`, which includes access token, refresh token, and expiry.

### Backend reference `POST /auth/login`

Referenced by:

- `AuthApi.kt` in `@POST("auth/login")`.
- `docs/PROJECT_STATUS.md` in the historical authentication sections.

Safe to remove from Android runtime code: Yes, after deleting `AuthApi` and its provider.

Reason:

No Auth V2 component calls this endpoint. Removing the Android reference does not require a backend change.

## C. Refactor Required Before Deletion

### `app/src/main/java/com/kasirkita/pos/presentation/navigation/AppNavigation.kt`

Auth V1 references:

- Imports and renders `LoginScreen` in the `Screen.Login` destination.
- Calls `onAuthV1Authenticated()` after legacy login success.
- Defines `NavigationSession.AuthV1`.
- Defines `AppNavigationViewModel.onAuthV1Authenticated()`.
- Imports `UserSession` for the legacy navigation state.

Called by:

- The application root navigation flow.

Safe to delete: No. Edit only the Auth V1 blocks.

Reason:

This file owns all authenticated business routes. Removing the whole file would remove Outlet, Shift, Home, Product, Cart, Checkout, Receipt, Transaction, Stock, and Reports navigation.

Required refactor:

- Remove the `Screen.Login` composable block and `LoginScreen` import.
- Remove `NavigationSession.AuthV1`.
- Remove `onAuthV1Authenticated()` and the `UserSession` import.
- Keep `AuthV2Navigation`, `NavigationSession.AuthV2`, session refresh, logout navigation, and every business route unchanged.

### `app/src/main/java/com/kasirkita/pos/presentation/navigation/Screen.kt`

Auth V1 reference:

- Defines `Screen.Login` with route `login`.

Called by:

- The Auth V1 destination and pop-up behavior in `AppNavigation.kt`.

Safe to delete: No. Remove only `Screen.Login`.

Reason:

The same sealed class defines every business route.

### `app/src/main/java/com/kasirkita/pos/di/NetworkModule.kt`

Auth V1 references:

- Imports `AuthApi`.
- Provides `AuthApi` through `provideAuthApi()`.
- Provides `CompositeAuthTokenProvider` as the active `AuthTokenProvider`.

Called by:

- Hilt singleton graph.

Safe to delete: No. Remove or replace only the Auth V1 bindings.

Required refactor:

- Remove `provideAuthApi()` and its import.
- Keep both Auth V2 API providers, including the qualified refresh client.
- Keep `AuthInterceptor` and `AuthAuthenticator`.
- Change token-provider wiring only after `CompositeAuthTokenProvider` no longer depends on `TokenDataStore`.

### `app/src/main/java/com/kasirkita/pos/di/RepositoryModule.kt`

Auth V1 references:

- Imports `AuthRepository` and `AuthRepositoryImpl`.
- Binds them through `bindAuthRepository()`.

Called by:

- Hilt singleton graph.

Safe to delete: No. Remove only the Auth V1 imports and binding.

Reason:

The module also binds every business repository and `AuthV2Repository`.

### `app/src/main/java/com/kasirkita/pos/core/network/CompositeAuthTokenProvider.kt`

Auth V1 reference:

- Injects `TokenDataStore` and uses its token as a fallback when no Auth V2 access token exists.

Called by:

- `NetworkModule.provideAuthTokenProvider()`.
- `AuthInterceptor` indirectly through the `AuthTokenProvider` interface.

Safe to delete: No, until a V2-only provider replaces it.

Reason:

The provider supplies bearer tokens for all authenticated Retrofit calls. Removing it without replacement would break network authentication.

Required refactor:

- Make the provider return only `AuthSessionDataStore.getAccessToken()`, or bind a new V2-only implementation of `AuthTokenProvider`.
- Do not remove `AuthInterceptor` or the `AuthTokenProvider` interface.

### `app/src/main/java/com/kasirkita/pos/core/datastore/LegacySessionReader.kt`

Contents and callers:

- `LegacySessionReader.getSession()` has no production consumer after Auth V2 became the default startup flow.
- `SessionModule` still binds `LegacySessionReader`.
- `AppNavigationViewModelTest` references the type to verify that startup no longer injects it.
- The same file defines `LegacySessionCleaner`.
- `TokenDataStoreLegacySessionReader` implements both interfaces and delegates to `TokenDataStore`.
- `LogoutViewModel` still consumes `LegacySessionCleaner`.

Safe to delete: No, not as a whole file.

Reason:

The reader is runtime-dead, but the cleaner and adapter remain part of Auth V2 logout. The file mixes removable read behavior with still-active cleanup behavior.

Required refactor:

- Remove the `LegacySessionCleaner` dependency from Auth V2 logout after the token provider becomes V2-only.
- Update the logout unit test.
- Remove the reader and cleaner bindings from `SessionModule`.
- Update the startup test so it no longer imports the deleted reader type.
- Delete the adapter and file only after those consumers are gone.

### `app/src/main/java/com/kasirkita/pos/di/SessionModule.kt`

Auth V1 references:

- Binds `TokenDataStoreLegacySessionReader` to `LegacySessionReader`.
- Binds the same adapter to `LegacySessionCleaner`.

Called by:

- Hilt singleton graph.

Safe to delete: Not yet.

Reason:

The cleaner binding is still required by `LogoutViewModel`. After removing that dependency, this module has no remaining bindings and the entire file can be deleted.

### `app/src/main/java/com/kasirkita/pos/presentation/authv2/LogoutViewModel.kt`

Auth V1 reference:

- Injects `LegacySessionCleaner`.
- Clears the legacy store during local Auth V2 logout cleanup.

Called by:

- `HomeScreen` as the active logout ViewModel.

Safe to delete: No. Remove only the legacy cleaner dependency and cleanup call.

Reason:

This is the active Auth V2 logout flow. It must continue clearing `AuthSessionDataStore`, selected outlet, and cart state.

### `app/src/main/java/com/kasirkita/pos/domain/model/UserSession.kt`

Called by:

- Auth V1 repository, use case, state, and screen.
- `TokenDataStore` and `LegacySessionReader`.
- `NavigationSession.AuthV1` and `onAuthV1Authenticated()`.

Safe to delete: Not yet.

Reason:

The model belongs only to Auth V1, but several shared files still reference it. Delete it after the Auth V1 navigation and legacy storage paths have been removed. Keep `UserRole`, which is shared with Auth V2 and authorization guards.

### `app/src/test/java/com/kasirkita/pos/presentation/navigation/AppNavigationViewModelTest.kt`

Auth V1 reference:

- Imports `LegacySessionReader` and uses reflection to assert that `AppNavigationViewModel` does not accept it.

Safe to delete: No. Update only the legacy assertion.

Reason:

The test also verifies active Auth V2 startup, refresh success, refresh failure, and logout state transitions.

Recommended replacement:

- Keep the behavior assertion that no V2 session opens `AuthV2Screen.Graph`.
- Remove the reflection assertion against `LegacySessionReader` after deleting that type.

### `app/src/test/java/com/kasirkita/pos/presentation/authv2/LogoutViewModelTest.kt`

Auth V1 reference:

- Imports and fakes `LegacySessionCleaner`.
- Asserts that legacy cleanup occurred during logout.

Safe to delete: No. Update only the legacy-cleaner fixture and assertions.

Reason:

The test verifies the active Auth V2 logout behavior, including local cleanup after network failure.

### Documentation references

Files:

- `docs/PROJECT_STATUS.md`
- `docs/AUTH_V2_TEST_REPORT.md`

Current references:

- `PROJECT_STATUS.md` documents `POST /auth/login`, `LoginViewModel`, `LoginScreen`, and legacy `TokenDataStore` behavior.
- `AUTH_V2_TEST_REPORT.md` states that Auth V1 remains as a temporary fallback and has not been removed.

Safe to delete: No. Update the relevant historical or limitation text after code cleanup.

Reason:

Deleting the documents would remove unrelated project history and test evidence. Only stale Auth V1 status statements should change.

## Test Coverage Finding

There are no dedicated unit tests for `LoginViewModel`, `LoginUseCase`, `AuthRepositoryImpl`, `AuthApi`, `LoginRequest`, or `LoginResponse`. Current Auth-related unit tests focus on Auth V2, session persistence, refresh, logout, and startup navigation.

This means Auth V1 removal does not require deleting a dedicated V1 test suite. Two active Auth V2 tests still contain legacy compatibility references and must be edited rather than deleted:

- `AppNavigationViewModelTest.kt`
- `LogoutViewModelTest.kt`

## Recommended Cleanup Order

1. Replace `CompositeAuthTokenProvider` with V2-only token lookup while preserving `AuthInterceptor` and `AuthAuthenticator`.
2. Remove `LegacySessionCleaner` from `LogoutViewModel` and update `LogoutViewModelTest`.
3. Remove `LegacySessionReader`, its adapter, `TokenDataStore`, and `SessionModule` after confirming no remaining callers.
4. Remove the Auth V1 destination, `NavigationSession.AuthV1`, and `onAuthV1Authenticated()` from shared navigation files.
5. Remove the Auth V1 Hilt API and repository bindings.
6. Delete the Auth V1 UI, ViewModel, state, use case, repository, API, request, response, and `UserSession` files as one atomic change.
7. Update the startup test and Auth documentation.
8. Run unit tests, full build, lint, and an emulator regression covering Auth V2 login, restart, refresh, logout, and authenticated API calls.

## Final Assessment

The Auth V1 login stack is isolated enough for removal, but direct deletion is not safe yet. The blocking couplings are the legacy bearer-token fallback and legacy-store cleanup inside Auth V2 logout. Once those two dependencies are removed, the remaining Auth V1 files and navigation fragments can be deleted without changing backend APIs or business features.
