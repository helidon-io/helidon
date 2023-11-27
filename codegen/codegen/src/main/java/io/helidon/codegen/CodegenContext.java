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

package io.helidon.codegen;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import io.helidon.codegen.spi.AnnotationMapper;
import io.helidon.codegen.spi.ElementMapper;
import io.helidon.codegen.spi.TypeMapper;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

/**
 * Code processing and generation context.
 */
public interface CodegenContext {

    /**
     * Module that is being processed.
     *
     * @return module info if defined, for modules without {@code module-info.java} returns empty optional
     */
    Optional<ModuleInfo> module();

    /**
     * Configured module name using {@link io.helidon.codegen.CodegenOptions#CODEGEN_MODULE}, or name of the
     * module if defined from {@link #module()}, or empty if not identified.
     *
     * @return name of the module
     */
    default Optional<String> moduleName() {
        return CodegenOptions.CODEGEN_MODULE.findValue(options())
                .or(() -> module().map(ModuleInfo::name));
    }

    /**
     * Filer to generate sources and resources.
     *
     * @return a filer abstraction
     */
    CodegenFiler filer();

    /**
     * Logger to log messages according to the environment we run in (Annotation processor, Maven plugin, command line).
     *
     * @return a logger abstraction
     */
    CodegenLogger logger();

    /**
     * Current code generation scope. Usually guessed from the environment, can be overridden using {@link CodegenOptions#CODEGEN_SCOPE}
     *
     * @return scope
     */
    CodegenScope scope();

    /**
     * Code generation options.
     *
     * @return options of the current environment
     */
    CodegenOptions options();

    /**
     * Discover information about the provided type.
     *
     * @param typeName type name to discover
     * @return discovered type information, or empty if the type cannot be discovered
     */
    Optional<TypeInfo> typeInfo(TypeName typeName);

    /**
     * Discover information about the provided type, with a predicate for child elements.
     *
     * @param typeName         type name to discover
     * @param elementPredicate predicate for child elements
     * @return discovered type information, or empty if the type cannot be discovered
     */
    Optional<TypeInfo> typeInfo(TypeName typeName, Predicate<TypedElementInfo> elementPredicate);

    /**
     * List of available element mappers in this environment.
     * Used for example when discovering {@link #typeInfo(io.helidon.common.types.TypeName)}.
     *
     * @return list of mapper
     */
    List<ElementMapper> elementMappers();

    /**
     * List of available type mappers in this environment.
     * Used for example when discovering {@link #typeInfo(io.helidon.common.types.TypeName)}.
     *
     * @return list of mapper
     */
    List<TypeMapper> typeMappers();

    /**
     * List of available annotation mappers in this environment.
     * Used for example when discovering {@link #typeInfo(io.helidon.common.types.TypeName)}.
     *
     * @return list of mapper
     */
    List<AnnotationMapper> annotationMappers();

    /**
     * Annotations supported by the mappers. This is augmented by the annotations supported by all extensions and used
     * to discover types.
     *
     * @return set of annotation types supported by the mapper
     */
    Set<TypeName> mapperSupportedAnnotations();

    /**
     * Annotation packages supported by the mappers.
     * This is augmented by the annotation packages supported by all extensions and used
     * to discover types.
     *
     * @return set of annotation packages
     */
    Set<String> mapperSupportedAnnotationPackages();

    /**
     * Codegen options supported by the mappers.
     * This is augmented by the options supported by all extensions.
     *
     * @return set of supported options
     */
    Set<Option<?>> supportedOptions();
}
