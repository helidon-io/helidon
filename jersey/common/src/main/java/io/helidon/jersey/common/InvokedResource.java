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
package io.helidon.jersey.common;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Optional;

import javax.ws.rs.container.ContainerRequestContext;

/**
 * Information about the current request - invoked resource information.
 */
public interface InvokedResource {
    /**
     * Create a new invoked resource from Jersey container request context.
     *
     * @param context request context
     * @return an instance of invoked resource to access information about resource class, method and annotations
     */
    static InvokedResource create(ContainerRequestContext context) {
        return InvokedResourceImpl.create(context);
    }

    /**
     * Method that defines the invoked resource method.
     * This may come from an interface.
     *
     * @return Method used to declared handling of the current request or empty if none found
     */
    Optional<Method> definitionMethod();

    /**
     * Method that handles the invoked resource method.
     * This must come from a class.
     *
     * @return Method used to handle current request or empty if none found
     */
    Optional<Method> handlingMethod();

    /**
     * Resource definition class.
     * The definition class is the class annotated with {@link javax.ws.rs.Path}
     *  annotation.
     *
     * @return class of the JAX-RS resource or empty if none found
     */
    Optional<Class<?>> definitionClass();

    /**
     * Resource handling class.
     * The handling class is the class that declares the handling method.
     *
     * @return class of the JAX-RS resource implementation or empty if none found
     */
    Optional<Class<?>> handlingClass();

    /**
     * Find the annotation by class closest to the handling method.
     * <p>
     * Search order:
     * <ol>
     *     <li>{@link #handlingMethod()}</li>
     *     <li>All methods from super classes up to {@link #definitionMethod()}</li>
     *     <li>{@link #handlingClass()}</li>
     *     <li>All super classes of the {@link #handlingClass()}</li>
     *     <li>All implemented interfaces</li>
     * </ol>
     * @param annotationClass class of the annotation to find
     * @param <T> type of the annotation
     * @return first annotation found, or empty if not declared
     */
    <T extends Annotation> Optional<T> findAnnotation(Class<T> annotationClass);

    /**
     * Find method annotation by class closest to the handling method.
     * <p>
     * Search order:
     * <ol>
     *     <li>{@link #handlingMethod()}</li>
     *     <li>All methods from super classes up to {@link #definitionMethod()}</li>
     * </ol>
     * @param annotationClass class of the annotation to find
     * @param <T> type of the annotation
     * @return first annotation found, or empty if not declared on a method
     */
    <T extends Annotation> Optional<T> findMethodAnnotation(Class<T> annotationClass);

    /**
     * Find class annotation by class closest to the handling class.
     * <p>
     * Search order:
     * <ol>
     *     <li>{@link #handlingClass()}</li>
     *     <li>All super classes of the {@link #handlingClass()}</li>
     *     <li>All implemented interfaces</li>
     * </ol>
     * @param annotationClass class of the annotation to find
     * @param <T> type of the annotation
     * @return first annotation found, or empty if not declared on a class
     */
    <T extends Annotation> Optional<T> findClassAnnotation(Class<T> annotationClass);
}
