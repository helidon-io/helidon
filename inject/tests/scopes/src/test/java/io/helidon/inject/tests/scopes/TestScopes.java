package io.helidon.inject.tests.scopes;

import java.util.Map;
import java.util.function.Supplier;

import io.helidon.inject.InjectionConfig;
import io.helidon.inject.InjectionServices;
import io.helidon.inject.RequestonControl;
import io.helidon.inject.Scope;
import io.helidon.inject.ScopeNotAvailableException;
import io.helidon.inject.Services;
import io.helidon.inject.service.Injection;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestScopes {
    private InjectionServices injectionServices;
    private Services services;

    @BeforeEach
    void init() {
        injectionServices = InjectionServices.create(InjectionConfig.create());
        services = injectionServices.services();
    }

    @AfterEach
    void destroy() {
        InjectionServices toShutdown = injectionServices;
        injectionServices = null;
        services = null;

        if (toShutdown != null) {
            toShutdown.shutdown();
        }
    }

    @Test
    void testScopeNotAvailable() {
        Supplier<SingletonContract> serviceProvider = services.get(SingletonContract.class);
        SingletonContract service = serviceProvider.get();

        ScopeNotAvailableException scopeNotAvailableException = assertThrows(ScopeNotAvailableException.class, service::id);
        assertThat(scopeNotAvailableException.scope(), is(Injection.Requeston.TYPE_NAME));
    }

    @Test
    void testDifferentScopeDifferentValue() {
        Supplier<SingletonContract> serviceProvider = services.get(SingletonContract.class);
        SingletonContract service = serviceProvider.get();

        RequestonControl requestonControl = services.get(RequestonControl.class).get();

        int id = -1;
        try (Scope scope = requestonControl.startRequestScope(Map.of())) {
            id = service.id();
            assertThat("We should get a request scope based id", id, not(-1));
        }

        ScopeNotAvailableException scopeNotAvailableException = assertThrows(ScopeNotAvailableException.class, service::id);
        assertThat("We should not be in scope when it has been closed",
                   scopeNotAvailableException.scope(),
                   is(Injection.Requeston.TYPE_NAME));

        try (Scope scope = requestonControl.startRequestScope(Map.of())) {
            int nextId = service.id();
            assertThat("We should get a request scope based id", nextId, not(-1));
            assertThat("We should get a different request scope than last time", nextId, not(id));
        }
    }
}
