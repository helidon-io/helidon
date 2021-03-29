package io.helidon.integrations.oci.connect;

import java.security.KeyPair;

import io.helidon.common.reactive.Single;

public abstract class OciConfigPrincipalBase {
    private final SessionKeySupplier keySupplier;
    private final FederationClient federationClient;

    protected OciConfigPrincipalBase(Builder<?> builder) {
        this.keySupplier = builder.keySupplier;
        this.federationClient = builder.federationClient;
    }

    protected interface SessionKeySupplier {
        KeyPair keyPair();
        Single<KeyPair> refresh();
    }

    protected interface FederationClient {
        Single<String> securityToken();

        Single<String> refreshSecurityToken();

        Single<String> claim(String claimName);
    }

    public static class Builder<B extends Builder<B>> {
        private SessionKeySupplier keySupplier;
        private FederationClient federationClient;
    }
}
