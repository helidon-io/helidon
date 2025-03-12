/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.transaction.jta;

import java.util.NoSuchElementException;
import java.util.function.Supplier;

import io.helidon.service.registry.Service;

import jakarta.transaction.UserTransaction;

/**
 * Jakarta Transactions {@link UserTransaction} supplier.
 */
@Service.Singleton
public class UserTransactionSupplier implements Supplier<UserTransaction> {

    private final JtaProviderSupplier jtaProviderSupplier;

    @Service.Inject
    UserTransactionSupplier(JtaProviderSupplier jtaProviderSupplier) {
        this.jtaProviderSupplier = jtaProviderSupplier;
    }

    @Override
    public UserTransaction get() {
        if (jtaProviderSupplier.get().isPresent()) {
            return jtaProviderSupplier.get().get().userTransaction();
        }
        throw new NoSuchElementException("No Jakarta Transactions provider was found.");
    }

}
