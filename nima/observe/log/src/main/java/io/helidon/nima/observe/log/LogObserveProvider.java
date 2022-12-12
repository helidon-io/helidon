package io.helidon.nima.observe.log;

import io.helidon.config.Config;
import io.helidon.nima.observe.spi.ObserveProvider;
import io.helidon.nima.webserver.http.HttpRouting;

public class LogObserveProvider implements ObserveProvider {
    @Override
    public String configKey() {
        return "log";
    }

    @Override
    public String defaultEndpoint() {
        return "log";
    }

    @Override
    public void register(Config config, String componentPath, HttpRouting.Builder routing) {
        routing.register(componentPath, LogService.create(config));
    }
}
