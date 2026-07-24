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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.TypeName;

import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.JSON_OBJECT;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_API_KEY_SECURITY_SCHEMES_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_API_KEY_SECURITY_SCHEME_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_HTTP_SECURITY_SCHEMES_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_HTTP_SECURITY_SCHEME_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_MUTUAL_TLS_SECURITY_SCHEMES_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_MUTUAL_TLS_SECURITY_SCHEME_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_OAUTH2_SECURITY_SCHEMES_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_OAUTH2_SECURITY_SCHEME_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_OIDC_SECURITY_SCHEMES_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_OIDC_SECURITY_SCHEME_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_SECURITY_SCHEMES_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_SECURITY_SCHEME_ANNOTATION;
import static java.util.function.Predicate.not;

final class OpenApiSecuritySchemeCodegen {
    private final OpenApiAnnotationValidator validator;
    private final OpenApiSourceExpressions expressions;

    OpenApiSecuritySchemeCodegen(OpenApiAnnotationValidator validator, OpenApiSourceExpressions expressions) {
        this.validator = validator;
        this.expressions = expressions;
    }

    List<OpenApiSecurityScheme> securitySchemes(Set<Annotation> annotations) {
        List<OpenApiSecurityScheme> result = new ArrayList<>();
        repeatableAnnotations(annotations, OPENAPI_SECURITY_SCHEMES_ANNOTATION, OPENAPI_SECURITY_SCHEME_ANNOTATION)
                .forEach(annotation -> result.add(new OpenApiSecurityScheme("@OpenApi.SecurityScheme",
                                                                            annotation,
                                                                            annotation.stringValue("type")
                                                                                    .orElse(""))));
        addSecuritySchemes(result,
                           annotations,
                           OPENAPI_API_KEY_SECURITY_SCHEMES_ANNOTATION,
                           OPENAPI_API_KEY_SECURITY_SCHEME_ANNOTATION,
                           "@OpenApi.ApiKeySecurityScheme",
                           "apiKey");
        addSecuritySchemes(result,
                           annotations,
                           OPENAPI_HTTP_SECURITY_SCHEMES_ANNOTATION,
                           OPENAPI_HTTP_SECURITY_SCHEME_ANNOTATION,
                           "@OpenApi.HttpSecurityScheme",
                           "http");
        addSecuritySchemes(result,
                           annotations,
                           OPENAPI_MUTUAL_TLS_SECURITY_SCHEMES_ANNOTATION,
                           OPENAPI_MUTUAL_TLS_SECURITY_SCHEME_ANNOTATION,
                           "@OpenApi.MutualTlsSecurityScheme",
                           "mutualTLS");
        addSecuritySchemes(result,
                           annotations,
                           OPENAPI_OAUTH2_SECURITY_SCHEMES_ANNOTATION,
                           OPENAPI_OAUTH2_SECURITY_SCHEME_ANNOTATION,
                           "@OpenApi.OAuth2SecurityScheme",
                           "oauth2");
        addSecuritySchemes(result,
                           annotations,
                           OPENAPI_OIDC_SECURITY_SCHEMES_ANNOTATION,
                           OPENAPI_OIDC_SECURITY_SCHEME_ANNOTATION,
                           "@OpenApi.OidcSecurityScheme",
                           "openIdConnect");
        return result;
    }

    void writeSecurityScheme(Method.Builder method, String owner, OpenApiSecurityScheme scheme) {
        String name = scheme.stringValue("name")
                .filter(not(String::isBlank))
                .orElseThrow(() -> new CodegenException(scheme.annotationName() + " name is required"));
        String schemeName = validator.expressionDefaultValue(name);
        String type = scheme.stringValue("type")
                .filter(not(String::isBlank))
                .orElseThrow(() -> new CodegenException(scheme.annotationName() + " type is required"));
        String resolvedType = validator.expressionDefaultValue(type);
        Optional<String> securityScheme = scheme.stringValue("scheme")
                .filter(not(String::isBlank));
        Optional<String> bearerFormat = scheme.stringValue("bearerFormat")
                .filter(not(String::isBlank));
        if ("http".equals(resolvedType)
                && securityScheme.filter(value -> value.startsWith("${")).isPresent()
                && bearerFormat.isPresent()) {
            method.addContent("document.components(components -> components.securityScheme(")
                    .addContent(expressions.validatedStringExpression(name))
                    .addContentLine(",")
                    .increaseContentPadding()
                    .increaseContentPadding()
                    .addContentLine("security -> {")
                    .increaseContentPadding()
                    .increaseContentPadding()
                    .addContent("String resolvedScheme = ")
                    .addContent(expressions.stringExpression(securityScheme.get()))
                    .addContentLine(";")
                    .addContent("security.type(")
                    .addContent(expressions.validatedStringExpression(type))
                    .addContentLine(");");
            scheme.stringValue("description")
                    .filter(not(String::isBlank))
                    .ifPresent(description -> method.addContent("security.description(")
                            .addContent(expressions.stringExpression(description))
                            .addContentLine(");"));
            method.addContentLine("security.scheme(resolvedScheme);")
                    .addContentLine("if (\"bearer\".equalsIgnoreCase(resolvedScheme)) {")
                    .increaseContentPadding()
                    .increaseContentPadding()
                    .addContent("security.bearerFormat(")
                    .addContent(expressions.stringExpression(bearerFormat.get()))
                    .addContentLine(");")
                    .decreaseContentPadding()
                    .decreaseContentPadding()
                    .addContentLine("}");
            scheme.booleanValue("deprecated")
                    .filter(Boolean::booleanValue)
                    .ifPresent(_ -> method.addContentLine("security.deprecated(true);"));
            method.decreaseContentPadding()
                    .decreaseContentPadding()
                    .addContentLine("}));")
                    .decreaseContentPadding()
                    .decreaseContentPadding();
            return;
        }
        method.addContent("document.components(components -> components.securityScheme(")
                .addContent(expressions.validatedStringExpression(name))
                .addContentLine(",")
                .increaseContentPadding()
                .increaseContentPadding()
                .addContent("security -> security.type(")
                .addContent(expressions.validatedStringExpression(type))
                .addContentLine(")")
                .increaseContentPadding()
                .increaseContentPadding();
        scheme.stringValue("description")
                .filter(not(String::isBlank))
                .ifPresent(description -> method.addContent(".description(")
                        .addContent(expressions.stringExpression(description))
                        .addContentLine(")"));
        scheme.stringValue("apiKeyName")
                .filter(not(String::isBlank))
                .ifPresent(apiKeyName -> method.addContent(".name(")
                        .addContent(expressions.stringExpression(apiKeyName))
                        .addContentLine(")"));
        securityScheme.ifPresent(value -> method.addContent(".scheme(")
                        .addContent(expressions.stringExpression(value))
                        .addContentLine(")"));
        bearerFormat.ifPresent(format -> method.addContent(".bearerFormat(")
                        .addContent(expressions.stringExpression(format))
                        .addContentLine(")"));
        scheme.stringValue("in")
                .filter(not(String::isBlank))
                .ifPresent(in -> method.addContent(".in(")
                        .addContent(expressions.validatedStringExpression(in))
                        .addContentLine(")"));
        scheme.annotationValue("flows")
                .flatMap(flows -> oauthFlowsExpression(owner, schemeName, flows))
                .ifPresent(flows -> method.addContent(".flows(")
                        .addContent(flows)
                        .addContentLine(")"));
        scheme.stringValue("openIdConnectUrl")
                .filter(not(String::isBlank))
                .ifPresent(url -> method.addContent(".openIdConnectUrl(")
                        .addContent(expressions.stringExpression(url))
                        .addContentLine(")"));
        scheme.stringValue("oauth2MetadataUrl")
                .filter(not(String::isBlank))
                .ifPresent(url -> method.addContent(".oauth2MetadataUrl(")
                        .addContent(expressions.stringExpression(url))
                        .addContentLine(")"));
        scheme.booleanValue("deprecated")
                .filter(Boolean::booleanValue)
                .ifPresent(_ -> method.addContentLine(".deprecated(true)"));
        method.addContentLine("));")
                .decreaseContentPadding()
                .decreaseContentPadding()
                .decreaseContentPadding()
                .decreaseContentPadding();
    }

    private void addSecuritySchemes(List<OpenApiSecurityScheme> result,
                                    Set<Annotation> annotations,
                                    TypeName containerType,
                                    TypeName annotationType,
                                    String annotationName,
                                    String type) {
        repeatableAnnotations(annotations, containerType, annotationType)
                .forEach(annotation -> result.add(new OpenApiSecurityScheme(annotationName, annotation, type)));
    }

    private List<Annotation> repeatableAnnotations(Set<Annotation> annotations,
                                                   TypeName containerType,
                                                   TypeName annotationType) {
        List<Annotation> result = new ArrayList<>();
        Annotations.findFirst(containerType, annotations)
                .flatMap(Annotation::annotationValues)
                .ifPresent(result::addAll);
        if (result.isEmpty()) {
            Annotations.findFirst(annotationType, annotations)
                    .ifPresent(result::add);
        }
        return result;
    }

    private Optional<String> oauthFlowsExpression(String owner, String schemeName, Annotation flows) {
        List<JsonObjectEntry> entries = new ArrayList<>();
        addOauthFlow(entries, owner, schemeName, flows, "implicit");
        addOauthFlow(entries, owner, schemeName, flows, "password");
        addOauthFlow(entries, owner, schemeName, flows, "clientCredentials");
        addOauthFlow(entries, owner, schemeName, flows, "authorizationCode");
        addOauthFlow(entries, owner, schemeName, flows, "deviceAuthorization");
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(jsonObjectExpression(entries));
    }

    private void addOauthFlow(List<JsonObjectEntry> entries,
                              String owner,
                              String schemeName,
                              Annotation flows,
                              String name) {
        flows.annotationValue(name)
                .flatMap(flow -> oauthFlowExpression(owner, schemeName, name, flow))
                .ifPresent(flow -> entries.add(new JsonObjectEntry(name, flow)));
    }

    private Optional<String> oauthFlowExpression(String owner, String schemeName, String flowName, Annotation flow) {
        List<JsonObjectEntry> entries = new ArrayList<>();
        addStringJsonEntry(entries, flow, "authorizationUrl");
        addStringJsonEntry(entries, flow, "deviceAuthorizationUrl");
        addStringJsonEntry(entries, flow, "tokenUrl");
        addStringJsonEntry(entries, flow, "refreshUrl");

        List<Annotation> scopes = flow.annotationValues("scopes").orElseGet(List::of);
        validator.validateOAuthScopes(owner, schemeName, flowName, scopes);
        if (entries.isEmpty() && scopes.isEmpty()) {
            return Optional.empty();
        }
        String scopesExpression = scopes.isEmpty() ? JSON_OBJECT.fqName() + ".empty()" : oauthScopesExpression(scopes);
        entries.add(new JsonObjectEntry("scopes", scopesExpression));
        return Optional.of(jsonObjectExpression(entries));
    }

    private void addStringJsonEntry(List<JsonObjectEntry> entries, Annotation annotation, String name) {
        annotation.stringValue(name)
                .filter(not(String::isBlank))
                .ifPresent(value -> entries.add(new JsonObjectEntry(name, expressions.stringExpression(value))));
    }

    private String oauthScopesExpression(List<Annotation> scopes) {
        List<JsonObjectEntry> entries = new ArrayList<>();
        scopes.forEach(scope -> {
            String name = scope.stringValue()
                    .filter(not(String::isBlank))
                    .orElseThrow(() -> new CodegenException("@OpenApi.OAuthScope value is required"));
            String description = scope.stringValue("description")
                    .orElse("");
            entries.add(new JsonObjectEntry(name, expressions.stringExpression(description)));
        });
        return jsonObjectExpression(entries);
    }

    private String jsonObjectExpression(List<JsonObjectEntry> entries) {
        StringBuilder result = new StringBuilder(JSON_OBJECT.fqName()).append(".builder()");
        entries.forEach(entry -> result.append(".set(")
                .append(expressions.validatedStringExpression(entry.name()))
                .append(", ")
                .append(entry.valueExpression())
                .append(")"));
        return result.append(".build()").toString();
    }

    private record JsonObjectEntry(String name, String valueExpression) {
    }
}
