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

package io.helidon.openapi;

import java.util.Objects;

import io.helidon.common.Api;

/**
 * Support methods for generated OpenAPI document sources.
 */
@Api.Internal
public final class OpenApiDocumentContextSupport {
    private OpenApiDocumentContextSupport() {
    }

    /**
     * Resolve an OpenAPI operation id for a generated Java method signature.
     *
     * @param context            document context
     * @param signature          Java method signature
     * @param defaultOperationId default operation id
     * @return configured operation id if present, otherwise the default
     */
    public static String operationId(OpenApiDocumentContext context, String signature, String defaultOperationId) {
        Objects.requireNonNull(context);
        if (context instanceof OpenApiDocumentContextImpl contextImpl) {
            return contextImpl.operationId(signature, defaultOperationId);
        }
        Objects.requireNonNull(signature);
        return Objects.requireNonNull(defaultOperationId);
    }

    /**
     * Resolve a generated OpenAPI annotation expression using runtime configuration when enabled for the OpenAPI feature.
     * Otherwise returns the expression unchanged.
     *
     * @param context    document context
     * @param expression expression to resolve
     * @return resolved expression value, or the original expression when resolution is disabled
     */
    public static String resolveExpression(OpenApiDocumentContext context, String expression) {
        Objects.requireNonNull(context);
        if (context instanceof OpenApiDocumentContextImpl contextImpl) {
            return contextImpl.resolveExpression(expression);
        }
        return Objects.requireNonNull(expression);
    }
}
