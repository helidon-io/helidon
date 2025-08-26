package io.helidon.security.providers.oidc.common;

import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Configuration of the OIDC client credentials flow.
 */
@Prototype.Blueprint
@Prototype.Configured
interface ClientCredentialsConfigBlueprint {

    /**
     * Scope used when obtaining access token in the client credentials flow.
     * It is required to set when {@code server-type} is set as an {@code idcs}.
     *
     * @return client credentials scope
     */
    @Option.Configured
    Optional<String> scope();

}
