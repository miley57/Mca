# Security Specification — MCA Task Manager

## 1. Data Invariants
- All user data lives strictly under `/users/{userId}/tasks/{taskId}`, `/users/{userId}/inbox_items/{itemId}`, and `/users/{userId}/focus_sessions/{sessionId}`.
- Every operation requires authentication (`request.auth != null`) and path ownership (`request.auth.uid == userId`).
- Every write (`create`, `update`) requires `incoming().userId == request.auth.uid` and strict schema validation via `isValidTask`, `isValidInboxItem`, or `isValidFocusSession`.
- `createdAt` and `userId` are immutable on `update` (`incoming().createdAt == existing().createdAt && incoming().userId == existing().userId`).
- All timestamp fields (`createdAt`, `updatedAt`) must be server timestamps (`is timestamp && <= request.time`).

## 2. The Dirty Dozen Payloads
1. **Unauthenticated Read/Write**: `auth = null` attempting `get`, `list`, `create`, `update`, or `delete` on `/users/alice/tasks/t1`.
2. **Cross-User Read (`get` / `list`)**: `auth.uid = "bob"` attempting to read `/users/alice/tasks/t1` or list `/users/alice/tasks`.
3. **Identity Spoofing on Create**: `auth.uid = "alice"` writing to `/users/alice/tasks/t1` with `userId: "bob"`.
4. **Shadow/Ghost Field Injection**: Adding `isAdmin: true` or `extraField: "hack"` not in `hasOnly(...)` allowlist.
5. **Immutable Field Mutation**: Updating `createdAt` or `userId` on an existing task.
6. **Title Size Overflow / Empty Title**: `title: ""` or a 500-character string exceeding `200` chars.
7. **Invalid Enum Value**: Setting `priority: "SUPER_CRITICAL"` or `status: "DONE"` outside allowed enum sets.
8. **Negative or Out-of-Range Numeric Values**: `estimatedMinutes: 0` or `estimatedMinutes: 99999`.
9. **Future Timestamp Spoofing**: Passing a client timestamp in the future (`createdAt > request.time`).
10. **Missing Required Keys on Create**: Omitting `priority`, `status`, or `updatedAt`.
11. **Path ID Poisoning**: Document ID exceeding 128 chars or containing invalid characters.
12. **Cross-User Delete**: `auth.uid = "bob"` attempting to delete `/users/alice/inbox_items/i1`.
