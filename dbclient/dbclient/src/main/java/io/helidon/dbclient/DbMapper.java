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
package io.helidon.dbclient;

import java.util.List;
import java.util.Map;

/**
 * A mapper to map database objects to/from a specific type.
 * <p>
 * Mappers can be either provided through {@link io.helidon.dbclient.spi.DbClientProvider} or registered directly
 * with the {@link io.helidon.dbclient.spi.DbClientProviderBuilder#addMapper(DbMapper, Class)}.
 *
 * @param <T> target mapping type
 */
public interface DbMapper<T> {
    /**
     * Read database row and convert it to target type instance.
     *
     * @param row source database row
     * @return target type instance containing database row
     */
    T read(DbRow row);

    /**
     * Convert target type instance to a statement named parameters map.
     *
     * @param value mapping type instance containing values to be set into statement
     * @return map of statement named parameters mapped to values to be set
     * @see io.helidon.dbclient.DbStatement#namedParam(Object)
     */
    Map<String, ?> toNamedParameters(T value);

    /**
     * Convert target type instance to a statement indexed parameters list.
     * <p>
     * Using indexed parameters with typed values is probably not going to work nicely, unless
     * the order is specified and the number of parameters is always related the provided value.
     * There are cases where this is useful though - e.g. for types that represent an iterable collection.
     *
     * @param value mapping type instance containing values to be set into statement
     * @return map of statement named parameters mapped to values to be set
     * @see io.helidon.dbclient.DbStatement#indexedParam(Object)
     */
    List<?> toIndexedParameters(T value);

}
