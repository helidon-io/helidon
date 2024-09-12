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

import java.util.Optional;
import java.util.Set;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.service.registry.Dependency;

/**
 * Unique identification, and metadata of an injection point.
 */
@Prototype.Blueprint
@Prototype.CustomMethods(IpSupport.CustomMethods.class)
interface IpBlueprint extends Dependency {
    /**
     * Kind of element we inject into (constructor, field, method).
     *
     * @return element kind (for parameters, the containing element)
     */
    @Option.Default("CONSTRUCTOR")
    ElementKind elementKind();

    /**
     * The qualifier type annotations on this element.
     *
     * @return the qualifier type annotations on this element
     */
    @Option.Singular
    @Option.Redundant(stringValue = false)
    // kind + service type + name is a unique identification already
    Set<Qualifier> qualifiers();

    /**
     * The access modifier on the injection point/receiver.
     * Defaults to {@link io.helidon.common.types.AccessModifier#PACKAGE_PRIVATE}.
     *
     * @return the access
     */
    @Option.Default("PACKAGE_PRIVATE")
    @Option.Redundant
    // kind + service type + name is a unique identification already
    AccessModifier access();

    /**
     * The annotations on this element.
     *
     * @return the annotations on this element
     */
    @Option.Singular
    @Option.Redundant
    // kind + service type + name is a unique identification already
    Set<Annotation> annotations();

    /**
     * Top level method that declares this method.
     * This is to provide information about overridden methods, as we should only inject such methods once.
     *
     * @return unique identification of a declaring method
     */
    @Option.Redundant(stringValue = false)
    Optional<String> method();
}
