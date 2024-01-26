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

package io.helidon.service.core;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

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
     * The provider is an implementation of a {@link io.helidon.service.core.Service.Contract} that is discoverable by
     * the service registry.
     * A service provider annotated with this annotation must provide an accessible no-argument constructor
     * (package private is sufficient), and must itself be at least package private.
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.TYPE)
    public @interface Provider {
        /**
         * Type name of this annotation.
         */
        TypeName TYPE_NAME = TypeName.create(Provider.class);
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
     *  provide a public constant called {@code INSTANCE}, and all its injection points ({@link io.helidon.inject.service.Ip}
     *  must be available as public constants (and correctly described in each Ip instance).
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE})
    public @interface Descriptor {
        /**
         * Type name of this interface.
         */
        TypeName TYPE_NAME = TypeName.create(Descriptor.class);
    }
}
