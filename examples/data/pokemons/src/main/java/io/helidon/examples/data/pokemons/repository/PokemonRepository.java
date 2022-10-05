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

import io.helidon.data.annotation.NativeQuery;
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
    Optional<Pokemon> getByName(String name);

    // Query defined by method name: List all pokemons with provided type name attribute
    List<Pokemon> findByTypeName(String typeName);

    // Query defined by annotation: Find pokemon by provided type name and name attributes
    @Query(value="SELECT p FROM Pokemon p WHERE p.type.name = :typeName AND p.name = :pokemonName")
    Optional<Pokemon> pokemonByTypeAndName(String typeName, String pokemonName);

    // Query defined by annotation: Find pokemon by provided type name and name attributes
    @Query(key="pokemons.jpql.by-type-and-name")
    Optional<Pokemon> pokemonByTypeAndName2(String typeName, String pokemonName);

    // Query defined by annotation: Find pokemon by provided type name and name attributes
    // ResultSet to Pokemon/Type mapping is defined by JPA @SqlResultSetMapping on entity.
    @NativeQuery(
            value = "SELECT p.ID, p.NAME, p.ID_TYPE, t.ID, t.NAME " +
                            "FROM POKEMON p INNER JOIN TYPE t ON p.ID_TYPE = t.ID " +
                            "WHERE t.NAME = :typeName AND p.NAME = :pokemonName",
            resultSetMapping = "PokemonByTypeAndNameRSMapping")
    Optional<Pokemon> pokemonByTypeAndName3(String typeName, String pokemonName);

    @NativeQuery(key="pokemons.native.by-type-and-name", resultSetMapping = "PokemonByTypeAndNameRSMapping")
    Optional<Pokemon> pokemonByTypeAndName4(String typeName, String pokemonName);

}
