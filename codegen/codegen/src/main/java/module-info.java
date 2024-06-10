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
 * Utilities for code generation.
 */
module io.helidon.codegen {
    requires transitive io.helidon.common;
    requires transitive io.helidon.common.types;
    requires transitive io.helidon.codegen.classmodel;

    exports io.helidon.codegen;
    exports io.helidon.codegen.spi;

    uses io.helidon.codegen.spi.CopyrightProvider;
    uses io.helidon.codegen.spi.GeneratedAnnotationProvider;
    uses io.helidon.codegen.spi.AnnotationMapperProvider;
    uses io.helidon.codegen.spi.TypeMapperProvider;
    uses io.helidon.codegen.spi.ElementMapperProvider;
    uses io.helidon.codegen.spi.CodegenExtensionProvider;
}