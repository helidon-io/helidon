package io.helidon.inject;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.inject.service.Injection;
import io.helidon.inject.service.Lookup;
import io.helidon.spi.HelidonStartupProvider;

/**
 * {@link java.util.ServiceLoader} provider implementation of {@link io.helidon.spi.HelidonStartupProvider}.
 * This provider starts the service registry, and activates all singleton services in
 * {@link io.helidon.inject.service.Injection.RunLevel#STARTUP} startup and lower.
 */
@Weight(Weighted.DEFAULT_WEIGHT - 10)
// lower weight, we may not want to start this up if other implementation is in control
public class InjectStartupProvider implements HelidonStartupProvider {
    @Override
    public void start(String[] arguments) {
        ManagedRegistry.create()
                .registry()
                .all(Lookup.builder()
                             .addScope(Injection.Singleton.TYPE_NAME)
                             .runLevel(Injection.RunLevel.STARTUP)
                             .build()
                );
    }
}
