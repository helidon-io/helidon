/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.examples.integrations.micronaut.data;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.helidon.examples.integrations.micronaut.data.model.NameDTO;
import io.helidon.examples.integrations.micronaut.data.model.Pet;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.PageableRepository;

/**
 * Micronaut data repository for pets.
 */
@JdbcRepository(dialect = Dialect.H2)
public abstract class DbPetRepository implements PageableRepository<Pet, UUID> {

    /**
     * Get all pets.
     *
     * @param pageable pageable instance
     * @return list of pets
     */
    public abstract List<NameDTO> list(Pageable pageable);

    /**
     * Find a pet by its name.
     *
     * @param name pet name
     * @return pet if it was found
     */
    @Join("owner")
    public abstract Optional<Pet> findByName(String name);
}
