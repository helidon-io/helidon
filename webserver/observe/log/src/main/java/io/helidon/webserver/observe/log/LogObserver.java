package io.helidon.webserver.observe.log;

import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.observe.spi.Observer;

@RuntimeType.PrototypedBy(LogObserverConfig.class)
public class LogObserver implements Observer, RuntimeType.Api<LogObserverConfig> {
    private final LogObserverConfig config;

    private LogObserver(LogObserverConfig config) {
        this.config = config;
    }

    /**
     * Create a new builder to configure Log observer.
     *
     * @return a new builder
     */
    public static LogObserverConfig.Builder builder() {
        return LogObserverConfig.builder();
    }

    /**
     * Create a new Log observer using the provided configuration.
     *
     * @param config configuration
     * @return a new observer
     */
    public static LogObserver create(LogObserverConfig config) {
        return new LogObserver(config);
    }

    /**
     * Create a new Log observer customizing its configuration.
     *
     * @param consumer configuration consumer
     * @return a new observer
     */
    public static LogObserver create(Consumer<LogObserverConfig.Builder> consumer) {
        return builder()
                .update(consumer)
                .build();
    }

    /**
     * Create a new Log observer with default configuration.
     *
     * @return a new observer
     */
    public static LogObserver create() {
        return builder()
                .build();
    }

    @Override
    public LogObserverConfig prototype() {
        return config;
    }

    @Override
    public String type() {
        return "log";
    }

    @Override
    public void register(HttpRouting.Builder routing, String endpoint) {
        // register the service itself
        routing.register(endpoint, new LogService(this.config));
    }
}
