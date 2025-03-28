/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

package io.helidon.service.registry;

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
import io.helidon.common.Weighted;
import io.helidon.common.types.TypeName;

/**
 * A set of annotations (and APIs) required to declare a service.
 */
public final class Service {
    private Service() {
    }

    /**
     * A service provider. This is similar to {@link java.util.ServiceLoader} service providers.
     * <p>
     * The provider is an implementation of a {@link io.helidon.service.registry.Service.Contract} that is discoverable by
     * the service registry.
     * A service provider annotated with this annotation must provide either of:
     * <ul>
     *     <li>an accessible no-argument constructor
     *      (package private is sufficient), and must itself be at least package private</li>
     *      <li>an accessible constructor with arguments that are all services as well</li>
     * </ul>
     *
     * The support for providing constructor arguments is limited to the following types:
     * <ul>
     *     <li>{@code Contract} - obtain an instance of a service providing that contract</li>
     *     <li>{@code Optional<Contract>} - the other service may not be available</li>
     *     <li>{@code List<Contract>} - obtain all instances of services providing the contract</li>
     *     <li>{@code Supplier<Contract>} - and <b>suppliers of all above</b>, to break instantiation chaining, and to support
     *     cyclic
     *                  service references, just make sure you call the {@code get} method outside of the constructor</li>
     * </ul>
     *
     * A service provider may implement the contract in two ways:
     * <ul>
     *     <li>Direct implementation of interface (or extending an abstract class)</li>
     *     <li>Implementing a {@link java.util.function.Supplier} of the contract; when using supplier, service registry
     *     supports the capability to return {@link java.util.Optional} in case the service cannot provide a value; such
     *     a service will be ignored and only other implementations (with lower weight) would be used. Supplier will be
     *     called each time the dependency is used, or each time a method on registry is called to request an instance. If the
     *     provided instance should be singleton-like as well, use {@link io.helidon.common.LazyValue} or
     *     similar approach to create it once and return the same instance every time</li>
     * </ul>
     *
     * @deprecated use one of the scope annotations instead ({@link io.helidon.service.registry.Service.Singleton},
     *         {@link io.helidon.service.registry.Service.PerLookup}).
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Inherited
    @Deprecated(forRemoval = true, since = "4.2.0")
    public @interface Provider {
        /**
         * Type name of this annotation.
         */
        TypeName TYPE = TypeName.create(Provider.class);
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
     * A singleton service.
     * The service registry will only contain a single instance of this service, and all injection points will be satisfied by
     * the same instance.
     * <p>
     * A singleton instance is guaranteed to have its constructor, post-construct, and pre-destroy methods invoked once within
     * the lifecycle of the service registry.
     * <p>
     * Alternative to this annotation is {@link io.helidon.service.registry.Service.PerLookup}, or a custom
     * {@link io.helidon.service.registry.Service.Scope} annotation.
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
    public @interface PerRequest {
        /**
         * This interface type.
         */
        TypeName TYPE = TypeName.create(PerRequest.class);
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
    public @interface PerLookup {
        /**
         * Type name of this interface.
         */
        TypeName TYPE = TypeName.create(PerLookup.class);
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
     * A method annotated with this annotation will be invoked after the constructor is finished
     * and all dependencies are satisfied.
     * <p>
     * The method must not have any parameters and must be accessible (not {@code private}).
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.METHOD)
    public @interface PostConstruct {
    }

    /**
     * A method annotated with this annotation will be invoked when the service registry shuts down.
     * <p>
     * Behavior of this annotation may differ based on the service registry implementation used. For example
     * when using Helidon Service Inject (to be introduced), a pre-destroy method would be used when the scope
     * a service is created in is finished. The core service registry behaves similar like a singleton scope - instance
     * is created once, and pre-destroy is called when the registry is shut down.
     * This also implies that instances that are NOT created within a scope cannot have their pre-destroy methods
     * invoked, as we do not control their lifecycle.
     * <p>
     * The method must not have any parameters and must be accessible (not {@code private}).
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.METHOD)
    public @interface PreDestroy {
    }

    /**
     * Marks annotations that act as qualifiers.
     * <p>
     * A qualifier annotation restricts the eligible service instances that can be injected into an injection point to those
     * qualified by the same qualifier.
     */
    @Target(ElementType.ANNOTATION_TYPE)
    @Retention(RetentionPolicy.RUNTIME) // we need this for our CDI integration
    @Documented
    public @interface Qualifier {
    }

    /**
     * A qualifier that can restrict injection to specifically named instances, or that qualifies services with that name.
     */
    @Qualifier
    // we need runtime retention policy to correctly handle CDI integration
    @Retention(RetentionPolicy.RUNTIME)
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
         * @return name this injection point requires, or this service provides, or a factory provides
         */
        String value();
    }

    /**
     * This annotation is effectively the same as {@link io.helidon.service.registry.Service.Named}
     * where the {@link io.helidon.service.registry.Service.Named#value()} is a {@link Class}
     * name instead of a {@link String}. The name that would be used is the fully qualified name of the type.
     */
    @Qualifier
    @Documented
    @Retention(RetentionPolicy.CLASS)
    public @interface NamedByType {
        /**
         * Type name of this interface.
         * {@link io.helidon.common.types.TypeName} is used in Helidon Inject APIs.
         */
        TypeName TYPE = TypeName.create(NamedByType.class);

        /**
         * The class used will function as the name.
         *
         * @return the class
         */
        Class<?> value();
    }

    /**
     * Indicates the desired startup sequence for a service class.
     *
     * Helidon honors run levels only when using one of the start methods on
     * {@link io.helidon.service.registry.ServiceRegistryManager}:
     * <ul>
     *     <li>{@link io.helidon.service.registry.ServiceRegistryManager#start()}</li>
     *     <li>{@link io.helidon.service.registry.ServiceRegistryManager#start(Binding)}</li>
     *     <li>{@link io.helidon.service.registry.ServiceRegistryManager#start(Binding, ServiceRegistryConfig)}</li>
     *     <li>{@link io.helidon.service.registry.ServiceRegistryManager#start(ServiceRegistryConfig)}</li>
     * </ul>
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
         *     .runLevel(Service.RunLevel.STARTUP)
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
     * The instance created will have the same {@link Named} qualifier as the
     * driving instance (in addition to all qualifiers defined on this service).
     * <p>
     * There are a few restrictions on this type of services:
     * <ul>
     * <li>The service MUST NOT implement {@link java.util.function.Supplier}</li>
     * <li>The service MUST NOT implement {@link InjectionPointFactory}</li>
     * <li>The service MUST NOT implement {@link ServicesFactory}</li>
     * <li>All types that inherit from this service will also inherit the driven by</li>
     * <li>There MAY be an injection point of the type defined in {@link #value()}, without any qualifiers -
     * this injection point will be satisfied by the driving instance</li>
     * <li>There MAY be a {@link String} injection point qualified with
     * {@link InstanceName} - this injection point will be satisfied by the
     * name of the driving instance</li>
     * </ul>
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.TYPE)
    public @interface PerInstance {
        /**
         * The service type driving this service. If the service provides more than one instance,
         * the instances MUST be {@link io.helidon.service.registry.Service.Named}.
         *
         * @return type of the service driving instances of this service
         */
        Class<?> value();
    }

    /**
     * For types that are {@link io.helidon.service.registry.Service.PerInstance}, an injection point (field, parameter) can
     * be annotated with this annotation to receive the name qualifier associated with this instance.
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.PARAMETER, ElementType.FIELD})
    @Qualifier
    public @interface InstanceName {
        /**
         * Type name of this interface.
         * {@link io.helidon.common.types.TypeName} is used in Helidon Inject APIs.
         */
        TypeName TYPE = TypeName.create(InstanceName.class);
    }

    /**
     * The {@code Contract} annotation is used to relay significance to the type that it annotates. While remaining optional in
     * its use, it is typically placed on an interface definition to signify that the given type can be used for lookup in the
     * service registry.
     * While normally placed on interface types, it can also be placed on abstract and concrete class as well. The main point is
     * that a {@code Contract} is the focal point for service lookup.
     * <p>
     * If the developer does not have access to the source to place this annotation on the interface definition directly then
     * consider using {@link ExternalContracts} instead - this annotation can be placed on the
     * implementation class implementing the given {@code Contract} interface(s).
     * <p>
     * Default behavior of the service registry is to assume any super type and implemented interface is a contract. This can
     * be changed through annotation processor/codegen configuration.
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.TYPE)
    public @interface Contract {
        // contract should not be @Inherited, as we do not want types implementing a contract inherit this trait
    }

    /**
     * Placed on the implementation of a service as an alternative to using a {@link Contract}.
     * <p>
     * Use this annotation when it is impossible to place an annotation on the interface itself - for instance of the interface
     * comes from a 3rd party library provider.
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.TYPE)
    public @interface ExternalContracts {

        /**
         * The advertised contract type(s) for the service class implementation.
         *
         * @return the external contract(s)
         */
        Class<?>[] value();
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
         * Customize the scope to use, defaults to {@link Singleton}.
         *
         * @return scope to use for the generated service descriptor
         */
        Class<? extends Annotation> value() default Singleton.class;
    }

    /**
     * Annotation to add a custom service descriptor.
     * <p>
     * The service descriptor will be added as any other service descriptor that is generated, only it is expected
     * as a source.
     * The descriptor MUST follow the approach of Helidon service descriptor, and must be public,
     * provide a public constant called {@code INSTANCE}, and all its dependencies
     * ({@link io.helidon.service.registry.Dependency}
     * must be available as public constants (and correctly described in each Dependency instance).
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE})
    public @interface Descriptor {
        /**
         * Type name of this interface.
         */
        TypeName TYPE = TypeName.create(Descriptor.class);

        /**
         * The weight of the service. This is required for predefined descriptors, as we do not want
         * to instantiate them unless really needed.
         *
         * @return weight of this descriptor
         */
        double weight() default Weighted.DEFAULT_WEIGHT;

        /**
         * Contracts of this service descriptor.
         * Normally these are discovered from the service provider type, but as this is a pre-built descriptor,
         * we need to get them through annotation.
         *
         * @return contracts of this service descriptor
         */
        Class<?>[] contracts();
    }

    /**
     * Instruction for the Helidon Service Codegen to generate application binding.
     * This is needed when a custom main class is to be used with fully generated binding.
     * The "real" binding can only be generated by the Helidon Service Maven Plugin, as it requires the full
     * runtime classpath, but the class must be available at compile time, so we can use it from the custom Main class.
     * <p>
     * Presence of this annotation will trigger generation of an application binding class (empty), that will be overwritten
     * by the Maven plugin.
     */
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.TYPE)
    public @interface GenerateBinding {
        /**
         * Name of the application binding class. When using generated main class, the same name must be
         * configured for the Maven plugin.
         *
         * @return application binding class name
         */
        String value() default "ApplicationBinding";

        /**
         * Package name of the generated binding. Defaults to the package of the type annotated
         * with this annotation.
         * <p>
         * When using generated main class, the same name must be configured for the Maven plugin, unless
         * it is discovered correctly (the Maven Plugin uses the top level package of the current module it sees).
         *
         * @return package name of the generated application binding class
         */
        String packageName() default Named.DEFAULT_NAME;
    }

    /**
     * Instruction for the Helidon Service Codegen to generate method metadata for methods
     * meta-annotated with this annotation.
     * <p>
     * An entry point is the first method invoked when Helidon is called from outside (i.e. an HTTP request),
     * or through some internal means (such as scheduling).
     * <p>
     * This annotation is required on annotations defining endpoints (i.e. HTTP Method annotations) to enable
     * {@link io.helidon.service.registry.EntryPointInterceptor} to work.
     * <p>
     * This annotation is used by framework developers that need to extend the set of entry points of an
     * application.
     */
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
    public @interface EntryPoint {
    }

    /**
     * Provides an ability to create more than one service instance from a single service definition.
     * This is useful when the cardinality can only be determined at runtime.
     *
     * @param <T> type of the provided services
     */
    public interface ServicesFactory<T> {
        /**
         * Type name of this interface.
         */
        TypeName TYPE = TypeName.create(ServicesFactory.class);

        /**
         * List of service instances.
         * Each instance may have a different set of qualifiers.
         * <p>
         * The following is inherited from this factory:
         * <ul>
         *     <li>Set of contracts, except for {@link ServicesFactory}</li>
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
     * at runtime. This API will provide service instances of type {@code T}.
     * <p>
     * The ordering of services, and the preferred service itself, is determined by the service registry implementation.
     * <p>
     * The service registry does not make any assumptions about qualifiers of the instances being created, though they should
     * be either the same as the injection point factory itself, or a subset of it, so the service can be discovered through
     * one of the lookup methods (i.e. the injection point factory may be annotated with a
     * {@link Named} with {@link Named#WILDCARD_NAME}
     * value, and each instance provided may use a more specific name qualifier).
     *
     * @param <T> the type that the factory produces
     */
    public interface InjectionPointFactory<T> {
        /**
         * Type name of this interface.
         */
        TypeName TYPE = TypeName.create(InjectionPointFactory.class);

        /**
         * Get (or create) an instance of this service type for the given injection point context. This is logically the same
         * as using the first element of the result from calling {@link #list(Lookup)}.
         *
         * @param lookup the service query
         * @return the best service instance matching the criteria, if any matched, with qualifiers (if any)
         */
        Optional<QualifiedInstance<T>> first(Lookup lookup);

        /**
         * Get (or create) a list of instances matching the criteria for the given injection point context.
         *
         * @param lookup the service query
         * @return the service instances matching criteria for the lookup in order of weight, or empty if none matching
         */
        default List<QualifiedInstance<T>> list(Lookup lookup) {
            return first(lookup).map(List::of).orElseGet(List::of);
        }
    }

    /**
     * A factory to resolve qualified injection points of any type.
     * <p>
     * As compared to {@link InjectionPointFactory}, this type is capable of resolving ANY injection
     * point as long as it is annotated by the qualifier. The contract of the injection point depends on how the implementation
     * service declares the type parameters of this interface. If you use any type other than {@link java.lang.Object}, that will
     * be the only supported contract, otherwise any type is expected to be supported.
     * <p>
     * A good practice is to create an accompanying codegen extension that validates injection points at build time.
     *
     * @param <T> type of the provided instance, the special case is {@link java.lang.Object} - if used, we consider this
     *            factory to be capable of handling ANY type, and will allow injection points with any type as long as it is
     *            qualified by the qualifier
     * @param <A> type of qualifier supported by this factory
     */
    public interface QualifiedFactory<T, A extends Annotation> {
        /**
         * Type name of this interface.
         */
        TypeName TYPE = TypeName.create(QualifiedFactory.class);

        /**
         * Get the first instance (if any) matching the qualifier and type.
         *
         * @param qualifier the qualifier this type supports (same type as the {@code A} type this type implements)
         * @param lookup    full lookup used to obtain the value, may contain the actual injection point
         * @param type      type to be injected (or type requested)
         * @return the qualified instance matching the request, or an empty optional if none match
         */
        Optional<QualifiedInstance<T>> first(io.helidon.service.registry.Qualifier qualifier,
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
        default List<QualifiedInstance<T>> list(io.helidon.service.registry.Qualifier qualifier,
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
     * to use different qualifiers than the factory service uses.
     *
     * @param <T> type of instance, as provided by the service
     * @see ServicesFactory
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
        static <T> QualifiedInstance<T> create(T instance, io.helidon.service.registry.Qualifier... qualifiers) {
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
        static <T> QualifiedInstance<T> create(T instance, Set<io.helidon.service.registry.Qualifier> qualifiers) {
            return new QualifiedInstanceImpl<>(instance, qualifiers);
        }

        /**
         * Get the instance that the registry manages (or an instance that is unmanaged, if the provider is in
         * {@link PerLookup}, or if the instance is created by a factory).
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
        Set<io.helidon.service.registry.Qualifier> qualifiers();
    }

    /**
     * Extension point for the service registry to support new scopes.
     * <p>
     * Implementation must be qualified with the fully qualified name of the corresponding scope annotation class.
     *
     * @see Named
     * @see NamedByType
     */
    @Service.Contract
    public interface ScopeHandler {
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
        Optional<io.helidon.service.registry.Scope> currentScope();

        /**
         * Activate the given scope.
         *
         * @param scope scope to activate
         */
        default void activate(io.helidon.service.registry.Scope scope) {
            scope.registry().activate();
        }

        /**
         * De-activate the given scope.
         *
         * @param scope scope to de-activate
         */
        default void deactivate(io.helidon.service.registry.Scope scope) {
            scope.registry().deactivate();
        }
    }
}
