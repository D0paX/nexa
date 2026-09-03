# Phase 6 — where the real system plugs in

Phase 5 built a mobile client with no backend. Every screen is fed by preview
data, every realtime event comes from a scripted transport, and every action
is answered by a preview publisher. That was deliberate: the client's
contracts, state model and security boundaries had to be settled before
anything real was connected to them, so that connecting reality would be an
integration rather than a rewrite.

This file records the seams that integration will use. It describes what
exists today, not what the backend should be — the interfaces below are real
and compile; the backend behind them does not exist yet.

---

## The four seams

### 1. Realtime — an interface, wired at startup

`RealtimeTransport` (`ui/realtime/RealtimeTransport.kt`) is the contract:

```kotlin
interface RealtimeTransport {
    val frames: Flow<Map<String, String>>
    val connectionState: Flow<RealtimeConnectionState>
    suspend fun connect(fromSequence: Long)
    // …
}
```

`PreviewRealtimeTransport` implements it today and `NexaApplication` wires it:

```kotlin
RealtimeConnectionManager.configure(transport = previewTransport, scopes = …)
```

**To integrate:** implement `RealtimeTransport` against the real stream and pass
it to `configure`. Nothing downstream changes. Frames still enter through
`RealtimeStore.submit`, which is the single ingress — they are parsed,
version-checked, scope-filtered, sequenced, deduplicated and reduced before any
screen sees them. A transport that emitted a malformed or out-of-order frame
would be refused by the same code that refuses one today.

The wire format the parser expects is defined by `RealtimeEventParser`: string
maps with `schemaVersion`, `eventId`, `sequence`, `eventType`, `occurredAt`,
`scope`, `subjectId`, plus per-type payload keys. Every enum is closed and an
unrecognised value is a rejection, not a default.

### 2. Push registration — an interface, currently inert

`PushTokenRegistrar` (`push/PushToken.kt`):

```kotlin
interface PushTokenRegistrar {
    suspend fun register(token: PushToken): PushRegistrationResult
    suspend fun unregister(): PushRegistrationResult
}
```

`NoOpPushTokenRegistrar` is installed today and returns `NotConfigured`, so no
token is transmitted or persisted. `PushTokenManager.configure(registrar)` is
the swap point.

The backend contract this expects is written out in full in `PushToken.kt`,
including the two requirements an implementer is most likely to get wrong: the
endpoint must authenticate the *operator* rather than the token, and
registrations must be scoped so a token cannot subscribe itself to scopes the
operator cannot already see.

There is also no Firebase project. `google-services.json` is deliberately absent
and gitignored; without it the plugin is not applied, `FirebaseApp` never
initialises, and the push layer reports its transport as unavailable rather than
pretending to be connected. Real push therefore needs both a project and a
registrar.

### 3. Deep links — two interfaces, one configure

`DeepLinkCatalog` answers whether a named object exists and what its current
address is. `DeepLinkAccessPolicy` answers whether this operator may open it.
Both live in `ui/deeplink/NexaDeepLinkResolver.kt`; `PreviewDeepLinkCatalog` and
`OpenAccessPolicy` are today's implementations, and
`DeepLinkRouter.configure(resolver)` installs a replacement.

Parsing is not part of the seam and does not change: a link is validated against
a closed scheme, a closed version, a closed destination set and a conservative
identifier pattern before the catalog or the policy is consulted at all.
Authorization is checked *before* existence, so a link an operator may not open
does not reveal whether the object is there.

### 4. Action execution — **not an interface**

This is the one seam that is a direct call rather than a contract:

```kotlin
// ActionViewModel.confirm()
PreviewActionPipeline.play(actionId, context, outcome)
```

`PreviewActionPipeline` lives in `src/main` and ships in the release build,
renamed by R8 but present. It is labelled `PREVIEW PIPELINE — NOT A REAL
ENFORCEMENT BACKEND` at its declaration. What it does is publish a scripted
lifecycle as realtime frames through `RealtimeStore.submit` — the same ingress a
live socket uses — so the client's own chain is what runs, not a `delay()` chain
inside a view model.

**To integrate:** extract an execution-publisher interface mirroring
`PushTokenRegistrar`, with the preview implementation as one of its
implementations, and have `ActionViewModel` depend on the interface. This was
identified in Phase 5.28 and deliberately not done there: it is a structural
change, and a hardening checkpoint was the wrong place for it.

Until then the honest description is: **the client requests actions and reports
what it is told; today what tells it is a preview publisher in the same
process.**

---

## What integration must not change

These are the properties Phase 5 spent its checkpoints establishing. A backend
that arrives and quietly relaxes one of them undoes the work rather than
completing it.

- **Authorization is never inferred.** Not from trust, not from presence, not
  from a notification, not from a link, not from a filter, not from a cache.
- **Unknown is not a value to resolve.** An unknown execution mode, an unknown
  authorization, an unknown enforcement state and an unknown outcome each block
  or stay unknown. None of them defaults to the permissive reading.
- **An absence is not a sighting.** An event reporting that a device is gone
  makes the observation stale, never current.
- **Execution is not reconciliation.** A succeeded action that has not been
  reconciled says so.
- **Simulation is not enforcement.** `AUDIT_ONLY` never claims a mutation.
- **One prepared context is one action.** The submission registry is the
  idempotency boundary and is keyed on the context, not the target.
- **The client never predicts.** Every lifecycle state on screen arrived as an
  event and was read back out of the store.

The test suites that hold these are `SecurityStateMatrixTest`,
`FailClosedWorkflowTest`, `ContextToActionWorkflowTest` (unit/integration) and
`SecurityWorkflowE2ETest` (instrumented, ten operator journeys).

---

## Release requirements still outstanding

- **Signing.** No production key exists. `assembleRelease` produces an unsigned
  APK; verification builds are signed locally with the debug key. A real upload
  key, and a `signingConfig` that reads it from somewhere outside the
  repository, are needed before distribution. `.gitignore` already refuses
  `*.jks`, `*.keystore`, `keystore.properties` and `signing.properties`.
- **Firebase project.** See seam 2.
- **Preview data.** Every screen's content is fixtures. Replacing the realtime
  transport does not replace them; the initial state each screen loads comes
  from its own `*PreviewData` object, and each is labelled `PREVIEW DATA — NOT
  LIVE SYSTEM STATE`.
