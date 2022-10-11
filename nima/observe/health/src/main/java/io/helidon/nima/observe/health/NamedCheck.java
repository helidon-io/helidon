package io.helidon.nima.observe.health;

import io.helidon.health.HealthCheck;

class NamedCheck {
    private final String name;
    private final HealthCheck check;

    NamedCheck(String name, HealthCheck check) {
        this.name = name;
        this.check = check;
    }

    String name() {
        return name;
    }

    HealthCheck check() {
        return check;
    }
}
