module io.helidon.integrations.vault.secrets.transit {
    requires java.json;

    requires io.helidon.integrations.common.rest;
    requires io.helidon.integrations.vault;
    requires io.helidon.common.http;
    requires static io.helidon.security;

    exports io.helidon.integrations.vault.secrets.transit;

    provides io.helidon.integrations.vault.spi.SecretsEngineProvider
            with io.helidon.integrations.vault.secrets.transit.TransitEngineProvider;

    provides io.helidon.security.spi.SecurityProviderService
            with io.helidon.integrations.vault.secrets.transit.TransitSecurityService;

    provides io.helidon.integrations.vault.spi.InjectionProvider
            with io.helidon.integrations.vault.secrets.transit.TransitEngineProvider;
}