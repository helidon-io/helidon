/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.examples.dbclient.pokemons2;

import javax.annotation.Priority;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.dbclient.DbColumn;
import io.helidon.dbclient.DbMapper;
import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.spi.DbMapperProvider;

/**
 * Provider for Pokemon mappers.
 */
@Priority(1000)
public class PokemonTypeMapperProvider implements DbMapperProvider {
    private static final PokemonTypeMapper MAPPER = new PokemonTypeMapper();

    @SuppressWarnings("unchecked")
    @Override
    public <T> Optional<DbMapper<T>> mapper(Class<T> type) {
        return type.equals(PokemonType.class) ? Optional.of((DbMapper<T>) MAPPER) : Optional.empty();
    }

    /**
     * Maps database types to a PokemonType class.
     */
    static class PokemonTypeMapper implements DbMapper<PokemonType> {

        @Override
        public PokemonType read(DbRow row) {
            DbColumn id = row.column("ID");
            DbColumn name = row.column("NAME");
            return new PokemonType(id.as(Integer.class), name.as(String.class));
        }

        @Override
        public Map<String, ?> toNamedParameters(PokemonType value) {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        public List<?> toIndexedParameters(PokemonType value) {
            throw new UnsupportedOperationException("Not supported");
        }
    }
}
