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
package io.helidon.integrations.cdi.referencecountedcontext;

import java.util.Objects;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.InjectionTarget;

/**
 * An {@link InjectionTarget} that forwards all method calls to an
 * underlying {@link InjectionTarget}.
 */
class DelegatingInjectionTarget<T> extends DelegatingProducer<T> implements InjectionTarget<T> {

    private final InjectionTarget<T> delegate;

    DelegatingInjectionTarget(final InjectionTarget<T> injectionTarget) {
        super(Objects.requireNonNull(injectionTarget));
        this.delegate = injectionTarget;
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
