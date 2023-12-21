package io.helidon.security;

import java.util.function.Supplier;

import io.helidon.common.config.Config;
import io.helidon.common.context.Contexts;
import io.helidon.inject.service.Injection;

@Injection.Singleton
class SecurityProvider implements Supplier<Security> {
    private final Config config;

    @Injection.Inject
    SecurityProvider(Config config) {
        this.config = config;
    }

    @Injection.Requeston
    @Override
    public Security get() {
        return Contexts.context()
                .flatMap(it -> it.get(Security.class))
                .orElseGet(() -> {
                    // eventually we should support security providers as services
                    Security security = Security.create(config);
                    Contexts.globalContext().register(security);
                    return security;
                });
    }
}
