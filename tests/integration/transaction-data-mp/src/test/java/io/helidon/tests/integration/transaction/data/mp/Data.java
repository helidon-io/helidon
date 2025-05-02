/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.transaction.data.mp;

import java.util.List;

import io.helidon.tests.integration.transaction.data.mp.model.Pokemon;
import io.helidon.tests.integration.transaction.data.mp.model.Trainer;
import io.helidon.tests.integration.transaction.data.mp.model.Type;

import jakarta.persistence.EntityManager;

public class Data {

    private static final System.Logger LOGGER = System.getLogger(Data.class.getName());

    /**
     * List of {@code Type}s. Array index matches ID.
     */
    public static final Type[] TYPES = new Type[] {
            null, // skip index 0
            new Type(1, "Normal"),
            new Type(2, "Fighting"),
            new Type(3, "Flying"),
            new Type(4, "Poison"),
            new Type(5, "Ground"),
            new Type(6, "Rock"),
            new Type(7, "Bug"),
            new Type(8, "Ghost"),
            new Type(9, "Steel"),
            new Type(10, "Fire"),
            new Type(11, "Water"),
            new Type(12, "Grass"),
            new Type(13, "Electric"),
            new Type(14, "Psychic"),
            new Type(15, "Ice"),
            new Type(16, "Dragon"),
            new Type(17, "Dark"),
            new Type(18, "Fairy")
    };

    /**
     * List of {@code Keeper}s. Array index matches ID.
     */
    public static final Trainer[] TRAINERS = new Trainer[] {
            null, // skip index 0
            new Trainer(1, "Ash"),
            new Trainer(2, "Brock"),
            new Trainer(3, "Misty"),
            new Trainer(4, "Jasmine"),
            new Trainer(5, "Falkner"),
            new Trainer(6, "Whitney")
    };

    /**
     * List of {@code Pokemons}s. Array index matches ID.
     */
    public static final Pokemon[] POKEMONS = new Pokemon[] {
            null, // skip index 0
            new Pokemon(1, TRAINERS[1], "Pikachu", 72, true, List.of(TYPES[13])),
            new Pokemon(2, TRAINERS[1], "Raichu", 115, true, List.of(TYPES[13])),
            new Pokemon(3, TRAINERS[1], "Machop", 132, true, List.of(TYPES[2])),
            new Pokemon(4, TRAINERS[1], "Snorlax", 285, true, List.of(TYPES[1])),
            new Pokemon(5, TRAINERS[2], "Charizard", 145, true, List.of(TYPES[10], TYPES[3])),
            new Pokemon(6, TRAINERS[2], "Meowth", 81, true, List.of(TYPES[1])),
            new Pokemon(7, TRAINERS[2], "Magikarp", 47, true, List.of(TYPES[11])),
            new Pokemon(8, TRAINERS[2], "Spearow", 81, true, List.of(TYPES[1], TYPES[3])),
            new Pokemon(9, TRAINERS[3], "Fearow", 123, true, List.of(TYPES[1], TYPES[3])),
            new Pokemon(10, TRAINERS[3], "Ekans", 72, true, List.of(TYPES[4])),
            new Pokemon(11, TRAINERS[3], "Arbok", 115, true, List.of(TYPES[4])),
            new Pokemon(12, TRAINERS[4], "Sandshrew", 98, true, List.of(TYPES[5])),
            new Pokemon(13, TRAINERS[4], "Sandslash", 140, true, List.of(TYPES[5])),
            new Pokemon(14, TRAINERS[4], "Diglett", 30, true, List.of(TYPES[5])),
            new Pokemon(15, TRAINERS[5], "Rayquaza", 191, true, List.of(TYPES[3], TYPES[16])),
            new Pokemon(16, TRAINERS[5], "Lugia", 193, true, List.of(TYPES[3], TYPES[14])),
            new Pokemon(17, TRAINERS[5], "Ho-Oh", 193, true, List.of(TYPES[3], TYPES[10])),
            new Pokemon(18, TRAINERS[6], "Raikou", 166, true, List.of(TYPES[13])),
            new Pokemon(19, TRAINERS[6], "Giratina", 268, true, List.of(TYPES[8], TYPES[16])),
            new Pokemon(20, TRAINERS[6], "Regirock", 149, true, List.of(TYPES[6]))
    };

    /**
     * Initialize database data.
     *
     * @param em JPA {@link jakarta.persistence.EntityManager}
     */
    public static void init(EntityManager em) {
        LOGGER.log(System.Logger.Level.DEBUG, "Data initialization");
        for (int i = 1; i < Data.TYPES.length; i++) {
            em.persist(Data.TYPES[i]);
        }
        for (int i = 1; i < Data.TRAINERS.length; i++) {
            em.persist(Data.TRAINERS[i]);
        }
        for (int i = 1; i < Data.POKEMONS.length; i++) {
            em.persist(Data.POKEMONS[i]);
        }
    }

}
