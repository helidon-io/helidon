/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

import io.helidon.common.types.TypeName;
import io.helidon.declarative.codegen.http.webserver.spi.HttpParameterCodegenProvider;

class ParamProviderContext implements HttpParameterCodegenProvider {
    private static final TypeName CONTEXT = TypeName.create("io.helidon.common.context.Context");

    @Override
    public boolean codegen(ParameterCodegenContext ctx) {
        if (!ctx.annotations().isEmpty()) {
            // any qualified instance is ignored
            return false;
        }

        if (CONTEXT.equals(ctx.parameterType())) {
            codegenContext(ctx);
            return true;
        }
        return false;
    }

    private void codegenContext(ParameterCodegenContext ctx) {
        ctx.contentBuilder()
                .addContent(ctx.serverRequestParamName())
                .addContent(".context();");
    }
}
