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
package io.helidon.tests.integration.dbclient.common;

import io.helidon.common.mapper.Mapper;
import io.helidon.common.mapper.spi.MapperProvider;

/**
 * Mapper provider.
 */
public class MapperProviderImpl implements MapperProvider {

    @Override
    public ProviderResponse mapper(Class<?> sourceClass, Class<?> targetClass, String qualifier) {
        if (Number.class.isAssignableFrom(sourceClass)) {
            Mapper<Number, ?> mapper = numberMapper(targetClass);
            if (mapper != null) {
                return new ProviderResponse(Support.SUPPORTED, mapper);
            }
        }
        return ProviderResponse.unsupported();
    }

    private Mapper<Number, ?> numberMapper(Class<?> targetClass) {
        return switch (targetClass.getName()) {
            case "byte", "java.lang.Byte" -> Number::byteValue;
            case "int", "java.lang.Integer" -> Number::intValue;
            case "long", "java.lang.Long" -> Number::longValue;
            case "float", "java.lang.Float" -> Number::floatValue;
            case "double", "java.lang.Double" -> Number::doubleValue;
            default -> null;
        };
    }
}
