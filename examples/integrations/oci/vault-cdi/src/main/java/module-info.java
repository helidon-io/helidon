module io.helidon.examples.integrations.oci.vault.cdi {
    requires java.json.bind;
    requires java.ws.rs;

    requires jakarta.inject.api;

    requires microprofile.config.api;

    requires io.helidon.config.yaml;
    requires io.helidon.integrations.oci.vault;
    requires io.helidon.microprofile.cdi;

    exports io.helidon.examples.integrations.oci.vault.cdi;

    opens io.helidon.examples.integrations.oci.vault.cdi to weld.core.impl, io.helidon.microprofile.cdi;
}