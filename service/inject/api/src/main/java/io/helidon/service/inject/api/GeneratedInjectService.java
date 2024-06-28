/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.service.inject.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.service.registry.DependencyContext;
import io.helidon.service.registry.GeneratedService;

/**
 * All types in this class are used from generated code for services.
 */
public final class GeneratedInjectService {
    private GeneratedInjectService() {
    }

    /**
     * A descriptor of a service. In addition to providing service metadata, this also allows instantiation
     * and injection to the service instance.
     *
     * @param <T> type of the service this descriptor describes
     */
    public interface Descriptor<T> extends GeneratedService.Descriptor<T>, InjectServiceInfo {
        /**
         * Create a new service instance.
         *
         * @param ctx                  injection context with all injection points data
         * @param interceptionMetadata interception metadata to use when the constructor should be intercepted
         * @return a new instance, must be of the type T or a subclass
         */
        // we cannot return T, as it does not allow us to correctly handle inheritance
        default Object instantiate(DependencyContext ctx, InterceptionMetadata interceptionMetadata) {
            throw new IllegalStateException("Cannot instantiate type " + serviceType().fqName() + ", as it is either abstract,"
                                                    + " or an interface.");
        }

        /**
         * Inject fields and methods.
         *
         * @param ctx                  injection context
         * @param interceptionMetadata interception metadata to support interception of field injection
         * @param injected             mutable set of already injected methods from subtypes
         * @param instance             instance to update
         */
        default void inject(DependencyContext ctx,
                            InterceptionMetadata interceptionMetadata,
                            Set<String> injected,
                            T instance) {
        }

        /**
         * Invoke {@link Injection.PostConstruct} annotated method(s).
         *
         * @param instance instance to use
         */
        default void postConstruct(T instance) {
        }

        /**
         * Invoke {@link Injection.PreDestroy} annotated method(s).
         *
         * @param instance instance to use
         */
        default void preDestroy(T instance) {
        }
    }

    /**
     * Provides a service descriptor, or an intercepted instance with information
     * whether to, and how to intercept elements.
     * <p>
     * Used by generated code (passed as a parameter to
     * {@link
     * io.helidon.service.inject.api.GeneratedInjectService.Descriptor#inject(io.helidon.service.registry.DependencyContext,
     * io.helidon.service.inject.api.GeneratedInjectService.InterceptionMetadata, java.util.Set, Object)}, and
     * {@link
     * io.helidon.service.inject.api.GeneratedInjectService.Descriptor#instantiate(io.helidon.service.registry.DependencyContext,
     * io.helidon.service.inject.api.GeneratedInjectService.InterceptionMetadata)}).
     */
    public interface InterceptionMetadata {
        /**
         * Create an invoker that handles interception if needed, for constructors.
         *
         * @param descriptor        metadata of the service being intercepted
         * @param typeQualifiers    qualifiers on the type
         * @param typeAnnotations   annotations on the type
         * @param element           element being intercepted
         * @param targetInvoker     invoker of the element
         * @param checkedExceptions expected checked exceptions that can be thrown by the invoker
         * @param <T>               type of the result of the invoker
         * @return an invoker that handles interception if enabled and if there are matching interceptors, any checkedException
         *         will
         *         be re-thrown, any runtime exception will be re-thrown
         */
        <T> Invoker<T> createInvoker(InjectServiceInfo descriptor,
                                     Set<Qualifier> typeQualifiers,
                                     List<Annotation> typeAnnotations,
                                     TypedElementInfo element,
                                     Invoker<T> targetInvoker,
                                     Set<Class<? extends Throwable>> checkedExceptions);

        /**
         * Create an invoker that handles interception if needed.
         *
         * @param serviceInstance   instance of the service that is being intercepted
         * @param descriptor        metadata of the service being intercepted
         * @param typeQualifiers    qualifiers on the type
         * @param typeAnnotations   annotations on the type
         * @param element           element being intercepted
         * @param targetInvoker     invoker of the element
         * @param checkedExceptions expected checked exceptions that can be thrown by the invoker
         * @param <T>               type of the result of the invoker
         * @return an invoker that handles interception if enabled and if there are matching interceptors, any checkedException
         *         will
         *         be re-thrown, any runtime exception will be re-thrown
         */
        <T> Invoker<T> createInvoker(Object serviceInstance,
                                     InjectServiceInfo descriptor,
                                     Set<Qualifier> typeQualifiers,
                                     List<Annotation> typeAnnotations,
                                     TypedElementInfo element,
                                     Invoker<T> targetInvoker,
                                     Set<Class<? extends Throwable>> checkedExceptions);
    }

    /**
     * Each descriptor for s service that is implements {@link io.helidon.service.inject.api.Injection.QualifiedProvider}
     * implements this interface to provide information about the qualifier it supports.
     */
    public interface QualifiedProviderDescriptor {
        /**
         * Type of qualifier a {@link io.helidon.service.inject.api.Injection.QualifiedProvider} provides.
         *
         * @return type name of the qualifier this qualified provider can provide instances for
         */
        TypeName qualifierType();
    }

    /**
     * Each descriptor for s service that is annotated with {@link io.helidon.service.inject.api.Injection.CreateFor}
     * implements this interface to provide information about the type that drives it.
     */
    public interface CreateForDescriptor {
        /**
         * Service instances may be created for instances of another service.
         * If a type is created for another type, it inherits ALL qualifiers of the type that it is based on.
         *
         * @return create for service type
         */
        TypeName createFor();
    }

    /**
     * Each descriptor for a service that implements {@link io.helidon.service.inject.api.Injection.ScopeHandler}
     * implements this interface to provide information about the scope it handles.
     */
    public interface ScopeHandlerDescriptor {
        /**
         * Scope handled by the scope handler service.
         *
         * @return type of the scope handled (annotation)
         */
        TypeName handledScope();
    }

    /**
     * Utility type to provide method to combine injection point information for inheritance support.
     */
    public static final class IpSupport {
        private IpSupport() {
        }

        /**
         * Combine dependencies from this type with dependencies from supertype.
         * This is a utility for code generated types.
         *
         * @param myType    this type's dependencies
         * @param superType super type's dependencies
         * @return a new list without constructor dependencies from super type
         */
        public static List<Ip> combineIps(List<Ip> myType, List<Ip> superType) {
            List<Ip> result = new ArrayList<>(myType);

            // always inject all fields
            result.addAll(superType.stream()
                                  .filter(it -> it.elementKind() == ElementKind.FIELD)
                                  .toList());
            // ignore constructors, as we only need to inject constructor on the instantiated type

            // and only add methods that are not already injected on existing type
            Set<String> injectedMethods = myType.stream()
                    .filter(it -> it.elementKind() == ElementKind.METHOD)
                    .map(Ip::method)
                    .flatMap(Optional::stream)
                    .collect(Collectors.toSet());

            result.addAll(superType.stream()
                                  .filter(it -> it.elementKind() == ElementKind.METHOD)
                                  .filter(it -> it.method().isPresent())
                                  .filter(it -> injectedMethods.add(it.method().get())) // we check presence above
                                  .toList());

            return List.copyOf(result);
        }
    }
}
