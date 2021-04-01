module io.helidon.examples.integrations.oci.metrics.reactive {
    requires java.logging;

    requires io.helidon.config;
    requires io.helidon.integrations.oci.telemetry;

    exports io.helidon.examples.integrations.oci.telemetry.reactive;
}