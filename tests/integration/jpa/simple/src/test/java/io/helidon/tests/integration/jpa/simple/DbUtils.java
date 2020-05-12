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
package io.helidon.tests.integration.jpa.simple;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import io.helidon.tests.integration.jpa.dao.Create;
import io.helidon.tests.integration.jpa.dao.Delete;
import io.helidon.tests.integration.jpa.model.Pokemon;
import io.helidon.tests.integration.jpa.model.Trainer;

/**
 * Database utilities.
 */
public class DbUtils {

    public static int ASH_ID = -1;

    private DbUtils() {
        throw new UnsupportedOperationException("Instances of DbUtils class are not allowed");
    }

    /**
     * Initialize database records.
     *
     * @param pu persistence unit context
     */
    public static void dbInit(PU pu) {
        dbInsertTypes(pu);
        dbInsertAsh(pu);
        pu.getEm().flush();
        pu.getEm().clear();
        pu.getEm().getEntityManagerFactory().getCache().evictAll();
        
    }

    /**
     * Insert pokemon types.
     *
     * @param pu persistence unit context
     */
    public static void dbInsertTypes(PU pu) {
        final EntityManager em = pu.getEm();
        Create.dbInsertTypes(em);
    }

    /**
     * Insert trainer Ash and his pokemons.
     *
     * @param pu persistence unit context
     */
    public static void dbInsertAsh(PU pu) {
        final EntityManager em = pu.getEm();
        ASH_ID = Create.dbInsertAsh(em);
    }

    /**
     * Delete all database records.
     *
     * @param pu persistence unit context
     */
    public static void dbCleanup(PU pu) {
        Delete.dbCleanup(pu.getEm());
    }

    /**
     * Find trainer by ID.
     *
     * @param pu persistence unit context
     * @param id trainer ID
     * @return trainer with specified ID or {@code null} if no such trainer exists
     */
    public static Trainer findTrainer(PU pu, int id) {
        final EntityManager em = pu.getEm();
        TypedQuery<Trainer> q = em.createQuery("SELECT t FROM Trainer t JOIN FETCH t.pokemons WHERE t.id = :id", Trainer.class);
        q.setParameter("id", ASH_ID);
        List<Trainer> result = q.getResultList();
        return result != null && result.size() > 0 ? result.get(0) : null;
    }

    /**
     * Find trainer's pokemons.
     *
     * @param pu persistence unit context
     * @param trainer trainer entity
     * @return {@code List} of trainer's pokemons
     */
    public static List<Pokemon> trainersPokemons(PU pu, Trainer trainer) {
        final EntityManager em = pu.getEm();
        TypedQuery<Pokemon> q = em.createQuery("SELECT p FROM Pokemon p WHERE p.trainer.id = :id", Pokemon.class);
        q.setParameter("id", trainer.getId());
        return q.getResultList();
    }

}
