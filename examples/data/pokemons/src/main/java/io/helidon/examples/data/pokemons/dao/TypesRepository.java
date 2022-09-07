/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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
package io.helidon.examples.data.pokemons.dao;

import io.helidon.data.annotation.Repository;
import io.helidon.data.repository.CrudRepository;
import io.helidon.examples.data.pokemons.model.Type;
import jakarta.persistence.EntityManager;

@Repository
public abstract class TypesRepository implements CrudRepository<Type, Integer>  {

    // TODO: Initialization - manual for SE/Pico, @Inject for MP
    private final EntityManager entityManager;

    public TypesRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

}
