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

package io.helidon.data.jakarta.persistence;

import java.util.List;

import io.helidon.common.Weight;
import io.helidon.data.DataConfig;
import io.helidon.data.jakarta.persistence.spi.JakartaPersistenceExtension;
import io.helidon.data.jakarta.persistence.spi.JakartaPersistenceExtensionProvider;
import io.helidon.data.jakarta.persistence.spi.JpaEntityProvider;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.transaction.spi.TxSupport;

@Weight(90)
@Service.Singleton
class JpaExtensionProviderService implements JakartaPersistenceExtensionProvider {
    private final List<JpaEntityProvider<?>> providers;
    private final ServiceRegistry registry;
    private final TxSupport txSupport;

    @Service.Inject
    JpaExtensionProviderService(List<JpaEntityProvider<?>> providers, ServiceRegistry registry, TxSupport txSupport) {
        this.providers = providers;
        this.registry = registry;
        this.txSupport = txSupport;
    }

    @Override
    public JakartaPersistenceExtension create(DataConfig config) {
        return new JpaExtensionImpl(config, registry, providers, txSupport);
    }

}
