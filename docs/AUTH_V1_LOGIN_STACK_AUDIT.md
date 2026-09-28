# Auth V1 Login Stack Cleanup Audit

## Audit Metadata

- Project: KasirKita POS Android
- Audit phase: Auth V1 Cleanup Phase 6.3
- Audit date: 2026-09-28
- Status: Completed - Historical migration record
- Scope: Remaining Auth V1 presentation, domain, data, navigation, and model dependencies
- Change policy: Audit only. No application or test source was changed.

## Classification

- **A. Safe to delete immediately**: Used only by the Auth V1 login stack. These files can be removed together in one cleanup patch after the shared references in category C are edited in that same patch.
- **B. Still required by Auth V2 or other features**: Shared code that must remain.
- **C. Requires extraction or refactor first**: Shared files or declarations that still contain an Auth V1 reference. Remove only the legacy declaration or extract the shared declaration before deleting the V1 stack.

"Safe to delete" in this report means safe at the dependency level, not safe to delete one file at a time while leaving its callers behind.

## Executive Summary

The remaining Auth V1 login implementation is isolated from the active Auth V2 startup and bearer-token flow. No Auth V1 presentation, repository, API, or DTO is consumed by Auth V2 or a business feature.

Deletion is not yet a file-only operation because four shared integration points remain:

1. `AppNavigation.kt` still registers the legacy login destination and keeps `NavigationSession.AuthV1`.
2. `Screen.kt` still declares `Screen.Login`.
3. `NetworkModule.kt` still provides `AuthApi`.
4. `RepositoryModule.kt` still binds `AuthRepositoryImpl` to `AuthRepository`.

`UserRole` is also declared in `UserSession.kt`. `UserSession` is legacy-only, but `UserRole` is used throughout Auth V2 and role-based business navigation. `UserRole` must be extracted before `UserSession.kt` can be removed.

## Current Dependency Chain

```text
Screen.Login
  -> AppNavigation legacy destination
  -> LoginScreen
  -> LoginViewModel
  -> LoginUseCase
  -> AuthRepository
  -> AuthRepositoryImpl
  -> AuthApi
  -> POST /auth/login

AuthRepositoryImpl
  -> LoginRequest
  -> LoginResponse
  -> UserSession
  -> UserRole
```

Auth V2 uses its own navigation graph, repository, API, DTOs, session storage, and token provider. Exact-symbol searches were used so similarly named V2 types such as `StoreLoginScreen`, `PinLoginUseCase`, `PinLoginRequest`, and `AuthV2Repository` were not counted as Auth V1 references.

## A. Safe to Delete in the Coordinated Auth V1 Removal

### Presentation

#### `LoginScreen`

- Path: `app/src/main/java/com/kasirkita/pos/presentation/auth/LoginScreen.kt`
- References found:
  - Imported and called by `presentation/navigation/AppNavigation.kt` in the legacy `Screen.Login` destination.
  - Uses `LoginViewModel`, `LoginState`, and `UserSession`.
- Reason: It is the legacy email, password, and tenant login UI. Auth V2 screens do not call it, and normal startup no longer routes to it.
- Recommended action: Delete with `LoginViewModel.kt` and `LoginState.kt` after removing the legacy destination from `AppNavigation.kt`.

#### `LoginViewModel`

- Path: `app/src/main/java/com/kasirkita/pos/presentation/auth/LoginViewModel.kt`
- References found:
  - Created only by `LoginScreen` through `hiltViewModel()`.
  - Injects only `LoginUseCase`.
- Reason: It has no Auth V2 or business-feature consumer.
- Recommended action: Delete in the same patch as `LoginScreen.kt` and `LoginUseCase.kt`.

#### `LoginState`

- Path: `app/src/main/java/com/kasirkita/pos/presentation/auth/LoginState.kt`
- References found:
  - Produced by `LoginViewModel`.
  - Rendered by `LoginScreen`.
  - Its success state contains `UserSession`.
- Reason: It represents only Auth V1 login UI state.
- Recommended action: Delete with the legacy login presentation stack.

### Domain

#### `LoginUseCase`

- Path: `app/src/main/java/com/kasirkita/pos/domain/usecase/LoginUseCase.kt`
- References found:
  - Injected only into `LoginViewModel`.
  - Calls `AuthRepository.login()` and returns `Result<UserSession>`.
- Reason: Auth V2 uses separate use cases, including `PinLoginUseCase`. No non-V1 caller was found.
- Recommended action: Delete after or together with `LoginViewModel.kt` and `AuthRepository.kt`.

#### `AuthRepository`

- Path: `app/src/main/java/com/kasirkita/pos/domain/repository/AuthRepository.kt`
- References found:
  - Called by `LoginUseCase`.
  - Implemented by `AuthRepositoryImpl`.
  - Bound in `di/RepositoryModule.kt`.
- Reason: This interface belongs exclusively to Auth V1. `AuthV2Repository` is separate.
- Recommended action: Delete with its use case and implementation, and remove its Hilt binding from `RepositoryModule.kt`.

### Data

#### `AuthRepositoryImpl`

- Path: `app/src/main/java/com/kasirkita/pos/data/repository/AuthRepositoryImpl.kt`
- References found:
  - Bound to `AuthRepository` by `di/RepositoryModule.kt`.
  - Injects `AuthApi` and `Gson`.
  - Creates `LoginRequest`, decodes the V1 JWT, and returns `UserSession`.
- Reason: It no longer persists a legacy session and has no Auth V2 or business-feature consumer.
- Recommended action: Delete with `AuthRepository.kt`, then remove `bindAuthRepository()` and its imports from `RepositoryModule.kt`.

#### `AuthApi`

- Path: `app/src/main/java/com/kasirkita/pos/data/api/AuthApi.kt`
- References found:
  - Injected only into `AuthRepositoryImpl`.
  - Provided by `di/NetworkModule.kt`.
  - Declares only `POST auth/login`.
- Reason: Auth V2 uses `AuthV2Api`; no shared endpoint is declared here.
- Recommended action: Delete with `AuthRepositoryImpl.kt`, then remove `provideAuthApi()` and its import from `NetworkModule.kt`.

#### `LoginRequest`

- Path: `app/src/main/java/com/kasirkita/pos/data/model/LoginRequest.kt`
- References found:
  - Constructed by `AuthRepositoryImpl`.
  - Used as the body type by `AuthApi`.
- Reason: It models only the legacy email, password, and tenant request. `PinLoginRequest` is a separate Auth V2 DTO.
- Recommended action: Delete with `AuthApi.kt` and `AuthRepositoryImpl.kt`.

#### `LoginResponse`

- Path: `app/src/main/java/com/kasirkita/pos/data/model/LoginResponse.kt`
- References found:
  - Used only as the response type of `AuthApi.login()`.
- Reason: It models only the Auth V1 access-token response.
- Recommended action: Delete with `AuthApi.kt`.

## B. Still Required by Auth V2 or Other Features

### `UserRole`

- Current path: `app/src/main/java/com/kasirkita/pos/domain/model/UserSession.kt`
- References found:
  - Auth V2 session and user models: `AuthSession.kt`, `AuthV2Models.kt`.
  - Auth V2 persistence and response mapping: `AuthSessionDataStore.kt`, `StoreResolveResponse.kt`.
  - Auth V2 UI: `UserSelectionScreen.kt`.
  - Navigation and authorization guards in `AppNavigation.kt`.
  - Product management, stock, reports, transaction, and shift navigation guard tests.
  - Auth V2 repository, ViewModel, session, authenticator, refresh, and logout tests.
- Reason: This enum is the shared role model for active Auth V2 sessions and role-based feature access. Removing it would break runtime code and tests outside Auth V1.
- Recommended action: Keep the enum and its package name. Move it to `domain/model/UserRole.kt` before deleting `UserSession.kt`.

## C. Requires Extraction or Refactor First

### `UserSession`

- Current path: `app/src/main/java/com/kasirkita/pos/domain/model/UserSession.kt`
- References found:
  - Returned by `AuthRepository` and `LoginUseCase`.
  - Created by `AuthRepositoryImpl`.
  - Used by `LoginState`, `LoginScreen`, and legacy navigation state in `AppNavigation.kt`.
- Reason: The `UserSession` type itself is Auth V1-only, but its file also declares the shared `UserRole` enum. Deleting the file now would remove a required Auth V2 type.
- Recommended action: First extract `UserRole` to `domain/model/UserRole.kt`. Then remove `NavigationSession.AuthV1` and the V1 login stack before deleting `UserSession.kt`.

### `Screen.Login`

- Path: `app/src/main/java/com/kasirkita/pos/presentation/navigation/Screen.kt`
- References found:
  - Declared as `data object Login : Screen("login")`.
  - Used by the legacy login destination and its `popUpTo` call in `AppNavigation.kt`.
- Reason: `Screen.kt` is shared by active business navigation and cannot be deleted. Only its V1 route declaration is obsolete.
- Recommended action: Remove only `Screen.Login` after removing all matching references from `AppNavigation.kt`.

### Legacy login route registration

- Path: `app/src/main/java/com/kasirkita/pos/presentation/navigation/AppNavigation.kt`
- References found:
  - `composable(Screen.Login.route)`.
  - Calls `LoginScreen`.
  - Calls `viewModel.onAuthV1Authenticated(session)`.
  - Uses `popUpTo(Screen.Login.route)`.
- Reason: This destination is the remaining route into the Auth V1 UI. It is not chosen by normal Auth V2 startup, but it keeps the legacy presentation stack reachable from the navigation graph.
- Recommended action: Remove the complete `Screen.Login` destination block and the `LoginScreen` import. Keep the Auth V2 graph and all authenticated business routes unchanged.

### `onAuthV1Authenticated`

- Path: `app/src/main/java/com/kasirkita/pos/presentation/navigation/AppNavigation.kt`
- References found:
  - Called only by the legacy `LoginScreen` destination.
  - Creates `NavigationSession.AuthV1`.
- Reason: It has no Auth V2 or business caller, but lives inside the shared `AppNavigationViewModel` source.
- Recommended action: Remove this method when removing the legacy login destination.

### `NavigationSession.AuthV1`

- Path: `app/src/main/java/com/kasirkita/pos/presentation/navigation/AppNavigation.kt`
- References found:
  - Declared as a navigation-session variant wrapping `UserSession`.
  - Constructed only by `onAuthV1Authenticated()`.
  - Supplies a `UserRole` through the shared `NavigationSession` interface.
- Reason: The variant is legacy-only, but the surrounding `NavigationSession` abstraction and its Auth V2 variant remain active.
- Recommended action: Remove only the `AuthV1` variant, then remove the `UserSession` import. Keep `NavigationSession.AuthV2` and shared role access.

### Auth V1 API provider

- Path: `app/src/main/java/com/kasirkita/pos/di/NetworkModule.kt`
- References found:
  - Imports `AuthApi`.
  - Provides it through `provideAuthApi(retrofit)`.
- Reason: `NetworkModule.kt` is shared by all Retrofit services and cannot be deleted. The V1 provider would fail compilation after `AuthApi.kt` is removed.
- Recommended action: Remove only the `AuthApi` import and `provideAuthApi()` method. Keep `AuthV2Api`, the authenticator, interceptor, and all business API providers.

### Auth V1 repository binding

- Path: `app/src/main/java/com/kasirkita/pos/di/RepositoryModule.kt`
- References found:
  - Imports `AuthRepository` and `AuthRepositoryImpl`.
  - Binds them through `bindAuthRepository()`.
- Reason: `RepositoryModule.kt` is shared by Auth V2 and business repositories. The legacy binding would fail compilation after the V1 repository types are removed.
- Recommended action: Remove only the two Auth V1 imports and `bindAuthRepository()`. Keep all Auth V2 and business repository bindings.

## Tests and Documentation References

- No exact references to `LoginScreen`, `LoginViewModel`, `LoginState`, `LoginUseCase`, `AuthRepository`, `AuthRepositoryImpl`, `AuthApi`, `LoginRequest`, `LoginResponse`, `UserSession`, `Screen.Login`, `onAuthV1Authenticated`, or `NavigationSession.AuthV1` were found under `app/src/test`.
- Tests extensively reference `UserRole`; those tests confirm why the enum must remain.
- Existing audit and project-status documents contain historical Auth V1 names. These are documentation references, not runtime dependencies. They should not block code deletion, but a later documentation pass should mark the final deletion state accurately.

## Recommended Removal Sequence

Perform the eventual deletion as one reviewed patch in this order:

1. Extract `UserRole` from `UserSession.kt` into `domain/model/UserRole.kt` without changing its package or enum values.
2. Remove `Screen.Login`, the legacy login destination, `onAuthV1Authenticated()`, and `NavigationSession.AuthV1`.
3. Remove the Auth V1 provider from `NetworkModule.kt` and binding from `RepositoryModule.kt`.
4. Delete the category A files and then delete `UserSession.kt`.
5. Search again using exact symbols and the endpoint string `auth/login`.
6. Run unit tests, the full build, and `git diff --check` during the deletion phase.

## Final Assessment

The Auth V1 login stack is ready for a coordinated deletion after the category C edits are included. No Auth V1 presentation, use case, repository, API, or DTO needs to be retained for Auth V2 or business features. `UserRole` is the only requested model that must survive, and it must be extracted from `UserSession.kt` before that legacy file is removed.
