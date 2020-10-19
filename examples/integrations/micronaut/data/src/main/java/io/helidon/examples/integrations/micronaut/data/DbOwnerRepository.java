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

import io.helidon.examples.integrations.micronaut.data.model.Owner;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

/**
 * Micronaut Data repository for pet owners.
 */
@JdbcRepository(dialect = Dialect.H2)
public interface DbOwnerRepository extends CrudRepository<Owner, Long> {
    /**
     * Get all owners from the database.
     *
     * @return all owners
     */
    @Override
    List<Owner> findAll();

    /**
     * Find an owner by name.
     *
     * @param name name of owner
     * @return owner if found
     */
    Optional<Owner> findByName(String name);
}
