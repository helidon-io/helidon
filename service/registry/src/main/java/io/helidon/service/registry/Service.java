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

package io.helidon.service.registry;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

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
     *     <li>{@code Supplier<Contract>} - and <b>suppliers of all above</b>, to break instantiation chaining, and to support cyclic
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
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Inherited
    public @interface Provider {
        /**
         * Type name of this annotation.
         */
        TypeName TYPE = TypeName.create(Provider.class);
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
     * Default behavior of the service registry is to only provide support lookup based on contracts.
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
     * Annotation to add a custom service descriptor.
     * <p>
     * The service descriptor will be added as any other service descriptor that is generated, only it is expected
     * as a source.
     * The descriptor MUST follow the approach of Helidon service descriptor, and must be public,
     * provide a public constant called {@code INSTANCE}, and all its dependencies
     * ({@link io.helidon.service.registry.Dependency}
     * must be available as public constants (and correctly described in each Ip instance).
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
         * Type of service registry that should read this descriptor. Defaults to
         * {@value DescriptorHandler#REGISTRY_TYPE_CORE}, so the descriptor must only implement
         * {@link io.helidon.service.registry.GeneratedService.Descriptor}.
         *
         * @return type of registry this descriptor supports
         */
        String registryType() default DescriptorHandler.REGISTRY_TYPE_CORE;

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
}
