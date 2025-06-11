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
package io.helidon.data.tests.common;

import java.util.List;
import java.util.Map;

import io.helidon.data.tests.model.League;
import io.helidon.data.tests.model.Pokemon;
import io.helidon.data.tests.model.Region;
import io.helidon.data.tests.model.Team;
import io.helidon.data.tests.model.Trainer;
import io.helidon.data.tests.model.Type;

import jakarta.persistence.EntityManager;

public class InitialData {

    private static final System.Logger LOGGER = System.getLogger(InitialData.class.getName());

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
     * List of {@code Regions}s. Array index matches ID.
     */
    public static final Region[] REGIONS = new Region[] {
            null, // skip index 0
            new Region(1, "Kanto"),
            new Region(2, "Johto"),
            new Region(3, "Sinnoh"),
            new Region(4, "Unova"),
            new Region(5, "Kalos"),
            new Region(6, "Alola"),
            new Region(7, "Galar"),
            new Region(8, "Paldea"),
            new Region(9, "Kitakami"),
            new Region(10, "Hoenn")
    };

    /**
     * List of {@code League}s. Array index matches ID.
     */
    public static final League[] LEAGUES = new League[] {
            null, // skip index 0
            new League(1, "Indigo League", REGIONS[1]),
            new League(2, "Johto League", REGIONS[2]),
            new League(3, "Sinnoh League", REGIONS[3]),
            new League(4, "Unova League", REGIONS[4]),
            new League(5, "Kalos League", REGIONS[5]),
            new League(6, "Alola League", REGIONS[6]),
            new League(7, "Galar League", REGIONS[7]),
            new League(8, "Paldea League", REGIONS[8]),
            new League(9, "Hoenn League", REGIONS[10]),
    };

    /**
     * List of {@code Team}s. Array index matches ID.
     */
    public static final Team[] TEAMS = new Team[] {
            null, // skip index 0
            new Team(1, "Kanto"),
            new Team(2, "Johto")
    };

    /**
     * List of {@code Keeper}s. Array index matches ID.
     */
    public static final Trainer[] TRAINERS = new Trainer[] {
            null, // skip index 0
            new Trainer(1, "Ash", TEAMS[1]),
            new Trainer(2, "Brock", TEAMS[1]),
            new Trainer(3, "Misty", TEAMS[1]),
            new Trainer(4, "Jasmine", TEAMS[2]),
            new Trainer(5, "Falkner", TEAMS[2]),
            new Trainer(6, "Whitney", TEAMS[2])
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
     * Pokemons not stored in the database.
     */
    public static final Map<Integer, Pokemon> NEW_POKEMONS = Map.of(
            100, new Pokemon(100, TRAINERS[1], "Diglett", 32, true, List.of(TYPES[5])),
            101, new Pokemon(101, TRAINERS[1], "Dugtrio", 72, true, List.of(TYPES[5])),
            102, new Pokemon(102, TRAINERS[2], "Meowth", 90, true, List.of(TYPES[1])),
            103, new Pokemon(103, TRAINERS[2], "Persian", 123, true, List.of(TYPES[1])),
            // TestTransaction.testManual<type>2ndLevel
            104, new Pokemon(104, TRAINERS[3], "Pikachu", 92, true, List.of(TYPES[13])),
            105, new Pokemon(105, TRAINERS[3], "Machop", 138, true, List.of(TYPES[2])),
            // TestTransaction.testAutomatic<type>2ndLevel
            106, new Pokemon(106, TRAINERS[3], "Snorlax", 293, true, List.of(TYPES[1])),
            107, new Pokemon(107, TRAINERS[3], "Charizard", 151, true, List.of(TYPES[10], TYPES[3])),
            // TestTransaction.testUser<type>2ndLevel
            108, new Pokemon(108, TRAINERS[3], "Meowth", 85, true, List.of(TYPES[1])),
            109, new Pokemon(109, TRAINERS[3], "Magikarp", 51, true, List.of(TYPES[11]))
    );

    private InitialData() {
        throw new UnsupportedOperationException("No instances of Data are allowed");
    }

    /**
     * Initialize database data.
     *
     * @param em JPA {@link jakarta.persistence.EntityManager}
     */
    public static void init(EntityManager em) {
        LOGGER.log(System.Logger.Level.DEBUG, "Data initialization");
        for (int i = 1; i < InitialData.TYPES.length; i++) {
            em.persist(InitialData.TYPES[i]);
        }
        for (int i = 1; i < InitialData.REGIONS.length; i++) {
            em.persist(InitialData.REGIONS[i]);
        }
        for (int i = 1; i < InitialData.LEAGUES.length; i++) {
            em.persist(InitialData.LEAGUES[i]);
        }
        for (int i = 1; i < InitialData.TEAMS.length; i++) {
            em.persist(InitialData.TEAMS[i]);
        }
        for (int i = 1; i < InitialData.TRAINERS.length; i++) {
            em.persist(InitialData.TRAINERS[i]);
        }
        for (int i = 1; i < InitialData.POKEMONS.length; i++) {
            em.persist(InitialData.POKEMONS[i]);
        }
    }

    /**
     * Verify database data
     */
    public static void verify(EntityManager em) {
        LOGGER.log(System.Logger.Level.DEBUG, "Data verification");
        List<Pokemon> pokemons = em.createQuery("SELECT p FROM Pokemon p", Pokemon.class)
                .getResultList();
        LOGGER.log(System.Logger.Level.DEBUG, String.format(" - pokemons: %d", pokemons.size()));
    }

    /**
     * Delete temporary data.
     *
     * @param em JPA {@link jakarta.persistence.EntityManager}
     */
    public static void deleteTemp(EntityManager em) {
        em.createNamedQuery("Pokemon.deleteTemp").executeUpdate();
    }

}
