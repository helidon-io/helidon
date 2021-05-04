/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.dbclient.appl.dbmapper;

import java.util.Optional;

import javax.json.JsonObject;

import io.helidon.dbclient.DbMapper;
import io.helidon.dbclient.spi.DbMapperProvider;
import io.helidon.tests.integration.dbclient.appl.model.Pokemon;
import io.helidon.tests.integration.dbclient.appl.model.RangePoJo;

/**
 * Provides DB Client mappers.
 */
public class DbClientMapperProvider implements DbMapperProvider {

    @Override
    public <T> Optional<DbMapper<T>> mapper(Class<T> type) {
        if (type.equals(JsonObject.class)) {
            return Optional.of((DbMapper<T>)DbRowToJsonObjectMapper.getInstance());
        } else if (type.equals(RangePoJo.class)) {
                return Optional.of((DbMapper<T>) RangePoJo.Mapper.INSTANCE);
            } else if (type.equals(Pokemon.class)) {
                return Optional.of((DbMapper<T>) Pokemon.PokemonMapper.INSTANCE);
        } else {
            return Optional.empty();
        }
    }

}
