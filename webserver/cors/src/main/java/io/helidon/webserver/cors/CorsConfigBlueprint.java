package io.helidon.webserver.cors;

import java.util.Optional;
import java.util.Set;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.config.Config;

/**
 * Configuration of CORS feature.
 */
@Prototype.Blueprint
@Prototype.Configured
interface CorsConfigBlueprint extends Prototype.Factory<CorsFeature> {

    /**
     * Weight of the CORS feature. As it is used by other features, the default is quite high:
     * {@value CorsFeature#WEIGHT}.
     *
     * @return weight of the feature
     */
    @Option.DefaultDouble(CorsFeature.WEIGHT)
    @Option.Configured
    double weight();

    /**
     * List of sockets to register this feature on. If empty, it would get registered on all sockets.
     *
     * @return socket names to register on, defaults to empty (all available sockets)
     */
    @Option.Configured
    @Option.Singular
    Set<String> sockets();

    /**
     * Name of this instance.
     *
     * @return instance name
     */
    @Option.Default(CorsFeature.CORS_ID)
    String name();

    /**
     * This feature can be disabled.
     *
     * @return whether the feature is enabled
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean enabled();

    /**
     * Access to config that was used to create this feature.
     *
     * @return configuration
     */
    Optional<Config> config();
}
