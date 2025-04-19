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

import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.common.types.TypeName;
import io.helidon.declarative.codegen.http.webserver.spi.HttpParameterCodegenProvider;

class ParamProviderHttpReqRes implements HttpParameterCodegenProvider {
    @Override
    public boolean codegen(ParameterCodegenContext ctx) {
        TypeName parameterType = ctx.parameterType();
        ContentBuilder<?> contentBuilder = ctx.contentBuilder();

        if (WebServerCodegenTypes.SERVER_REQUEST.equals(parameterType)) {
            contentBuilder.addContent(ctx.serverRequestParamName())
                    .addContent(";");
            return true;
        }
        if (WebServerCodegenTypes.SERVER_RESPONSE.equals(parameterType)) {
            contentBuilder.addContent(ctx.serverResponseParamName())
                    .addContent(";");
            return true;
        }
        return false;
    }
}
