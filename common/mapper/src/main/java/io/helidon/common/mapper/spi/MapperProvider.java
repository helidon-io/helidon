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
package io.helidon.common.mapper.spi;

import java.util.Optional;

import io.helidon.common.GenericType;
import io.helidon.common.mapper.Mapper;

/**
 * Java Service loader service to get mappers.
 */
@FunctionalInterface
public interface MapperProvider {
    /**
     * Find a mapper that is capable of mapping from source to target classes.
     *
     * @param sourceClass class of the source
     * @param targetClass class of the target
     * @param <SOURCE> type of the source
     * @param <TARGET> type of the target
     * @return a mapper that is capable of mapping (or converting) sources to targets
     */
    <SOURCE, TARGET> Optional<Mapper<?, ?>> mapper(Class<SOURCE> sourceClass, Class<TARGET> targetClass);

    /**
     * Find a mapper that is capable of mapping from source to target types.
     * This method supports mapping to/from types that contain generics.
     *
     * @param sourceType generic type of the source
     * @param targetType generic type of the target
     * @param <SOURCE> type of the source
     * @param <TARGET> type of the target
     * @return a mapper that is capable of mapping (or converting) sources to targets
     */
    default <SOURCE, TARGET> Optional<Mapper<?, ?>> mapper(GenericType<SOURCE> sourceType,
                                                           GenericType<TARGET> targetType) {
        return Optional.empty();
    }
}
