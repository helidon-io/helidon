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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.data.api.Data;
import io.helidon.data.api.DataConfig;
import io.helidon.service.inject.api.Injection;
import io.helidon.service.inject.api.Qualifier;

import static io.helidon.data.jakarta.persistence.DataJpaSupportProviderService.PROVIDER_TYPE;

@Injection.Singleton
@Data.SupportType(PROVIDER_TYPE)
@Injection.Named(Injection.Named.WILDCARD_NAME)
class DataJpaSupportFactory implements Injection.ServicesFactory<DataJpaSupport> {
    private static final Qualifier SUPPORT_TYPE_QUALIFIER = Qualifier.create(Data.SupportType.class, PROVIDER_TYPE);
    private final List<DataConfig> dataConfig;
    private final DataJpaSupportProviderService provider;

    @Injection.Inject
    DataJpaSupportFactory(@Injection.Named(Injection.Named.WILDCARD_NAME) List<DataConfig> dataConfigs,
                          @Injection.Named(PROVIDER_TYPE) DataJpaSupportProviderService provider) {
        this.dataConfig = dataConfigs;
        this.provider = provider;
    }

    @Override
    public List<Injection.QualifiedInstance<DataJpaSupport>> services() {
        return dataConfig.stream()
                .filter(it -> it.provider().type().equals(PROVIDER_TYPE))
                .map(this::mapConfig)
                .collect(Collectors.toUnmodifiableList());
    }

    private Injection.QualifiedInstance<DataJpaSupport> mapConfig(DataConfig dataConfig) {
        Set<Qualifier> qualifiers = new HashSet<>();
        qualifiers.add(Qualifier.createNamed(dataConfig.name()));
        qualifiers.add(SUPPORT_TYPE_QUALIFIER);
        return Injection.QualifiedInstance.create(provider.create(dataConfig), qualifiers);
    }
}
