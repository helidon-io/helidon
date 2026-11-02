/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.data.jdbc.declarative;

import java.util.List;
import java.util.Optional;

import io.helidon.service.registry.Service;
import io.helidon.tests.integration.data.jdbc.application.ContactView;
import io.helidon.tests.integration.data.jdbc.application.SqlInjectionOperations;
import io.helidon.tests.integration.data.jdbc.declarative.repository.SqlInjectionRepository;

/**
 * Adapts a generated JDBC repository to the shared SQL-injection contract.
 */
@Service.Singleton
public final class DeclarativeSqlInjectionOperations implements SqlInjectionOperations {
    private final SqlInjectionRepository repository;

    /**
     * Creates the declarative SQL-injection operation adapter.
     *
     * @param repository generated repository
     */
    @Service.Inject
    DeclarativeSqlInjectionOperations(SqlInjectionRepository repository) {
        this.repository = repository;
    }

    @Override
    public long insertContact(String name, String email) {
        return repository.insert(name, email);
    }

    @Override
    public Optional<ContactView> findByName(String name) {
        return repository.findByName(name);
    }

    @Override
    public List<ContactView> findAllByName(String name) {
        return repository.findAllByName(name);
    }

    @Override
    public List<ContactView> findAllByNameOrEmail(String value) {
        return repository.findAllByNameOrEmail(value);
    }

    @Override
    public long renameByName(String sourceName, String replacementName) {
        return repository.renameByName(replacementName, sourceName);
    }

    @Override
    public long deleteByName(String name) {
        return repository.deleteByName(name);
    }
}
