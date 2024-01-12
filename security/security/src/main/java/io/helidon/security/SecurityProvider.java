package io.helidon.security;

import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.common.config.Config;
import io.helidon.common.context.Contexts;
import io.helidon.inject.service.Injection;

@Injection.Singleton
class SecurityProvider implements Supplier<Security> {
    private final LazyValue<Security> security;

    SecurityProvider(Config config) {
        security = LazyValue.create(() -> getOrCreate(config));
    }

    @Override
    public Security get() {
        return security.get();
    }

    private static Security getOrCreate(Config config) {
        return Contexts.globalContext()
                .get(Security.class)
                .orElseGet(() -> Security.create(config.get("security")));
    }
}
