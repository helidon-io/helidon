package io.helidon.webserver.observe.health;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.config.Config;
import io.helidon.cors.CrossOriginConfig;
import io.helidon.health.HealthCheck;
import io.helidon.health.spi.HealthCheckProvider;
import io.helidon.http.Status;
import io.helidon.webserver.cors.CorsSupport;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.observe.spi.Observer;

@RuntimeType.PrototypedBy(HealthObserverConfig.class)
public class HealthObserver implements Observer, RuntimeType.Api<HealthObserverConfig> {
    private final HealthObserverConfig config;
    private final List<HealthCheck> all;

    private HealthObserver(HealthObserverConfig config) {
        this.config = config;

        List<HealthCheck> checks = new ArrayList<>(config.healthChecks());
        if (config.useSystemServices()) {
            Config cfg = config.config().orElseGet(Config::empty);
            HelidonServiceLoader.create(ServiceLoader.load(HealthCheckProvider.class))
                    .asList()
                    .stream()
                    .map(provider -> provider.healthChecks(cfg))
                    .flatMap(Collection::stream)
                    .forEach(checks::add);
        }
        // checks now contain all health checks we want to use in this instance
        this.all = List.copyOf(checks);
    }

    public static HealthObserver create(HealthCheck... healthChecks) {
        return builder()
                .addHealthChecks(Arrays.asList(healthChecks))
                .build();
    }

    public static HealthObserverConfig.Builder builder() {
        return HealthObserverConfig.builder();
    }

    public static HealthObserver create(HealthObserverConfig config) {
        return new HealthObserver(config);
    }

    public static HealthObserver create(Consumer<HealthObserverConfig.Builder> consumer) {
        return builder()
                .update(consumer)
                .build();
    }

    @Override
    public void register(HttpRouting.Builder routing, UnaryOperator<String> endpointFunction, CrossOriginConfig cors) {
        String endpoint = endpointFunction.apply(config.endpoint());

        if (config.enabled()) {
            // configure CORS
            routing.register(endpoint + "/*", CorsSupport.builder()
                    .name("health")
                    .addCrossOrigin(cors)
                    .build());

            // register the service itself
            routing.register(endpoint, new HealthService(config, all));
        } else {
            // not available
            routing.get(endpoint + "/*", (req, res) -> res.status(Status.SERVICE_UNAVAILABLE_503)
                    .send());
        }
    }

    @Override
    public String type() {
        return "health";
    }

    @Override
    public HealthObserverConfig prototype() {
        return config;
    }
}
