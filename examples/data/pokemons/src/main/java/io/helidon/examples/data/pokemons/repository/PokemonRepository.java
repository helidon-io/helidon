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
package io.helidon.examples.data.pokemons.repository;

import io.helidon.data.annotation.Query;
import io.helidon.data.annotation.Repository;
import io.helidon.data.repository.CrudRepository;
import io.helidon.examples.data.pokemons.model.Pokemon;

import java.util.List;
import java.util.Optional;

// Micronaut marks those interfaces/abstract classes with annotation. It may help with processing.
// But it's not mandatory - all have GenericRepository as parent interface.
@Repository
public interface PokemonRepository extends CrudRepository<Pokemon, Integer> {

    // Query defined by method name: Find pokemon by provided name attribute
    public abstract Optional<Pokemon> findByName(String name);

    // Query defined by method name: List all pokemons with provided type name attribute
    public abstract List<Pokemon> listByTypeName(String typeName);


    // Query defined by annotation: Find pokemon by provided type name and name attributes
    @Query("SELECT p FROM Pokemon p WHERE p.type.name = :typeName AND p.name = :pokemonName")
    public abstract Optional<Pokemon> pokemonsByTypeAndName(String typeName, String pokemonName);

}
