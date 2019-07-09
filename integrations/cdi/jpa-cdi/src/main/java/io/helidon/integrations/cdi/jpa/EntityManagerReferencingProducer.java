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

import java.util.Collection;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.Producer;

import io.helidon.integrations.cdi.delegates.DelegatingProducer;

final class EntityManagerReferencingProducer<T> extends DelegatingProducer<T> {

    private final Collection<?> keys;

    EntityManagerReferencingProducer(final Producer<T> delegate, final Collection<?> keys) {
        super(delegate);
        this.keys = keys;
    }

    @Override
    public T produce(final CreationalContext<T> cc) {
        final T returnValue = super.produce(cc);
        for (final Object key : this.keys) {
            NonTransactionalTransactionScopedEntityManagerReferences.incrementReferenceCount(key);
        }
        return returnValue;
    }

    @Override
    public void dispose(final T instance) {
        super.dispose(instance);
        for (final Object key : this.keys) {
            NonTransactionalTransactionScopedEntityManagerReferences.decrementReferenceCount(key);
        }
    }

}
