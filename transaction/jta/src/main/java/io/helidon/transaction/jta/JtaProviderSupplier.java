/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.transaction.jta;

import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.Service;

/**
 * JTA Providers registry.
 */
@Service.Singleton
class JtaProviderSupplier implements Supplier<Optional<JtaProvider>> {

    private static final System.Logger LOGGER = System.getLogger(JtaProviderSupplier.class.getName());

    private final Optional<JtaProvider> provider;

    @Service.Inject
    JtaProviderSupplier(Optional<JtaProvider> provider) {
        this.provider = provider;
    }

    @Override
    public Optional<JtaProvider> get() {
        return provider;
    }

    // Pkg only shortcut
    boolean exists() {
        return provider.isPresent();
    }

    static JtaProviderSupplier getInstance() {
        return Instance.INSTANCE;
    }

    // Lazy initialization of programmatic lookup with 1st getInstance usage.
    // Missing service will not cause an exception to be thrown, but it will mark provider as non-existent.
    private final static class Instance {

        private static final JtaProviderSupplier INSTANCE = GlobalServiceRegistry.registry()
                .get(JtaProviderSupplier.class);

    }

}
