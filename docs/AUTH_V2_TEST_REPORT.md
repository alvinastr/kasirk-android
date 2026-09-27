# Android Auth V2 Test Report

## Document Information

| Field | Value |
| --- | --- |
| Project | KasirKita POS Android |
| Test date | 2026-09-27 |
| Test type | Manual end-to-end verification |
| Environment | Android Studio Emulator, Medium Phone API 37 |
| Backend | Local development environment |
| Overall result | PASS |

## Purpose

This report records the completed Android Auth V2 implementation and the results of manual end-to-end testing. The test scope covers store resolution, user selection, PIN authentication, persisted sessions, refresh token rotation, and logout cleanup.

## Implemented Components

The Android Auth V2 implementation includes:

- Store Login
- User Selection
- PIN Login
- `AuthSessionDataStore`
- `DeviceIdProvider`
- `RefreshTokenCoordinator`
- `AuthAuthenticator`
- Logout flow
- Global `AppNavigation` integration

## Backend Endpoints

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `POST` | `/auth/v2/store/resolve` | Resolve a tenant and list active users from a store code |
| `POST` | `/auth/v2/pin/login` | Authenticate a selected user with a PIN and device identity |
| `POST` | `/auth/v2/refresh` | Rotate the access token and refresh token pair |
| `POST` | `/auth/v2/logout` | Revoke the current device session |
| `GET` | `/auth/v2/me` | Retrieve the authenticated user |

## Test Accounts

Tenant: `KasirKita Demo`

| User | Role |
| --- | --- |
| Owner KasirKita | OWNER |
| Cashier Test | CASHIER |

## Test Summary

| ID | Test case | Status |
| --- | --- | --- |
| AUTH-V2-01 | Store Resolve | PASS |
| AUTH-V2-02 | User Selection | PASS |
| AUTH-V2-03 | PIN Login | PASS |
| AUTH-V2-04 | Session Persistence | PASS |
| AUTH-V2-05 | Refresh Token Rotation | PASS |
| AUTH-V2-06 | Logout | PASS |

## Test Results

### AUTH-V2-01: Store Resolve

Status: PASS

Verified results:

- The store code resolved the expected tenant.
- The application displayed the user list.
- The application did not display inactive users.

### AUTH-V2-02: User Selection

Status: PASS

Verified results:

- The cashier user could be selected.
- The application passed the selected user identity to the PIN login flow.

### AUTH-V2-03: PIN Login

Status: PASS

Verified results:

- A valid PIN produced an access token.
- The backend returned a refresh token.
- The backend created a device session.
- The Android client sent the device ID in the login request.

### AUTH-V2-04: Session Persistence

Status: PASS

Scenario:

1. Complete a successful login.
2. Force close the application.
3. Open the application again.

Expected result:

- The authenticated session remains active.

Actual result:

- The application opened the outlet selection screen without requiring another login.

### AUTH-V2-05: Refresh Token Rotation

Status: PASS

Scenario:

1. Advance the emulator time until the access token is expired.
2. Force close the application.
3. Open the application again.

Verified results:

- The Android client called the refresh endpoint.
- The backend marked the previous session as `REVOKED`.
- The backend created a new `ACTIVE` session.
- The refresh token expiry was extended.

### AUTH-V2-06: Logout

Status: PASS

Scenario:

1. Press the logout button.
2. Force close the application.
3. Open the application again.

Verified results:

- The backend marked the device session as `REVOKED`.
- The Android client cleared the local Auth V2 session.
- The Android client retained the stable device ID.
- The application returned to Store Login after restart.

## Known Limitations

- Auth V1 remains available as a temporary fallback.
- The Android project has not removed the Auth V1 implementation.
- Offline PIN verification is not available.
- Logout currently revokes only the active device session, not every session belonging to the user.

## Conclusion

The Android Auth V2 flow passed end-to-end testing. It is ready to become the primary authentication flow after the remaining UI and login migration work is complete.
