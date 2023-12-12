/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.inject.service;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.helidon.common.types.TypeName;

import static java.lang.annotation.ElementType.TYPE;

/**
 * Injection annotations.
 * This is the entry point for any annotation related to service definition in Helidon Inject.
 * <p>
 * To create a service, mark a package local or public class with one of the service annotations (currently either
 * {@link io.helidon.inject.service.Injection.Singleton} or {@link io.helidon.inject.service.Injection.Service}).
 * In addition, you can mark constructor or methods with {@link io.helidon.inject.service.Injection.Inject} to receive
 * other service instances.
 * <p>
 * Explore other annotations in this type to find out how to enhance your service behavior.
 * <p>
 * Note that to utilize Helidon Inject and its service registry, you need to configure annotation processor to generate
 * required source files.
 */
public final class Injection {
    private Injection() {
    }

    /**
     * Method, constructor, or method marked with this annotation is considered an injection point, and will be satisfied with
     * services from the service registry.
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
    @Target({ElementType.CONSTRUCTOR, ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD, TYPE})
    public @interface Named {
        /**
         * Type name of this annotation.
         */
        TypeName TYPE_NAME = TypeName.create(Named.class);
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
     * Annotation to force generation of a service descriptor, even if it otherwise would not be created.
     * <p>
     * Helidon generates service descriptors for types that have the type, field, constructor, method, or a parameter annotated
     * with any of the supported annotations (such as {@link Injection}.
     * This annotation can be used if we want to use a POJO with an accessible no-args constructor and no other
     * annotations as a service, as otherwise no descriptor would be generated.
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE})
    public @interface Service {
    }

    /**
     * A singleton service.
     * The service registry will only contain a single instance of this service, and all injection points will be satisfied by
     * the same instance.
     * <p>
     * A singleton instance is guaranteed to have its constructor, post-construct, and pre-destroy methods invoked once within
     * the lifecycle of the service registry.
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Service
    @Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    public @interface Singleton {
        /*
        Implementation note: we currently do not support custom scopes, so there is no Scope meta annotation.
        If we decide to support scopes, we may want to introduce such an annotation.
        */
    }

    /**
     * Placed on the implementation of a service as an alternative to using a {@link Injection.Contract}.
     * <p>
     * Use this annotation when it is impossible to place an annotation on the interface itself - for instance of the interface
     * comes
     * from a 3rd party library provider.
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
     * The {@code Contract} annotation is used to relay significance to the type that it annotates. While remaining optional in
     * its
     * use, it is typically placed on an interface definition to signify that the given type can be used for lookup in the
     * {@code Services} registry, and be eligible for injection via standard {@code @Inject}.
     * While normally placed on interface types, it can also be placed on abstract and concrete class as well. The main point is
     * that
     * a {@code Contract} is the focal point for service lookup and injection.
     * <p>
     * If the developer does not have access to the source to place this annotation on the interface definition directly then
     * consider
     * using {@link Injection.ExternalContracts} instead - this annotation can be placed on the
     * implementation class implementing the given
     * {@code Contract} interface(s).
     * <p>
     * Default behavior of the service registry is to only provide injections based on
     * {@link Injection.Contract}
     * annotated types.
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.TYPE)
    public @interface Contract {

    }

    /**
     * This annotation is effectively the same as {@link Injection.Named}
     * where the {@link Injection.Named#value()} is a {@link Class}
     * name instead of a {@link String}. The name that would be used is the fully qualified name of the class.
     */
    @Qualifier
    @Documented
    @Retention(RetentionPolicy.CLASS)
    public @interface ClassNamed {

        /**
         * The class used will function as the name.
         *
         * @return the class
         */
        Class<?> value();
    }

    /**
     * A method annotated with this annotation will be invoked once all injection points are satisfied.
     * The method must not have any parameters and must be accessible (not {@code private}).
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.METHOD)
    public @interface PostConstruct {
    }

    /**
     * A method annotated with this annotation will be invoked once for instances within a scope
     * (such as @{@link Injection.Singleton} service instances) that is being terminated.
     * <p>
     * The method must not have any parameters and must be accessible (not {@code private}).
     * <p>
     * Note that instances that are not created within a scope will not have this method invoked (as their lifecycle is not
     * fully managed by the injector).
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.METHOD)
    public @interface PreDestroy {
    }

    /**
     * Indicates the desired startup sequence for a service class. This is not used internally by Injection, but is available as a
     * convenience to the caller in support for a specific startup sequence for service activations.
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target(TYPE)
    public @interface RunLevel {

        /**
         * Represents an eager singleton that should be started at "startup". Note, however, that callers control the actual
         * activation for these services, not the framework itself, as shown below:
         * <pre>
         * {@code
         * List<ServiceProvider<Object>> startupServices = services
         *               .lookup(ServiceInfoCriteria.builder().runLevel(RunLevel.STARTUP).build());
         *       startupServices.stream().forEach(ServiceProvider::get);
         * }
         * </pre>
         */
        int STARTUP = 10;

        /**
         * Anything > 0 is left to the underlying provider implementation's discretion for meaning; this is just a default for
         * something that is deemed "other than startup".
         */
        int NORMAL = 100;

        /**
         * The service ranking applied when not declared explicitly.
         *
         * @return the startup int value, defaulting to {@link #NORMAL}
         */
        int value() default NORMAL;

    }
}
