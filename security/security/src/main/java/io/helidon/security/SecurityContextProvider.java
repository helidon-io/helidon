package io.helidon.security;

import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.common.context.Context;
import io.helidon.inject.service.Injection;

@Injection.RequestScope
class SecurityContextProvider implements Supplier<SecurityContext> {
    private final Security security;
    private final Context context;

    SecurityContextProvider(Security security, Context context) {
        this.security = security;
        this.context = context;
    }

    @Override
    public SecurityContext get() {
        Optional<SecurityContext> securityContext = context.get(SecurityContext.class);

        if (securityContext.isPresent()) {
            return securityContext.get();
        }
        SecurityContext ctx = security.createContext(context.id());
        context.register(ctx);

        return ctx;
    }
}
