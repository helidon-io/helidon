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

import io.helidon.common.features.api.Features;
import io.helidon.common.features.api.HelidonFlavor;

/**
 * Helidon Service Registry.
 * <p>
 * This module is a replacement for Java {@link java.util.ServiceLoader} that supports dependency injections and interception.
 * It is fully build-time based, and when used together with Helidon Declarative features, and Service Maven plugin, it
 * allows creation of applications that have no discovery at startup, and computed bindings.
 * <p>
 * The concept is based on the following principals:
 * <ul>
 *     <li>Build time processing - using annotation processors (we use "Codegen" in Helidon)</li>
 *     <li>Reflection free where possible - all generated code is reflection free</li>
 *     <li>Discovery free - when used together with the Service Maven Plugin and Declarative programming model, there is
 *     no runtime discovery (i.e. no classpath scanning, no loading of resources etc.)</li>
 * </ul>
 *
 * This module is a production feature of Helidon (we have moved it from Preview), when used with Helidon SE.
 * It can be used to register custom instances (see {@link io.helidon.service.registry.Services#set(Class, Object[])}), or
 * to get services that are "global" (see {@link io.helidon.service.registry.Services#get(Class)}).
 * <p>
 * The two main modules that are required to utilize the full power of service registry are the Service Codegen
 * ({@code io.helidon.service:helidon-service-codegen}, to set it up as an annotation processor, also needs
 * {@code io.helidon.codegen:helidon-codegen-apt}) - this module is required to declare new services,
 * and the Service Maven Plugin ({@code io.helidon.service:helidon-service-maven-plugin}) - this module is only needed
 * to bypass discovery and runtime service binding for Helidon Declarative applications.
 *
 * @see io.helidon.service.registry.Services
 * @see io.helidon.service.registry.Service.Singleton
 */
@Features.Name("Registry")
@Features.Description("Service Registry")
@Features.Flavor(HelidonFlavor.SE)
@Features.Path("Registry")
module io.helidon.service.registry {
    requires static io.helidon.common.features.api;

    requires io.helidon.common.context;
    requires io.helidon.service.metadata;
    requires io.helidon.metadata.hson;
    requires io.helidon;

    requires transitive io.helidon.builder.api;
    requires transitive io.helidon.common.types;
    requires java.naming;
    requires io.helidon.metadata;

    exports io.helidon.service.registry;

    provides io.helidon.spi.HelidonStartupProvider
            with io.helidon.service.registry.RegistryStartupProvider;
}
