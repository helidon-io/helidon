/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import io.helidon.common.GenericType;
import io.helidon.common.types.TypeName;

/**
 * Injection annotations. These annotations can extend support provided through
 * {@link io.helidon.service.registry.Service} annotations for injection.
 * <p>
 * This is the entry point for any annotation related to service definition with injection support in Helidon Service Registry.
 * <p>
 * Explore annotations in this type to find out how to enhance your service behavior.
 * <p>
 * Note that to utilize Helidon Inject and its service registry, you need to configure annotation processor to generate
 * required source files.
 */
public final class Injection {
    private Injection() {
    }

    /**
     * Method, constructor, or field marked with this annotation is considered as injectable, and its injection points
     * will be satisfied with services from the service registry. An injection point is a field, or a single parameter.
     * <p>
     * An injection point may expect instance of a service, or a {@link java.util.function.Supplier} of the same.
     * <p>
     * Annotating an inaccessible component will always be marked as an error at compilation time
     * (private fields, methods, constructors).
     * <p>
     * Annotating a final field will always be marked as an error at compilation time.
     * <p>
     * We recommend to use constructor injection, as field injection makes testing harder.
     */
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD})
    @Documented
    public @interface Inject {
    }

    /**
     * Marks annotations that act as qualifiers.
     * <p>
     * A qualifier annotation restricts the eligible service instances that can be injected into an injection point to those
     * qualified by the same qualifier.
     */
    @Target(ElementType.ANNOTATION_TYPE)
    @Retention(RetentionPolicy.CLASS)
    @Documented
    public @interface Qualifier {
    }

    /**
     * A qualifier that can restrict injection to specifically named instances, or that qualifies services with that name.
     */
    @Qualifier
    @Retention(RetentionPolicy.CLASS)
    @Documented
    @Target({ElementType.CONSTRUCTOR, ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD, ElementType.TYPE})
    public @interface Named {
        /**
         * Type name of this annotation.
         */
        TypeName TYPE = TypeName.create(Named.class);
        /**
         * Represents a wildcard name (i.e., matches anything).
         */
        String WILDCARD_NAME = "*";
        /**
         * Default name to identify a default instance.
         */
        String DEFAULT_NAME = "@default";

        /**
         * The name.
         *
         * @return name this injection point requires, or this service provides, or a supplier provider
         */
        String value();
    }

    /**
     * Scope annotation.
     * A scope defines the cardinality of instances. This is a meta-annotation used to define that an annotation is a scope.
     * Note that a single service can only have one scope annotation, and that scopes are not inheritable.
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.ANNOTATION_TYPE)
    @Inherited
    public @interface Scope {
    }

    /**
     * A partial scope that creates a new instance for each injection point/lookup.
     * The "partial scope" means that the service instances are not managed. If this
     * service gets injected, a new instance is created for each injection. The service is instantiated,
     * post construct method (if any) is called, and then it is ignored (i.e. it never gets a pre destroy
     * method invocation).
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE})
    @Scope
    public @interface Instance {
        /**
         * Type name of this interface.
         */
        TypeName TYPE = TypeName.create(Instance.class);
    }

    /**
     * A singleton service.
     * The service registry will only contain a single instance of this service, and all injection points will be satisfied by
     * the same instance.
     * <p>
     * A singleton instance is guaranteed to have its constructor, post-construct, and pre-destroy methods invoked once within
     * the lifecycle of the service registry.
     * <p>
     * Alternative to this annotation is {@link io.helidon.service.inject.api.Injection.Instance} (or no annotation on a type
     * that has {@link Injection.Inject} on its elements). Such a service would be injected
     * every time its provider is invoked (each injection point, or on call to {@link java.util.function.Supplier#get()} if
     * supplier is injected), and {@link Injection.RequestScope} for request bound instances.
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Scope
    @Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    public @interface Singleton {
        /**
         * Type name of this annotation.
         */
        TypeName TYPE = TypeName.create(Singleton.class);
    }

    /**
     * A service with an instance per request.
     * Injections to different scopes are supported, but must be through a {@link java.util.function.Supplier},
     * as we do not provide a proxy mechanism for instances.
     * <p>
     * Request scope is not started by default. If you want to use request scope, you can add the following
     * library to your classpath to add support for it:
     * <ul>
     *     <li>{@code io.helidon.declarative.webserver:helidon-declarative-webserver-request-scope}</li>
     * </ul>
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Scope
    @Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE, ElementType.METHOD})
    public @interface RequestScope {
        /**
         * This interface type.
         */
        TypeName TYPE = TypeName.create(RequestScope.class);
    }

    /**
     * This annotation is effectively the same as {@link Injection.Named}
     * where the {@link Injection.Named#value()} is a {@link Class}
     * name instead of a {@link String}. The name that would be used is the fully qualified name of the class.
     */
    @Qualifier
    @Documented
    @Retention(RetentionPolicy.CLASS)
    public @interface NamedByClass {
        /**
         * Type name of this interface.
         * {@link io.helidon.common.types.TypeName} is used in Helidon Inject APIs.
         */
        TypeName TYPE = TypeName.create(NamedByClass.class);

        /**
         * The class used will function as the name.
         *
         * @return the class
         */
        Class<?> value();
    }

    /**
     * Indicates the desired startup sequence for a service class. This is not used internally by Injection, but is available as a
     * convenience to the caller in support for a specific startup sequence for service activations.
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.TYPE)
    public @interface RunLevel {

        /**
         * Represents an eager singleton that should be started at "startup". Note, however, that callers control the actual
         * activation for these services, not the injection framework itself, as shown below:         * <pre>
         * {@code
         * registry.all(Lookup.builder()
         *     .runLevel(Injection.RunLevel.STARTUP)
         *     .build());
         * }
         * </pre>
         */
        double STARTUP = 10D;

        /**
         * Represents services that have the concept of "serving" something, such as webserver.
         */
        double SERVER = 50D;

        /**
         * Anything > 0 is left to the underlying provider implementation's discretion for meaning; this is just a default for
         * something that is deemed "other than startup".
         */
        double NORMAL = 100D;

        /**
         * The service ranking applied when not declared explicitly.
         *
         * @return the startup int value, defaulting to {@link #NORMAL}
         */
        double value() default NORMAL;
    }

    /**
     * A service that has instances created for each named instance of the service it is driven by.
     * The instance created will have the same {@link Injection.Named} qualifier as the
     * driving instance (in addition to all qualifiers defined on this service).
     * <p>
     * There are a few restrictions on this type of services:
     * <ul>
     * <li>The service MUST NOT implement {@link java.util.function.Supplier}</li>
     * <li>The service MUST NOT implement {@link Injection.InjectionPointProvider}</li>
     * <li>The service MUST NOT implement {@link Injection.ServicesProvider}</li>
     * <li>All types that inherit from this service will also inherit the driven by</li>
     * <li>There MAY be an injection point of the type defined in {@link #value()}, without any qualifiers -
     * this injection point will be satisfied by the driving instance</li>
     * <li>There MAY be a {@link String} injection point qualified with
     * {@link io.helidon.service.inject.api.Injection.CreateForName} - this injection point will be satisfied by the
     * name of the driving instance</li>
     * </ul>
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.TYPE)
    public @interface CreateFor {
        /**
         * The service type driving this service. The service type MUST provide {@link Injection.Named}
         * instances.
         *
         * @return type of the service driving instances of this service
         */
        Class<?> value();
    }

    /**
     * For types that are {@link io.helidon.service.inject.api.Injection.CreateFor}, an injection point (field, parameter) can
     * be annotated with this annotation to receive the name qualifier associated with this instance.
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.PARAMETER, ElementType.FIELD})
    @Qualifier
    public @interface CreateForName {
        /**
         * Type name of this interface.
         * {@link io.helidon.common.types.TypeName} is used in Helidon Inject APIs.
         */
        TypeName TYPE = TypeName.create(CreateForName.class);
    }

    /**
     * Describe the annotated type. This will generate a service descriptor that cannot create an instance.
     * This is useful for scoped instances that are provided when the scope is activated.
     * <p>
     * This annotation will ignore type hierarchy (the descriptor will never have a super type).
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.TYPE)
    public @interface Describe {
        /**
         * Customize the scope to use, defaults to {@link io.helidon.service.inject.api.Injection.Singleton}.
         *
         * @return scope to use for the generated service descriptor
         */
        Class<? extends Annotation> value() default Singleton.class;
    }

    /**
     * Provides an ability to create more than one service instance from a single service definition.
     * This is useful when the cardinality can only be determined at runtime.
     *
     * @param <T> type of the provided services
     */
    public interface ServicesProvider<T> {
        /**
         * Type name of this interface.
         */
        TypeName TYPE = TypeName.create(ServicesProvider.class);

        /**
         * List of service suppliers.
         * Each instance may have a different set of qualifiers.
         * <p>
         * The following is inherited from this provider:
         * <ul>
         *     <li>Set of contracts, except for {@link Injection.ServicesProvider}</li>
         *     <li>Scope</li>
         *     <li>Run level</li>
         *     <li>Weight</li>
         * </ul>
         *
         * @return qualified suppliers of service instances
         */
        List<QualifiedInstance<T>> services();
    }

    /**
     * Provides ability to contextualize the injected service by the target receiver of the injection point dynamically
     * at runtime. This API will provide service instances of type {@code T}. These services may be singleton, or created based
     * upon
     * scoping cardinality that is defined by the provider implementation of the given type. This is why the javadoc reads "get
     * (or
     * create)".
     * <p>
     * The ordering of services, and the preferred service itself, is determined by the service registry implementation.
     * <p>
     * The service registry does not make any assumptions about qualifiers of the instances being created, though they should
     * be either the same as the injection point provider itself, or a subset of it, so the service can be discovered through
     * one of the lookup methods (i.e. the injection point provider may be annotated with a
     * {@link Named} with {@link Named#WILDCARD_NAME}
     * value,
     * and each instance provided may use a more specific name qualifier).
     *
     * @param <T> the type that the provider produces
     */
    public interface InjectionPointProvider<T> {
        /**
         * Type name of this interface.
         */
        TypeName TYPE = TypeName.create(InjectionPointProvider.class);

        /**
         * Get (or create) an instance of this service type for the given injection point context. This is logically the same
         * as using the first element of the result from calling {@link #list(io.helidon.service.inject.api.Lookup)}.
         *
         * @param query the service query
         * @return the best service provider matching the criteria, if any matched, with qualifiers (if any)
         */
        Optional<QualifiedInstance<T>> first(Lookup query);

        /**
         * Get (or create) a list of instances matching the criteria for the given injection point context.
         *
         * @param query the service query
         * @return the resolved services matching criteria for the injection point in order of weight, or empty if none matching
         */
        default List<QualifiedInstance<T>> list(Lookup query) {
            return first(query).map(List::of).orElseGet(List::of);
        }
    }

    /**
     * A provider to resolve qualified injection points of any type.
     * <p>
     * As compared to {@link InjectionPointProvider}, this type is capable of resolving ANY injection
     * point as long as it is annotated by the qualifier. The contract of the injection point depends on how the implementation
     * service declares the type parameters of this interface. If you use any type other than {@link java.lang.Object}, that will
     * be the only supported contract, otherwise any type is expected to be supported.
     * <p>
     * A good practice is to create an accompanying codegen extension that validates injection points at build time.
     *
     * @param <A> type of qualifier supported by this provider
     * @param <T> type of the provided instance, the special case is {@link java.lang.Object} - if used, we consider this
     *            provider to be capable of handling ANY type, and will allow injection points with any type as long as it is
     *            qualified by the qualifier
     */
    public interface QualifiedProvider<A extends Annotation, T> {
        /**
         * Type name of this interface.
         */
        TypeName TYPE = TypeName.create(QualifiedProvider.class);

        /**
         * Get the first instance (if any) matching the qualifier and type.
         *
         * @param qualifier the qualifier this type supports (same type as the {@code A} type this type implements)
         * @param lookup    full lookup used to obtain the value, may contain the actual injection point
         * @param type      type to be injected (or type requested)
         * @return the qualified instance matching the request, or an empty optional if none match
         */
        Optional<QualifiedInstance<T>> first(io.helidon.service.inject.api.Qualifier qualifier,
                                             Lookup lookup,
                                             GenericType<T> type);

        /**
         * Get all instances matching the qualifier and type.
         *
         * @param qualifier the qualifier this type supports (same type as the {@code A} type this type implements)
         * @param lookup    full lookup used to obtain the value, may contain the actual injection point
         * @param type      type to be injected (or type requested)
         * @return the qualified instance matching the request, or an empty optional if none match
         */
        default List<QualifiedInstance<T>> list(io.helidon.service.inject.api.Qualifier qualifier,
                                                Lookup lookup,
                                                GenericType<T> type) {
            return first(qualifier, lookup, type)
                    .map(List::of)
                    .orElseGet(List::of);
        }
    }

    /**
     * An instance with its qualifiers.
     * Some services are allowed to create more than one instance, and there may be a need
     * to use different qualifiers than the provider service uses.
     *
     * @param <T> type of instance, as provided by the service
     * @see Injection.ServicesProvider
     */
    public interface QualifiedInstance<T> extends Supplier<T> {
        /**
         * Create a new qualified instance.
         *
         * @param instance   the instance
         * @param qualifiers qualifiers to use
         * @param <T>        type of the instance
         * @return a new qualified instance
         */
        static <T> QualifiedInstance<T> create(T instance, io.helidon.service.inject.api.Qualifier... qualifiers) {
            /*
                Developer note: this method is used from generated code of __ConfigBean
            */
            return new QualifiedInstanceImpl<>(instance, Set.of(qualifiers));
        }

        /**
         * Create a new qualified instance.
         *
         * @param instance   the instance
         * @param qualifiers qualifiers to use
         * @param <T>        type of the instance
         * @return a new qualified instance
         */
        static <T> QualifiedInstance<T> create(T instance, Set<io.helidon.service.inject.api.Qualifier> qualifiers) {
            return new QualifiedInstanceImpl<>(instance, qualifiers);
        }

        /**
         * Get the instance that the registry manages (or an instance that is unmanaged, if the provider is not within a scope).
         * The instance must be guaranteed to be constructed and if managed by the registry, and activation scope is not limited,
         * then injected as well.
         *
         * @return instance
         */
        @Override
        T get();

        /**
         * Qualifiers of the instance.
         *
         * @return qualifiers of the service instance
         */
        Set<io.helidon.service.inject.api.Qualifier> qualifiers();
    }

    /**
     * Extension point for the service registry.
     * To support additional scope, a service implementing this interface must be available in the registry.
     * It should be accompanied by a way to start and stop the scope (such as {@link RequestScopeControl} for
     * request scope).
     *
     * @param <T> Type of the supported scope
     */
    @io.helidon.service.registry.Service.Contract
    public interface ScopeHandler<T extends Annotation> {
        /**
         * Type name of this interface.
         * Service registry uses {@link io.helidon.common.types.TypeName} in its APIs.
         */
        TypeName TYPE = TypeName.create(ScopeHandler.class);

        /**
         * Get the current scope if available.
         *
         * @return current scope instance, or empty if the scope is not active
         */
        Optional<io.helidon.service.inject.api.Scope> currentScope();
    }
}
