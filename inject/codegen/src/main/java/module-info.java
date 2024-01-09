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

/**
 * Code generation for Helidon Inject.
 */
module io.helidon.inject.codegen {
    requires transitive io.helidon.builder.api;
    requires transitive io.helidon.codegen.classmodel;
    requires transitive io.helidon.codegen;

    exports io.helidon.inject.codegen;
    exports io.helidon.inject.codegen.spi;

    uses io.helidon.inject.codegen.spi.InjectCodegenObserverProvider;
    uses io.helidon.inject.codegen.spi.InjectCodegenExtensionProvider;
    uses io.helidon.inject.codegen.spi.InjectAssignmentProvider;

    provides io.helidon.codegen.spi.CodegenExtensionProvider
            with io.helidon.inject.codegen.InjectCodegenProvider;

    provides io.helidon.inject.codegen.spi.InjectCodegenExtensionProvider
            with io.helidon.inject.codegen.InjectionExtensionProvider;

    provides io.helidon.codegen.spi.AnnotationMapperProvider
            with io.helidon.inject.codegen.MapClassNamedProvider,
                    io.helidon.inject.codegen.MapDrivenBy;
}