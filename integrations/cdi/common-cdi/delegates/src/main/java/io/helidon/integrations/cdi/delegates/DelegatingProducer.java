/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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
package io.helidon.integrations.cdi.delegates;

import java.util.Objects;
import java.util.Set;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.Producer;

/**
 * A {@link Producer} implementation that forwards all operations to
 * another {@link Producer}.
 *
 * @param <T> the type of produced object
 */
public class DelegatingProducer<T> implements Producer<T> {

    private final Producer<T> delegate;

    /**
     * Creates a new {@link DelegatingProducer}.
     *
     * @param delegate the {@link Producer} to which all operations
     * will be forwarded; must not be {@code null}
     *
     * @exception NullPointerException if {@code delegate} is {@code
     * null}
     */
    public DelegatingProducer(final Producer<T> delegate) {
        super();
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    public T produce(final CreationalContext<T> cc) {
        return this.delegate.produce(cc);
    }

    @Override
    public void dispose(final T instance) {
        this.delegate.dispose(instance);
    }

    @Override
    public Set<InjectionPoint> getInjectionPoints() {
        return this.delegate.getInjectionPoints();
    }

}
