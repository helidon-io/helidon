package io.helidon.webserver.observe.info;

import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.observe.spi.Observer;

@RuntimeType.PrototypedBy(InfoObserverConfig.class)
public class InfoObserver implements Observer, RuntimeType.Api<InfoObserverConfig> {
    private final InfoObserverConfig config;

    private InfoObserver(InfoObserverConfig config) {
        this.config = config;
    }

    /**
     * Create a new builder to configure Info observer.
     *
     * @return a new builder
     */
    public static InfoObserverConfig.Builder builder() {
        return InfoObserverConfig.builder();
    }

    /**
     * Create a new Info observer using the provided configuration.
     *
     * @param config configuration
     * @return a new observer
     */
    public static InfoObserver create(InfoObserverConfig config) {
        return new InfoObserver(config);
    }

    /**
     * Create a new Info observer customizing its configuration.
     *
     * @param consumer configuration consumer
     * @return a new observer
     */
    public static InfoObserver create(Consumer<InfoObserverConfig.Builder> consumer) {
        return builder()
                .update(consumer)
                .build();
    }

    /**
     * Create a new Info observer with default configuration.
     *
     * @return a new observer
     */
    public static InfoObserver create() {
        return builder()
                .build();
    }

    @Override
    public InfoObserverConfig prototype() {
        return config;
    }

    @Override
    public String type() {
        return "info";
    }

    @Override
    public void register(HttpRouting.Builder routing, String endpoint) {
        // register the service itself
        routing.register(endpoint, new InfoService(this.config.values()));
    }
}
