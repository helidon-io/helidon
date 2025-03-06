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

package io.helidon.declarative.codegen;

import java.util.Set;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.spi.CodegenExtension;
import io.helidon.codegen.spi.CodegenExtensionProvider;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.types.TypeName;

import static io.helidon.declarative.codegen.WebServerCodegenTypes.HTTP_PATH_ANNOTATION;

/**
 * Java {@link java.util.ServiceLoader} provider implementation of {@link io.helidon.codegen.spi.CodegenExtensionProvider}
 * to support code generation for WebServer declarative.
 */
@Weight(Weighted.DEFAULT_WEIGHT + 10)
public class WebServerCodegenProvider implements CodegenExtensionProvider {
    /**
     * Required by {@link java.util.ServiceLoader}.
     *
     * @deprecated required by service loader
     */
    @Deprecated
    public WebServerCodegenProvider() {
        super();
    }

    @Override
    public Set<TypeName> supportedAnnotations() {
        return Set.of(HTTP_PATH_ANNOTATION);
    }

    @Override
    public CodegenExtension create(CodegenContext ctx, TypeName generatorType) {
        return new WebServerCodegenExtension(ctx);
    }
}
