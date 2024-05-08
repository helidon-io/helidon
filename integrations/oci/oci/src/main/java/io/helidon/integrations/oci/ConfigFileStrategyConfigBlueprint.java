package io.helidon.integrations.oci;

import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

@Prototype.Blueprint
@Prototype.Configured
interface ConfigFileStrategyConfigBlueprint {
    /**
     * The OCI configuration profile path.
     *
     * @return the OCI configuration profile path
     */
    @Option.Configured
    Optional<String> path();

    /**
     * The OCI configuration/auth profile name.
     *
     * @return the optional OCI configuration/auth profile name
     */
    @Option.Configured
    @Option.Default(AtnStrategyConfigFile.DEFAULT_PROFILE_NAME)
    String profile();
}
