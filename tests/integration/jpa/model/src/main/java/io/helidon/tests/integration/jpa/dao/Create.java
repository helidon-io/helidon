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
package io.helidon.tests.integration.jpa.dao;

import java.util.Arrays;
import java.util.List;

import javax.persistence.EntityManager;

import io.helidon.tests.integration.jpa.model.City;
import io.helidon.tests.integration.jpa.model.Pokemon;
import io.helidon.tests.integration.jpa.model.Stadium;
import io.helidon.tests.integration.jpa.model.Trainer;
import io.helidon.tests.integration.jpa.model.Type;

/**
 * Create data for the tests.
 */
public class Create {

    private Create() {
        throw new UnsupportedOperationException("Instances of Create class are not allowed");
    }

    /**
     * Insert pokemon types.
     *
     * @param em Entity manager instance
     */
    public static void dbInsertTypes(final EntityManager em) {
        em.persist(new Type(1, "Normal"));
        em.persist(new Type(2, "Fighting"));
        em.persist(new Type(3, "Flying"));
        em.persist(new Type(4, "Poison"));
        em.persist(new Type(5, "Ground"));
        em.persist(new Type(6, "Rock"));
        em.persist(new Type(7, "Bug"));
        em.persist(new Type(8, "Ghost"));
        em.persist(new Type(9, "Steel"));
        em.persist(new Type(10, "Fire"));
        em.persist(new Type(11, "Water"));
        em.persist(new Type(12, "Grass"));
        em.persist(new Type(13, "Electric"));
        em.persist(new Type(14, "Psychic"));
        em.persist(new Type(15, "Ice"));
        em.persist(new Type(16, "Dragon"));
        em.persist(new Type(17, "Dark"));
        em.persist(new Type(18, "Fairy"));
        em.flush();
    }

    /**
     * Insert trainer Ash and his pokemons.
     * Ash and his pokemons are used for query tests only.
     *
     * @param em Entity manager instance
     * @return ID of the trainer (Ash)
     */
    public static int dbInsertAsh(final EntityManager em) {
        Trainer trainer = new Trainer("Ash Ketchum", 10);
        Type normal = em.find(Type.class, 1);
        Type flying = em.find(Type.class, 3);
        Type poison = em.find(Type.class, 4);
        Type bug = em.find(Type.class, 7);
        Type fire = em.find(Type.class, 10);
        Type water = em.find(Type.class, 11);
        Type grass = em.find(Type.class, 12);
        Type electric = em.find(Type.class, 13);
        em.persist(trainer);
        em.persist(new Pokemon(trainer, "Pikachu", 252, Arrays.asList(electric)));
        em.persist(new Pokemon(trainer, "Caterpie", 123, Arrays.asList(bug)));
        em.persist(new Pokemon(trainer, "Charmander", 207, Arrays.asList(fire)));
        em.persist(new Pokemon(trainer, "Squirtle", 187, Arrays.asList(water)));
        em.persist(new Pokemon(trainer, "Bulbasaur", 204, Arrays.asList(grass, poison)));
        em.persist(new Pokemon(trainer, "Pidgey", 107, Arrays.asList(normal, flying)));
        em.flush();
        return trainer.getId();
    }

    /**
     * Create Brock and his pokemons.
     * Brock and his pokemons are used for update tests only.
     *
     * @param em Entity manager instance
     */
    public static void dbInsertBrock(final EntityManager em) {
        Trainer trainer = new Trainer("Brock", 12);
        Type normal = em.find(Type.class, 1);
        Type ground = em.find(Type.class, 5);
        Type rock = em.find(Type.class, 6);
        Type water = em.find(Type.class, 11);
        Type psychic = em.find(Type.class, 14);
        List<Type> types = Arrays.asList(rock, ground);
        em.persist(trainer);
        em.persist(new Pokemon(trainer, "Geodude", 236, types));
        em.persist(new Pokemon(trainer, "Onix", 251, types));
        em.persist(new Pokemon(trainer, "Rhyhorn", 251, types));
        em.persist(new Pokemon(trainer, "Slowpoke", 251, Arrays.asList(water, psychic)));
        em.persist(new Pokemon(trainer, "Teddiursa", 275, Arrays.asList(normal)));
        em.persist(new Pokemon(trainer, "Omanyte", 275, Arrays.asList(rock, water)));
        em.flush();
        em.clear();
        em.getEntityManagerFactory().getCache().evictAll();
    }

    /**
     * Create Misty and her pokemons.
     * Misty and her pokemons are used for delete tests only.
     *
     * @param em Entity manager instance
     */
    public static void dbInsertMisty(final EntityManager em) {
        Trainer trainer = new Trainer("Misty", 10);
        Type normal = em.find(Type.class, 1);
        Type rock = em.find(Type.class, 6);
        Type water = em.find(Type.class, 11);
        Type fairy = em.find(Type.class, 18);
        em.persist(trainer);
        em.persist(new Pokemon(trainer, "Staryu", 184, Arrays.asList(water)));
        em.persist(new Pokemon(trainer, "Psyduck", 92, Arrays.asList(water)));
        em.persist(new Pokemon(trainer, "Corsola", 147, Arrays.asList(rock)));
        em.persist(new Pokemon(trainer, "Horsea", 64, Arrays.asList(water)));
        em.persist(new Pokemon(trainer, "Azurill", 217, Arrays.asList(normal, fairy)));
        em.persist(new Pokemon(trainer, "Togepi", 51, Arrays.asList(fairy)));
        em.flush();
        em.clear();
        em.getEntityManagerFactory().getCache().evictAll();
    }

    /**
     * Create Celadon City with stadium and trainer.
     * Used for query tests only.
     */
    public static void dbInsertCeladon(final EntityManager em) {
        Type poison = em.find(Type.class, 4);
        Type grass = em.find(Type.class, 12);
        Type psychic = em.find(Type.class, 14);
        Trainer trainer = new Trainer("Erika", 16);
        Stadium stadium = new Stadium("Celadon Gym", trainer);
        City city = new City("Celadon City", "Madam Celadon", stadium);
        em.persist(trainer);
        em.persist(new Pokemon(trainer, "Gloom", 651, Arrays.asList(grass, poison)));
        em.persist(new Pokemon(trainer, "Victreebel", 751, Arrays.asList(grass, poison)));
        em.persist(new Pokemon(trainer, "Tangela", 234, Arrays.asList(grass)));
        em.persist(new Pokemon(trainer, "Vileplume", 1571, Arrays.asList(grass, poison)));
        em.persist(new Pokemon(trainer, "Weepinbell", 1923, Arrays.asList(grass, poison)));
        em.persist(new Pokemon(trainer, "Exeggcute", 317, Arrays.asList(grass, psychic)));
        //em.persist(stadium);
        em.persist(city);
    }

    /**
     * Create Saffron City with stadium and trainer.
     * Used for update tests only.
     */
    public static void dbInsertSaffron(final EntityManager em) {
        Type fighting = em.find(Type.class, 2);
        Type psychic = em.find(Type.class, 14);
        Type ice = em.find(Type.class, 15);
        Trainer trainer = new Trainer("Sabrina", 23);
        Stadium stadium = new Stadium("Saffron Gym", trainer);
        City city = new City("Saffron City", "Koichi", stadium);
        em.persist(trainer);
        em.persist(new Pokemon(trainer, "Alakazam", 2178, Arrays.asList(psychic)));
        em.persist(new Pokemon(trainer, "Espeon", 2745, Arrays.asList(psychic)));
        em.persist(new Pokemon(trainer, "Mr. Mime", 1478, Arrays.asList(psychic)));
        em.persist(new Pokemon(trainer, "Jynx", 2471, Arrays.asList(psychic, ice)));
        em.persist(new Pokemon(trainer, "Wobbuffet", 1478, Arrays.asList(psychic)));
        em.persist(new Pokemon(trainer, "Gallade", 2147, Arrays.asList(psychic, fighting)));
        //em.persist(stadium);
        em.persist(city);
    }

    /**
     * Create Viridian City with stadium and trainer.
     * Used for delete tests only.
     */
    public static void dbInsertViridian(final EntityManager em) {
        Type poison = em.find(Type.class, 4);
        Type ground = em.find(Type.class, 5);
        Type rock = em.find(Type.class, 6);
        Trainer trainer = new Trainer("Giovanni", 37);
        Stadium stadium = new Stadium("Viridian Gym", trainer);
        City city = new City("Viridian City", "Koichi", stadium);
        em.persist(trainer);
        em.persist(new Pokemon(trainer, "Rhyperior", 3841, Arrays.asList(ground, rock)));
        em.persist(new Pokemon(trainer, "Golem", 3651, Arrays.asList(ground, rock)));
        em.persist(new Pokemon(trainer, "Nidoking", 2451, Arrays.asList(ground, poison)));
        em.persist(new Pokemon(trainer, "Marowak", 2249, Arrays.asList(ground)));
        em.persist(new Pokemon(trainer, "Sandslash", 1953, Arrays.asList(ground)));
        em.persist(new Pokemon(trainer, "Nidoqueen", 3147, Arrays.asList(ground)));
        //em.persist(stadium);
        em.persist(city);
    }

}
