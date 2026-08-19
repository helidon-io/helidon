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
package io.helidon.data.jdbc.tests.application.transaction;

import io.helidon.transaction.Tx;

/**
 * Unannotated operation or one of the six local transaction policies.
 */
public enum TransactionPolicy {
    NONE(null),
    MANDATORY(Tx.Type.MANDATORY),
    NEW(Tx.Type.NEW),
    NEVER(Tx.Type.NEVER),
    REQUIRED(Tx.Type.REQUIRED),
    SUPPORTED(Tx.Type.SUPPORTED),
    UNSUPPORTED(Tx.Type.UNSUPPORTED);

    private final Tx.Type type;

    TransactionPolicy(Tx.Type type) {
        this.type = type;
    }

    /**
     * Returns the transaction type for an annotated or imperative policy.
     *
     * @return transaction type
     * @throws IllegalStateException for the unannotated policy
     */
    public Tx.Type type() {
        if (type == null) {
            throw new IllegalStateException("The unannotated transaction policy has no transaction type.");
        }
        return type;
    }
}
