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
import io.helidon.pico.spi.PicoServicesProvider;
import io.helidon.pico.spi.Resetable;

/**
 * The holder for the globally active {@link PicoServices} singleton instance, as well as its associated
 * {@link io.helidon.pico.Bootstrap} primordial configuration.
 */
public class PicoServicesHolder {
    private static final AtomicReference<Bootstrap> BOOTSTRAP = new AtomicReference<>();
    private static final AtomicReference<ProviderAndServicesTuple> INSTANCE = new AtomicReference<>();

    /**
     * Default Constructor.
     *
     * @deprecated
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
    public static Optional<PicoServices> picoServices() {
        if (INSTANCE.get() == null) {
            INSTANCE.compareAndSet(null, new ProviderAndServicesTuple(load()));
        }
        return Optional.ofNullable(INSTANCE.get().picoServices);
    }

    /**
     * Resets the bootstrap state.
     *
     * @deprecated
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
        if (!BOOTSTRAP.compareAndSet(null, Objects.requireNonNull(bootstrap))) {
            throw new IllegalStateException("bootstrap already set");
        }
    }

    static Optional<Bootstrap> bootstrap(
            boolean assignIfNeeded) {
        if (assignIfNeeded) {
            BOOTSTRAP.compareAndSet(null, DefaultBootstrap.builder().build());
        }

        return Optional.ofNullable(BOOTSTRAP.get());
    }

    private static Optional<PicoServicesProvider> load() {
        return HelidonServiceLoader.create(ServiceLoader.load(PicoServicesProvider.class))
                .asList()
                .stream()
                .findFirst();
    }

    // we need to keep the provider and the instance the provider creates together as one entity
    private static class ProviderAndServicesTuple {
        final PicoServicesProvider provider;
        final PicoServices picoServices;

        private ProviderAndServicesTuple(
                Optional<PicoServicesProvider> provider) {
            this.provider = provider.orElse(null);
            this.picoServices = (provider.isPresent())
                    ? this.provider.services(bootstrap(true).orElse(null)) : null;
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
