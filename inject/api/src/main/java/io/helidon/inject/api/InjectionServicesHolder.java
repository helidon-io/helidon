/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.inject.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.inject.spi.InjectionServicesProvider;

/**
 * The holder for the globally active {@link InjectionServices} singleton instance, as well as its associated
 * {@link Bootstrap} primordial configuration.
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
// exposed in the testing module as non deprecated
public abstract class InjectionServicesHolder {
    /**
     * Helpful hint to give developers needing to see more debug info.
     */
    public static final String DEBUG_HINT = "use the (-D and/or -A) tag 'inject.debug=true' to see full trace output.";

    private static final AtomicReference<InternalBootstrap> BOOTSTRAP = new AtomicReference<>();
    private static final AtomicReference<ProviderAndServicesTuple> INSTANCE = new AtomicReference<>();
    private static final List<Resettable> RESETTABLES = new ArrayList<>();
    private static final Lock RESETTABLES_LOCK = new ReentrantLock();

    /**
     * Default Constructor.
     *
     * @deprecated use {@link InjectionServices#injectionServices()} or {@link InjectionServices#globalBootstrap()}.
     */
    // exposed in the testing module as non deprecated
    @Deprecated
    protected InjectionServicesHolder() {
    }

    /**
     * Returns the global Injection services instance. The returned service instance will be initialized with any bootstrap
     * configuration that was previously established.
     *
     * @return the loaded global services instance
     */
    static Optional<InjectionServices> injectionServices() {
        if (INSTANCE.get() == null) {
            INSTANCE.compareAndSet(null, new ProviderAndServicesTuple(load()));
            if (INSTANCE.get().injectionServices == null) {
                System.getLogger(InjectionServices.class.getName())
                        .log(System.Logger.Level.WARNING,
                             "Injection runtime services not detected on the classpath");
            }
        }
        return Optional.ofNullable(INSTANCE.get().injectionServices);
    }

    /**
     * Resets the bootstrap state.
     */
    protected static void reset() {
        ProviderAndServicesTuple instance = INSTANCE.get();
        if (instance != null) {
            instance.reset();
        }

        try {
            RESETTABLES_LOCK.lock();
            RESETTABLES.forEach(it -> it.reset(true));
            RESETTABLES.clear();
        } finally {
            RESETTABLES_LOCK.unlock();
        }

        INSTANCE.set(null);
        BOOTSTRAP.set(null);
    }

    /**
     * Register a resettable instance. When {@link #reset()} is called, this instance is removed from the list.
     *
     * @param instance resettable type that can be reset during testing
     */
    protected static void addResettable(Resettable instance) {
        try {
            RESETTABLES_LOCK.lock();
            RESETTABLES.add(instance);
        } finally {
            RESETTABLES_LOCK.unlock();
        }

    }

    static void bootstrap(Bootstrap bootstrap) {
        Objects.requireNonNull(bootstrap);
        InternalBootstrap iBootstrap = InternalBootstrap.builder().bootStrap(bootstrap).build();

        InternalBootstrap existing = BOOTSTRAP.compareAndExchange(null, iBootstrap);
        if (existing != null) {
            CallingContext callingContext = existing.callingContext().orElse(null);
            StackTraceElement[] trace = (callingContext == null)
                    ? new StackTraceElement[] {}
                    : callingContext.stackTrace().orElse(null);
            if (trace != null && trace.length > 0) {
                throw new IllegalStateException(
                        "bootstrap was previously set from this code path:\n" + prettyPrintStackTraceOf(trace)
                                + "; module name is '" + callingContext.moduleName().orElse("undefined") + "'");
            }
            throw new IllegalStateException("The bootstrap has already been set - " + DEBUG_HINT);
        }
    }

    static Optional<Bootstrap> bootstrap(boolean assignIfNeeded) {
        if (assignIfNeeded) {
            InternalBootstrap iBootstrap = InternalBootstrap.create();
            BOOTSTRAP.compareAndSet(null, iBootstrap);
        }

        InternalBootstrap iBootstrap = BOOTSTRAP.get();
        return Optional.ofNullable(iBootstrap)
                .flatMap(InternalBootstrap::bootStrap);
    }

    /**
     * Returns a stack trace as a list of strings.
     *
     * @param trace the trace
     * @return the list of strings for the stack trace
     */
    static List<String> stackTraceOf(StackTraceElement[] trace) {
        List<String> result = new ArrayList<>();
        for (StackTraceElement e : trace) {
            result.add(e.toString());
        }
        return result;
    }

    /**
     * Returns a stack trace as a CRLF joined string.
     *
     * @param trace the trace
     * @return the stringified stack trace
     */
    static String prettyPrintStackTraceOf(StackTraceElement[] trace) {
        return String.join("\n", stackTraceOf(trace));
    }

    private static Optional<InjectionServicesProvider> load() {
        return HelidonServiceLoader.create(ServiceLoader.load(InjectionServicesProvider.class,
                                                              InjectionServicesProvider.class.getClassLoader()))
                .asList()
                .stream()
                .findFirst();
    }

    // we need to keep the provider and the instance the provider creates together as one entity
    private static class ProviderAndServicesTuple {
        private final InjectionServicesProvider provider;
        private final InjectionServices injectionServices;

        private ProviderAndServicesTuple(Optional<InjectionServicesProvider> provider) {
            this.provider = provider.orElse(null);
            this.injectionServices = provider.isPresent()
                    ? this.provider.services(bootstrap(true)
                                                     .orElseThrow(() -> new InjectionException("Failed to assign bootstrap")))
                    : null;
        }

        private void reset() {
            if (provider instanceof Resettable) {
                ((Resettable) provider).reset(true);
            } else if (injectionServices instanceof Resettable) {
                ((Resettable) injectionServices).reset(true);
            }
        }
    }

}
