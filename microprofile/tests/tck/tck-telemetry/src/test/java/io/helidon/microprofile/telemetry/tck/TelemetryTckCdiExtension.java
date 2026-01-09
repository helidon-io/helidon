package io.helidon.microprofile.telemetry.tck;

import io.opentelemetry.api.GlobalOpenTelemetry;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.BeforeShutdown;
import jakarta.enterprise.inject.spi.Extension;

public class TelemetryTckCdiExtension implements Extension {

    void clear(@Observes BeforeShutdown beforeShutdown) {
        GlobalOpenTelemetry.resetForTest();
    }
}
