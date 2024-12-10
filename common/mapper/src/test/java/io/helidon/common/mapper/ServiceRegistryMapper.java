/*
 * Copyright (c) 2019, 2024 Oracle and/or its affiliates.
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

import io.helidon.common.GenericType;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.mapper.spi.MapperProvider;
import io.helidon.service.registry.Service;

/**
 * Maps String to Integer, and String to Short using type.
 */
@Weight(Weighted.DEFAULT_WEIGHT + 1)
@Service.Singleton
class ServiceRegistryMapper implements MapperProvider {
    static final GenericType<String> STRING_TYPE = GenericType.create(String.class);
    static final GenericType<Integer> INTEGER_TYPE = GenericType.create(Integer.class);
    static final GenericType<Short> SHORT_TYPE = GenericType.create(Short.class);

    @Override
    public ProviderResponse mapper(Class<?> sourceClass, Class<?> targetClass, String qualifier) {
        return ProviderResponse.unsupported();
    }

    @Override
    public ProviderResponse mapper(GenericType<?> sourceType, GenericType<?> targetType, String qualifier) {
        if (sourceType.equals(STRING_TYPE) && targetType.equals(INTEGER_TYPE)) {
            return new ProviderResponse(Support.SUPPORTED, string -> Integer.parseInt((String) string) + 1);
        }

        if (sourceType.equals(STRING_TYPE) && targetType.equals(SHORT_TYPE)) {
            return new ProviderResponse(Support.SUPPORTED, string -> Short.parseShort((String) string));
        }
        return ProviderResponse.unsupported();
    }
}
