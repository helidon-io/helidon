/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.json.codegen;

import java.util.Set;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.spi.CodegenExtension;
import io.helidon.codegen.spi.CodegenExtensionProvider;
import io.helidon.common.types.TypeName;

/**
 * Code generation provider for JSON processing.
 * <p>
 * This provider creates code generation extensions that handle JSON serialization
 * and deserialization for types annotated with {@code @Json.Entity}.
 * </p>
 */
public class JsonCodegenProvider implements CodegenExtensionProvider {

    /**
     * Public constructor is required for {@link java.util.ServiceLoader}.
     *
     * @deprecated please do not use directly
     */
    @Deprecated
    public JsonCodegenProvider() {
    }

    @Override
    public CodegenExtension create(CodegenContext ctx, TypeName generatorType) {
        return new JsonCodegen(ctx);
    }

    @Override
    public Set<TypeName> supportedAnnotations() {
        return Set.of(JsonTypes.JSON_ENTITY);
    }
}
