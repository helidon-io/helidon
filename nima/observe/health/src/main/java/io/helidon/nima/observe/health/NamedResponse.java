package io.helidon.nima.observe.health;

import io.helidon.health.HealthCheckResponse;

record NamedResponse(String name, HealthCheckResponse response) {
}
