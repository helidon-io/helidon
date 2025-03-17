/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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

import java.util.NoSuchElementException;

import io.helidon.common.types.TypeName;
import io.helidon.declarative.codegen.http.webserver.spi.HttpParameterCodegenProvider;

class ParamProviderSecurityContext implements HttpParameterCodegenProvider {
    private static final TypeName COMMON_SECURITY_CONTEXT = TypeName.create("io.helidon.common.security.SecurityContext");
    private static final TypeName SECURITY_CONTEXT = TypeName.create("io.helidon.security.SecurityContext");

    @Override
    public boolean codegen(ParameterCodegenContext ctx) {
        if (!ctx.annotations().isEmpty()) {
            // any qualified instance is ignored
            return false;
        }
        TypeName typeName = ctx.parameterType();
        if (COMMON_SECURITY_CONTEXT.equals(typeName) || SECURITY_CONTEXT.equals(typeName)) {
            codegenSecurityContext(ctx, typeName);
            return true;
        }
        return false;
    }

    private void codegenSecurityContext(ParameterCodegenContext ctx, TypeName typeName) {
        String param = ctx.paramName();

        ctx.contentBuilder()
                .addContentLine(ctx.serverRequestParamName())
                .increaseContentPadding()
                .increaseContentPadding()
                .addContentLine(".context()")
                .addContent(".get(")
                .addContent(typeName)
                .addContentLine(".class)")
                .addContent(".orElseThrow(() -> new ")
                .addContent(NoSuchElementException.class)
                .addContent("(\"")
                .addContent(typeName)
                .addContent(" is not present in request context, required for parameter ")
                .addContent(param)
                .addContentLine(". Maybe security is not configured?\"));")
                .decreaseContentPadding()
                .decreaseContentPadding();
    }
}
