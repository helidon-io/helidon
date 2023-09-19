package io.helidon.webserver.observe.spi;

import java.util.function.UnaryOperator;

import io.helidon.common.config.NamedService;
import io.helidon.cors.CrossOriginConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.observe.ObserverConfigBase;

public interface Observer extends NamedService {
    /**
     * Configuration of this observer.
     *
     * @return configuration of the observer
     */
    ObserverConfigBase prototype();

    /**
     * Type of this observer, to make sure we do not configure an observer both from {@link java.util.ServiceLoader} and
     * using programmatic approach.
     * If it is desired to have more than one observer of the same type, always use programmatic approach
     *
     * @return type of this observer, should match {@link io.helidon.webserver.observe.spi.ObserveProvider#type()}
     */
    @Override
    String type();

    @Override
    default String name() {
        return prototype().name();
    }

    /**
     * Register the provider's services and handlers to the routing builder.
     *
     * @param routing routing builder
     */
    default void register(HttpRouting.Builder routing) {
        register(routing,
                 UnaryOperator.identity(),
                 prototype().cors().orElseGet(CrossOriginConfig::create));
    }

    /**
     * Register the observer features, CORS, services, and/or filters.
     * This is used by the observe feature.
     *
     * @param routing          routing builder
     * @param endpointFunction to obtain component path based on the configured observer endpoint
     * @param cors             cors to use when setting up the endpoint
     */
    void register(HttpRouting.Builder routing,
                  UnaryOperator<String> endpointFunction,
                  CrossOriginConfig cors);
}
