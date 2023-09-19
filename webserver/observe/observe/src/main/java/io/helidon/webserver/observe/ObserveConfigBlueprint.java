package io.helidon.webserver.observe;

import java.util.List;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.config.Config;
import io.helidon.cors.CrossOriginConfig;
import io.helidon.webserver.observe.spi.ObserveProvider;
import io.helidon.webserver.observe.spi.Observer;

@Prototype.Blueprint
@Prototype.Configured
interface ObserveConfigBlueprint extends Prototype.Factory<ObserveFeature> {
    double WEIGHT = 80;

    /**
     * Cors support inherited by each observe provider, unless explicitly configured.
     *
     * @return cors support to use
     */
    @Option.Configured
    @Option.DefaultCode("@io.helidon.webserver.cors.CrossOriginConfig@.create()")
    CrossOriginConfig cors();

    /**
     * Whether the observe support is enabled.
     *
     * @return {@code false} to disable observe feature
     */
    @Option.DefaultBoolean(true)
    @Option.Configured
    boolean enabled();

    /**
     * Root endpoint to use for observe providers. By default, all observe endpoint are under this root endpoint.
     * <p>
     * Example:
     * <br>
     * If root endpoint is {@code /observe} (the default), and default health endpoint is {@code health} (relative),
     * health endpoint would be {@code /observe/health}.
     *
     * @return endpoint to use
     */
    @Option.Default("/observe")
    @Option.Configured
    String endpoint();

    /**
     * Change the weight of this feature. This may change the order of registration of this feature.
     * By default, observability weight is {@value #WEIGHT} so it is registered after routing.
     *
     * @return weight to use
     */
    @Option.DefaultDouble(WEIGHT)
    @Option.Configured
    double weight();

    /**
     * Observers to use with this observe features.
     * Each observer type is registered only once, unless it uses a custom name (default name is the same as the type).
     *
     * @return list of observers to use in this feature
     */
    @Option.Singular
    @Option.Configured
    @Option.Provider(ObserveProvider.class)
    List<Observer> observers();

    /**
     * Configuration of the observe feature, if present.
     *
     * @return config node of the feature
     */
    Optional<Config> config();
}
