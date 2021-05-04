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

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.InjectionTarget;
import javax.enterprise.inject.spi.Producer;

/**
 * A {@link DelegatingProducer} and an {@link InjectionTarget} that
 * forwards all method calls to underlying {@link InjectionTarget} and
 * {@link Producer} implementations.
 *
 * @param <T> the type of produced object
 *
 * @see InjectionTarget
 *
 * @see Producer
 */
public class DelegatingInjectionTarget<T> extends DelegatingProducer<T> implements InjectionTarget<T> {

    private final InjectionTarget<T> delegate;

    /**
     * Creates a new {@link DelegatingInjectionTarget}.
     *
     * @param delegate the {@link InjectionTarget} to which all
     * operations will be forwarded; must not be {@code null}
     *
     * @exception NullPointerException if {@code delegate} is {@code
     * null}
     *
     * @see #DelegatingInjectionTarget(InjectionTarget, Producer)
     */
    public DelegatingInjectionTarget(final InjectionTarget<T> delegate) {
        this(delegate, delegate);
    }

    /**
     * Creates a new {@link DelegatingInjectionTarget}.
     *
     * @param injectionTargetDelegate the {@link InjectionTarget} to
     * which {@link InjectionTarget}-specific operations will be
     * forwarded; must not be {@code null}
     *
     * @param producerDelegate the {@link Producer} to which {@link
     * Producer}-specific operations will be forwarded; must not be
     * {@code null}
     *
     * @exception NullPointerException if either parameter is {@code
     * null}
     */
    public DelegatingInjectionTarget(final InjectionTarget<T> injectionTargetDelegate, final Producer<T> producerDelegate) {
        super(Objects.requireNonNull(producerDelegate));
        this.delegate = Objects.requireNonNull(injectionTargetDelegate);
    }

    @Override
    public void inject(final T instance, final CreationalContext<T> cc) {
        this.delegate.inject(instance, cc);
    }

    @Override
    public void postConstruct(final T instance) {
        this.delegate.postConstruct(instance);
    }

    @Override
    public void preDestroy(final T instance) {
        this.delegate.preDestroy(instance);
    }

}
