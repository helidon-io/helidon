package io.helidon.webserver.observe.config;

import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import io.helidon.builder.api.RuntimeType;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.observe.spi.Observer;

/**
 * Config Observer configuration.
 */
@RuntimeType.PrototypedBy(ConfigObserverConfig.class)
public class ConfigObserver implements Observer, RuntimeType.Api<ConfigObserverConfig> {
    private final ConfigObserverConfig config;
    private final List<Pattern> patterns;

    private ConfigObserver(ConfigObserverConfig config, List<Pattern> patterns) {
        this.config = config;
        this.patterns = patterns;
    }

    /**
     * Create a new builder to configure Config observer.
     *
     * @return a new builder
     */
    public static ConfigObserverConfig.Builder builder() {
        return ConfigObserverConfig.builder();
    }

    /**
     * Create a new Config observer using the provided configuration.
     *
     * @param config configuration
     * @return a new observer
     */
    public static ConfigObserver create(ConfigObserverConfig config) {
        List<Pattern> patterns = config.secrets()
                .stream()
                .map(Pattern::compile)
                .toList();
        return new ConfigObserver(config, patterns);
    }

    /**
     * Create a new Config observer customizing its configuration.
     *
     * @param consumer configuration consumer
     * @return a new observer
     */
    public static ConfigObserver create(Consumer<ConfigObserverConfig.Builder> consumer) {
        return builder()
                .update(consumer)
                .build();
    }

    /**
     * Create a new Config observer with default configuration.
     *
     * @return a new observer
     */
    public static ConfigObserver create() {
        return builder()
                .build();
    }

    @Override
    public ConfigObserverConfig prototype() {
        return config;
    }

    @Override
    public String type() {
        return "config";
    }

    @Override
    public void register(HttpRouting.Builder routing, String endpoint) {
        // register the service itself
        routing.register(endpoint, new ConfigService(patterns, findProfile(), config.permitAll()));
    }

    private static String findProfile() {
        // we may want to have this directly in config
        String name = System.getenv("HELIDON_CONFIG_PROFILE");
        if (name != null) {
            return name;
        }
        name = System.getProperty("helidon.config.profile");
        if (name != null) {
            return name;
        }
        name = System.getProperty("config.profile");
        if (name != null) {
            return name;
        }
        return "";
    }
}
