/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.dbclient.spi;

import java.util.Optional;

import io.helidon.common.GenericType;
import io.helidon.dbclient.DbMapper;

/**
 * Java Service loader interface for database mappers.
 *
 * @see io.helidon.dbclient.DbMapper
 */
public interface DbMapperProvider {
    /**
     * Returns mapper for specific type.
     *
     * @param <T>  target mapping type
     * @param type class of the returned mapper type
     * @return a mapper for the specified type or empty
     */
    <T> Optional<DbMapper<T>> mapper(Class<T> type);

    /**
     * Returns mapper for specific type supporting generic types as well.
     * To get a list of strings: {@code mapper(new GenericType<List<String>>(){})}
     *
     * @param type type to find mapper for
     * @param <T>  type of the response
     * @return a mapper for the specified type or empty
     */
    default <T> Optional<DbMapper<T>> mapper(GenericType<T> type) {
        return Optional.empty();
    }
}
