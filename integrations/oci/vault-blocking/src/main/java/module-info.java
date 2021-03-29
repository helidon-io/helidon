module io.helidon.integrations.oci.vault.blocking {
    requires io.helidon.integrations.oci.connect;
    requires io.helidon.integrations.oci.vault;
    requires io.helidon.config;
    requires io.helidon.integrations.common.rest;

    exports io.helidon.integrations.oci.vault.blocking;

    provides io.helidon.integrations.oci.connect.spi.InjectionProvider
        with io.helidon.integrations.oci.vault.blocking.OciVaultInjectionProvider;
}