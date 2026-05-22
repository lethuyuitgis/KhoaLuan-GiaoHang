# P6 Code Review — Live Location + Realtime Map

Reviewed commits: `b1cb115^..b57a251` (13 commits, 32 files, +1133 LoC).

## Summary
- Files reviewed: 18 source + 2 test files
- Critical: 3
- Important: 5
- Minor: 6
- Overall verdict: **NEEDS FIXES** — one true security hole (STOMP subscribe authz) plus an ambiguous-shipper bug that will silently send the wrong shipper's GPS to a customer. The rest can land if you accept the risk for the thesis demo.

---

## Critical findings

### CR-1. STOMP `SUBSCRIBE` is not authorized — any Telegram user can spy on any order's live location
File: `backend/app/src/main/java/com/shop/delivery/config/WebSocketConfig.java:51-76`
File: `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/api/ws/LocationBroadcaster.java:36-43`

Issue: The CONNECT interceptor verifies `X-Telegram-Init-Data` and attaches a `TelegramUserPrincipal` (good). But it intercepts **only** `StompCommand.CONNECT`. There is no authz on `SUBSCRIBE`. Combined with `ws.convertAndSend("/topic/order/" + orderId + "/location", ...)` (a public broker topic, not a `/user/...` queue), any authenticated Telegram user can call:

```
SUBSCRIBE /topic/order/<any-uuid>/location
```

…and receive the live GPS of any shipper delivering any order, as long as they know or can guess the orderId. The REST fallback `GET /api/orders/{id}/location` correctly authorizes (customer-owner), but the realtime path completely bypasses that check.

This is a privacy leak: a hostile customer (or any random Telegram user who installed the Mini App) can watch a shipper's real-time location to a stranger's address.

Suggested fix (pick one):
1. **Per-user destination (preferred)**: change broadcast to `ws.convertAndSendToUser(String.valueOf(orderResolveCustomerId), "/queue/order/" + orderId + "/location", payload)` and have the client subscribe to `/user/queue/order/{orderId}/location`. The `LocationPingReceivedEvent` already carries `customerId` — use it. Then a user only ever receives messages for orders Spring resolves to *their* principal.
2. **Subscribe interceptor**: extend the `ChannelInterceptor` in `WebSocketConfig` to also handle `StompCommand.SUBSCRIBE`. Parse `/topic/order/{orderId}/location` from the destination, look up the order, reject if `order.customerId != principal.userId`.

Option 1 is simpler and matches the existing `setUserDestinationPrefix("/user")` already configured at line 41. Option 2 is more flexible but requires hitting the DB on every SUBSCRIBE.

### CR-2. Wrong shipper attribution when a shipper has multiple `STARTED` assignments
File: `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/service/LocationPingService.java:47-53`

```java
List<DeliveryAssignment> active = assignmentRepo.findAllByShipperIdAndStatusIn(
    shipperId, List.of(AssignmentStatus.STARTED));
if (active.isEmpty()) { ... return; }
DeliveryAssignment a = active.get(0);   // ← arbitrary order; no ORDER BY
```

`findAllByShipperIdAndStatusIn` has no ordering clause, so JPA returns them in whatever order the DB feels like. If a shipper has two `STARTED` assignments (no DB constraint prevents this — `delivery_assignment` allows multiple rows per shipper with status=STARTED), every ping will be assigned to whichever row JPA picks first, and broadcast to that order's customer. The other customer just sees a stale map.

This may seem unlikely, but the current flow does not enforce "one active assignment per shipper" anywhere I can find. With the Live Location handler running every ~10 seconds, this will silently break in production the first time a dispatcher hands a shipper two parallel deliveries.

Suggested fix: enforce the invariant explicitly. Either
- Add a partial unique index in a new migration: `CREATE UNIQUE INDEX uq_assignment_shipper_started ON delivery_assignment(shipper_id) WHERE status = 'STARTED';` and have `LocationPingService` log+ignore when `active.size() > 1` (defense in depth).
- Or change the Live Location flow: send the orderId/code in the Telegram bot caption when the shipper hits "Bắt đầu giao", parse it from the message, and look up assignment by `(shipperId, orderId)` instead of "the first STARTED one".

Until then, document the limitation in the plan (P6 assumes one active assignment per shipper).

### CR-3. `NullPointerException` on `msg.getFrom().getId()` for channel/anonymous location messages
File: `backend/modules/bot/src/main/java/com/shop/delivery/bot/handler/shipper/LiveLocationHandler.java:42`

```java
Long userId = msg.getFrom().getId();
```

Telegram `Message.from` can be `null` for:
- Messages posted in channels (where `sender_chat` is set instead).
- Anonymous group admins.
- Some service messages.

`canHandle()` only checks `getLocation() != null`, not `getFrom() != null`. If a Telegram channel ever forwards a Live Location or a bot is misconfigured into a channel admin, the webhook handler will NPE. `UpdateRouter.route()` catches it (line 48), so it won't crash the server, but it will create a noisy error log on every ping for that channel.

Suggested fix:
```java
if (msg.getFrom() == null) {
    log.debug("Ignoring location update without from-user (channel/anonymous)");
    return;
}
Long userId = msg.getFrom().getId();
```
Also update `canHandle()` to require `msg.getFrom() != null` so the dispatch consistently skips the handler.

---

## Important findings

### IM-1. Duplicate STOMP endpoint registration likely causes one of the two transports to be unreachable
File: `backend/app/src/main/java/com/shop/delivery/config/WebSocketConfig.java:46-47`

```java
registry.addEndpoint("/ws").setAllowedOriginPatterns("*");                  // raw WS
registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();     // SockJS
```

Spring registers these in two separate `WebSocketHandlerMapping`s. The miniapp uses SockJS (`frontend/miniapp/src/lib/ws.ts:7`), so SockJS works, but the raw `/ws` endpoint above it is dead weight that just shadows the path. More importantly, if both register to the same servlet path, behaviour is order-dependent. Pick one.

Suggested fix: drop the first line — only the SockJS variant is used and SockJS already handles native WebSocket as its first transport choice.
```java
registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
```

### IM-2. Vite dev proxy doesn't forward `/ws` to the backend
File: `frontend/miniapp/vite.config.ts:15-20`

```ts
server: {
  proxy: {
    '/api': { target: 'http://localhost:8080', changeOrigin: true },
  },
},
```

`createStompClient()` connects to `new SockJS('/ws')` — at dev time that hits Vite (5173), which has no proxy rule for `/ws`, so SockJS gets a 404 / HTML from Vite and fails to connect. Tracking won't work in `pnpm dev` mode.

Suggested fix: add the proxy rule:
```ts
proxy: {
  '/api': { target: 'http://localhost:8080', changeOrigin: true },
  '/ws':  { target: 'http://localhost:8080', changeOrigin: true, ws: true },
},
```

### IM-3. `OrderTrackingMap` will throw / show a broken map if pickup or destination coords are missing
File: `frontend/miniapp/src/features/tracking/OrderTrackingMap.tsx:48-55`

```ts
const pickup: [number, number] = [Number(pickupLat), Number(pickupLng)];
const destination: [number, number] = [Number(deliveryLat), Number(deliveryLng)];
...
const center: [number, number] = [
  (pickup[0] + destination[0]) / 2,
  (pickup[1] + destination[1]) / 2,
];
```

If any of the four coordinate strings is `undefined`/`null`/`""`, `Number(...)` returns `NaN`, and Leaflet's `MapContainer` will throw "Invalid LatLng object" on render. The current OrderResponse always populates these (good — that's what `b57a251` fixed), but the component is now coupled to "backend never sends nulls here" — there's no defensive guard. A future order created before P5 (or via a bad migration) could break the customer's order detail page.

Suggested fix: short-circuit and render a fallback when any coord is invalid:
```ts
if ([pickup, destination].some(p => !Number.isFinite(p[0]) || !Number.isFinite(p[1]))) {
  return <div className="text-sm text-tg-hint italic">Không có dữ liệu bản đồ.</div>;
}
```

### IM-4. STOMP subscription is created on every reconnect but never cleaned up if the component unmounts during the connecting window
File: `frontend/miniapp/src/features/tracking/use-live-location.ts:50-77`

```ts
client.onConnect = () => {
  setIsConnected(true);
  subscribeOrderLocation(client, orderId, ...);   // ← unsub fn discarded
};
...
return () => {
  client.deactivate();
  setIsConnected(false);
};
```

Two issues:
1. The `unsubscribe` function returned by `subscribeOrderLocation` is thrown away. `client.deactivate()` does close the underlying connection and the broker drops the subscription, so this is not a true leak — but if `client.activate()` ever auto-reconnects (it can, `reconnectDelay: 5000` is set), `onConnect` will fire again and create a **second** subscription on the same client. After N reconnects you have N duplicate handlers, each calling `setLocation`. This is a memory + render-thrash bug.
2. `setIsConnected(false)` runs synchronously in the unmount cleanup, which is fine, but `setLocation` from the still-in-flight subscribe callback can fire *after* unmount → React warns "Can't perform a state update on unmounted component". Use a ref guard or capture the unsubscribe in cleanup.

Suggested fix:
```ts
useEffect(() => {
  if (!orderId || !enabled || !tg.isInTelegram()) return;
  let unsub: (() => void) | null = null;
  let alive = true;
  const client = createStompClient();
  client.onConnect = () => {
    if (!alive) return;
    setIsConnected(true);
    unsub?.();
    unsub = subscribeOrderLocation(client, orderId, (msg) => {
      if (!alive) return;
      setLocation({ lat: Number(msg.lat), lng: Number(msg.lng), recordedAt: msg.recordedAt });
    });
  };
  client.onDisconnect = () => alive && setIsConnected(false);
  client.activate();
  return () => {
    alive = false;
    unsub?.();
    client.deactivate();
  };
}, [orderId, enabled]);
```

### IM-5. `pingRepo.save(...)` ignores the `@Column(insertable=false)` on `recorded_at` and may write a clock-skewed timestamp
File: `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/entity/LocationPing.java:38-39`
File: `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/service/LocationPingService.java:62`

The entity initializes `recordedAt = Instant.now()` (line 39) at construction, then the service overwrites with `ping.setRecordedAt(Instant.now())` (line 62) before save. Both use the Spring app server's clock, not Postgres'. The SQL column has `DEFAULT NOW()` but JPA always sends a value, so the DB default never fires.

In a deployment where the app server's wall clock drifts from the DB's, broadcast ordering and the `idx_location_ping_assignment_time` index won't agree with reality. Minor in single-host dev, but worth noting.

Suggested fix: either let Hibernate respect the DB default (`@Generated(GenerationTime.INSERT)` + drop the field initializer + don't set in service), or unify on the app clock and drop the SQL `DEFAULT NOW()` to make the source of truth explicit.

---

## Minor findings

### MN-1. `setAllowedOriginPatterns("*")` is too permissive for production
File: `backend/app/src/main/java/com/shop/delivery/config/WebSocketConfig.java:47`

Mirror what was done in `SecurityConfig`'s CORS: read allowed origins from config so prod can lock down to `t.me` / your Mini App domain.

### MN-2. `/ws/**` permitAll bypasses the HTTP-side auth filter for the SockJS info handshake
File: `backend/modules/auth/src/main/java/com/shop/delivery/auth/config/SecurityConfig.java:42`

The CONNECT-level interceptor is the right place to auth STOMP, but the SockJS handshake itself (`/ws/info`, `/ws/xxx/yyy/websocket`) accepts everyone. That's necessary because SockJS can't carry custom headers on its initial xhr probe. Just call this out in the plan — it's intentional, but a reviewer might flag it as "WS is public". The actual access control still works because STOMP CONNECT is gated.

### MN-3. `findAllByShipperIdAndStatusIn(... STARTED)` issues two queries per ping (assignment + order)
File: `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/service/LocationPingService.java:47,54`

Each Live Location update is now 4 DB round-trips per ping (find assignment, find order, save ping, … + transaction). At Telegram's ~10 s update cadence and N shippers this should be fine for a thesis, but in V8 consider denormalizing `customer_id` onto `delivery_assignment` so the service doesn't need to look up `Order` at all.

### MN-4. Leftover "(P6 sẽ có map)" placeholder string in shipper detail page
File: `frontend/miniapp/src/pages/ShipperAssignmentDetailPage.tsx:76`

```tsx
📍 {assignment.deliveryLat}, {assignment.deliveryLng} (P6 sẽ có map)
```

P6 is exactly the phase that was supposed to add the map. The text is stale. Either embed a tiny static-map preview for the shipper or just drop the "(P6 sẽ có map)" parenthetical.

### MN-5. Marker icons are loaded from `unpkg.com` and `raw.githubusercontent.com` over the public internet
File: `frontend/miniapp/src/features/tracking/OrderTrackingMap.tsx:11-31`

Telegram Mini Apps are Vietnamese-user-facing — `raw.githubusercontent.com` is sometimes flaky in VN ISPs. Bundle the marker PNGs locally (4 files, ~5 KB each) under `frontend/miniapp/public/markers/` and reference with absolute paths. Same for the Leaflet CSS in `index.html:8`.

### MN-6. `LocationMessage` type is duplicated in `@shop/shared` and `frontend/miniapp/src/lib/ws.ts`
File: `frontend/shared/src/types/location.ts:9-16`
File: `frontend/miniapp/src/lib/ws.ts:18-25`

Two identical record definitions. The miniapp's `ws.ts` should `import type { LocationMessage } from '@shop/shared'` instead of redefining it.

---

## What was done well

- **Transactional event listener with `AFTER_COMMIT` is correct.** `LocationBroadcaster` (line 35) won't fire on rollback, so customers never see ghost positions from a transaction that failed to persist.
- **Idempotent webhook handling.** `UpdateRouter` calls `processedUpdateService.markIfNew(updateId)` before dispatch (line 35), so Telegram's "retry until 200" behaviour can't double-insert pings on a slow response.
- **Customer-ownership check on REST fallback is correct.** `OrderTrackingController:39-41` rejects with the same `ORDER_NOT_FOUND` code whether the order is missing or belongs to someone else — no enumeration leak.
- **DTO conversion uses `BigDecimal` end-to-end on the backend.** No `double` rounding of lat/lng anywhere on the server path. Precision is preserved at 7 decimal places (~1 cm), matching the SQL column.
- **STOMP CONNECT header check is in place.** Even though SUBSCRIBE authz is missing (see CR-1), at least the connection itself is gated on a verified initData — unauthenticated WS connections can't be opened at all.
- **Pagination + indexes on `location_ping`.** Composite index `(assignment_id, recorded_at DESC)` is exactly what `findFirstByAssignmentIdOrderByRecordedAtDesc` needs. Good.
- **Test coverage on the two highest-risk components.** `LiveLocationHandlerTest` covers message vs edited_message vs no-location, and `LocationPingServiceTest` covers the "no active assignment" no-op branch. Both are the right tests to write first.
- **`b57a251` (the late fix to add `pickupLat`/`pickupLng` to `OrderResponse`) was the right catch.** Without it the Mini App would render `Number(undefined) → NaN` into Leaflet (see IM-3) and crash the order detail page.
- **Live Location instructions banner** (`ShipperAssignmentDetailPage:91-101`) is genuinely helpful — shippers will not know how to enable Telegram Live Location without it.
