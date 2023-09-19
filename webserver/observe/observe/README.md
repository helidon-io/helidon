Observability support
----

The observability support groups all observe endpoints together under a single context root (the default behavior) `/observe`.

Each provider usually adds a new endpoint (such as `health`, `metrics`).
This is to have a single easily configurable path for security, proxy etc. purposes, rather than expose multiple "root" endpoints
that may collide with the business code.

# Discovery

`ObserveProvider` instances are discovered using `ServiceLoader`. In case an explicit `Observer` is registered with the
same `type` as a provider, the provider will not be used (so we do not duplicate services).

# Endpoints

The "Observe" service endpoint can be modified on the `ObserveFeature` that is registered with routing. The feature endpoint
defaults to `/observe`, and all observers are prefixed with it (see further)

Each observer has customizable endpoints as well, and the result is decided as follows:
1. If the custom endpoint is relative, the result would be under observe endpoint (e.g. for `health` -> `/observe/health`)
2. If the custom endpoint is absolute, the result would be absolute as well (e.g. for `/health` -> `/health`)

To customize endpoint of an observer:
1. Configure a custom endpoint through configuration to modify the `ObserveProvider` setup (such as `observe.health.endpoint`)
2. Configure a custom endpoint through a builder on the specific `Observer` (`HealthObserver.builder().endpoint("myhealth")`)  
