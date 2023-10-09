package io.helidon.webserver.context;

import java.util.Set;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Configuration of context feature.
 */
@Prototype.Blueprint
@Prototype.Configured
interface ContextFeatureConfigBlueprint extends Prototype.Factory<ContextFeature> {

    /**
     * Weight of the context feature. As it is used by other features, the default is quite high:
     * {@value io.helidon.webserver.context.ContextFeature#WEIGHT}.
     *
     * @return weight of the feature
     */
    @Option.DefaultDouble(ContextFeature.WEIGHT)
    @Option.Configured
    double weight();

    /**
     * List of sockets to register this feature on. If empty, it would get registered on all sockets.
     *
     * @return socket names to register on, defaults to empty (all available sockets)
     */
    @Option.Configured
    Set<String> sockets();

    /**
     * Name of this instance.
     *
     * @return instance name
     */
    @Option.Default("context")
    String name();
}
