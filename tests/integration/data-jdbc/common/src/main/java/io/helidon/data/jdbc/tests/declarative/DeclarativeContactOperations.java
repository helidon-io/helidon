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

import io.helidon.data.jdbc.tests.application.ContactOperations;
import io.helidon.data.jdbc.tests.application.ContactView;
import io.helidon.data.jdbc.tests.declarative.repository.ContactRepository;
import io.helidon.service.registry.Service;

/**
 * Adapts a generated JDBC repository to the shared application contract.
 */
@Service.Singleton
public final class DeclarativeContactOperations implements ContactOperations {
    private final ContactRepository repository;

    /**
     * Creates the operations adapter.
     *
     * @param repository generated repository
     */
    @Service.Inject
    DeclarativeContactOperations(ContactRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<ContactView> findAll() {
        return repository.findAll();
    }

    @Override
    public ContactView oneByName(String name) {
        return repository.oneByName(name);
    }

    @Override
    public Optional<ContactView> findByName(String name) {
        return repository.findByName(name);
    }

    @Override
    public List<ContactView> findByEmail(String email) {
        return repository.findByEmail(email, email);
    }

    @Override
    public String requiredEmail(long id) {
        return repository.requiredEmail(id);
    }

    @Override
    public Optional<String> optionalEmail(long id) {
        return repository.email(id);
    }

    @Override
    public ContactView oneFromAll() {
        return repository.oneFromAll();
    }

    @Override
    public ContactView missingRecordLabel(long id) {
        return repository.missingRecordLabel(id);
    }

    @Override
    public void executeInvalidQuery() {
        repository.invalidQuery();
    }

    @Override
    public long insert(String name, String email) {
        return repository.insertWithEmail(name, email);
    }

    @Override
    public long insertNullName(String email) {
        return repository.insertNullName(email);
    }

    @Override
    public long insertWithoutEmail(String name) {
        return repository.insert(name);
    }

    @Override
    public long insertWithDefaultKey(String name) {
        return repository.insertWithDefaultKey(name);
    }

    @Override
    public long rename(long id, String name) {
        return repository.rename(name, id);
    }

    @Override
    public long renameAll(String name) {
        return repository.renameAll(name);
    }

    @Override
    public long delete(long id) {
        return repository.delete(id);
    }
}
