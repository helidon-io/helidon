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
