/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

package io.helidon.security.spi;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Set;

import io.helidon.security.EndpointConfig;
import io.helidon.security.SecurityContext;

/**
 * Base interface for all security providers.
 *
 * @see EndpointConfig
 * @see SecurityContext#endpointConfig()
 */
public interface SecurityProvider {
    /**
     * Provide extension annotations supported by this provider (e.g. {@code javax.annotation.security.RolesAllowed}).
     * Annotations will be collected according to framework in use. For JAX-RS, annotations from application class, resource
     * class and resource methods will be collected.
     *
     * @return Collection of annotations this provider expects.
     */
    default Collection<Class<? extends Annotation>> supportedAnnotations() {
        return Set.of();
    }

    /**
     * Keys expected in configuration. This may be used in integrations that can
     * be fully configured through a file (e.g. integration with web server).
     * This is a configuration of a specific resource access (e.g. GET on /my/resource) and
     * is to be used by this provider to evaluate security.
     *
     * @return name of the configuration key or empty (default)
     * @see EndpointConfig#config(String)
     */
    default Collection<String> supportedConfigKeys() {
        return Set.of();
    }

    /**
     * Class of the configuration type.
     * The provider may use a POJO implementing a {@link ProviderConfig} to
     * configure it. When configuring security, you user can provide an instance
     * of such a class to configure that provider.
     *
     * @return class of the type or empty (default)
     * @see EndpointConfig#instance(Class)
     */
    default Collection<Class<? extends ProviderConfig>> supportedCustomObjects() {
        return Set.of();
    }

    /**
     * A collection of attribute names expected by this provider to override endpoint
     * configuration.
     *
     * @return collection of supported attribute names
     * @see EndpointConfig#abacAttribute(String)
     */
    default Collection<String> supportedAttributes() {
        return Set.of();
    }

}
