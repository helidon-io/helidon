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
package io.helidon.docs.mp.data;

import java.util.List;

import io.helidon.docs.includes.data.Keeper;
import io.helidon.docs.includes.data.SimpleSnippets.KeeperRepository;

import jakarta.inject.Inject;

// tag::session_access[]
public class PetService {

    // tag::repository_init[]
    @Inject
    private KeeperRepository repository;
    // end::repository_init[]

    public List<Keeper> keeperQuery(String name) {
        return repository.call(em -> em.createQuery("SELECT k FROM Keeper k WHERE k.name = :name",
                                                    Keeper.class)
                .setParameter("name", name)
                .getResultList());
    }

    public void updateKeeperName(String name, int id) {
        repository.run(em -> em.createQuery("UPDATE Keeper k SET k.name = :name WHERE k.id = :id",
                                                    Keeper.class)
                .setParameter("name", name)
                .setParameter("id", id)
                .executeUpdate());
    }

}
// end::session_access[]
