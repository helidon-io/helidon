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
package io.helidon.tests.integration.jpa.simple.test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.EntityManager;

import io.helidon.tests.integration.jpa.model.Pokemon;
import io.helidon.tests.integration.jpa.model.Trainer;
import io.helidon.tests.integration.jpa.model.Type;
import io.helidon.tests.integration.jpa.simple.PU;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verify create/insert operations of ORM.
 */
public class InsertIT {

    private static PU pu;

    private static final Set<Integer> DELETE_POKEMONS = new HashSet<>();
    private static final Set<Integer> DELETE_TRAINERS = new HashSet<>();

    @BeforeAll
    public static void setup() {
        pu = PU.getInstance();
    }

    @AfterAll
    public static void destroy() {
        pu.tx(pu -> {
            final EntityManager em = pu.getEm();
            // testInsertType cleanup
            em.createQuery("DELETE FROM Type t WHERE t.id = :id")
                    .setParameter("id", 20)
                    .executeUpdate();
            // Pokemons cleanup
            for (int id : DELETE_POKEMONS) {
                em.createQuery("DELETE FROM Pokemon p WHERE p.id = :id")
                        .setParameter("id", id)
                        .executeUpdate();
            }
            // Trainers cleanup
            for (int id : DELETE_TRAINERS) {
                em.createQuery("DELETE FROM Trainer t WHERE t.id = :id")
                        .setParameter("id", id)
                        .executeUpdate();
            }
        });
        pu = null;
    }

    /**
     * Verify simple create operation (persist) on a single database row.
     */
    @Test
    public void testInsertType() {
        pu.tx(pu -> {
            final EntityManager em = pu.getEm();
            Type type = new Type(20, "TestType");
            em.persist(type);
            em.flush();
            Type dbType = em.find(Type.class, 20);
            assertEquals(type, dbType);
        });
    }

    /**
     * Verify complex create operation (persist) on a full ORM model (Gary Oak and his 6 pokemons).
     * Relations are not marked for cascade persist operation so every entity instance has to be persisted separately.
     */
    @Test
    void testInsertTrainerWithPokemons() {
        // Pass data between lambdas
        final Pokemon[] pokemons = new Pokemon[6];
        final Trainer[] trainers = new Trainer[1];
        pu.tx(pu -> {
            final EntityManager em = pu.getEm();
            Type normal = em.find(Type.class, 1);
            Type flying = em.find(Type.class, 3);
            Type poison = em.find(Type.class, 4);
            Type fire = em.find(Type.class, 10);
            Type water = em.find(Type.class, 11);
            Type electric = em.find(Type.class, 13);
            trainers[0] = new Trainer("Gary Oak", 10);
            pokemons[0] = new Pokemon(trainers[0], "Krabby", 236, Arrays.asList(water));
            pokemons[1] = new Pokemon(trainers[0], "Nidoran", 251, Arrays.asList(poison));
            pokemons[2] = new Pokemon(trainers[0], "Eevee", 115, Arrays.asList(normal));
            pokemons[3] = new Pokemon(trainers[0], "Electivire", 648, Arrays.asList(electric));
            pokemons[4] = new Pokemon(trainers[0], "Dodrio", 346, Arrays.asList(normal, flying));
            pokemons[5] = new Pokemon(trainers[0], "Magmar", 648, Arrays.asList(fire));
            em.persist(trainers[0]);
            em.persist(pokemons[0]);
            em.persist(pokemons[1]);
            em.persist(pokemons[2]);
            em.persist(pokemons[3]);
            em.persist(pokemons[4]);
            em.persist(pokemons[5]);
            em.flush();
        });
        pu.tx(pu -> {
            final EntityManager em = pu.getCleanEm();
            Pokemon dbKrabby = em.find(Pokemon.class, pokemons[0].getId());
            Pokemon dbNidoran = em.find(Pokemon.class, pokemons[1].getId());
            Pokemon dbEvee = em.find(Pokemon.class, pokemons[2].getId());
            Pokemon dbElectivire = em.find(Pokemon.class, pokemons[3].getId());
            Pokemon dbDodrio = em.find(Pokemon.class, pokemons[4].getId());
            Pokemon dbMagmar = em.find(Pokemon.class, pokemons[5].getId());
            Trainer dbTrainer = dbKrabby.getTrainer();
            assertEquals(pokemons[0], dbKrabby);
            assertEquals(pokemons[1], dbNidoran);
            assertEquals(pokemons[2], dbEvee);
            assertEquals(pokemons[3], dbElectivire);
            assertEquals(pokemons[4], dbDodrio);
            assertEquals(pokemons[5], dbMagmar);
            assertEquals(trainers[0], dbTrainer);
            for (Pokemon pokemon : pokemons) {
                DELETE_POKEMONS.add(pokemon.getId());
            }
            DELETE_TRAINERS.add(dbTrainer.getId());
        });
    }

}
