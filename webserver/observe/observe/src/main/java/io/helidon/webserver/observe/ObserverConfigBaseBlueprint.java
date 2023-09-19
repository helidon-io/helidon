package io.helidon.webserver.observe;

import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.cors.CrossOriginConfig;

@Prototype.Blueprint(builderPublic = false, createEmptyPublic = false, createFromConfigPublic = false)
@Prototype.Configured
interface ObserverConfigBaseBlueprint {
    /**
     * Cors support specific to this observer.
     *
     * @return cors support to use
     */
    @Option.Configured
    Optional<CrossOriginConfig> cors();

    /**
     * Whether this observer is enabled.
     *
     * @return {@code false} to disable observer
     */
    @Option.DefaultBoolean(true)
    @Option.Configured
    boolean enabled();

    /**
     * Endpoint of this observer. Each observer should provide its own default for this property.
     *
     * @return endpoint to use
     */
    @Option.Configured
    @Option.Required
    String endpoint();

    /**
     * Name of this observer. Each observer should provide its own default for this property.
     *
     * @return observer name
     */
    @Option.Configured
    @Option.Required
    String name();
}
