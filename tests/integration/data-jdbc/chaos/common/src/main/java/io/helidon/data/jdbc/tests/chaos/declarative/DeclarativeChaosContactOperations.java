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
package io.helidon.data.jdbc.tests.chaos.declarative;

import io.helidon.data.jdbc.tests.chaos.application.ChaosContactOperations;
import io.helidon.data.jdbc.tests.chaos.declarative.repository.ChaosContactRepository;
import io.helidon.service.registry.Service;

/**
 * Adapts the generated repository to the shared JDBC chaos smoke contract.
 */
@Service.Singleton
public final class DeclarativeChaosContactOperations implements ChaosContactOperations {
    private final ChaosContactRepository repository;

    /**
     * Creates the declarative chaos operation adapter.
     *
     * @param repository generated chaos repository
     */
    @Service.Inject
    DeclarativeChaosContactOperations(ChaosContactRepository repository) {
        this.repository = repository;
    }

    @Override
    public void executeMalformedSql() {
        repository.executeMalformedSql();
    }

    @Override
    public void insertContact(long id, String name) {
        repository.insertContact(id, name);
    }

    @Override
    public long executeConversionFailureQuery() {
        return repository.executeConversionFailureQuery();
    }

    @Override
    public long insertGeneratedContact(String name) {
        return repository.insertGeneratedContact(name);
    }

    @Override
    public long countContacts() {
        return repository.countContacts();
    }
}
