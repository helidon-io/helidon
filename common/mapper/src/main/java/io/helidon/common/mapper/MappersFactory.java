/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.common.mapper;

import java.util.List;
import java.util.function.Supplier;

import io.helidon.common.mapper.spi.MapperProvider;
import io.helidon.service.registry.Service;

@Service.Singleton
class MappersFactory implements Supplier<Mappers> {
    private final List<MapperProvider> mapperProviders;
    private final List<Mapper<?, ?>> mappers;

    @Service.Inject
    MappersFactory(List<MapperProvider> mapperProviders, List<Mapper<?, ?>> mappers) {
        this.mapperProviders = mapperProviders;
        this.mappers = mappers;
    }

    @Override
    public Mappers get() {
        return Mappers.builder()
                .mapperProvidersDiscoverServices(false)
                .mappersDiscoverServices(false)
                .update(it -> mappers.forEach(it::addMapper))
                .update(it -> mapperProviders.forEach(it::addMapperProvider))
                .build();
    }
}
