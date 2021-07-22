/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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

import java.util.Optional;

import io.helidon.common.mapper.spi.MapperProvider;

/**
 * Maps String to Integer and String to Long using class.
 */
public class ServiceLoaderMapper1 implements MapperProvider {
    @Override
    public <SOURCE, TARGET> Optional<Mapper<?, ?>> mapper(Class<SOURCE> sourceClass, Class<TARGET> targetClass) {
        if ((sourceClass.equals(String.class)) && (targetClass.equals(Integer.class))) {
            return Optional.of(source -> Integer.parseInt((String) source));
        }

        if ((sourceClass.equals(String.class)) && (targetClass.equals(Long.class))) {
            return Optional.of(source -> Long.parseLong((String) source));
        }

        return Optional.empty();
    }
}
