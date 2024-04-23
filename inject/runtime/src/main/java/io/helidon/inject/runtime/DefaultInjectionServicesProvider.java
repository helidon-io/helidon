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

package io.helidon.inject.runtime;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.Weight;
import io.helidon.inject.api.Bootstrap;
import io.helidon.inject.api.InjectionServices;
import io.helidon.inject.api.Resettable;
import io.helidon.inject.spi.InjectionServicesProvider;

import jakarta.inject.Singleton;

import static io.helidon.inject.api.ServiceInfoBasics.DEFAULT_INJECT_WEIGHT;

/**
 * The default implementation for {@link InjectionServicesProvider}.
 * The first instance created (or first after calling deep {@link #reset}) will be the global services instance. The global
 * instance will track the set of loaded modules and applications that are loaded by this JVM.
 *
 * @see InjectionServices#injectionServices()
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
@Singleton
@Weight(DEFAULT_INJECT_WEIGHT)
public class DefaultInjectionServicesProvider implements InjectionServicesProvider, Resettable {
    private static final AtomicReference<DefaultInjectionServices> INSTANCE = new AtomicReference<>();

    /**
     * Service loader based constructor.
     *
     * @deprecated this is a Java ServiceLoader implementation and the constructor should not be used directly
     */
    @Deprecated
    public DefaultInjectionServicesProvider() {
    }

    @Override
    public InjectionServices services(Bootstrap bootstrap) {
        Objects.requireNonNull(bootstrap);
        if (INSTANCE.get() == null) {
            DefaultInjectionServices global = new DefaultInjectionServices(bootstrap, true);
            INSTANCE.compareAndSet(null, global);
        }

        if (INSTANCE.get().bootstrap().equals(bootstrap)) {
            // the global one
            return INSTANCE.get();
        }

        // not the global one
        return new DefaultInjectionServices(bootstrap, false);
    }

    @Override
    public boolean reset(boolean deep) {
        DefaultInjectionServices services = INSTANCE.get();
        boolean result = (services != null);
        if (services != null) {
            services.reset(deep);
            if (deep) {
                INSTANCE.set(null);
            }
        }
        return result;
    }

}
