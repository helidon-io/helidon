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

import io.helidon.data.jdbc.tests.application.transaction.FocusedTransactionOperations;
import io.helidon.data.jdbc.tests.declarative.repository.FocusedTransactionRepository;
import io.helidon.service.registry.Service;

/**
 * Dispatches focused transaction behavior tests to generated repository methods.
 */
@Service.Singleton
public final class DeclarativeFocusedTransactionOperations implements FocusedTransactionOperations {
    private final FocusedTransactionRepository repository;

    /**
     * Creates the declarative focused transaction adapter.
     *
     * @param repository generated repository
     */
    @Service.Inject
    DeclarativeFocusedTransactionOperations(FocusedTransactionRepository repository) {
        this.repository = repository;
    }

    @Override
    public long insertRequired(String value) {
        return repository.insertRequired(value);
    }

    @Override
    public long insertNew(String value) {
        return repository.insertNew(value);
    }

    @Override
    public long insertUnsupported(String value) {
        return repository.insertUnsupported(value);
    }

    @Override
    public void failRequired() {
        repository.failRequired();
    }
}
