package io.helidon.webserver.security;

import java.util.List;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Configuration of security feature fow webserver.
 */
@Prototype.Blueprint
@Prototype.Configured
interface SecurityServerFeatureConfigBlueprint {
    /**
     * The default security handler.
     *
     * @return security handler defaults
     */
    @Option.Configured
    SecurityHandlerConfig defaults();

    /**
     * Configuration for webserver paths.
     *
     * @return path configuration
     */
    @Option.Configured
    @Option.Singular
    List<PathsConfig> paths();
}
