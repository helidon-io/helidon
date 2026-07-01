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

package io.helidon.declarative.codegen.openapi;

import java.util.Set;

import io.helidon.common.Api;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.types.TypeName;
import io.helidon.service.codegen.RegistryCodegenContext;
import io.helidon.service.codegen.spi.RegistryCodegenExtension;
import io.helidon.service.codegen.spi.RegistryCodegenExtensionProvider;

/**
 * Java {@link java.util.ServiceLoader} provider implementation for declarative OpenAPI code generation.
 */
@Weight(Weighted.DEFAULT_WEIGHT + 20)
public class OpenApiExtensionProvider implements RegistryCodegenExtensionProvider {
    /**
     * Required public constructor for {@link java.util.ServiceLoader}.
     */
    @Api.Internal
    public OpenApiExtensionProvider() {
        super();
    }

    @Override
    public Set<TypeName> supportedAnnotations() {
        return Set.of(OpenApiCodegenTypes.OPENAPI_DOCUMENT_ANNOTATION,
                      OpenApiCodegenTypes.OPENAPI_API_KEY_SECURITY_SCHEMES_ANNOTATION,
                      OpenApiCodegenTypes.OPENAPI_API_KEY_SECURITY_SCHEME_ANNOTATION,
                      OpenApiCodegenTypes.OPENAPI_HTTP_SECURITY_SCHEMES_ANNOTATION,
                      OpenApiCodegenTypes.OPENAPI_HTTP_SECURITY_SCHEME_ANNOTATION,
                      OpenApiCodegenTypes.OPENAPI_MUTUAL_TLS_SECURITY_SCHEMES_ANNOTATION,
                      OpenApiCodegenTypes.OPENAPI_MUTUAL_TLS_SECURITY_SCHEME_ANNOTATION,
                      OpenApiCodegenTypes.OPENAPI_OAUTH2_SECURITY_SCHEMES_ANNOTATION,
                      OpenApiCodegenTypes.OPENAPI_OAUTH2_SECURITY_SCHEME_ANNOTATION,
                      OpenApiCodegenTypes.OPENAPI_OIDC_SECURITY_SCHEMES_ANNOTATION,
                      OpenApiCodegenTypes.OPENAPI_OIDC_SECURITY_SCHEME_ANNOTATION);
    }

    @Override
    public RegistryCodegenExtension create(RegistryCodegenContext ctx) {
        return new OpenApiExtension(ctx);
    }
}
