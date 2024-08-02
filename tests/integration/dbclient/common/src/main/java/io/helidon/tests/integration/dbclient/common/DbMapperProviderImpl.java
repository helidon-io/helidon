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
package io.helidon.tests.integration.dbclient.common;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.dbclient.DbMapper;
import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.spi.DbMapperProvider;
import io.helidon.tests.integration.dbclient.common.model.Pokemon;
import io.helidon.tests.integration.dbclient.common.model.Range;

/**
 * Mapper provider used in integration tests.
 */
public final class DbMapperProviderImpl implements DbMapperProvider {

    private static final PokemonMapper POKEMON_MAPPER = new PokemonMapper();
    private static final RangeMapper RANGE_MAPPER = new RangeMapper();

    @Override
    @SuppressWarnings({"unchecked", "IfCanBeSwitch"})
    public <T> Optional<DbMapper<T>> mapper(Class<T> type) {
        if (type.equals(Range.class)) {
            return Optional.of((DbMapper<T>) RANGE_MAPPER);
        }
        if (type.equals(Pokemon.class)) {
            return Optional.of((DbMapper<T>) POKEMON_MAPPER);
        }
        return Optional.empty();
    }

    private static final class RangeMapper implements DbMapper<Range> {

        @Override
        public Range read(DbRow row) {
            throw new UnsupportedOperationException("Read operation is not implemented.");
        }

        @Override
        public Map<String, ?> toNamedParameters(Range value) {
            return Map.of(
                    "idmin", value.idMin(),
                    "idmax", value.idMax());
        }

        @Override
        public List<?> toIndexedParameters(Range value) {
            return List.of(value.idMin(), value.idMax());
        }
    }

    private static final class PokemonMapper implements DbMapper<Pokemon> {

        @Override
        public Pokemon read(DbRow row) {
            return new Pokemon(row.column("id").get(Integer.class), row.column("name").get(String.class));
        }

        @Override
        public Map<String, ?> toNamedParameters(Pokemon pokemon) {
            return Map.of(
                    "id", pokemon.id(),
                    "name", pokemon.name());
        }

        @Override
        public List<?> toIndexedParameters(Pokemon pokemon) {
            return List.of(pokemon.name(), pokemon.id());
        }
    }
}
