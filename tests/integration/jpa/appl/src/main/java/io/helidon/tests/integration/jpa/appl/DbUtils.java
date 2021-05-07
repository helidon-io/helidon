/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.jpa.appl;

import java.util.List;

import javax.persistence.EntityManager;

import io.helidon.tests.integration.jpa.model.Pokemon;

/**
 * Database utilities.
 */
public class DbUtils {
    
    private DbUtils() {
        throw new UnsupportedOperationException("Instances of DbUtils class are not allowed");
    }

    /**
     * Flush Entity manager and clear caches.
     *
     * @param em Entity manager instance
     */
    public static void cleanEm(final EntityManager em) {
        em.flush();
        em.clear();
        em.getEntityManagerFactory().getCache().evictAll();
    }

    /**
     * Find pokemon by name from pokemon List.
     *
     * @param pokemons List to search
     * @param name name of pokemon
     * @return found pokemon or null when no such pokemon exists
     */
    public static Pokemon findPokemonByName(List<Pokemon> pokemons, String name) {
        if (pokemons != null && !pokemons.isEmpty()) {
            for (Pokemon pokemon : pokemons) {
                if (pokemon.getName().equals(name)) {
                    return pokemon;
                }
            }
        }
        return null;
    }

}
