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

/**
 * Code generation extension to support Jakarta inject (JSR-330) in {@code javax} packages.
 */
module io.helidon.inject.codegen.javax {
    requires io.helidon.inject.codegen;

    exports io.helidon.inject.codegen.javax;

    provides io.helidon.inject.codegen.spi.InjectAssignmentProvider
            with io.helidon.inject.codegen.javax.JavaxAssignmentProvider;

    provides io.helidon.inject.codegen.spi.InjectCodegenExtensionProvider
            with io.helidon.inject.codegen.javax.JavaxExtensionProvider,
                    io.helidon.inject.codegen.javax.UnsupportedTypesExtensionProvider;

    provides io.helidon.codegen.spi.AnnotationMapperProvider
            with io.helidon.inject.codegen.javax.MapJavaxProvider,
                    io.helidon.inject.codegen.javax.MapApplicationScopedProvider;
}
