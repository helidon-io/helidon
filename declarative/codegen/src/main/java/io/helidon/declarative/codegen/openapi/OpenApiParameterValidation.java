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

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiFunction;

import io.helidon.codegen.CodegenException;
import io.helidon.declarative.codegen.model.http.RestMethodParameter;

final class OpenApiParameterValidation {
    private OpenApiParameterValidation() {
    }

    static void validateGeneratedParameters(String restMethodDescription,
                                            List<RestMethodParameter> pathParameters,
                                            List<RestMethodParameter> queryParameters,
                                            List<RestMethodParameter> headerParameters,
                                            List<RestMethodParameter> cookieParameters,
                                            BiFunction<RestMethodParameter, String, String> parameterName) {
        Set<String> keys = new HashSet<>();
        validateParameters(restMethodDescription, keys, pathParameters, "path", parameterName);
        validateParameters(restMethodDescription, keys, queryParameters, "query", parameterName);
        validateParameters(restMethodDescription, keys, headerParameters, "header", parameterName);
        validateParameters(restMethodDescription, keys, cookieParameters, "cookie", parameterName);
    }

    private static void validateParameters(String restMethodDescription,
                                           Set<String> keys,
                                           List<RestMethodParameter> parameters,
                                           String in,
                                           BiFunction<RestMethodParameter, String, String> parameterName) {
        for (RestMethodParameter parameter : parameters) {
            String name = parameterName.apply(parameter, in);
            if (!keys.add(parameterKey(in, name))) {
                throw new CodegenException("Generated OpenAPI parameters on " + restMethodDescription
                                                   + " cannot define " + in + " parameter " + name
                                                   + " more than once");
            }
        }
    }

    private static String parameterKey(String in, String name) {
        if ("header".equals(in)) {
            return in + "\n" + name.toLowerCase(Locale.ROOT);
        }
        return in + "\n" + name;
    }
}
