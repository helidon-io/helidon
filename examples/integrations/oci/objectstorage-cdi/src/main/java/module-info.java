module io.helidon.examples.integrations.oci.objectstorage.cdi {
    requires java.ws.rs;
    requires java.json.bind;
    requires jakarta.inject.api;
    requires microprofile.config.api;

    requires io.helidon.config.yaml;
    requires io.helidon.common.http;
    requires io.helidon.integrations.common.rest;
    requires io.helidon.integrations.oci.objectstorage;
    requires io.helidon.microprofile.cdi;

    exports io.helidon.examples.integrations.oci.objectstorage.cdi;

    opens io.helidon.examples.integrations.oci.objectstorage.cdi to weld.core.impl, io.helidon.microprofile.cdi;
}