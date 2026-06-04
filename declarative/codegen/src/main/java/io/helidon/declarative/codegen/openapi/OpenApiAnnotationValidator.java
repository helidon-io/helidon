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
import java.util.Optional;
import java.util.Set;

import io.helidon.codegen.CodegenException;
import io.helidon.common.types.Annotation;

import static java.util.function.Predicate.not;

final class OpenApiAnnotationValidator {
    private static final String DEFAULT_MEDIA_TYPE = "application/json";
    private static final Set<String> API_KEY_LOCATIONS = Set.of("query", "header", "cookie");

    void validateTags(String owner, List<Annotation> tags) {
        Set<String> names = new HashSet<>();
        for (Annotation tag : tags) {
            String name = stringValue(tag, "value")
                    .filter(not(String::isBlank))
                    .orElseThrow(() -> new CodegenException("@OpenApi.Tag value is required"));
            validateUnique("@OpenApi.Tag on " + owner, "tag", name, names);
        }
    }

    void validateServers(String owner, List<Annotation> servers) {
        Set<String> urls = new HashSet<>();
        for (Annotation server : servers) {
            String url = server.stringValue()
                    .filter(not(String::isBlank))
                    .orElseThrow(() -> new CodegenException("@OpenApi.Server value is required"));
            validateUnique("@OpenApi.Server on " + owner, "server", url, urls);
        }
    }

    void validateOperationTags(String owner, List<String> tags) {
        validateUniqueValues("@OpenApi.Operation on " + owner,
                             "tag",
                             tags.stream()
                                     .map(this::expressionDefaultValue)
                                     .toList());
    }

    void validateExtensions(String owner, List<Annotation> extensions) {
        Set<String> names = new HashSet<>();
        for (Annotation extension : extensions) {
            String name = stringValue(extension, "name")
                    .filter(not(String::isBlank))
                    .orElseThrow(() -> new CodegenException("@OpenApi.Extension name is required"));
            validateUnique("@OpenApi.Extension on " + owner, "extension", name, names);
        }
    }

    void validateSecuritySchemes(String owner, List<Annotation> schemes) {
        Set<String> names = new HashSet<>();
        for (Annotation scheme : schemes) {
            String name = stringValue(scheme, "name")
                    .filter(not(String::isBlank))
                    .orElseThrow(() -> new CodegenException("@OpenApi.SecurityScheme name is required"));
            validateUnique("@OpenApi.SecurityScheme on " + owner, "security scheme", name, names);
            validateSecurityScheme(owner, scheme, name);
        }
    }

    void validateSecurityRequirements(String owner, List<Annotation> requirements) {
        Set<String> signatures = new HashSet<>();
        for (Annotation requirement : requirements) {
            List<String> schemes = stringValues(requirement, "value");
            List<String> scopes = stringValues(requirement, "scopes");
            validateUniqueValues("@OpenApi.SecurityRequirement on " + owner, "scheme", schemes);
            validateUniqueValues("@OpenApi.SecurityRequirement on " + owner, "scope", scopes);
            if (!schemes.isEmpty()) {
                String signature = String.join("\n", schemes) + "\n\n" + String.join("\n", scopes);
                validateUnique("@OpenApi.SecurityRequirement on " + owner,
                               "security requirement",
                               schemes + (scopes.isEmpty() ? "" : " with scopes " + scopes),
                               signature,
                               signatures);
            }
        }
    }

    void validateResponses(String restMethodDescription, List<Annotation> responses) {
        Set<Integer> statuses = new HashSet<>();
        for (Annotation response : responses) {
            int status = response.intValue("status")
                    .orElseThrow(() -> new CodegenException("@OpenApi.Response status is required"));
            if (status < 100 || status > 599) {
                throw new CodegenException("@OpenApi.Response on " + restMethodDescription
                                                   + " must define an HTTP response status from 100 to 599: "
                                                   + status);
            }
            if (!statuses.add(status)) {
                throw new CodegenException("@OpenApi.Response on " + restMethodDescription
                                                   + " cannot define response status " + status + " more than once");
            }
        }
    }

    void validateOAuthScopes(String owner, String schemeName, String flowName, List<Annotation> scopes) {
        Set<String> names = new HashSet<>();
        for (Annotation scope : scopes) {
            String name = stringValue(scope, "value")
                    .filter(not(String::isBlank))
                    .orElseThrow(() -> new CodegenException("@OpenApi.OAuthScope value is required"));
            validateUnique("@OpenApi.OAuthFlow on " + owner + " for security scheme " + schemeName
                                   + " " + flowName + " flow",
                           "scope",
                           name,
                           names);
        }
    }

    void validateOAuthFlow(String owner, String schemeName, String flowName, Annotation flow) {
        switch (flowName) {
        case "implicit" -> requireString("@OpenApi.OAuthFlow on " + owner
                                                 + " for security scheme " + schemeName + " implicit flow",
                                         flow,
                                         "authorizationUrl");
        case "password", "clientCredentials" -> requireString("@OpenApi.OAuthFlow on " + owner
                                                                      + " for security scheme " + schemeName + " "
                                                                      + flowName + " flow",
                                                              flow,
                                                              "tokenUrl");
        case "authorizationCode" -> {
            String location = "@OpenApi.OAuthFlow on " + owner
                    + " for security scheme " + schemeName + " authorizationCode flow";
            requireString(location, flow, "authorizationUrl");
            requireString(location, flow, "tokenUrl");
        }
        case "deviceAuthorization" -> {
            String location = "@OpenApi.OAuthFlow on " + owner
                    + " for security scheme " + schemeName + " deviceAuthorization flow";
            requireString(location, flow, "deviceAuthorizationUrl");
            requireString(location, flow, "tokenUrl");
        }
        default -> throw new CodegenException("Unsupported OAuth flow " + flowName);
        }
    }

    void validateContentMediaTypes(String owner,
                                   List<Annotation> contentAnnotations,
                                   List<String> inferredMediaTypes) {
        Set<String> mediaTypes = new HashSet<>();
        for (Annotation content : contentAnnotations) {
            for (String mediaType : contentMediaTypes(content, inferredMediaTypes)) {
                validateUnique(owner, "content media type", mediaType, mediaTypes);
            }
            validateContentExamples(owner, content);
        }
    }

    void validateResponseHeaders(String restMethodDescription,
                                 List<Annotation> explicitHeaders,
                                 List<String> inferredHeaderNames) {
        Set<String> names = new HashSet<>();
        inferredHeaderNames.forEach(name -> names.add(name.toLowerCase(Locale.ROOT)));
        for (Annotation header : explicitHeaders) {
            String name = stringValue(header, "name")
                    .filter(not(String::isBlank))
                    .orElseThrow(() -> new CodegenException("@OpenApi.Header name is required"));
            if (isContentTypeHeader(name)) {
                throw new CodegenException("@OpenApi.Response on " + restMethodDescription
                                                   + " cannot define response header " + name
                                                   + "; use @OpenApi.Content to define response media types");
            }
            if (!names.add(name.toLowerCase(Locale.ROOT))) {
                throw new CodegenException("@OpenApi.Response on " + restMethodDescription
                                                   + " cannot define response header " + name
                                                   + " more than once, including inferred Helidon response headers");
            }
            validateResponseHeaderContent(restMethodDescription,
                                          name,
                                          header.annotationValues("content").orElseGet(List::of));
        }
    }

    void validateMethodParameters(String restMethodDescription, List<Annotation> methodParameters) {
        Set<String> parameterKeys = new HashSet<>();
        for (Annotation methodParameter : methodParameters) {
            Optional<String> name = stringValue(methodParameter, "name").filter(not(String::isBlank));
            Optional<String> in = stringValue(methodParameter, "in").filter(not(String::isBlank));
            if (name.isPresent() && in.isPresent()) {
                String location = in.get();
                String parameterName = name.get();
                validateUnique("Method-level @OpenApi.Parameter on " + restMethodDescription,
                               "parameter",
                               location + " " + parameterName,
                               parameterKey(location, parameterName),
                               parameterKeys);
            }
        }
    }

    void validateParameterAnnotations(String restMethodDescription,
                                      String in,
                                      String name,
                                      List<Annotation> parameterAnnotations) {
        if (parameterAnnotations.size() > 1) {
            throw new CodegenException("@OpenApi.Parameter on " + restMethodDescription
                                               + " cannot define metadata for " + in
                                               + " parameter " + name + " more than once");
        }
    }

    void validateParameterExamples(String restMethodDescription,
                                   String in,
                                   String name,
                                   Optional<String> example,
                                   List<Annotation> examples) {
        if (example.isPresent() && !examples.isEmpty()) {
            throw new CodegenException("@OpenApi.Parameter on " + restMethodDescription
                                               + " cannot define both example and examples for " + in
                                               + " parameter " + name);
        }
        validateExamples("@OpenApi.Parameter on " + restMethodDescription
                                 + " for " + in + " parameter " + name,
                         examples);
    }

    void validateParameterContent(String restMethodDescription,
                                  String in,
                                  String name,
                                  List<Annotation> contentAnnotations) {
        if (contentAnnotations.size() > 1) {
            throw new CodegenException("@OpenApi.Parameter on " + restMethodDescription
                                               + " cannot define more than one content entry for " + in
                                               + " parameter " + name);
        }
        contentAnnotations.forEach(content -> validateContentExamples("@OpenApi.Parameter on "
                                                                              + restMethodDescription
                                                                              + " for " + in + " parameter " + name,
                                                                      content));
    }

    List<String> contentMediaTypes(Annotation content, List<String> inferredMediaTypes) {
        return stringValue(content, "value")
                .filter(not(String::isBlank))
                .map(List::of)
                .orElseGet(() -> mediaTypes(inferredMediaTypes));
    }

    String exampleName(Annotation example, int index) {
        return stringValue(example, "name")
                .filter(not(String::isBlank))
                .orElse(index == 0 ? "example" : "example" + (index + 1));
    }

    private void validateResponseHeaderContent(String restMethodDescription,
                                               String name,
                                               List<Annotation> contentAnnotations) {
        if (contentAnnotations.size() > 1) {
            throw new CodegenException("@OpenApi.Header on " + restMethodDescription
                                               + " cannot define more than one content entry for response header "
                                               + name);
        }
        contentAnnotations.forEach(content -> validateContentExamples("@OpenApi.Header on "
                                                                              + restMethodDescription
                                                                              + " for response header " + name,
                                                                      content));
    }

    private void validateSecurityScheme(String owner, Annotation scheme, String name) {
        String location = "@OpenApi.SecurityScheme on " + owner + " for security scheme " + name;
        String type = requireString(location, scheme, "type");
        switch (type) {
        case "apiKey" -> {
            requireString(location, scheme, "apiKeyName");
            String in = requireString(location, scheme, "in");
            if (!API_KEY_LOCATIONS.contains(in)) {
                throw new CodegenException(location
                                                   + " apiKey in must be one of query, header, or cookie: " + in);
            }
        }
        case "http" -> requireString(location, scheme, "scheme");
        case "mutualTLS" -> {
        }
        case "oauth2" -> validateOAuth2SecurityScheme(owner, scheme, name, location);
        case "openIdConnect" -> requireString(location, scheme, "openIdConnectUrl");
        default -> throw new CodegenException(location + " type must be one of apiKey, http, mutualTLS, oauth2, "
                                                      + "or openIdConnect: " + type);
        }
    }

    private void validateOAuth2SecurityScheme(String owner, Annotation scheme, String name, String location) {
        Annotation flows = scheme.annotationValue("flows")
                .orElseThrow(() -> new CodegenException(location + " requires OAuth flows"));
        boolean hasFlow = false;
        for (String flowName : List.of("implicit", "password", "clientCredentials", "authorizationCode",
                                       "deviceAuthorization")) {
            Optional<Annotation> flow = flows.annotationValue(flowName)
                    .filter(this::hasOAuthFlowMetadata);
            if (flow.isPresent()) {
                hasFlow = true;
                validateOAuthFlow(owner, name, flowName, flow.get());
            }
        }
        if (!hasFlow) {
            throw new CodegenException(location + " requires at least one OAuth flow");
        }
    }

    private boolean hasOAuthFlowMetadata(Annotation flow) {
        return hasStringValue(flow, "authorizationUrl")
                || hasStringValue(flow, "deviceAuthorizationUrl")
                || hasStringValue(flow, "tokenUrl")
                || hasStringValue(flow, "refreshUrl")
                || !flow.annotationValues("scopes").orElseGet(List::of).isEmpty();
    }

    private String requireString(String location, Annotation annotation, String property) {
        return stringValue(annotation, property)
                .filter(not(String::isBlank))
                .orElseThrow(() -> new CodegenException(location + " requires " + property));
    }

    private boolean hasStringValue(Annotation annotation, String property) {
        return stringValue(annotation, property).filter(not(String::isBlank)).isPresent();
    }

    private Optional<String> stringValue(Annotation annotation, String property) {
        Optional<String> value = "value".equals(property)
                ? annotation.stringValue()
                : annotation.stringValue(property);
        return value.map(this::expressionDefaultValue);
    }

    private List<String> stringValues(Annotation annotation, String property) {
        Optional<List<String>> values = "value".equals(property)
                ? annotation.stringValues()
                : annotation.stringValues(property);
        return values.orElseGet(List::of)
                .stream()
                .map(this::expressionDefaultValue)
                .toList();
    }

    String expressionDefaultValue(String value) {
        if (!value.startsWith("${") || !value.endsWith("}") || value.indexOf("${", 2) >= 0) {
            return value;
        }
        int colon = value.indexOf(':', 2);
        if (colon < 0) {
            return value;
        }
        return value.substring(colon + 1, value.length() - 1);
    }

    private void validateContentExamples(String owner, Annotation content) {
        validateExamples(owner, content.annotationValues("examples").orElseGet(List::of));
    }

    private void validateExamples(String owner, List<Annotation> examples) {
        Set<String> names = new HashSet<>();
        for (int i = 0; i < examples.size(); i++) {
            String name = exampleName(examples.get(i), i);
            validateUnique(owner, "example", name, names);
        }
    }

    private void validateUniqueValues(String owner, String valueDescription, List<String> values) {
        Set<String> unique = new HashSet<>();
        values.forEach(value -> validateUnique(owner, valueDescription, value, unique));
    }

    private String parameterKey(String in, String name) {
        if ("header".equals(in)) {
            return in + "\n" + name.toLowerCase(Locale.ROOT);
        }
        return in + "\n" + name;
    }

    private boolean isContentTypeHeader(String name) {
        return "content-type".equals(name.toLowerCase(Locale.ROOT));
    }

    private void validateUnique(String owner, String valueDescription, String value, Set<String> values) {
        if (!values.add(value)) {
            throw new CodegenException(owner + " cannot define " + valueDescription + " " + value + " more than once");
        }
    }

    private void validateUnique(String owner,
                                String valueDescription,
                                String displayValue,
                                String uniqueValue,
                                Set<String> values) {
        if (!values.add(uniqueValue)) {
            throw new CodegenException(owner + " cannot define " + valueDescription + " "
                                               + displayValue + " more than once");
        }
    }

    private List<String> mediaTypes(List<String> mediaTypes) {
        return mediaTypes.isEmpty() ? List.of(DEFAULT_MEDIA_TYPE) : mediaTypes;
    }
}
