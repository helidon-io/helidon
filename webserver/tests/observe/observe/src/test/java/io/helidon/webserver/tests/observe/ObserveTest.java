package io.helidon.webserver.tests.observe;

import io.helidon.common.config.Config;
import io.helidon.common.config.GlobalConfig;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.observe.ObserveFeature;
import io.helidon.webserver.observe.health.HealthObserver;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

@ServerTest
class ObserveTest {
    private static TestHealthCheck healthCheck;

    @SetUpRoute
    static void routing(HttpRouting.Builder routing) {
        Config config = GlobalConfig.config();

        // quite often we need to pass something to the health check, so this represents a real usage
        healthCheck = new TestHealthCheck("message");
        // possible customization of metrics
        MeterRegistry meterRegistry = Metrics.globalRegistry();

        InfoObserver info = InfoObserver.builder()
                .addValue("name", "ObserveTest")
                .addValue("description", "Test for observability features")
                .addValue("version", "1.0.0")
                .build();
        MetricsObserver metrics = MetricsObserver.builder()
                .meterRegistry(meterRegistry)
                .build();

        HealthObserver health = HealthObserver.create(healthCheck);

        routing.addFeature(ObserveFeature.builder()
                                   .addObserver(health)
                                   .addObservers(info)
                                   .addObserver(metrics)
                                   .config(config.get("observe"))
                                   .build());
    }
}
