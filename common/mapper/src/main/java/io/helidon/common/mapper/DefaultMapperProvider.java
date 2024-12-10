/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.helidon.common.Weight;
import io.helidon.common.mapper.spi.MapperProvider;
import io.helidon.service.registry.Service;

@Service.Singleton
@Weight(0.1)
class DefaultMapperProvider implements MapperProvider {
    private static final Map<CacheKey, ProviderResponse> CACHE = new ConcurrentHashMap<>();

    @Override
    public ProviderResponse mapper(Class<?> sourceClass, Class<?> targetClass, String qualifier) {
        return CACHE.computeIfAbsent(new CacheKey(sourceClass, targetClass), key -> {
            if (sourceClass.equals(String.class)) {
                return fromString(targetClass);
            }
            if (targetClass.equals(String.class)) {
                return toString(targetClass);
            }
            return ProviderResponse.unsupported();
        });
    }

    private static ProviderResponse fromString(Class<?> target) {
        if (target.equals(int.class) || target.equals(Integer.class)) {
            return new ProviderResponse(Support.COMPATIBLE, o -> Integer.parseInt((String) o));
        }
        if (target.equals(long.class) || target.equals(Long.class)) {
            return new ProviderResponse(Support.COMPATIBLE, o -> Long.parseLong((String) o));
        }
        return ProviderResponse.unsupported();
    }

    private static ProviderResponse toString(Class<?> source) {
        return new ProviderResponse(Support.COMPATIBLE, String::valueOf);
    }

    private record CacheKey(Class<?> source, Class<?> target) {
    }
}
