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

import io.helidon.data.jdbc.tests.application.transaction.TransactionMatrixOperations;
import io.helidon.data.jdbc.tests.application.transaction.TransactionOperation;
import io.helidon.data.jdbc.tests.application.transaction.TransactionPolicy;
import io.helidon.data.jdbc.tests.declarative.repository.TransactionMatrixRepository;
import io.helidon.service.registry.Service;

/**
 * Dispatches transaction scenarios to generated, method-annotated repository operations.
 */
@Service.Singleton
public final class DeclarativeTransactionMatrixOperations implements TransactionMatrixOperations {
    private final TransactionMatrixRepository repository;

    /**
     * Creates the declarative transaction adapter.
     *
     * @param repository generated repository
     */
    @Service.Inject
    DeclarativeTransactionMatrixOperations(TransactionMatrixRepository repository) {
        this.repository = repository;
    }

    @Override
    public long execute(TransactionPolicy policy, TransactionOperation operation) {
        return switch (policy) {
        case NONE -> switch (operation) {
            case QUERY -> repository.noneQuery();
            case UPDATE -> repository.noneUpdate();
            case GENERATED_KEY -> repository.noneGeneratedKey();
        };
        case MANDATORY -> switch (operation) {
            case QUERY -> repository.mandatoryQuery();
            case UPDATE -> repository.mandatoryUpdate();
            case GENERATED_KEY -> repository.mandatoryGeneratedKey();
        };
        case NEW -> switch (operation) {
            case QUERY -> repository.newQuery();
            case UPDATE -> repository.newUpdate();
            case GENERATED_KEY -> repository.newGeneratedKey();
        };
        case NEVER -> switch (operation) {
            case QUERY -> repository.neverQuery();
            case UPDATE -> repository.neverUpdate();
            case GENERATED_KEY -> repository.neverGeneratedKey();
        };
        case REQUIRED -> switch (operation) {
            case QUERY -> repository.requiredQuery();
            case UPDATE -> repository.requiredUpdate();
            case GENERATED_KEY -> repository.requiredGeneratedKey();
        };
        case SUPPORTED -> switch (operation) {
            case QUERY -> repository.supportedQuery();
            case UPDATE -> repository.supportedUpdate();
            case GENERATED_KEY -> repository.supportedGeneratedKey();
        };
        case UNSUPPORTED -> switch (operation) {
            case QUERY -> repository.unsupportedQuery();
            case UPDATE -> repository.unsupportedUpdate();
            case GENERATED_KEY -> repository.unsupportedGeneratedKey();
        };
        };
    }
}
