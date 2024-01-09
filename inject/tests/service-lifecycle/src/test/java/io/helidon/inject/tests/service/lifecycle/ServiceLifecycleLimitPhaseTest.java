package io.helidon.inject.tests.service.lifecycle;

import java.util.Map;
import java.util.function.Supplier;

import io.helidon.common.types.TypeName;
import io.helidon.inject.ActivationResult;
import io.helidon.inject.InjectionConfig;
import io.helidon.inject.InjectionServices;
import io.helidon.inject.Phase;
import io.helidon.inject.Services;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

class ServiceLifecycleLimitPhaseTest {
    private static InjectionServices injectionServices;
    private static Services services;

    @BeforeAll
    static void initClass() {
        ASingletonContractImpl.INJECTIONS.set(0);
        ASingletonContractImpl.INSTANCES.set(0);
        AServiceContractImpl.INJECTIONS.set(0);
        AServiceContractImpl.INSTANCES.set(0);

        injectionServices = InjectionServices.create(InjectionConfig.builder()
                                                             .limitRuntimePhase(Phase.CONSTRUCTING)
                                                             .build());
        services = injectionServices.services();

        assertThat(services, notNullValue());
    }

    @AfterAll
    static void destroyClass() {
        if (injectionServices != null) {
            Map<TypeName, ActivationResult> shutdown = injectionServices.shutdown();
            shutdown.values()
                    .forEach(it -> {
                        if (it.failure()) {
                            fail(it.toString(), it.error().orElse(null));
                        }
                    });
        }
        injectionServices = null;
        services = null;
    }

    @Test
    void singletonLifecycleTest() {
        // there should be no instances "just" created when registry starts
        assertThat(ASingletonContractImpl.INSTANCES.get(), is(0));

        Supplier<ASingletonContract> supplier = services.supply(ASingletonContract.class);

        // there should be no instances created even when I lookup a supplier
        assertThat(ASingletonContractImpl.INSTANCES.get(), is(0));
        assertThat(supplier, notNullValue());

        ASingletonContract instance = supplier.get();

        assertThat(instance, notNullValue());

        // now we should have created a single instance
        assertThat(ASingletonContractImpl.INSTANCES.get(), is(1));
        assertThat(ASingletonContractImpl.INJECTIONS.get(), is(0));

        // now on the next lookup, I should get the same instance
        supplier = services.supply(ASingletonContract.class);
        ASingletonContract secondInstance = supplier.get();

        // must be the same
        assertThat(secondInstance, sameInstance(instance));
        // and we still should have created only a single instance
        assertThat(ASingletonContractImpl.INSTANCES.get(), is(1));
        assertThat(ASingletonContractImpl.INJECTIONS.get(), is(0));

        InjectedService firstService = instance.service();
        assertThat(firstService, nullValue());
        InjectedService secondService = secondInstance.service();
        assertThat(secondService, nullValue());
    }

    @Test
    void serviceLifecycleTest() {
        // there should be no instances "just" created when registry starts
        assertThat(AServiceContractImpl.INSTANCES.get(), is(0));

        Supplier<AServiceContract> supplier = services.supply(AServiceContract.class);

        // there should be no instances created even when I lookup a supplier
        assertThat(AServiceContractImpl.INSTANCES.get(), is(0));
        assertThat(supplier, notNullValue());

        AServiceContract instance = supplier.get();

        assertThat(instance, notNullValue());

        // now we should have created a single instance
        assertThat(AServiceContractImpl.INSTANCES.get(), is(1));
        assertThat(AServiceContractImpl.INJECTIONS.get(), is(0));

        AServiceContract secondInstance = supplier.get();

        // now we should have created another instance
        assertThat(AServiceContractImpl.INSTANCES.get(), is(2));
        assertThat(AServiceContractImpl.INJECTIONS.get(), is(0));

        // and it should be a different instance
        assertThat(secondInstance, not(sameInstance(instance)));

        // now on the next lookup, we should get another instance
        supplier = services.supply(AServiceContract.class);
        AServiceContract thirdInstance = supplier.get();

        // and another instance created
        assertThat(AServiceContractImpl.INSTANCES.get(), is(3));
        assertThat(AServiceContractImpl.INJECTIONS.get(), is(0));

        // must not be the same
        assertThat(thirdInstance, not(sameInstance(instance)));
        assertThat(thirdInstance, not(sameInstance(secondInstance)));

        // now validate the injected singleton
        InjectedService service = instance.service();
        assertThat(service, nullValue());

        InjectedService secondService = secondInstance.service();
        assertThat(secondService, nullValue());

        InjectedService thirdService = thirdInstance.service();
        assertThat(thirdService, nullValue());
    }
}
