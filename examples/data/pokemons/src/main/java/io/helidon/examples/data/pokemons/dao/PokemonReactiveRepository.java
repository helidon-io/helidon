/*
 * Copyright (c) 2019, 2022 Oracle and/or its affiliates.
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
package io.helidon.examples.data.pokemons.dao;

import io.helidon.common.reactive.Multi;
import io.helidon.data.annotation.Query;
import io.helidon.data.annotation.QueryMethod;
import io.helidon.data.annotation.Repository;
import io.helidon.data.repository.reactive.ReactiveCrudRepository;
import io.helidon.examples.data.pokemons.model.Pokemon;
import io.helidon.reactive.dbclient.DbClient;

@Repository
public abstract class PokemonReactiveRepository implements ReactiveCrudRepository<Pokemon, Integer> {

    // TODO: Initialization - manual for SE/Pico, @Inject for MP
    private final DbClient dbClient;

    public PokemonReactiveRepository(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    // Query defined by method name
    @QueryMethod
    public abstract Multi<Pokemon> findByName(String name);

    // Query defined by annotation
    @Query("SELECT p FROM Pokemon p WHERE p.type.name = :typeName")
    public abstract Multi<Pokemon> pokemonsByType(String typeName);

}
