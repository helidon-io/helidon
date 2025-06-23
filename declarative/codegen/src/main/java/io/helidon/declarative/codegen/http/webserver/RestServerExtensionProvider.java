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

package io.helidon.declarative.codegen.http.webserver;

import java.util.Set;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.types.TypeName;
import io.helidon.service.codegen.RegistryCodegenContext;
import io.helidon.service.codegen.spi.RegistryCodegenExtension;
import io.helidon.service.codegen.spi.RegistryCodegenExtensionProvider;

/**
 * Java {@link java.util.ServiceLoader} provider implementation of {@link io.helidon.codegen.spi.CodegenExtensionProvider}
 * to support code generation for WebServer declarative.
 */
@Weight(Weighted.DEFAULT_WEIGHT + 10)
public class RestServerExtensionProvider implements RegistryCodegenExtensionProvider {
    /**
     * Required by {@link java.util.ServiceLoader}.
     *
     * @deprecated required by service loader
     */
    @Deprecated
    public RestServerExtensionProvider() {
        super();
    }

    @Override
    public Set<TypeName> supportedAnnotations() {
        return Set.of(WebServerCodegenTypes.REST_SERVER_ENDPOINT);
    }

    @Override
    public RegistryCodegenExtension create(RegistryCodegenContext ctx) {
        return new RestServerExtension(ctx);
    }
}
