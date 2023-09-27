/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.integrations.oci.tls.certificates;

import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

import io.helidon.common.serviceloader.HelidonServiceLoader;

import jakarta.inject.Provider;

class InjectionServices {

    private InjectionServices() {
    }

    static Services realizedServices() {
        return new Services();
    }

    static class Services {
        public <T> Provider<T> lookupFirst(Class<T> serviceProviderType) {
            return lookupFirst(serviceProviderType, true).orElseThrow();
        }

        public <T> Optional<Provider<T>> lookupFirst(Class<T> serviceProviderType,
                                                     boolean expected) {
            List<T> exemplarServices =
                    HelidonServiceLoader.create(ServiceLoader.load(serviceProviderType)).asList();
            if (exemplarServices.isEmpty()) {
                if (expected) {
                    throw new IllegalStateException("Expected to find a service provider of type: " + serviceProviderType);
                }

                return Optional.empty();
            }

            return Optional.of(new SingletonProvider<>(exemplarServices.get(0)));
        }
    }

    static class SingletonProvider<T> implements Provider<T> {
        private final T instance;

        SingletonProvider(T instance) {
            this.instance = instance;
        }

        @Override
        public T get() {
            return instance;
        }
    }

}
