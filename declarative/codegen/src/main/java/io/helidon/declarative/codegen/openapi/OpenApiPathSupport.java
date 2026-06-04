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

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import io.helidon.codegen.CodegenException;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.declarative.codegen.model.http.RestMethod;
import io.helidon.declarative.codegen.model.http.RestMethodParameter;
import io.helidon.declarative.codegen.model.http.ServerEndpoint;

import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_PATH_PARAM_ANNOTATION;
import static java.util.function.Predicate.not;

final class OpenApiPathSupport {
    private OpenApiPathSupport() {
    }

    static String openApiPath(ServerEndpoint endpoint, RestMethod method, Optional<Annotation> operation) {
        String endpointPath = endpoint.path().orElse("");
        String methodPath = method.path().orElse("");
        String joined = joinPath(endpointPath, methodPath);
        String path = joined.isBlank() ? "/" : joined;
        PathTemplate pathTemplate = operation.flatMap(it -> it.stringValue("path"))
                .filter(not(String::isBlank))
                .map(override -> validateOpenApiPathOverride(method, override))
                .orElseGet(() -> normalizeOpenApiPath(method, path));
        validatePathParameters(method, pathTemplate);
        return pathTemplate.path();
    }

    private static PathTemplate validateOpenApiPathOverride(RestMethod method, String path) {
        if (!path.startsWith("/")) {
            throw invalidOpenApiPathOverride(method, path, "paths must start with '/'");
        }
        return validateOpenApiPathTemplate(method, path, PathValidationMode.OPENAPI_OVERRIDE);
    }

    private static PathTemplate normalizeOpenApiPath(RestMethod method, String path) {
        return validateOpenApiPathTemplate(method, ensureLeadingSlash(path), PathValidationMode.HTTP_PATH);
    }

    private static PathTemplate validateOpenApiPathTemplate(RestMethod method, String path, PathValidationMode mode) {
        StringBuilder result = new StringBuilder(path.length());
        Set<String> pathParameters = new LinkedHashSet<>();
        for (int i = 0; i < path.length(); i++) {
            char ch = path.charAt(i);
            switch (ch) {
            case '{' -> {
                PathParameter parameter = pathParameter(method, path, i, mode);
                pathParameters.add(parameter.name());
                result.append('{')
                        .append(parameter.name())
                        .append('}');
                i = parameter.endIndex();
            }
            case '}' -> throw openApiPathException(method, path, "unmatched path parameter end", mode);
            case '[', ']' -> throw openApiPathException(method, path, "optional path segments are not supported", mode);
            case '*' -> throw openApiPathException(method, path, "wildcard path segments are not supported", mode);
            case '\\' -> throw openApiPathException(method, path, "escaped path characters are not supported", mode);
            default -> result.append(ch);
            }
        }
        return new PathTemplate(result.toString(), pathParameters);
    }

    private static void validatePathParameters(RestMethod method, PathTemplate pathTemplate) {
        Set<String> routeParameters = pathParameterNames(method);
        if (routeParameters.equals(pathTemplate.pathParameters())) {
            return;
        }

        throw invalidOpenApiPathOverride(method,
                                         pathTemplate.path(),
                                         "must declare the same path parameters as the generated route; "
                                                 + "generated route parameters: " + routeParameters
                                                 + ", OpenAPI path parameters: " + pathTemplate.pathParameters());
    }

    private static Set<String> pathParameterNames(RestMethod method) {
        Set<String> result = new LinkedHashSet<>();
        for (RestMethodParameter parameter : method.pathParameters()) {
            result.add(pathParameterName(parameter));
        }
        return result;
    }

    private static String pathParameterName(RestMethodParameter parameter) {
        return Annotations.findFirst(HTTP_PATH_PARAM_ANNOTATION, parameter.annotations())
                .flatMap(Annotation::stringValue)
                .filter(not(String::isBlank))
                .orElse(parameter.name());
    }

    private static PathParameter pathParameter(RestMethod method, String path, int startIndex, PathValidationMode mode) {
        StringBuilder template = new StringBuilder();
        boolean regexp = false;
        int regexpNestedBraces = 0;
        for (int i = startIndex + 1; i < path.length(); i++) {
            char ch = path.charAt(i);
            if (regexp) {
                switch (ch) {
                case '{' -> regexpNestedBraces++;
                case '}' -> {
                    if (regexpNestedBraces == 0) {
                        return pathParameter(method, path, template.toString(), i, mode);
                    }
                    regexpNestedBraces--;
                }
                default -> {
                }
                }
            } else {
                switch (ch) {
                case ':' -> regexp = true;
                case '}' -> {
                    return pathParameter(method, path, template.toString(), i, mode);
                }
                case '{' -> throw openApiPathException(method, path, "nested path parameters are not supported", mode);
                default -> {
                }
                }
            }
            template.append(ch);
        }
        throw openApiPathException(method, path, "path parameter is missing a closing '}'", mode);
    }

    private static PathParameter pathParameter(RestMethod method,
                                               String path,
                                               String template,
                                               int endIndex,
                                               PathValidationMode mode) {
        String trimmed = template.trim();
        if (trimmed.isBlank()) {
            throw openApiPathException(method, path, "path parameter name is required", mode);
        }
        if (trimmed.charAt(0) == '+') {
            throw openApiPathException(method, path, "greedy path parameters are not supported", mode);
        }
        if ("*".equals(trimmed)) {
            throw openApiPathException(method, path, "wildcard path parameters are not supported", mode);
        }

        int regexStart = trimmed.indexOf(':');
        String name = trimmed;
        if (regexStart >= 0) {
            if (mode == PathValidationMode.OPENAPI_OVERRIDE) {
                throw invalidOpenApiPathOverride(method, path, "path parameters cannot define regex constraints");
            }
            name = trimmed.substring(0, regexStart).trim();
            if (name.isBlank()) {
                throw openApiPathException(method, path, "unnamed regex path parameters are not supported", mode);
            }
        }

        validatePathParameterName(method, path, name, mode);
        return new PathParameter(name, endIndex);
    }

    private static void validatePathParameterName(RestMethod method, String path, String name, PathValidationMode mode) {
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            switch (ch) {
            case '/', '{', '}', '[', ']', ':', '*', '\\' ->
                    throw openApiPathException(method, path, "path parameter name '" + name + "' is not supported", mode);
            default -> {
            }
            }
        }
    }

    private static CodegenException openApiPathException(RestMethod method,
                                                         String path,
                                                         String reason,
                                                         PathValidationMode mode) {
        return switch (mode) {
        case HTTP_PATH -> unsupportedOpenApiPath(method, path, reason);
        case OPENAPI_OVERRIDE -> invalidOpenApiPathOverride(method, path, reason);
        };
    }

    private static CodegenException unsupportedOpenApiPath(RestMethod method, String path, String reason) {
        return new CodegenException("@Http.Path on " + restMethodDescription(method)
                                            + " cannot be represented as an OpenAPI path: " + path
                                            + " (" + reason + "). Use @OpenApi.Operation(path = ...) to provide "
                                            + "the OpenAPI path.");
    }

    private static CodegenException invalidOpenApiPathOverride(RestMethod method, String path, String reason) {
        return new CodegenException("@OpenApi.Operation path on " + restMethodDescription(method)
                                            + " must be an OpenAPI path template: " + path
                                            + " (" + reason + ")");
    }

    private static String joinPath(String first, String second) {
        if (first.isBlank() || "/".equals(first)) {
            return second.isBlank() ? "/" : ensureLeadingSlash(second);
        }
        if (second.isBlank() || "/".equals(second)) {
            return ensureLeadingSlash(first);
        }
        return ensureLeadingSlash(first).replaceAll("/+$", "") + "/" + second.replaceAll("^/+", "");
    }

    private static String ensureLeadingSlash(String path) {
        return path.startsWith("/") ? path : "/" + path;
    }

    private static String restMethodDescription(RestMethod restMethod) {
        return restMethod.type().typeName().fqName() + "." + restMethod.method().signature().text();
    }

    private record PathParameter(String name, int endIndex) {
    }

    private record PathTemplate(String path, Set<String> pathParameters) {
    }

    private enum PathValidationMode {
        HTTP_PATH,
        OPENAPI_OVERRIDE
    }
}
