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

package io.helidon.codegen.spi;

import io.helidon.codegen.CodegenContext;
import io.helidon.common.types.TypeName;

/**
 * Java {@link java.util.ServiceLoader} provider interface for extensions used to process and code generate.
 * Each implementation will be called with types that match its declared {@link #supportedAnnotations()} and
 * {@link #supportedAnnotationPackages()}.
 */
public interface CodegenExtensionProvider extends CodegenProvider {
    /**
     * Create a new instance of the extension provider.
     *
     * @param ctx           codegen context for the current environment
     * @param generatorType type of the generator (annotation processor, maven plugin etc.), for reporting purposes
     * @return a new codegen extension
     */
    CodegenExtension create(CodegenContext ctx, TypeName generatorType);
}
