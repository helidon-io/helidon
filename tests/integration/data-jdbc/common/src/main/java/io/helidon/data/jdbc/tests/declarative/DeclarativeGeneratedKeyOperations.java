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
package io.helidon.data.jdbc.tests.declarative;

import java.util.List;
import java.util.Optional;

import io.helidon.data.jdbc.tests.application.ContactLabel;
import io.helidon.data.jdbc.tests.application.ContactView;
import io.helidon.data.jdbc.tests.application.GeneratedKeyOperations;
import io.helidon.data.jdbc.tests.declarative.repository.ContactRepository;
import io.helidon.service.registry.Service;

/**
 * Adapts generated repository methods to the shared generated-key contract.
 */
@Service.Singleton
public final class DeclarativeGeneratedKeyOperations implements GeneratedKeyOperations {
    private final ContactRepository repository;

    /**
     * Creates the declarative generated-key operation adapter.
     *
     * @param repository generated repository
     */
    @Service.Inject
    DeclarativeGeneratedKeyOperations(ContactRepository repository) {
        this.repository = repository;
    }

    @Override
    public long insertScalar(String name) {
        return repository.insert(name);
    }

    @Override
    public Optional<Long> insertOptionalScalar(String name) {
        return repository.insertOptional(name);
    }

    @Override
    public List<Long> insertScalarList(String name) {
        return repository.insertList(name);
    }

    @Override
    public ContactView insertRecord(String name, String email) {
        return repository.insertRecord(name, email);
    }

    @Override
    public ContactLabel insertMapped(String name) {
        return repository.insertMapped(name);
    }

    @Override
    public long insertWithInvalidGeneratedKeyColumn(String name) {
        return repository.insertWithInvalidGeneratedKeyColumn(name);
    }
}
