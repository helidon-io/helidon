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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.helidon.codegen.apt.AptProcessor;
import io.helidon.codegen.testing.TestCompiler;
import io.helidon.common.Api;
import io.helidon.common.Default;
import io.helidon.common.Generated;
import io.helidon.common.GenericType;
import io.helidon.common.LazyValue;
import io.helidon.common.mapper.Mappers;
import io.helidon.common.parameters.Parameters;
import io.helidon.common.types.Annotation;
import io.helidon.common.uri.UriQuery;
import io.helidon.config.Config;
import io.helidon.http.Http;
import io.helidon.openapi.OpenApi;
import io.helidon.service.registry.Dependency;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceDescriptor;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.Handler;
import io.helidon.webserver.http.HttpEntryPoint;
import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRoute;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.RestServer;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class OpenApiDuplicateValuesCodegenTest {
    private static final List<Class<?>> CLASSPATH = List.of(
            Annotation.class,
            Api.class,
            Config.class,
            Default.class,
            Dependency.class,
            Generated.class,
            GenericType.class,
            Handler.class,
            Http.class,
            HttpEntryPoint.class,
            HttpFeature.class,
            HttpRoute.class,
            HttpRouting.class,
            HttpRules.class,
            LazyValue.class,
            Mappers.class,
            OpenApi.class,
            Parameters.class,
            RestServer.class,
            ServerRequest.class,
            ServerResponse.class,
            Service.class,
            ServiceDescriptor.class,
            UriQuery.class,
            WebServer.class
    );

    @Test
    void documentCannotDeclareDuplicateServers() {
        var result = compile("openapi-duplicate-document-servers", """
                @OpenApi.Server("https://api.example.com")
                @OpenApi.Server("https://api.example.com")
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    String get() {
                        return "ok";
                    }
                }
                """);

        assertCompilationFails(result,
                               "@OpenApi.Server on com.example.InvalidOpenApiEndpoint",
                               "cannot define server https://api.example.com more than once");
    }

    @Test
    void documentCannotDeclareDuplicateTags() {
        var result = compile("openapi-duplicate-document-tags", """
                @OpenApi.Tag("greeting")
                @OpenApi.Tag("greeting")
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    String get() {
                        return "ok";
                    }
                }
                """);

        assertCompilationFails(result,
                               "@OpenApi.Tag on com.example.InvalidOpenApiEndpoint",
                               "cannot define tag greeting more than once");
    }

    @Test
    void documentCannotDeclareDuplicateExtensions() {
        var result = compile("openapi-duplicate-document-extensions", """
                @OpenApi.Extension(name = "x-test", value = "one")
                @OpenApi.Extension(name = "x-test", value = "two")
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    String get() {
                        return "ok";
                    }
                }
                """);

        assertCompilationFails(result,
                               "@OpenApi.Extension on com.example.InvalidOpenApiEndpoint",
                               "cannot define extension x-test more than once");
    }

    @Test
    void operationCannotDeclareDuplicateServers() {
        var result = compile("openapi-duplicate-operation-servers", """
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    @OpenApi.Server("https://api.example.com")
                    @OpenApi.Server("https://api.example.com")
                    String get() {
                        return "ok";
                    }
                }
                """);

        assertCompilationFails(result,
                               "@OpenApi.Server on com.example.InvalidOpenApiEndpoint.get",
                               "cannot define server https://api.example.com more than once");
    }

    @Test
    void operationCannotDeclareDuplicateTags() {
        var result = compile("openapi-duplicate-operation-tags", """
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    @OpenApi.Operation(tags = {"${openapi.tag:greeting}", "greeting"})
                    String get() {
                        return "ok";
                    }
                }
                """);

        assertCompilationFails(result,
                               "@OpenApi.Operation on com.example.InvalidOpenApiEndpoint.get",
                               "cannot define tag greeting more than once");
    }

    @Test
    void operationTagsUseConfigurationExpressionDefaults() throws IOException {
        var result = compile("openapi-operation-tag-config-expression-defaults", """
                @OpenApi.Tag("${openapi.tag:greeting}")
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/valid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    @OpenApi.Operation(tags = "${openapi.tag:greeting}")
                    String get() {
                        return "ok";
                    }
                }
                """);

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));

        String generated = generatedSource(result);
        assertThat(generated, containsString(".tag(tag -> tag.name(\"greeting\")"));
        assertThat(generated, containsString(".tag(\"greeting\")"));
    }

    @Test
    void securitySchemeCannotUseDuplicateName() {
        var result = compile("openapi-duplicate-security-scheme", """
                @OpenApi.SecurityScheme(name = "bearerAuth", type = "http", scheme = "bearer")
                @OpenApi.SecurityScheme(name = "bearerAuth",
                                        type = "apiKey",
                                        in = "header",
                                        apiKeyName = "Authorization")
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    String get() {
                        return "ok";
                    }
                }
        """);

        assertCompilationFails(result,
                               "@OpenApi.SecurityScheme on com.example.InvalidOpenApiEndpoint",
                               "cannot define security scheme bearerAuth more than once");
    }

    @Test
    void securitySchemeCannotUseDuplicateNameAcrossTypedAndGenericAnnotations() {
        var result = compile("openapi-duplicate-typed-security-scheme", """
                @OpenApi.SecurityScheme(name = "bearerAuth", type = "http", scheme = "bearer")
                @OpenApi.HttpSecurityScheme(name = "bearerAuth", scheme = "basic")
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    String get() {
                        return "ok";
                    }
                }
        """);

        assertCompilationFails(result,
                               "@OpenApi.HttpSecurityScheme on com.example.InvalidOpenApiEndpoint",
                               "cannot define security scheme bearerAuth more than once");
    }

    @Test
    void securitySchemeAllowsConfigurationExpressionDefaults() throws IOException {
        var result = compile("openapi-security-scheme-config-expression-defaults", """
                @OpenApi.Server("https://${openapi.host:api.example.com}")
                @OpenApi.SecurityScheme(name = "apiKeyAuth",
                                        type = "apiKey",
                                        in = "${openapi.api-key.in:header}",
                                        apiKeyName = "${openapi.api-key.name:X-API-Key}")
                @OpenApi.Document
                @OpenApi.Info(title = "${openapi.title:Test}", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/valid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    String get() {
                        return "ok";
                    }
                }
                """);

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));

        String generated = generatedSource(result);
        assertThat(generated, containsString("OpenApiDocumentContextSupport.resolveExpression(context, "
                                                     + "\"https://${openapi.host:api.example.com}\")"));
        assertThat(generated, containsString("OpenApiDocumentContextSupport.resolveExpression(context, "
                                                     + "\"${openapi.title:Test}\")"));
        assertThat(generated, containsString(".type(\"apiKey\")"));
        assertThat(generated, containsString(".in(\"header\")"));
        assertThat(generated, containsString("OpenApiDocumentContextSupport.resolveExpression(context, "
                                                     + "\"${openapi.api-key.name:X-API-Key}\")"));
    }

    @Test
    void httpSecuritySchemeWithConfiguredSchemeGuardsBearerFormat() throws IOException {
        var result = compile("openapi-http-security-scheme-configured-scheme-bearer-format", """
                @OpenApi.HttpSecurityScheme(name = "bearerAuth",
                                            scheme = "${auth.scheme:bearer}",
                                            bearerFormat = "JWT")
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/valid")
                class ValidOpenApiEndpoint {
                    @Http.GET
                    String get() {
                        return "ok";
                    }
                }
                """);

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));

        String generated = generatedSource(result);
        assertThat(generated, containsString("String resolvedScheme = io.helidon.openapi.OpenApiDocumentContextSupport"
                                                     + ".resolveExpression(context, \"${auth.scheme:bearer}\");"));
        assertThat(generated, containsString("security.scheme(resolvedScheme);"));
        assertThat(generated, containsString("if (\"bearer\".equalsIgnoreCase(resolvedScheme)) {"));
        assertThat(generated, containsString("security.bearerFormat(io.helidon.openapi.OpenApiDocumentContextSupport"
                                                     + ".resolveExpression(context, \"JWT\"));"));
    }

    @Test
    void typedSecuritySchemesGenerateComponents() throws IOException {
        var result = compile("openapi-typed-security-schemes", """
                @OpenApi.ApiKeySecurityScheme(name = "apiKeyAuth",
                                              apiKeyName = "X-API-Key",
                                              in = "header")
                @OpenApi.HttpSecurityScheme(name = "bearerAuth",
                                            scheme = "bearer",
                                            bearerFormat = "JWT")
                @OpenApi.MutualTlsSecurityScheme(name = "mtls")
                @OpenApi.OAuth2SecurityScheme(
                        name = "oauth2",
                        flows = @OpenApi.OAuthFlows(
                                clientCredentials = @OpenApi.OAuthFlow(
                                        tokenUrl = "https://api.example.com/token",
                                        scopes = @OpenApi.OAuthScope(value = "read", description = "Read"))),
                        oauth2MetadataUrl = "https://api.example.com/.well-known/oauth-authorization-server")
                @OpenApi.OidcSecurityScheme(
                        name = "oidc",
                        openIdConnectUrl = "https://id.example.com/.well-known/openid-configuration")
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/valid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    String get() {
                        return "ok";
                    }
                }
                """);

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));

        String generated = generatedSource(result);
        assertThat(generated, containsString(".securityScheme(\"apiKeyAuth\","));
        assertThat(generated, containsString(".type(\"apiKey\")"));
        assertThat(generated, containsString(".name(io.helidon.openapi.OpenApiDocumentContextSupport"
                                                     + ".resolveExpression(context, \"X-API-Key\"))"));
        assertThat(generated, containsString(".in(\"header\")"));
        assertThat(generated, containsString(".securityScheme(\"bearerAuth\","));
        assertThat(generated, containsString(".type(\"http\")"));
        assertThat(generated, containsString(".scheme(io.helidon.openapi.OpenApiDocumentContextSupport"
                                                     + ".resolveExpression(context, \"bearer\"))"));
        assertThat(generated, containsString(".bearerFormat(io.helidon.openapi.OpenApiDocumentContextSupport"
                                                     + ".resolveExpression(context, \"JWT\"))"));
        assertThat(generated, containsString(".securityScheme(\"mtls\","));
        assertThat(generated, containsString(".type(\"mutualTLS\")"));
        assertThat(generated, containsString(".securityScheme(\"oauth2\","));
        assertThat(generated, containsString(".type(\"oauth2\")"));
        assertThat(generated, containsString(".flows("));
        assertThat(generated, containsString(".oauth2MetadataUrl(io.helidon.openapi.OpenApiDocumentContextSupport"
                                                     + ".resolveExpression(context, \"https://api.example.com/"
                                                     + ".well-known/oauth-authorization-server\"))"));
        assertThat(generated, containsString(".securityScheme(\"oidc\","));
        assertThat(generated, containsString(".type(\"openIdConnect\")"));
        assertThat(generated, containsString(".openIdConnectUrl(io.helidon.openapi.OpenApiDocumentContextSupport"
                                                     + ".resolveExpression(context, \"https://id.example.com/"
                                                     + ".well-known/openid-configuration\"))"));
    }

    @Test
    void apiKeySecuritySchemeRequiresNameAndLocation() {
        var result = compile("openapi-security-scheme-api-key-missing-name", """
                @OpenApi.SecurityScheme(name = "apiKeyAuth", type = "apiKey", in = "header")
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    String get() {
                        return "ok";
                    }
                }
                """);

        assertCompilationFails(result,
                               "@OpenApi.SecurityScheme on com.example.InvalidOpenApiEndpoint "
                                       + "for security scheme apiKeyAuth",
                               "requires apiKeyName");
    }

    @Test
    void apiKeySecuritySchemeRejectsInvalidLocationDefault() {
        var result = compile("openapi-security-scheme-api-key-invalid-location", """
                @OpenApi.SecurityScheme(name = "apiKeyAuth",
                                        type = "apiKey",
                                        in = "${openapi.api-key.in:body}",
                                        apiKeyName = "X-API-Key")
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    String get() {
                        return "ok";
                    }
                }
                """);

        assertCompilationFails(result,
                               "@OpenApi.SecurityScheme on com.example.InvalidOpenApiEndpoint "
                                       + "for security scheme apiKeyAuth",
                               "apiKey in must be one of query, header, or cookie: body");
    }

    @Test
    void httpSecuritySchemeRequiresScheme() {
        var result = compile("openapi-security-scheme-http-missing-scheme", """
                @OpenApi.SecurityScheme(name = "httpAuth", type = "http")
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    String get() {
                        return "ok";
                    }
                }
                """);

        assertCompilationFails(result,
                               "@OpenApi.SecurityScheme on com.example.InvalidOpenApiEndpoint "
                                       + "for security scheme httpAuth",
                               "requires scheme");
    }

    @Test
    void httpSecuritySchemeRejectsApiKeyFields() {
        var result = compile("openapi-security-scheme-http-api-key-fields", """
                @OpenApi.SecurityScheme(name = "httpAuth",
                                        type = "http",
                                        scheme = "bearer",
                                        apiKeyName = "X-API-Key",
                                        in = "header")
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    String get() {
                        return "ok";
                    }
                }
                """);

        assertCompilationFails(result,
                               "@OpenApi.SecurityScheme on com.example.InvalidOpenApiEndpoint "
                                       + "for security scheme httpAuth",
                               "type http cannot define apiKeyName");
    }

    @Test
    void securitySchemeRejectsInvalidFieldWithBlankExpressionDefault() {
        var result = compile("openapi-security-scheme-invalid-field-blank-expression-default", """
                @OpenApi.SecurityScheme(name = "apiKeyAuth",
                                        type = "apiKey",
                                        in = "header",
                                        apiKeyName = "X-API-Key",
                                        scheme = "${openapi.http.scheme:}")
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    String get() {
                        return "ok";
                    }
                }
                """);

        assertCompilationFails(result,
                               "@OpenApi.SecurityScheme on com.example.InvalidOpenApiEndpoint "
                                       + "for security scheme apiKeyAuth",
                               "type apiKey cannot define scheme");
    }

    @Test
    void securitySchemeRejectsInvalidFlowWithBlankExpressionDefault() {
        var result = compile("openapi-security-scheme-invalid-flow-blank-expression-default", """
                @OpenApi.SecurityScheme(name = "mtls",
                                        type = "mutualTLS",
                                        flows = @OpenApi.OAuthFlows(
                                                clientCredentials = @OpenApi.OAuthFlow(
                                                        tokenUrl = "${openapi.oauth.token:}")))
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    String get() {
                        return "ok";
                    }
                }
                """);

        assertCompilationFails(result,
                               "@OpenApi.SecurityScheme on com.example.InvalidOpenApiEndpoint "
                                       + "for security scheme mtls",
                               "type mutualTLS cannot define flows");
    }

    @Test
    void apiKeySecuritySchemeRejectsHttpFields() {
        var result = compile("openapi-security-scheme-api-key-http-fields", """
                @OpenApi.SecurityScheme(name = "apiKeyAuth",
                                        type = "apiKey",
                                        apiKeyName = "X-API-Key",
                                        in = "header",
                                        scheme = "bearer")
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    String get() {
                        return "ok";
                    }
                }
                """);

        assertCompilationFails(result,
                               "@OpenApi.SecurityScheme on com.example.InvalidOpenApiEndpoint "
                                       + "for security scheme apiKeyAuth",
                               "type apiKey cannot define scheme");
    }

    @Test
    void oauth2SecuritySchemeRequiresFlow() {
        var result = compile("openapi-security-scheme-oauth2-missing-flow", """
                @OpenApi.SecurityScheme(name = "oauth2", type = "oauth2")
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    String get() {
                        return "ok";
                    }
                }
                """);

        assertCompilationFails(result,
                               "@OpenApi.SecurityScheme on com.example.InvalidOpenApiEndpoint "
                                       + "for security scheme oauth2",
                               "requires at least one OAuth flow");
    }

    @Test
    void oauth2AuthorizationCodeFlowRequiresTokenUrl() {
        var result = compile("openapi-security-scheme-oauth2-missing-token-url", """
                @OpenApi.SecurityScheme(name = "oauth2",
                                        type = "oauth2",
                                        flows = @OpenApi.OAuthFlows(
                                                authorizationCode = @OpenApi.OAuthFlow(
                                                        authorizationUrl = "https://id.example.com/authorize")))
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    String get() {
                        return "ok";
                    }
                }
                """);

        assertCompilationFails(result,
                               "@OpenApi.OAuthFlow on com.example.InvalidOpenApiEndpoint "
                                       + "for security scheme oauth2 authorizationCode flow",
                               "requires tokenUrl");
    }

    @Test
    void openIdConnectSecuritySchemeRequiresUrl() {
        var result = compile("openapi-security-scheme-openid-missing-url", """
                @OpenApi.SecurityScheme(name = "oidc", type = "openIdConnect")
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    String get() {
                        return "ok";
                    }
                }
                """);

        assertCompilationFails(result,
                               "@OpenApi.SecurityScheme on com.example.InvalidOpenApiEndpoint "
                                       + "for security scheme oidc",
                               "requires openIdConnectUrl");
    }

    @Test
    void oauthFlowCannotDeclareDuplicateScopes() {
        var result = compile("openapi-duplicate-oauth-scopes", """
                @OpenApi.SecurityScheme(
                        name = "oauth2",
                        type = "oauth2",
                        flows = @OpenApi.OAuthFlows(
                                clientCredentials = @OpenApi.OAuthFlow(
                                        tokenUrl = "https://api.example.com/token",
                                        scopes = {
                                                @OpenApi.OAuthScope(value = "read", description = "Read"),
                                                @OpenApi.OAuthScope(value = "read", description = "Read again")
                                        })))
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    String get() {
                        return "ok";
                    }
                }
                """);

        assertCompilationFails(result,
                               "@OpenApi.OAuthFlow on com.example.InvalidOpenApiEndpoint",
                               "cannot define scope read more than once");
    }

    @Test
    void securityRequirementCannotDeclareDuplicateSchemes() {
        var result = compile("openapi-duplicate-security-requirement-schemes", """
                @OpenApi.SecurityRequirement({
                        @OpenApi.SecuritySchemeRequirement("bearerAuth"),
                        @OpenApi.SecuritySchemeRequirement("bearerAuth")
                })
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    String get() {
                        return "ok";
                    }
                }
                """);

        assertCompilationFails(result,
                               "@OpenApi.SecurityRequirement on com.example.InvalidOpenApiEndpoint",
                               "cannot define scheme bearerAuth more than once");
    }

    @Test
    void securityRequirementCannotRepeatSameRequirement() {
        var result = compile("openapi-duplicate-security-requirement", """
                @OpenApi.SecurityRequirement(@OpenApi.SecuritySchemeRequirement("bearerAuth"))
                @OpenApi.SecurityRequirement(@OpenApi.SecuritySchemeRequirement("bearerAuth"))
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    String get() {
                        return "ok";
                    }
                }
                """);

        assertCompilationFails(result,
                               "@OpenApi.SecurityRequirement on com.example.InvalidOpenApiEndpoint",
                               "cannot define security requirement [bearerAuth] more than once");
    }

    @Test
    void securityRequirementCannotRepeatSameRequirementInDifferentOrder() {
        var result = compile("openapi-duplicate-security-requirement-order", """
                @OpenApi.SecurityRequirement({
                        @OpenApi.SecuritySchemeRequirement("bearerAuth"),
                        @OpenApi.SecuritySchemeRequirement(value = "oauth2", scopes = {"write", "read"})
                })
                @OpenApi.SecurityRequirement({
                        @OpenApi.SecuritySchemeRequirement(value = "oauth2", scopes = {"read", "write"}),
                        @OpenApi.SecuritySchemeRequirement("bearerAuth")
                })
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    String get() {
                        return "ok";
                    }
                }
                """);

        assertCompilationFails(result,
                               "@OpenApi.SecurityRequirement on com.example.InvalidOpenApiEndpoint",
                               "cannot define security requirement");
    }

    @Test
    void securitySchemeRequirementCannotUseDuplicateScopes() {
        var result = compile("openapi-duplicate-security-requirement-scopes", """
                @OpenApi.SecuritySchemeRequirement(value = "oauth2", scopes = {"read", "read"})
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    String get() {
                        return "ok";
                    }
                }
                """);

        assertCompilationFails(result,
                               "@OpenApi.SecuritySchemeRequirement on com.example.InvalidOpenApiEndpoint"
                                       + " for scheme oauth2",
                               "cannot define scope read more than once");
    }

    @Test
    void securitySchemeRequirementCannotCombineWithSecurityRequirement() {
        var result = compile("openapi-mixed-security-requirements", """
                @OpenApi.SecuritySchemeRequirement("bearerAuth")
                @OpenApi.SecurityRequirement(@OpenApi.SecuritySchemeRequirement(value = "oauth2", scopes = "read"))
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    String get() {
                        return "ok";
                    }
                }
                """);

        assertCompilationFails(result,
                               "@OpenApi.SecuritySchemeRequirement on com.example.InvalidOpenApiEndpoint",
                               "cannot be combined with @OpenApi.SecurityRequirement or @OpenApi.SecurityRequirements");
    }

    @Test
    void securityRequirementScopesApplyOnlyToTheirSchemes() throws IOException {
        var result = compile("openapi-security-requirement-scopes", """
                @OpenApi.SecurityRequirement({
                        @OpenApi.SecuritySchemeRequirement("bearerAuth"),
                        @OpenApi.SecuritySchemeRequirement(value = "oauth2", scopes = "read")
                })
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/valid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    String get() {
                        return "ok";
                    }
                }
                """);

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));
        String generated = generatedSource(result);
        assertThat(generated, containsString(".scheme(\"bearerAuth\", java.util.List.of())"));
        assertThat(generated, containsString(".scheme(\"oauth2\", java.util.List.of(\"read\"))"));
    }

    @Test
    void methodSecurityRequirementOverridesInheritedRequirement() throws IOException {
        var result = compile("openapi-method-security-requirement-overrides-inherited-requirement", """
                interface SecuredApi {
                    @Http.GET
                    @OpenApi.SecurityRequirement(@OpenApi.SecuritySchemeRequirement("oauth2"))
                    String get();
                }

                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/valid")
                class ValidOpenApiEndpoint implements SecuredApi {
                    @Override
                    @OpenApi.SecuritySchemeRequirement("bearerAuth")
                    public String get() {
                        return "ok";
                    }
                }
                """);

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));

        String generated = generatedSource(result);
        assertThat(generated, containsString(".scheme(\"bearerAuth\", java.util.List.of())"));
        assertThat(generated.contains(".scheme(\"oauth2\", java.util.List.of())"), is(false));
    }

    @Test
    void methodParameterCannotRepeatSameLocationAndName() {
        var result = compile("openapi-duplicate-method-parameter", """
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    @OpenApi.Parameter(name = "${openapi.param:value}", in = "${openapi.in:query}", value = "First")
                    @OpenApi.Parameter(name = "value", in = "query", value = "Second")
                    String get(@Http.QueryParam("value") String value) {
                        return value;
                    }
                }
                """);

        assertCompilationFails(result,
                               "Method-level @OpenApi.Parameter on com.example.InvalidOpenApiEndpoint.get",
                               "cannot define parameter query value more than once");
    }

    @Test
    void parameterAnnotationCannotRepeatOnSameParameter() {
        var result = compile("openapi-duplicate-parameter-annotation", """
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    String get(@OpenApi.Parameter("First")
                               @OpenApi.Parameter("Second")
                               @Http.QueryParam("value") String value) {
                        return value;
                    }
                }
                """);

        assertCompilationFails(result,
                               "@OpenApi.Parameter on com.example.InvalidOpenApiEndpoint.get",
                               "cannot define metadata for query parameter value more than once");
    }

    @Test
    void parameterExamplesCannotUseDuplicateNames() {
        var result = compile("openapi-duplicate-parameter-examples", """
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    String get(@OpenApi.Parameter(examples = {
                                       @OpenApi.Example(name = "${openapi.example:sample}", value = "one"),
                                       @OpenApi.Example(name = "sample", value = "two")
                               })
                               @Http.QueryParam("value") String value) {
                        return value;
                    }
                }
                """);

        assertCompilationFails(result,
                               "@OpenApi.Parameter on com.example.InvalidOpenApiEndpoint.get",
                               "cannot define example sample more than once");
    }

    @Test
    void requestBodyCannotDeclareDuplicateContentMediaTypes() {
        var result = compile("openapi-duplicate-request-body-content", """
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.POST
                    @OpenApi.RequestBody(content = {
                            @OpenApi.Content("${openapi.content:application/json}"),
                            @OpenApi.Content("application/json")
                    })
                    String post(@Http.Entity String value) {
                        return value;
                    }
                }
                """);

        assertCompilationFails(result,
                               "@OpenApi.RequestBody on com.example.InvalidOpenApiEndpoint.post",
                               "cannot define content media type application/json more than once");
    }

    @Test
    void responseCannotDeclareDuplicateContentMediaTypes() {
        var result = compile("openapi-duplicate-response-content", """
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    @OpenApi.Response(status = 200,
                                      description = "OK",
                                      content = {
                                              @OpenApi.Content("${openapi.content:application/json}"),
                                              @OpenApi.Content("application/json")
                                      })
                    String get() {
                        return "ok";
                    }
                }
                """);

        assertCompilationFails(result,
                               "@OpenApi.Response on com.example.InvalidOpenApiEndpoint.get",
                               "cannot define content media type application/json more than once");
    }

    @Test
    void contentExamplesCannotUseDuplicateNames() {
        var result = compile("openapi-duplicate-content-examples", """
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    @OpenApi.Response(status = 200,
                                      description = "OK",
                                      content = @OpenApi.Content(
                                              examples = {
                                                      @OpenApi.Example(name = "${openapi.example:sample}", value = "one"),
                                                      @OpenApi.Example(name = "sample", value = "two")
                                              }))
                    String get() {
                        return "ok";
                    }
                }
                """);

        assertCompilationFails(result,
                               "@OpenApi.Response on com.example.InvalidOpenApiEndpoint.get",
                               "cannot define example sample more than once");
    }

    @Test
    void contentExampleCannotUseValueWithDataValue() {
        var result = compile("openapi-example-value-and-data-value", """
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    @OpenApi.Response(status = 200,
                                      description = "OK",
                                      content = @OpenApi.Content(
                                              examples = @OpenApi.Example(name = "sample",
                                                                          value = "one",
                                                                          dataValue = "two")))
                    String get() {
                        return "ok";
                    }
                }
                """);

        assertCompilationFails(result,
                               "@OpenApi.Response on com.example.InvalidOpenApiEndpoint.get",
                               "example sample cannot define value with dataValue, serializedValue, or externalValue");
    }

    @Test
    void contentExampleCannotUseValueWithExternalValue() {
        var result = compile("openapi-example-value-and-external-value", """
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    @OpenApi.Response(status = 200,
                                      description = "OK",
                                      content = @OpenApi.Content(
                                              examples = @OpenApi.Example(name = "sample",
                                                                          value = "one",
                                                                          externalValue = "examples/sample.json")))
                    String get() {
                        return "ok";
                    }
                }
                """);

        assertCompilationFails(result,
                               "@OpenApi.Response on com.example.InvalidOpenApiEndpoint.get",
                               "example sample cannot define value with dataValue, serializedValue, or externalValue");
    }

    @Test
    void contentExampleCannotUseValueWithSerializedValue() {
        var result = compile("openapi-example-value-and-serialized-value", """
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    @OpenApi.Response(status = 200,
                                      description = "OK",
                                      content = @OpenApi.Content(
                                              examples = @OpenApi.Example(name = "sample",
                                                                          value = "one",
                                                                          serializedValue = "two")))
                    String get() {
                        return "ok";
                    }
                }
                """);

        assertCompilationFails(result,
                               "@OpenApi.Response on com.example.InvalidOpenApiEndpoint.get",
                               "example sample cannot define value with dataValue, serializedValue, or externalValue");
    }

    @Test
    void contentExampleCannotUseSerializedValueWithExternalValue() {
        var result = compile("openapi-example-serialized-value-and-external-value", """
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    @OpenApi.Response(status = 200,
                                      description = "OK",
                                      content = @OpenApi.Content(
                                              examples = @OpenApi.Example(name = "sample",
                                                                          dataValue = "{\\"value\\":\\"one\\"}",
                                                                          serializedValue = "{\\"value\\":\\"one\\"}",
                                                                          externalValue = "examples/sample.json")))
                    String get() {
                        return "ok";
                    }
                }
                """);

        assertCompilationFails(result,
                               "@OpenApi.Response on com.example.InvalidOpenApiEndpoint.get",
                               "example sample cannot define serializedValue and externalValue together");
    }

    @Test
    void responseCannotDeclareDuplicateHeaderNames() {
        var result = compile("openapi-duplicate-response-headers", """
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    @OpenApi.Response(status = 200,
                                      description = "OK",
                                      headers = {
                                              @OpenApi.Header(name = "X-Value", value = "First"),
                                              @OpenApi.Header(name = "x-value", value = "Second")
                                      })
                    String get() {
                        return "ok";
                    }
                }
                """);

        assertCompilationFails(result,
                               "@OpenApi.Response on com.example.InvalidOpenApiEndpoint.get",
                               "cannot define response header x-value more than once");
    }

    private static TestCompiler.Result compile(String workDir, String source) {
        return TestCompiler.builder()
                .currentRelease()
                .procOnly()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/" + workDir))
                .addSource("InvalidOpenApiEndpoint.java", """
                        package com.example;

                        import io.helidon.http.Http;
                        import io.helidon.openapi.OpenApi;
                        import io.helidon.service.registry.Service;
                        import io.helidon.webserver.http.RestServer;

                        %s
                        """.formatted(source))
                .addSource("Main.java", """
                        package com.example;

                        import io.helidon.service.registry.Service;

                        @Service.GenerateBinding
                        class Main {
                        }
                        """)
                .build()
                .compile();
    }

    private static String generatedSource(TestCompiler.Result result) throws IOException {
        StringBuilder generatedContent = new StringBuilder();
        var generatedSources = Files.walk(result.sourceOutput())
                .filter(it -> it.getFileName().toString().endsWith(".java"))
                .toList();
        for (Path generatedSource : generatedSources) {
            generatedContent.append(Files.readString(generatedSource, StandardCharsets.UTF_8));
            generatedContent.append('\n');
        }
        return generatedContent.toString();
    }

    private static void assertCompilationFails(TestCompiler.Result result, String... diagnosticParts) {
        String diagnostics = String.join("\n", result.diagnostics());
        assertThat("Build should fail", result.success(), is(false));
        for (String diagnosticPart : diagnosticParts) {
            assertThat(diagnostics, containsString(diagnosticPart));
        }
    }
}
