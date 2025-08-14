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
package io.helidon.docs.includes.data;

import java.util.List;

import io.helidon.data.Data;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;

public class SimpleSnippets {

    // Avoid static kw to make them look as top level classes in the doc.
    // tag::simple_model[]
    @Entity
    public class Pet {
        Keeper keeper;
    }

    @Entity
    public class Keeper {
        String name;
    }

    @Data.Repository
    public interface PetRepository extends Data.GenericRepository<Pet, Integer> {
        List<String> listKeeper_Name();
    }
    // end::simple_model[]

    // tag::session_repository[]
    @Data.Repository
    public interface KeeperRepository
            extends Data.GenericRepository<Keeper, Integer>, Data.SessionRepository<EntityManager> {
    }
    // end::session_repository[]

}
