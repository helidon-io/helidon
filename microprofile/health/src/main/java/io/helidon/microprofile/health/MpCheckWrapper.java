package io.helidon.microprofile.health;

import java.util.Locale;

import io.helidon.health.HealthCheckResponse;
import io.helidon.health.HealthCheckType;

class MpCheckWrapper implements MpHealthCheck {
    private final String name;
    private final String path;
    private final HealthCheckType type;
    private final org.eclipse.microprofile.health.HealthCheck delegate;

    MpCheckWrapper(String name, String path, HealthCheckType type, org.eclipse.microprofile.health.HealthCheck delegate) {
        this.name = name;
        this.path = path;
        this.type = type;
        this.delegate = delegate;
    }

    static MpCheckWrapper create(HealthCheckType type, org.eclipse.microprofile.health.HealthCheck delegate) {
        String name;
        try {
            name = delegate.call().getName();
        } catch (Throwable e) {
            name = delegate.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        }
        return new MpCheckWrapper(name,
                                  name,
                                  type,
                                  delegate);
    }

    @Override
    public HealthCheckType type() {
        return type;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String path() {
        return path;
    }

    @Override
    public HealthCheckResponse call() {
        org.eclipse.microprofile.health.HealthCheckResponse response = delegate.call();

        // map to Helidon health check response
        return HealthCheckResponse.builder()
                .status(response.getStatus() == org.eclipse.microprofile.health.HealthCheckResponse.Status.UP)
                .update(it -> response.getData().ifPresent(details -> details.forEach(it::detail)))
                .build();
    }

    public Class<?> checkClass() {
        return delegate.getClass();
    }
}
