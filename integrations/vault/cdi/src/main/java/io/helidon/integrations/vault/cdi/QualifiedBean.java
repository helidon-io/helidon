/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.integrations.vault.cdi;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.PassivationCapable;

class QualifiedBean<T> implements Bean<T>, PassivationCapable {
    private final Class<?> beanClass;
    private final Class<T> type;
    private final Set<Annotation> qualifiers;
    private final Supplier<T> creator;
    private final Set<Annotation> customQualifiers;

    QualifiedBean(Class<?> beanClass, Class<T> type, Supplier<T> creator) {
        this.beanClass = beanClass;
        this.type = type;
        this.qualifiers = Set.of(Any.Literal.INSTANCE, Default.Literal.INSTANCE);
        this.customQualifiers = Set.of();
        this.creator = creator;
    }

    QualifiedBean(Class<?> beanClass, Class<T> type, Set<Annotation> qualifiers, Supplier<T> creator) {
        this.beanClass = beanClass;
        this.type = type;
        Set<Annotation> finalQualifiers = new HashSet<>(qualifiers);
        finalQualifiers.add(Any.Literal.INSTANCE);
        this.qualifiers = finalQualifiers;
        this.customQualifiers = qualifiers;
        this.creator = creator;
    }

    @Override
    public Class<?> getBeanClass() {
        return beanClass;
    }

    @Override
    public Set<InjectionPoint> getInjectionPoints() {
        return Set.of();
    }

    @Override
    public boolean isNullable() {
        return false;
    }

    @Override
    public T create(CreationalContext<T> creationalContext) {
        return creator.get();
    }

    @Override
    public void destroy(T instance, CreationalContext<T> creationalContext) {
    }

    @Override
    public Set<Type> getTypes() {
        return Set.of(type);
    }

    @Override
    public Set<Annotation> getQualifiers() {
        return qualifiers;
    }

    @Override
    public Class<? extends Annotation> getScope() {
        return ApplicationScoped.class;
    }

    @Override
    public String getName() {
        return type.getName() + "." + qualifiers;
    }

    @Override
    public Set<Class<? extends Annotation>> getStereotypes() {
        return Set.of();
    }

    @Override
    public boolean isAlternative() {
        return false;
    }

    @Override
    public String toString() {
        return "QualifiedBean{"
                + "beanClass=" + beanClass.getSimpleName()
                + ", type=" + type.getSimpleName()
                + ", qualifiers=" + customQualifiers
                + '}';
    }

    @Override
    public String getId() {
        return getName();
    }
}
