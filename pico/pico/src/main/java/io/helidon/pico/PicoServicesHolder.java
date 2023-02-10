/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.pico;

import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.pico.spi.CallingContext;
import io.helidon.pico.spi.PicoServicesProvider;
import io.helidon.pico.spi.Resetable;

import static io.helidon.pico.spi.CallingContext.DEBUG_HINT;
import static io.helidon.pico.spi.CallingContext.stackTraceOf;

/**
 * The holder for the globally active {@link PicoServices} singleton instance, as well as its associated
 * {@link io.helidon.pico.Bootstrap} primordial configuration.
 *
 * @deprecated use {@link PicoServices#picoServices()} or {@link PicoServices#globalBootstrap()}.
 */
// exposed in the testing module as non deprecated
public abstract class PicoServicesHolder {
    private static final AtomicReference<InternalBootstrap> BOOTSTRAP = new AtomicReference<>();
    private static final AtomicReference<ProviderAndServicesTuple> INSTANCE = new AtomicReference<>();

    /**
     * Default Constructor.
     */
    // exposed in the testing module as non deprecated
    protected PicoServicesHolder() {
    }

    /**
     * Returns the global Pico services instance. The returned service instance will be initialized with any bootstrap
     * configuration that was previously established.
     *
     * @return the loaded global pico services instance
     */
    static Optional<PicoServices> picoServices() {
        if (INSTANCE.get() == null) {
            INSTANCE.compareAndSet(null, new ProviderAndServicesTuple(load()));
            if (INSTANCE.get().picoServices == null) {
                System.getLogger(PicoServices.class.getName())
                        .log(System.Logger.Level.WARNING,
                             DefaultPicoServicesConfig.NAME + " runtime services not detected on the classpath");
            }
        }
        return Optional.ofNullable(INSTANCE.get().picoServices);
    }

    /**
     * Resets the bootstrap state.
     */
    protected static synchronized void reset() {
        ProviderAndServicesTuple instance = INSTANCE.get();
        if (instance != null) {
            instance.reset();
        }
        INSTANCE.set(null);
        BOOTSTRAP.set(null);
    }

    static void bootstrap(
            Bootstrap bootstrap) {
        Objects.requireNonNull(bootstrap);
        InternalBootstrap iBootstrap = InternalBootstrap.create(bootstrap, null);
        if (!BOOTSTRAP.compareAndSet(null, iBootstrap)) {
            InternalBootstrap existing = BOOTSTRAP.get();
            CallingContext callingContext = (existing == null) ? null : existing.callingContext().orElse(null);
            StackTraceElement[] trace = (callingContext == null) ? new StackTraceElement[] {} : callingContext.trace();
            if (trace != null && trace.length > 0) {
                throw new IllegalStateException(
                        "bootstrap was previously set from this code path:\n" + stackTraceOf(trace)
                + "; module name is '" + callingContext.moduleName().orElse("undefined") + "'");
            }
            throw new IllegalStateException("bootstrap already set - " + DEBUG_HINT);
        }
    }

    static Optional<Bootstrap> bootstrap(
            boolean assignIfNeeded) {
        if (assignIfNeeded) {
            InternalBootstrap iBootstrap = InternalBootstrap.create();
            BOOTSTRAP.compareAndSet(null, iBootstrap);
        }

        InternalBootstrap iBootstrap = BOOTSTRAP.get();
        return Optional.ofNullable((iBootstrap != null) ? iBootstrap.bootStrap() : null);
    }

    private static Optional<PicoServicesProvider> load() {
        return HelidonServiceLoader.create(ServiceLoader.load(PicoServicesProvider.class,
                                                              PicoServicesProvider.class.getClassLoader()))
                .asList()
                .stream()
                .findFirst();
    }

    // we need to keep the provider and the instance the provider creates together as one entity
    private static class ProviderAndServicesTuple {
        private final PicoServicesProvider provider;
        private final PicoServices picoServices;

        private ProviderAndServicesTuple(
                Optional<PicoServicesProvider> provider) {
            this.provider = provider.orElse(null);
            this.picoServices = (provider.isPresent())
                    ? this.provider.services(bootstrap(true).orElseThrow()) : null;
        }

        private void reset() {
            if (provider instanceof Resetable) {
                ((Resetable) provider).reset(true);
            } else if (picoServices instanceof Resetable) {
                ((Resetable) picoServices).reset(true);
            }
        }
    }

}
