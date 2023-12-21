package io.helidon.security;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import io.helidon.common.context.Contexts;
import io.helidon.inject.service.Injection;

@Injection.Requeston
class SecurityContextProvider implements Supplier<SecurityContext> {
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private final Security security;

    @Injection.Inject
    SecurityContextProvider(Security security) {
        this.security = security;
    }

    @Override
    public SecurityContext get() {
        return Contexts.context()
                .flatMap(it -> it.get(SecurityContext.class))
                .orElseGet(() -> {
                    SecurityContext context = security.createContext("ctx_" + COUNTER.incrementAndGet());
                    Contexts.context().ifPresent(it -> it.register(context));
                    return context;
                });
    }
}
