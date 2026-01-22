package io.helidon.service.tests.shutdown.from.startup;

import io.helidon.service.registry.Service;

@Service.Singleton
@Service.RunLevel(Service.RunLevel.STARTUP + 10)
class ShutdownService {
    @Service.PostConstruct
    void init() {
        System.exit(191);
    }
}
