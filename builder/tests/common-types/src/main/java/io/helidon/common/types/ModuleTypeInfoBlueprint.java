/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.common.types;

import java.util.List;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Module info type information.
 */
@Prototype.Blueprint
interface ModuleTypeInfoBlueprint extends Annotated {
    /**
     * Module name.
     *
     * @return name of this module
     */
    String name();

    /**
     * Description, such as javadoc, if available.
     *
     * @return description of this element
     */
    @Option.Redundant
    Optional<String> description();

    /**
     * Whether this is an open module.
     *
     * @return if open
     */
    boolean isOpen();

    /**
     * List of requires directives.
     *
     * @return requires
     */
    @Option.Singular
    List<ModuleInfoRequires> requires();

    /**
     * List of exports directives.
     *
     * @return exports
     */
    @Option.Singular
    List<ModuleInfoExports> exports();

    /**
     * List of opens directives.
     *
     * @return opens
     */
    @Option.Singular
    List<ModuleInfoOpens> opens();

    /**
     * List of uses directives.
     *
     * @return uses
     */
    @Option.Singular
    List<ModuleInfoUses> uses();

    /**
     * List of provides directives.
     *
     * @return provides
     */
    @Option.Singular
    List<ModuleInfoProvides> provides();

    /**
     * The element used to create this instance.
     * The type of the object depends on the environment we are in - it may be an {@code TypeElement} in annotation processing,
     * or a {@code ClassInfo} when using classpath scanning.
     *
     * @return originating element
     */
    @Option.Redundant
    Optional<Object> originatingElement();

    /**
     * The element used to create this instance, or {@link io.helidon.common.types.TypeInfo#typeName()} if none provided.
     * The type of the object depends on the environment we are in - it may be an {@code TypeElement} in annotation processing,
     * or a {@code ClassInfo} when using classpath scanning.
     *
     * @return originating element, or the type of this type info
     */
    default Object originatingElementValue() {
        return originatingElement().orElseGet(this::name);
    }
}
