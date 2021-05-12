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

package io.helidon.microprofile.grpc.client;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

import javax.enterprise.inject.spi.BeanAttributes;

/**
 * A {@link BeanAttributes} implementation.
 *
 * @param <T> the class of the bean instance
 */
class DelegatingBeanAttributes<T> implements BeanAttributes<T> {

    private final BeanAttributes<?> delegate;
    private final Set<Type> types;

    /**
     * Create a {@link DelegatingBeanAttributes}.
     *
     * @param delegate  the {@link BeanAttributes} to delegate to
     * @param types the {@link Type}s for this bean
     */
    private DelegatingBeanAttributes(BeanAttributes<?> delegate, Set<Type> types) {
        super();
        Objects.requireNonNull(delegate);
        this.delegate = delegate;
        this.types = Collections.unmodifiableSet(types);
    }

    /**
     * Create a {@link DelegatingBeanAttributes}.
     *
     * @param delegate  the {@link BeanAttributes} to delegate to
     * @param types the {@link Type}s for this bean
     */
    static <T> DelegatingBeanAttributes<T> create(BeanAttributes<?> delegate, Set<Type> types) {
        return new DelegatingBeanAttributes<>(delegate, types);
    }

    @Override
    public String getName() {
        return this.delegate.getName();
    }

    @Override
    public Set<Annotation> getQualifiers() {
        return this.delegate.getQualifiers();
    }

    @Override
    public Class<? extends Annotation> getScope() {
        return this.delegate.getScope();
    }

    @Override
    public Set<Class<? extends Annotation>> getStereotypes() {
        return this.delegate.getStereotypes();
    }

    @Override
    public Set<Type> getTypes() {
        if (types == null || types.isEmpty()) {
            return this.delegate.getTypes();
        } else {
            return types;
        }
    }

    @Override
    public boolean isAlternative() {
        return this.delegate.isAlternative();
    }

    @Override
    public String toString() {
        return this.delegate.toString();
    }
}
