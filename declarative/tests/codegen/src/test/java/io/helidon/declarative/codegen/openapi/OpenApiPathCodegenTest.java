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
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

class OpenApiPathCodegenTest {
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

    @TempDir
    private Path workDirRoot;

    @Test
    void openApiAnnotationOnMethodTriggersEndpointGeneration() throws IOException {
        var result = compile("openapi-operation-endpoint", """
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/cross-module")
                class CrossModuleEndpoint {
                    @Http.GET
                    @OpenApi.Operation
                    String get() {
                        return "ok";
                    }
                }
                """);

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));
        assertThat(generatedSource(result), containsString("document.path(\"/cross-module\""));
    }

    @Test
    void endpointMarkerGeneratesMetadataFromSignature() throws IOException {
        var result = compile("openapi-marker-endpoint", """
                @OpenApi.Endpoint
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/defaults")
                class DefaultOpenApiEndpoint {
                    @Http.GET
                    String get() {
                        return "ok";
                    }
                }
                """);

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));
        assertThat(generatedSource(result), containsString("document.path(\"/defaults\""));
    }

    @Test
    void endpointMarkerIsInheritedWithEndpointContract() throws IOException {
        var result = compile("inherited-openapi-marker-endpoint", """
                @OpenApi.Endpoint
                @RestServer.Endpoint
                interface OpenApiEndpointContract {
                    @Http.GET
                    String get();
                }

                @Service.Singleton
                @Http.Path("/inherited")
                class InheritedOpenApiEndpoint implements OpenApiEndpointContract {
                    @Override
                    public String get() {
                        return "ok";
                    }
                }
                """);

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));
        assertThat(generatedSource(result), containsString("document.path(\"/inherited\""));
    }

    @Test
    void methodAnnotationOnEndpointContractTriggersGeneration() throws IOException {
        var result = compile("contract-method-openapi-endpoint", """
                @RestServer.Endpoint
                interface OpenApiEndpointContract {
                    @Http.GET
                    @OpenApi.Operation
                    String get();
                }

                @Service.Singleton
                @Http.Path("/contract-method")
                class ContractMethodOpenApiEndpoint implements OpenApiEndpointContract {
                    @Override
                    public String get() {
                        return "ok";
                    }
                }
                """);

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));
        assertThat(generatedSource(result), containsString("document.path(\"/contract-method\""));
    }

    @Test
    void securityAnnotationOnEndpointContractTriggersGeneration() throws IOException {
        var result = compile("contract-security-openapi-endpoint", """
                @RestServer.Endpoint
                @OpenApi.SecuritySchemeRequirement("bearerAuth")
                interface SecuredOpenApiEndpointContract {
                    @Http.GET
                    String get();
                }

                @Service.Singleton
                @Http.Path("/contract-security")
                class ContractSecurityOpenApiEndpoint implements SecuredOpenApiEndpointContract {
                    @Override
                    public String get() {
                        return "ok";
                    }
                }
                """);

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));
        String generated = generatedSource(result);
        assertThat(generated, containsString("document.path(\"/contract-security\""));
        assertThat(generated, containsString(".scheme(\"bearerAuth\", java.util.List.of())"));
    }

    @Test
    void securityAnnotationsOnUnrelatedEndpointContractsArePreserved() throws IOException {
        for (String interfaces : List.of("FirstApi, SecondApi", "SecondApi, FirstApi")) {
            var result = compile("unrelated-endpoint-security-"
                                         + interfaces.substring(0, interfaces.indexOf(',')), """
                    @OpenApi.SecuritySchemeRequirement("firstAuth")
                    interface FirstApi {
                    }

                    @OpenApi.SecuritySchemeRequirement("secondAuth")
                    interface SecondApi {
                    }

                    @RestServer.Endpoint
                    @Service.Singleton
                    @OpenApi.Endpoint
                    @Http.Path("/unrelated-endpoint-security")
                    class SecuredEndpoint implements %s {
                        @Http.GET
                        public String get() {
                            return "ok";
                        }
                    }
                    """.formatted(interfaces));

            String diagnostics = String.join("\n", result.diagnostics());
            assertThat(diagnostics, result.success(), is(true));
            String generated = generatedSource(result);
            String first = ".scheme(\"firstAuth\", java.util.List.of())";
            assertThat(generated, containsString(first));
            assertThat(generated.lastIndexOf(first), is(generated.indexOf(first)));
            String second = ".scheme(\"secondAuth\", java.util.List.of())";
            assertThat(generated, containsString(second));
            assertThat(generated.lastIndexOf(second), is(generated.indexOf(second)));
        }
    }

    @Test
    void mixedSecurityAnnotationFormsOnUnrelatedEndpointContractsArePreserved() throws IOException {
        for (String interfaces : List.of("FirstApi, SecondApi", "SecondApi, FirstApi")) {
            var result = compile("mixed-unrelated-endpoint-security-"
                                         + interfaces.substring(0, interfaces.indexOf(',')), """
                    @OpenApi.SecuritySchemeRequirement("firstAuth")
                    interface FirstApi {
                    }

                    @OpenApi.SecurityRequirement(@OpenApi.SecuritySchemeRequirement("secondAuth"))
                    interface SecondApi {
                    }

                    @RestServer.Endpoint
                    @Service.Singleton
                    @OpenApi.Endpoint
                    @Http.Path("/mixed-unrelated-endpoint-security")
                    class SecuredEndpoint implements %s {
                        @Http.GET
                        public String get() {
                            return "ok";
                        }
                    }
                    """.formatted(interfaces));

            String diagnostics = String.join("\n", result.diagnostics());
            assertThat(diagnostics, result.success(), is(true));
            String generated = generatedSource(result);
            String first = ".scheme(\"firstAuth\", java.util.List.of())";
            assertThat(generated, containsString(first));
            assertThat(generated.lastIndexOf(first), is(generated.indexOf(first)));
            String second = ".scheme(\"secondAuth\", java.util.List.of())";
            assertThat(generated, containsString(second));
            assertThat(generated.lastIndexOf(second), is(generated.indexOf(second)));
        }
    }

    @Test
    void equivalentInheritedEndpointSecurityRequirementFormsAreAccepted() throws IOException {
        for (String interfaces : List.of("DirectSecurityContract, StructuredSecurityContract",
                                         "StructuredSecurityContract, DirectSecurityContract")) {
            var result = compile("equivalent-inherited-endpoint-security-"
                                         + interfaces.substring(0, interfaces.indexOf(',')), """
                    @OpenApi.SecuritySchemeRequirement("apiKey")
                    interface DirectSecurityContract {
                    }

                    @OpenApi.SecurityRequirement(@OpenApi.SecuritySchemeRequirement("apiKey"))
                    interface StructuredSecurityContract {
                    }

                    @RestServer.Endpoint
                    @Service.Singleton
                    @OpenApi.Endpoint
                    @Http.Path("/equivalent-endpoint-security")
                    class SecuredEndpoint implements %s {
                        @Http.GET
                        String get() {
                            return "ok";
                        }
                    }
                    """.formatted(interfaces));

            String diagnostics = String.join("\n", result.diagnostics());
            assertThat(diagnostics, result.success(), is(true));
            String generated = generatedSource(result);
            String requirement = ".scheme(\"apiKey\", java.util.List.of())";
            assertThat(generated, containsString(requirement));
            assertThat(generated.lastIndexOf(requirement), is(generated.indexOf(requirement)));
        }
    }

    @Test
    void conflictingInheritedEndpointSecurityClearFails() {
        for (String interfaces : List.of("PublicContract, SecuredContract", "SecuredContract, PublicContract")) {
            var result = compile("conflicting-inherited-endpoint-security-clear-"
                                         + interfaces.substring(0, interfaces.indexOf(',')), """
                    @OpenApi.SecurityRequirements({})
                    interface PublicContract {
                    }

                    @OpenApi.SecurityRequirement(@OpenApi.SecuritySchemeRequirement("requiredAuth"))
                    interface SecuredContract {
                    }

                    @RestServer.Endpoint
                    @Service.Singleton
                    @OpenApi.Endpoint
                    @Http.Path("/conflicting-endpoint-security")
                    class ConflictingSecurityEndpoint implements %s {
                        @Http.GET
                        String get() {
                            return "ok";
                        }
                    }
                    """.formatted(interfaces));

            assertCompilationFails(result,
                                   "Conflicting inherited OpenAPI security requirements on "
                                           + "com.example.ConflictingSecurityEndpoint");
        }
    }

    @Test
    void identicalInheritedEndpointSecurityClearsAreDeduplicated() throws IOException {
        for (String interfaces : List.of("FirstPublicContract, SecondPublicContract",
                                         "SecondPublicContract, FirstPublicContract")) {
            var result = compile("identical-inherited-endpoint-security-clears-"
                                         + interfaces.substring(0, interfaces.indexOf(',')), """
                    @OpenApi.SecurityRequirements({})
                    interface FirstPublicContract {
                    }

                    @OpenApi.SecurityRequirements({})
                    interface SecondPublicContract {
                    }

                    @RestServer.Endpoint
                    @Service.Singleton
                    @OpenApi.Endpoint
                    @Http.Path("/public-endpoint")
                    class PublicEndpoint implements %s {
                        @Http.GET
                        String get() {
                            return "ok";
                        }
                    }
                    """.formatted(interfaces));

            String diagnostics = String.join("\n", result.diagnostics());
            assertThat(diagnostics, result.success(), is(true));
            String generated = generatedSource(result);
            String securityClear = ".security(java.util.List.of())";
            assertThat(generated, containsString(securityClear));
            assertThat(generated.lastIndexOf(securityClear), is(generated.indexOf(securityClear)));
        }
    }

    @Test
    void overridingEndpointContractSecurityRequirementReplacesBaseRequirement() throws IOException {
        var result = compile("overriding-endpoint-contract-security", """
                @OpenApi.SecuritySchemeRequirement("baseAuth")
                interface BaseApi {
                }

                @OpenApi.SecuritySchemeRequirement("narrowedAuth")
                interface NarrowedApi extends BaseApi {
                }

                @RestServer.Endpoint
                @Service.Singleton
                @OpenApi.Endpoint
                @Http.Path("/overriding-endpoint-security")
                class SecuredEndpoint implements NarrowedApi {
                    @Http.GET
                    public String get() {
                        return "ok";
                    }
                }
                """);

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));
        String generated = generatedSource(result);
        assertThat(generated, containsString(".scheme(\"narrowedAuth\", java.util.List.of())"));
        assertThat(generated, not(containsString(".scheme(\"baseAuth\", java.util.List.of())")));
    }

    @Test
    void unannotatedEndpointContractInheritsBaseSecurityRequirement() throws IOException {
        var result = compile("unannotated-endpoint-contract-security", """
                @OpenApi.SecuritySchemeRequirement("baseAuth")
                interface BaseApi {
                }

                interface NarrowedApi extends BaseApi {
                }

                @RestServer.Endpoint
                @Service.Singleton
                @OpenApi.Endpoint
                @Http.Path("/inherited-endpoint-security")
                class SecuredEndpoint implements NarrowedApi {
                    @Http.GET
                    public String get() {
                        return "ok";
                    }
                }
                """);

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));
        assertThat(generatedSource(result), containsString(".scheme(\"baseAuth\", java.util.List.of())"));
    }

    @Test
    void composedEndpointSecurityRequirementOverridesInheritedRequirements() throws IOException {
        var result = compile("endpoint-security-overrides-inherited-requirements", """
                @RestServer.Endpoint
                @OpenApi.SecurityRequirement(@OpenApi.SecuritySchemeRequirement("contractOne"))
                @OpenApi.SecurityRequirement(@OpenApi.SecuritySchemeRequirement("contractTwo"))
                interface SecuredOpenApiEndpointContract {
                    @Http.GET
                    String get();
                }

                @OpenApi.SecurityRequirement(@OpenApi.SecuritySchemeRequirement("bearerAuth"))
                @interface BearerAuth {
                }

                @Service.Singleton
                @Http.Path("/endpoint-security-override")
                @BearerAuth
                class ContractSecurityOpenApiEndpoint implements SecuredOpenApiEndpointContract {
                    @Override
                    public String get() {
                        return "ok";
                    }
                }
                """);

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));
        String generated = generatedSource(result);
        assertThat(generated, containsString("document.path(\"/endpoint-security-override\""));
        assertThat(generated, containsString(".scheme(\"bearerAuth\", java.util.List.of())"));
        assertThat(generated, not(containsString(".scheme(\"contractOne\", java.util.List.of())")));
        assertThat(generated, not(containsString(".scheme(\"contractTwo\", java.util.List.of())")));
    }

    @Test
    void directAndComposedEndpointSecurityRequirementsArePreserved() throws IOException {
        var result = compile("direct-and-composed-endpoint-security-requirements", """
                @OpenApi.SecuritySchemeRequirement("meta")
                @interface MetaAuth {
                }

                @RestServer.Endpoint
                @Service.Singleton
                @OpenApi.Endpoint
                @Http.Path("/mixed-endpoint-security")
                @OpenApi.SecuritySchemeRequirement("direct")
                @MetaAuth
                class MixedEndpointSecurityEndpoint {
                    @Http.GET
                    String get() {
                        return "ok";
                    }
                }
                """);

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));
        String generated = generatedSource(result);
        String direct = ".scheme(\"direct\", java.util.List.of())";
        assertThat(generated, containsString(direct));
        assertThat(generated.lastIndexOf(direct), is(generated.indexOf(direct)));
        String meta = ".scheme(\"meta\", java.util.List.of())";
        assertThat(generated, containsString(meta));
        assertThat(generated.lastIndexOf(meta), is(generated.indexOf(meta)));
    }

    @Test
    void directEndpointSecurityClearOverridesComposedRequirement() throws IOException {
        var result = compile("direct-endpoint-security-clear", """
                @OpenApi.SecurityRequirement(@OpenApi.SecuritySchemeRequirement("metaAuth"))
                @interface Secured {
                }

                @RestServer.Endpoint
                @Service.Singleton
                @OpenApi.Endpoint
                @OpenApi.SecurityRequirements({})
                @Secured
                @Http.Path("/public-endpoint")
                class PublicEndpoint {
                    @Http.GET
                    String get() {
                        return "ok";
                    }
                }
                """);

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));
        String generated = generatedSource(result);
        assertThat(generated, containsString(".security(java.util.List.of())"));
        assertThat(generated, not(containsString(".scheme(\"metaAuth\", java.util.List.of())")));
    }

    @Test
    void composedMethodSecuritySchemeRequirementsAreAllPreserved() throws IOException {
        var result = compile("composed-method-security-scheme-requirements", """
                @OpenApi.SecuritySchemeRequirement("bearerAuth")
                @interface BearerAuth {
                }

                @OpenApi.SecuritySchemeRequirement("apiKey")
                @interface ApiKeyAuth {
                }

                @RestServer.Endpoint
                @Service.Singleton
                @OpenApi.Endpoint
                @Http.Path("/composed-method-security")
                class ComposedMethodSecurityEndpoint {
                    @Http.GET
                    @BearerAuth
                    @ApiKeyAuth
                    String get() {
                        return "ok";
                    }
                }
                """);

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));
        String generated = generatedSource(result);
        assertThat(generated, containsString(".scheme(\"bearerAuth\", java.util.List.of())"));
        assertThat(generated, containsString(".scheme(\"apiKey\", java.util.List.of())"));
    }

    @Test
    void directMethodSecurityClearsOverrideComposedRequirements() throws IOException {
        var result = compile("direct-method-security-clears", """
                @OpenApi.SecurityRequirement(@OpenApi.SecuritySchemeRequirement("structuredAuth"))
                @interface StructuredAuth {
                }

                @OpenApi.SecuritySchemeRequirement("schemeAuth")
                @interface SchemeAuth {
                }

                @RestServer.Endpoint
                @Service.Singleton
                @OpenApi.Endpoint
                @Http.Path("/public-methods")
                class PublicMethodsEndpoint {
                    @Http.GET
                    @OpenApi.SecurityRequirements({})
                    @StructuredAuth
                    String get() {
                        return "ok";
                    }

                    @Http.POST
                    @OpenApi.SecurityRequirements({})
                    @SchemeAuth
                    String post() {
                        return "ok";
                    }
                }
                """);

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));
        String generated = generatedSource(result);
        String securityClear = ".security(java.util.List.of())";
        int firstClear = generated.indexOf(securityClear);
        assertThat(firstClear, not(is(-1)));
        assertThat(generated.indexOf(securityClear, firstClear + 1), not(is(-1)));
        assertThat(generated, not(containsString(".scheme(\"structuredAuth\", java.util.List.of())")));
        assertThat(generated, not(containsString(".scheme(\"schemeAuth\", java.util.List.of())")));
    }

    @Test
    void inheritedComposedMethodSecuritySchemeRequirementsAreAllPreserved() throws IOException {
        var result = compile("inherited-composed-method-security-scheme-requirements", """
                @OpenApi.SecuritySchemeRequirement("bearerAuth")
                @interface BearerAuth {
                }

                @OpenApi.SecuritySchemeRequirement("apiKey")
                @interface ApiKeyAuth {
                }

                interface SecuredApi {
                    @Http.GET
                    @BearerAuth
                    @ApiKeyAuth
                    String get();
                }

                @RestServer.Endpoint
                @Service.Singleton
                @OpenApi.Endpoint
                @Http.Path("/inherited-composed-method-security")
                class InheritedComposedMethodSecurityEndpoint implements SecuredApi {
                    @Override
                    public String get() {
                        return "ok";
                    }
                }
                """);

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));
        String generated = generatedSource(result);
        assertThat(generated, containsString(".scheme(\"bearerAuth\", java.util.List.of())"));
        assertThat(generated, containsString(".scheme(\"apiKey\", java.util.List.of())"));
    }

    @Test
    void conflictingInheritedMethodSecuritySchemeRequirementsFail() {
        var result = compile("conflicting-inherited-method-security-scheme-requirements", """
                interface FirstApi {
                    @Http.GET
                    @OpenApi.SecuritySchemeRequirement("firstAuth")
                    String get();
                }

                interface SecondApi {
                    @Http.GET
                    @OpenApi.SecuritySchemeRequirement("secondAuth")
                    String get();
                }

                @RestServer.Endpoint
                @Service.Singleton
                @OpenApi.Endpoint
                @Http.Path("/conflicting-inherited-security")
                class ConflictingSecurityEndpoint implements FirstApi, SecondApi {
                    @Override
                    public String get() {
                        return "ok";
                    }
                }
                """);

        assertCompilationFails(result,
                               "Conflicting inherited @OpenApi.SecuritySchemeRequirement annotations",
                               "com.example.ConflictingSecurityEndpoint.get");
    }

    @Test
    void overridingInheritedMethodSecurityRequirementReplacesBaseRequirement() throws IOException {
        var result = compile("overriding-inherited-method-security-requirement", """
                interface BaseApi {
                    @Http.GET
                    @OpenApi.SecuritySchemeRequirement("baseAuth")
                    String get();
                }

                interface NarrowedApi extends BaseApi {
                    @Override
                    @OpenApi.SecuritySchemeRequirement("narrowedAuth")
                    String get();
                }

                @RestServer.Endpoint
                @Service.Singleton
                @OpenApi.Endpoint
                @Http.Path("/overriding-inherited-security")
                class NarrowedSecurityEndpoint implements NarrowedApi {
                    @Override
                    public String get() {
                        return "ok";
                    }
                }
                """);

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));
        String generated = generatedSource(result);
        assertThat(generated, containsString(".scheme(\"narrowedAuth\", java.util.List.of())"));
        assertThat(generated, not(containsString(".scheme(\"baseAuth\", java.util.List.of())")));
    }

    @Test
    void conflictingInheritedMethodSecurityRequirementsFail() {
        for (String interfaces : List.of("FirstApi, SecondApi", "SecondApi, FirstApi")) {
            var result = compile("conflicting-inherited-method-security-requirements-"
                                         + interfaces.substring(0, interfaces.indexOf(',')), """
                    interface FirstApi {
                        @Http.GET
                        @OpenApi.SecurityRequirement(@OpenApi.SecuritySchemeRequirement("firstAuth"))
                        String get();
                    }

                    interface SecondApi {
                        @Http.GET
                        @OpenApi.SecurityRequirement(@OpenApi.SecuritySchemeRequirement("secondAuth"))
                        String get();
                    }

                    @RestServer.Endpoint
                    @Service.Singleton
                    @OpenApi.Endpoint
                    @Http.Path("/conflicting-inherited-security")
                    class ConflictingSecurityEndpoint implements %s {
                        @Override
                        public String get() {
                            return "ok";
                        }
                    }
                    """.formatted(interfaces));

            assertCompilationFails(result,
                                   "Conflicting inherited OpenAPI security requirements",
                                   "com.example.ConflictingSecurityEndpoint.get");
        }
    }

    @Test
    void conflictingInheritedMethodSecurityRequirementContainersFail() {
        var result = compile("conflicting-inherited-method-security-requirement-containers", """
                interface FirstApi {
                    @Http.GET
                    @OpenApi.SecurityRequirements({
                            @OpenApi.SecurityRequirement(@OpenApi.SecuritySchemeRequirement("firstAuth"))
                    })
                    String get();
                }

                interface SecondApi {
                    @Http.GET
                    @OpenApi.SecurityRequirements({
                            @OpenApi.SecurityRequirement(@OpenApi.SecuritySchemeRequirement("secondAuth"))
                    })
                    String get();
                }

                @RestServer.Endpoint
                @Service.Singleton
                @OpenApi.Endpoint
                @Http.Path("/conflicting-inherited-security")
                class ConflictingSecurityEndpoint implements FirstApi, SecondApi {
                    @Override
                    public String get() {
                        return "ok";
                    }
                }
                """);

        assertCompilationFails(result,
                               "Conflicting inherited OpenAPI security requirements",
                               "com.example.ConflictingSecurityEndpoint.get");
    }

    @Test
    void identicalInheritedMethodSecuritySchemeRequirementsAreDeduplicated() throws IOException {
        var result = compile("identical-inherited-method-security-scheme-requirements", """
                @OpenApi.SecuritySchemeRequirement("sharedAuth")
                @interface SharedAuth {
                }

                @OpenApi.SecuritySchemeRequirement("apiKey")
                @interface ApiKeyAuth {
                }

                interface FirstApi {
                    @Http.GET
                    @SharedAuth
                    @ApiKeyAuth
                    String get();
                }

                interface SecondApi {
                    @Http.GET
                    @ApiKeyAuth
                    @SharedAuth
                    String get();
                }

                @RestServer.Endpoint
                @Service.Singleton
                @OpenApi.Endpoint
                @Http.Path("/identical-inherited-security")
                class SharedSecurityEndpoint implements FirstApi, SecondApi {
                    @Override
                    public String get() {
                        return "ok";
                    }
                }
                """);

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));
        String generated = generatedSource(result);
        String scheme = ".scheme(\"sharedAuth\", java.util.List.of())";
        assertThat(generated, containsString(scheme));
        assertThat(generated.lastIndexOf(scheme), is(generated.indexOf(scheme)));
        String apiKey = ".scheme(\"apiKey\", java.util.List.of())";
        assertThat(generated, containsString(apiKey));
        assertThat(generated.lastIndexOf(apiKey), is(generated.indexOf(apiKey)));
    }

    @Test
    void identicalInheritedMethodSecurityRequirementsAreDeduplicated() throws IOException {
        var result = compile("identical-inherited-method-security-requirements", """
                interface FirstApi {
                    @Http.GET
                    @OpenApi.SecurityRequirement(
                            @OpenApi.SecuritySchemeRequirement(value = "oauth2", scopes = {"read", "write"}))
                    @OpenApi.SecurityRequirement(@OpenApi.SecuritySchemeRequirement("apiKey"))
                    String get();
                }

                interface SecondApi {
                    @Http.GET
                    @OpenApi.SecurityRequirements({
                            @OpenApi.SecurityRequirement(@OpenApi.SecuritySchemeRequirement("apiKey")),
                            @OpenApi.SecurityRequirement(
                                    @OpenApi.SecuritySchemeRequirement(value = "oauth2", scopes = {"write", "read"}))
                    })
                    String get();
                }

                @RestServer.Endpoint
                @Service.Singleton
                @OpenApi.Endpoint
                @Http.Path("/identical-inherited-security")
                class SharedSecurityEndpoint implements FirstApi, SecondApi {
                    @Override
                    public String get() {
                        return "ok";
                    }
                }
                """);

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));
        String generated = generatedSource(result);
        String oauth2 = ".scheme(\"oauth2\", java.util.List.of(\"read\", \"write\"))";
        assertThat(generated, containsString(oauth2));
        assertThat(generated.lastIndexOf(oauth2), is(generated.indexOf(oauth2)));
        String apiKey = ".scheme(\"apiKey\", java.util.List.of())";
        assertThat(generated, containsString(apiKey));
        assertThat(generated.lastIndexOf(apiKey), is(generated.indexOf(apiKey)));
    }

    @Test
    void concreteMethodSecurityRequirementOverridesConflictingInheritedRequirements() throws IOException {
        var result = compile("concrete-security-overrides-conflicting-inherited-requirements", """
                interface FirstApi {
                    @Http.GET
                    @OpenApi.SecuritySchemeRequirement("firstAuth")
                    String get();
                }

                interface SecondApi {
                    @Http.GET
                    @OpenApi.SecuritySchemeRequirement("secondAuth")
                    String get();
                }

                @RestServer.Endpoint
                @Service.Singleton
                @OpenApi.Endpoint
                @Http.Path("/concrete-security-override")
                class ConcreteSecurityEndpoint implements FirstApi, SecondApi {
                    @Override
                    @OpenApi.SecuritySchemeRequirement("methodAuth")
                    public String get() {
                        return "ok";
                    }
                }
                """);

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));
        String generated = generatedSource(result);
        assertThat(generated, containsString(".scheme(\"methodAuth\", java.util.List.of())"));
        assertThat(generated, not(containsString(".scheme(\"firstAuth\", java.util.List.of())")));
        assertThat(generated, not(containsString(".scheme(\"secondAuth\", java.util.List.of())")));
    }

    @Test
    void concreteStructuredSecurityRequirementOverridesConflictingInheritedRequirements() throws IOException {
        var result = compile("concrete-structured-security-overrides-conflicting-inherited-requirements", """
                interface FirstApi {
                    @Http.GET
                    @OpenApi.SecurityRequirement(@OpenApi.SecuritySchemeRequirement("firstAuth"))
                    String get();
                }

                interface SecondApi {
                    @Http.GET
                    @OpenApi.SecurityRequirement(@OpenApi.SecuritySchemeRequirement("secondAuth"))
                    String get();
                }

                @RestServer.Endpoint
                @Service.Singleton
                @OpenApi.Endpoint
                @Http.Path("/concrete-structured-security-override")
                class ConcreteSecurityEndpoint implements FirstApi, SecondApi {
                    @Override
                    @OpenApi.SecurityRequirement(@OpenApi.SecuritySchemeRequirement("methodAuth"))
                    public String get() {
                        return "ok";
                    }
                }
                """);

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));
        String generated = generatedSource(result);
        assertThat(generated, containsString(".scheme(\"methodAuth\", java.util.List.of())"));
        assertThat(generated, not(containsString(".scheme(\"firstAuth\", java.util.List.of())")));
        assertThat(generated, not(containsString(".scheme(\"secondAuth\", java.util.List.of())")));
    }

    @Test
    void hiddenAnnotationOnEndpointContractHidesImplementation() throws IOException {
        var result = compile("contract-hidden-openapi-endpoint", """
                @OpenApi.Endpoint
                @OpenApi.Hidden
                @RestServer.Endpoint
                interface HiddenOpenApiEndpointContract {
                    @Http.GET
                    String get();
                }

                @Service.Singleton
                @Http.Path("/contract-hidden")
                class ContractHiddenOpenApiEndpoint implements HiddenOpenApiEndpointContract {
                    @Override
                    public String get() {
                        return "ok";
                    }
                }
                """);

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));
        assertThat(Files.exists(result.sourceOutput()
                                        .resolve("com/example/ContractHiddenOpenApiEndpoint__OpenApiEndpointSource.java")),
                   is(false));
    }

    @Test
    void unannotatedEndpointDoesNotTriggerOpenApiGeneration() throws IOException {
        var result = compile("unannotated-endpoint", """
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/without-openapi")
                class EndpointWithoutOpenApi {
                    @Http.GET
                    String get() {
                        return "ok";
                    }
                }
                """);

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));
        assertThat(generatedSource(result), not(containsString("OpenApiEndpoint")));
    }

    @Test
    void documentOnlyAnnotationDoesNotTriggerEndpointGeneration() throws IOException {
        var result = compile("document-only-annotation-endpoint", """
                @OpenApi.Info(title = "Not a document", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/document-only")
                class EndpointWithDocumentOnlyAnnotation {
                    @Http.GET
                    String get() {
                        return "ok";
                    }
                }
                """);

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));
        assertThat(generatedSource(result), not(containsString("OpenApiEndpoint")));
    }

    @Test
    void documentOnlyTypePlacementDoesNotTriggerEndpointGeneration() throws IOException {
        var result = compile("document-only-type-placement-endpoint", """
                @OpenApi.Server("https://example.test")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/document-only-type-placement")
                class EndpointWithDocumentOnlyTypePlacement {
                    @Http.GET
                    String get() {
                        return "ok";
                    }
                }
                """);

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));
        assertThat(generatedSource(result), not(containsString("OpenApiEndpoint")));
    }

    @Test
    void methodLevelAnnotationTriggersEndpointGeneration() throws IOException {
        var result = compile("method-level-openapi-endpoint", """
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/method-level")
                class MethodLevelOpenApiEndpoint {
                    @Http.GET
                    @OpenApi.Server("https://example.test")
                    String get() {
                        return "ok";
                    }
                }
                """);

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));
        assertThat(generatedSource(result), containsString("document.path(\"/method-level\""));
    }

    @Test
    void parameterAnnotationTriggersEndpointGeneration() throws IOException {
        var result = compile("parameter-openapi-endpoint", """
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/parameter")
                class ParameterOpenApiEndpoint {
                    @Http.GET
                    String get(@OpenApi.Parameter("Search term")
                               @Http.QueryParam("q") String query) {
                        return query;
                    }
                }
                """);

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));
        assertThat(generatedSource(result), containsString("document.path(\"/parameter\""));
    }

    @Test
    void annotatedEndpointDoesNotOptInOtherEndpoint() throws IOException {
        var result = compile("mixed-openapi-endpoints", """
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/annotated")
                class AnnotatedEndpoint {
                    @Http.GET
                    @OpenApi.Operation
                    String get() {
                        return "ok";
                    }
                }

                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/unannotated")
                class UnannotatedEndpoint {
                    @Http.GET
                    String get() {
                        return "ok";
                    }
                }
                """);

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));
        assertThat(Files.exists(result.sourceOutput()
                                        .resolve("com/example/AnnotatedEndpoint__OpenApiEndpointSource.java")),
                   is(true));
        assertThat(Files.exists(result.sourceOutput()
                                        .resolve("com/example/UnannotatedEndpoint__OpenApiEndpointSource.java")),
                   is(false));
    }

    @Test
    void restEndpointCompilesWithoutOpenApiOnClasspath() throws IOException {
        var result = compile("rest-endpoint-without-openapi", """
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/without-openapi")
                class EndpointWithoutOpenApi {
                    @Http.GET
                    String get() {
                        return "ok";
                    }
                }
                """, CLASSPATH.stream()
                        .filter(it -> it != OpenApi.class)
                        .toList());

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));
        String generated = generatedSource(result);
        assertThat(generated, containsString("EndpointWithoutOpenApi__HttpFeature"));
        assertThat(generated, not(containsString("OpenApiEndpoint")));
    }

    @Test
    void interfaceEndpointIsNotDocumented() throws IOException {
        var result = compile("openapi-interface-endpoint", """
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

                @RestServer.Endpoint
                @Http.Path("/ghost")
                interface GhostEndpoint {
                    @Http.GET
                    String get();
                }
                """);

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));
        String generated = generatedSource(result);
        assertThat(generated, containsString("document.path(\"/valid\""));
        assertThat(generated, not(containsString("document.path(\"/ghost\"")));
    }

    @Test
    void repeatedHttpPathParameterCannotBeRepresented() {
        var result = compile("openapi-repeated-http-path-parameter", """
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/items")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    @Http.Path("/{id}/{id}")
                    String get(@Http.PathParam("id") String id) {
                        return id;
                    }
                }
                """);

        assertCompilationFails(result,
                               "@Http.Path on com.example.InvalidOpenApiEndpoint.get",
                               "cannot be represented as an OpenAPI path: /items/{id}/{id}",
                               "path parameter 'id' appears more than once");
    }

    @Test
    void unsupportedHttpPathRequiresOpenApiPathOverride() {
        var result = compile("openapi-unsupported-http-path", """
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    @Http.Path("/files/{+}")
                    String get() {
                        return "ok";
                    }
                }
                """);

        assertCompilationFails(result,
                               "@Http.Path on com.example.InvalidOpenApiEndpoint.get",
                               "cannot be represented as an OpenAPI path: /invalid/files/{+}",
                               "Use @OpenApi.Operation(path = ...) to provide the OpenAPI path");
    }

    @Test
    void operationPathOverrideCannotRepeatPathParameter() {
        var result = compile("openapi-repeated-operation-path-parameter", """
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/items")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    @Http.Path("/{id}")
                    @OpenApi.Operation(path = "/items/{id}/{id}")
                    String get(@Http.PathParam("id") String id) {
                        return id;
                    }
                }
                """);

        assertCompilationFails(result,
                               "@OpenApi.Operation path on com.example.InvalidOpenApiEndpoint.get",
                               "must be an OpenAPI path template: /items/{id}/{id}",
                               "path parameter 'id' appears more than once");
    }

    @Test
    void operationPathOverrideMustBeOpenApiPathTemplate() {
        var result = compile("openapi-invalid-operation-path", """
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    @Http.Path("/files/{+}")
                    @OpenApi.Operation(path = "/invalid/files/{id:\\\\d+}")
                    String get() {
                        return "ok";
                    }
                }
                """);

        assertCompilationFails(result,
                               "@OpenApi.Operation path on com.example.InvalidOpenApiEndpoint.get",
                               "must be an OpenAPI path template: /invalid/files/{id:\\d+}",
                               "path parameters cannot define regex constraints");
    }

    @Test
    void operationPathOverrideCannotContainQuery() {
        var result = compile("openapi-operation-path-query", """
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/items")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    @OpenApi.Operation(path = "/items?mode=full")
                    String get() {
                        return "ok";
                    }
                }
                """);

        assertCompilationFails(result,
                               "@OpenApi.Operation path on com.example.InvalidOpenApiEndpoint.get",
                               "must be an OpenAPI path template: /items?mode=full",
                               "query and fragment characters are not valid in OpenAPI path templates");
    }

    @Test
    void operationPathOverrideCannotContainFragment() {
        var result = compile("openapi-operation-path-fragment", """
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/items")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    @OpenApi.Operation(path = "/items#details")
                    String get() {
                        return "ok";
                    }
                }
                """);

        assertCompilationFails(result,
                               "@OpenApi.Operation path on com.example.InvalidOpenApiEndpoint.get",
                               "must be an OpenAPI path template: /items#details",
                               "query and fragment characters are not valid in OpenAPI path templates");
    }

    @Test
    void operationPathParameterNameCanContainQuery() throws IOException {
        var result = compile("openapi-operation-path-parameter-query", """
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/items")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    @Http.Path("/{id?mode}")
                    @OpenApi.Operation(path = "/items/{id?mode}")
                    String get(@Http.PathParam("id?mode") String id) {
                        return id;
                    }
                }
                """);

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));
        assertThat(generatedSource(result), containsString("document.path(\"/items/{id?mode}\""));
    }

    @Test
    void operationPathParameterNameCanContainFragment() throws IOException {
        var result = compile("openapi-operation-path-parameter-fragment", """
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/items")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    @Http.Path("/{id#fragment}")
                    @OpenApi.Operation(path = "/items/{id#fragment}")
                    String get(@Http.PathParam("id#fragment") String id) {
                        return id;
                    }
                }
                """);

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));
        assertThat(generatedSource(result), containsString("document.path(\"/items/{id#fragment}\""));
    }

    @Test
    void operationPathOverrideCannotAddPathParameter() {
        var result = compile("openapi-extra-operation-path-parameter", """
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    @Http.Path("/files")
                    @OpenApi.Operation(path = "/invalid/files/{id}")
                    String get() {
                        return "ok";
                    }
                }
                """);

        assertCompilationFails(result,
                               "@OpenApi.Operation path on com.example.InvalidOpenApiEndpoint.get",
                               "must declare the same path parameters as the generated route",
                               "generated route parameters: []",
                               "OpenAPI path parameters: [id]");
    }

    @Test
    void operationPathOverrideMustUseGeneratedPathParameterNames() {
        var result = compile("openapi-renamed-operation-path-parameter", """
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    @Http.Path("/files/{name}")
                    @OpenApi.Operation(path = "/invalid/files/{id}")
                    String get(@Http.PathParam("name") String name) {
                        return name;
                    }
                }
                """);

        assertCompilationFails(result,
                               "@OpenApi.Operation path on com.example.InvalidOpenApiEndpoint.get",
                               "must declare the same path parameters as the generated route",
                               "generated route parameters: [name]",
                               "OpenAPI path parameters: [id]");
    }

    private TestCompiler.Result compile(String workDir, String source) {
        return compile(workDir, source, CLASSPATH);
    }

    private TestCompiler.Result compile(String workDir, String source, List<Class<?>> classpath) {
        return TestCompiler.builder()
                .currentRelease()
                .procOnly()
                .addClasspath(classpath)
                .addProcessor(AptProcessor::new)
                .workDir(workDirRoot.resolve(workDir))
                .addSource("InvalidOpenApiEndpoint.java", """
                        package com.example;

                        import io.helidon.http.Http;
                        import io.helidon.service.registry.Service;
                        import io.helidon.webserver.http.RestServer;

                        %s
                        %s
                        """.formatted(classpath.contains(OpenApi.class)
                                                 ? "import io.helidon.openapi.OpenApi;"
                                                 : "",
                                         source))
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
