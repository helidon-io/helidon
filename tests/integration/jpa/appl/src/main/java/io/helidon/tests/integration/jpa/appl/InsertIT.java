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
package io.helidon.tests.integration.jpa.appl;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import io.helidon.tests.integration.jpa.model.Pokemon;
import io.helidon.tests.integration.jpa.model.Trainer;
import io.helidon.tests.integration.jpa.model.Type;

/**
 * Verify create/insert operations of ORM.
 */
@ApplicationScoped
public class InsertIT {
    
    private static final Set<Integer> DELETE_POKEMONS = new HashSet<>();
    private static final Set<Integer> DELETE_TRAINERS = new HashSet<>();

    @PersistenceContext(unitName = "test")
    private EntityManager em;

    /**
     * Clean up test suite.
     *
     * @param result test execution result
     * @return test execution result
     */
    @MPTest
    public TestResult destroy(TestResult result) {
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
        return result;
    }

    /**
     * Verify simple create operation (persist) on a single database row.
     *
     * @param result test execution result
     * @return test execution result
     */
    @MPTest
    public TestResult testInsertType(TestResult result) {
        Type type = new Type(20, "TestType");
        em.persist(type);
        em.flush();
        Type dbType = em.find(Type.class, 20);
        result.assertEquals(type, dbType);
        return result;
    }

    /**
     * Verify complex create operation (persist) on a full ORM model (Gary Oak and his 6 pokemons).
     * Relations are not marked for cascade persist operation so every entity instance has to be persisted separately.
     *
     * @param result test execution result
     * @return test execution result
     */
    @MPTest
    public TestResult testInsertTrainerWithPokemons(TestResult result) {
        final Pokemon[] pokemons = new Pokemon[6];
        Type normal = em.find(Type.class, 1);
        Type flying = em.find(Type.class, 3);
        Type poison = em.find(Type.class, 4);
        Type fire = em.find(Type.class, 10);
        Type water = em.find(Type.class, 11);
        Type electric = em.find(Type.class, 13);
        final Trainer trainer = new Trainer("Gary Oak", 10);
        pokemons[0] = new Pokemon(trainer, "Krabby", 236, Arrays.asList(water));
        pokemons[1] = new Pokemon(trainer, "Nidoran", 251, Arrays.asList(poison));
        pokemons[2] = new Pokemon(trainer, "Eevee", 115, Arrays.asList(normal));
        pokemons[3] = new Pokemon(trainer, "Electivire", 648, Arrays.asList(electric));
        pokemons[4] = new Pokemon(trainer, "Dodrio", 346, Arrays.asList(normal, flying));
        pokemons[5] = new Pokemon(trainer, "Magmar", 648, Arrays.asList(fire));
        em.persist(trainer);
        for (Pokemon pokemon : pokemons) {
            em.persist(pokemon);
        }
        DbUtils.cleanEm(em);
        Pokemon dbKrabby = em.find(Pokemon.class, pokemons[0].getId());
        Pokemon dbNidoran = em.find(Pokemon.class, pokemons[1].getId());
        Pokemon dbEvee = em.find(Pokemon.class, pokemons[2].getId());
        Pokemon dbElectivire = em.find(Pokemon.class, pokemons[3].getId());
        Pokemon dbDodrio = em.find(Pokemon.class, pokemons[4].getId());
        Pokemon dbMagmar = em.find(Pokemon.class, pokemons[5].getId());
        Trainer dbTrainer = dbKrabby.getTrainer();
        result.assertEquals(pokemons[0], dbKrabby);
        result.assertEquals(pokemons[1], dbNidoran);
        result.assertEquals(pokemons[2], dbEvee);
        result.assertEquals(pokemons[3], dbElectivire);
        result.assertEquals(pokemons[4], dbDodrio);
        result.assertEquals(pokemons[5], dbMagmar);
        result.assertEquals(trainer, dbTrainer);
        for (Pokemon pokemon : pokemons) {
            DELETE_POKEMONS.add(pokemon.getId());
        }
        DELETE_TRAINERS.add(dbTrainer.getId());
        return result;
    }
    
}
