package io.helidon.integrations.oci.telemetry;

import java.util.LinkedList;
import java.util.List;

import io.helidon.integrations.oci.connect.spi.InjectionProvider;

public class OciTelemetryInjectionProvider implements InjectionProvider {
    private static final List<InjectionType<?>> INJECTABLES;

    static {
        List<InjectionType<?>> injectables = new LinkedList<>();

        injectables.add(InjectionType.create(OciMetricsRx.class,
                                             (restApi, config) -> OciMetricsRx.builder()
                                                     .restApi(restApi)
                                                     .config(config)
                                                     .build()));

        injectables.add(InjectionType.create(OciMetrics.class,
                                             (restApi, config) -> OciMetrics.create(OciMetricsRx.builder()
                                                                                            .restApi(restApi)
                                                                                            .config(config)
                                                                                            .build())));

        INJECTABLES = List.copyOf(injectables);
    }

    @Override
    public List<InjectionType<?>> injectables() {
        return INJECTABLES;
    }
}
