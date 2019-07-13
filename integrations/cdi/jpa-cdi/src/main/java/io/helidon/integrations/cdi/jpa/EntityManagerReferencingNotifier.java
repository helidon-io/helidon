/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.integrations.cdi.jpa;

import java.util.Objects;
import java.util.Set;

import javax.enterprise.inject.spi.EventContext;
import javax.enterprise.inject.spi.ObserverMethod;
import javax.enterprise.inject.spi.configurator.ObserverMethodConfigurator.EventConsumer;

final class EntityManagerReferencingNotifier<T> implements EventConsumer<T> {

    private final ObserverMethod<T> delegate;

    private final Object key;

    EntityManagerReferencingNotifier(final ObserverMethod<T> delegate, final Set<?> keys) {
        super();
        this.delegate = Objects.requireNonNull(delegate);
        if (keys == null || keys.isEmpty()) {
            this.key = "";
        } else if (keys.size() != 1) {
            throw new IllegalArgumentException("keys.size() != 1: " + keys);
        } else {
            final Object key = keys.iterator().next();
            if (key == null) {
                this.key = "";
            } else {
                this.key = key;
            }
        }
    }

    @Override
    public void accept(final EventContext<T> eventContext) {
        try {
            NonTransactionalTransactionScopedEntityManagerReferences.incrementReferenceCount(this.key);
            this.delegate.notify(eventContext);
        } finally {
            NonTransactionalTransactionScopedEntityManagerReferences.decrementReferenceCount(this.key);
        }
    }

}
