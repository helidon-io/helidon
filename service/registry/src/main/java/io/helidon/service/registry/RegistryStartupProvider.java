package io.helidon.service.registry;

import java.util.List;
import java.util.Optional;

import io.helidon.Main;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.spi.HelidonShutdownHandler;
import io.helidon.spi.HelidonStartupProvider;

/**
 * {@link java.util.ServiceLoader} implementation of a Helidon startup provider for Helidon Service Registry based
 * applications.
 */
@Weight(Weighted.DEFAULT_WEIGHT) // explicit default weight, this should be the "default" startup class
public class RegistryStartupProvider implements HelidonStartupProvider {
    private static final System.Logger LOGGER = System.getLogger(RegistryStartupProvider.class.getName());

    /**
     * Default constructor required by {@link java.util.ServiceLoader}.
     *
     * @deprecated please do not use directly
     */
    @Deprecated
    public RegistryStartupProvider() {
    }

    /**
     * Register a shutdown handler.
     * The handler is registered, and never de-registered. This method should only be used by main classes of applications.
     * If a custom shutdown is desired, please use
     * {@link io.helidon.Main#addShutdownHandler(io.helidon.spi.HelidonShutdownHandler)} directly.
     *
     * @param registryManager registry manager
     */
    public static void registerShutdownHandler(ServiceRegistryManager registryManager) {
        System.Logger logger = System.getLogger(RegistryStartupProvider.class.getName());
        Main.addShutdownHandler(new RegistryShutdownHandler(logger, registryManager));
    }

    @Override
    public void start(String[] arguments) {
        var manager = ServiceRegistryManager.create();
        var registry = manager.registry();
        registerShutdownHandler(manager);

        for (Double runLevel : runLevels(registry)) {
            List<Object> all = registry.all(Lookup.builder()
                                                    .addScope(Service.Singleton.TYPE)
                                                    .runLevel(runLevel)
                                                    .build());
            if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                LOGGER.log(System.Logger.Level.DEBUG, "Starting services in run level: " + runLevel + ": ");
                for (Object o : all) {
                    LOGGER.log(System.Logger.Level.DEBUG, "\t" + o);
                }
            } else if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                LOGGER.log(System.Logger.Level.TRACE, "Starting services in run level: " + runLevel);
            }
        }
    }

    private List<Double> runLevels(ServiceRegistry registry) {
        // child classes will have this method code generated at build time
        return registry.lookupServices(Lookup.EMPTY)
                .stream()
                .map(ServiceInfo::runLevel)
                .flatMap(Optional::stream)
                .distinct()
                .sorted()
                .toList();
    }

    // higher than default, so we stop server as a service, not through shutdown
    @Weight(Weighted.DEFAULT_WEIGHT + 10)
    private static final class RegistryShutdownHandler implements HelidonShutdownHandler {
        private final System.Logger logger;
        private final ServiceRegistryManager registryManager;

        private RegistryShutdownHandler(System.Logger logger, ServiceRegistryManager registryManager) {
            this.logger = logger;
            this.registryManager = registryManager;
        }

        @Override
        public void shutdown() {
            try {
                registryManager.shutdown();
            } catch (Exception e) {
                logger.log(System.Logger.Level.ERROR,
                           "Failed to shutdown Helidon Inject registry",
                           e);
            }
        }

        @Override
        public String toString() {
            return "Helidon Inject shutdown handler";
        }
    }
}
