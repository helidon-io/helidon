/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.microprofile.faulttolerance;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jakarta.enterprise.inject.spi.AnnotatedConstructor;
import jakarta.enterprise.inject.spi.AnnotatedField;
import jakarta.enterprise.inject.spi.AnnotatedMethod;
import jakarta.enterprise.inject.spi.AnnotatedParameter;
import jakarta.enterprise.inject.spi.AnnotatedType;

import static java.util.Arrays.asList;

/**
 * Wrapper for a Java method that implements the {@link AnnotatedMethod} interface.
 * This wrapper is necessary to handle proxy methods, such as those created from
 * a RestClient interface.
 */
class FtAnnotatedMethod implements AnnotatedMethod<Object> {

    private final Method method;

    private final AnnotatedType<Object> type;

    FtAnnotatedMethod(Method method) {
        this.method = method;
        this.type = new AnnotatedType<>() {
            private final Class<?> clazz = method.getDeclaringClass();

            @Override
            @SuppressWarnings("unchecked")
            public Class<Object> getJavaClass() {
                return (Class<Object>) clazz;
            }

            @Override
            public <T extends Annotation> T getAnnotation(Class<T> annotationType) {
                return clazz.getAnnotation(annotationType);
            }

            @Override
            public Set<Annotation> getAnnotations() {
                return Set.of(clazz.getAnnotations());
            }

            @Override
            public boolean isAnnotationPresent(Class<? extends Annotation> annotationType) {
                return clazz.isAnnotationPresent(annotationType);
            }

            @Override
            public Set<AnnotatedConstructor<Object>> getConstructors() {
                throw new IllegalStateException("Should not be called");
            }

            @Override
            public Set<AnnotatedMethod<? super Object>> getMethods() {
                throw new IllegalStateException("Should not be called");
            }

            @Override
            public Set<AnnotatedField<? super Object>> getFields() {
                throw new IllegalStateException("Should not be called");
            }

            @Override
            public Type getBaseType() {
                throw new IllegalStateException("Should not be called");
            }

            @Override
            public Set<Type> getTypeClosure() {
                throw new IllegalStateException("Should not be called");
            }
        };
    }

    @Override
    public Method getJavaMember() {
        return method;
    }

    @Override
    public AnnotatedType<Object> getDeclaringType() {
        return type;
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationType) {
        Set<T> set = getAnnotations(annotationType);
        return set.isEmpty() ? null : set.iterator().next();
    }

    @Override
    public <T extends Annotation> Set<T> getAnnotations(Class<T> annotationType) {
        T[] annotationsByType = getJavaMember().getAnnotationsByType(annotationType);
        return new LinkedHashSet<>(asList(annotationsByType));
    }

    @Override
    public boolean isAnnotationPresent(Class<? extends Annotation> annotationType) {
        return getJavaMember().isAnnotationPresent(annotationType);
    }

    public Set<Annotation> getAnnotations() {
        Annotation[] annotations = getJavaMember().getAnnotations();
        return new LinkedHashSet<>(asList(annotations));
    }

    @Override
    public Type getBaseType() {
        throw new IllegalStateException("Should not be called");
    }

    @Override
    public Set<Type> getTypeClosure() {
        throw new IllegalStateException("Should not be called");
    }

    @Override
    public List<AnnotatedParameter<Object>> getParameters() {
        throw new IllegalStateException("Should not be called");
    }

    @Override
    public boolean isStatic() {
        throw new IllegalStateException("Should not be called");
    }
}
