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

package io.helidon.openapi.v32;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.openapi.OpenApiDocument;
import io.helidon.openapi.OpenApiDocumentContext;
import io.helidon.openapi.OpenApiGeneratedMode;
import io.helidon.openapi.spi.OpenApiVersion;
import io.helidon.openapi.spi.OpenApiVersionProvider;
import io.helidon.openapi.v30.OpenApi30Version;
import io.helidon.openapi.v31.OpenApi31Version;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenApi32VersionTest {
    @Test
    void parsesAndRendersOpenApi32Fields() {
        OpenApi32Version version = OpenApi32Version.create();
        OpenApiDocumentContext context = context(version);
        OpenApiDocument document = version.parse(context, static32(), MediaTypes.APPLICATION_OPENAPI_YAML);

        Map<String, Object> rendered = parse(version.render(context, document));

        assertThat(rendered.get("openapi"), is("3.2.0"));
        assertThat(rendered.get("$self"), is("https://example.com/openapi/static-3.2.yaml"));

        Map<?, ?> server = (Map<?, ?>) ((List<?>) rendered.get("servers")).getFirst();
        assertThat(server.get("name"), is("local"));

        Map<?, ?> secondTag = (Map<?, ?>) ((List<?>) rendered.get("tags")).get(1);
        assertThat(secondTag.get("summary"), is("Internal"));
        assertThat(secondTag.get("parent"), is("static"));
        assertThat(secondTag.get("kind"), is("badge"));

        Map<?, ?> staticPath = map(map(rendered, "paths"), "/static/{id}");
        assertThat(staticPath.containsKey("query"), is(true));
        assertThat(staticPath.containsKey("additionalOperations"), is(true));
        assertThat(map(map(map(staticPath, "get"), "responses"), "200").get("summary"), is("Static item"));

        Map<?, ?> queryResponse = map(map(staticPath, "query"), "responses");
        assertThat(map(queryResponse, "200").get("summary"), is("Static query stream"));
        Map<?, ?> queryContent = map(map(queryResponse, "200"), "content");
        assertThat(map(queryContent, "application/jsonl").containsKey("itemSchema"), is(true));

        Map<?, ?> search = map(map(map(rendered, "paths"), "/search"), "get");
        Map<?, ?> queryString = (Map<?, ?>) ((List<?>) search.get("parameters")).getFirst();
        assertThat(queryString.get("in"), is("querystring"));
        Map<?, ?> formExample = map(map(map(map(queryString, "content"), "application/x-www-form-urlencoded"), "examples"),
                                    "form");
        assertThat(formExample.containsKey("dataValue"), is(true));
        assertThat(formExample.get("serializedValue"), is("q=static+item&active=true"));

        Map<?, ?> securityScheme = map(map(map(rendered, "components"), "securitySchemes"), "bearerAuth");
        assertThat(securityScheme.get("deprecated"), is(true));
        Map<?, ?> oauthFlows = map(map(map(map(rendered, "components"), "securitySchemes"), "oauthDevice"), "flows");
        assertThat(oauthFlows.containsKey("deviceAuthorization"), is(true));
    }

    @Test
    void rendersOpenApi32StaticDocumentAsOpenApi31() {
        OpenApi32Version parseVersion = OpenApi32Version.create();
        OpenApiDocument document = parseVersion.parse(context(parseVersion),
                                                      static32WithoutDeviceAuthorization(),
                                                      MediaTypes.APPLICATION_OPENAPI_YAML);

        OpenApi31Version renderVersion = OpenApi31Version.create();
        Map<String, Object> rendered = parse(renderVersion.render(context(renderVersion), document));

        assertThat(rendered.get("openapi"), is("3.1.1"));
        assertThat(rendered.containsKey("$self"), is(false));
        assertThat(rendered.get("jsonSchemaDialect"), is("https://spec.openapis.org/oas/3.1/dialect/base"));

        Map<?, ?> server = (Map<?, ?>) ((List<?>) rendered.get("servers")).getFirst();
        assertThat(server.containsKey("name"), is(false));

        Map<?, ?> secondTag = (Map<?, ?>) ((List<?>) rendered.get("tags")).get(1);
        assertThat(secondTag.containsKey("summary"), is(false));
        assertThat(secondTag.containsKey("parent"), is(false));
        assertThat(secondTag.containsKey("kind"), is(false));

        Map<?, ?> staticPath = map(map(rendered, "paths"), "/static/{id}");
        assertThat(staticPath.containsKey("query"), is(false));
        assertThat(staticPath.containsKey("additionalOperations"), is(false));
        assertThat(map(map(map(staticPath, "get"), "responses"), "200").containsKey("summary"), is(false));

        Map<?, ?> search = map(map(map(rendered, "paths"), "/search"), "get");
        assertThat(search.get("parameters"), is(List.of()));

        Map<?, ?> securityScheme = map(map(map(rendered, "components"), "securitySchemes"), "bearerAuth");
        assertThat(securityScheme.containsKey("deprecated"), is(false));
        Map<?, ?> oauthFlows = map(map(map(map(rendered, "components"), "securitySchemes"), "oauthDevice"), "flows");
        assertThat(oauthFlows.containsKey("deviceAuthorization"), is(false));
        assertThat(oauthFlows.containsKey("authorizationCode"), is(true));
    }

    @Test
    void rendersOpenApi32StaticDocumentAsOpenApi30() {
        OpenApi32Version parseVersion = OpenApi32Version.create();
        OpenApiDocument document = parseVersion.parse(context(parseVersion),
                                                      static32WithoutDeviceAuthorization(),
                                                      MediaTypes.APPLICATION_OPENAPI_YAML);

        OpenApi30Version renderVersion = OpenApi30Version.create();
        Map<String, Object> rendered = parse(renderVersion.render(context(renderVersion), document));

        assertThat(rendered.get("openapi"), is("3.0.3"));
        assertThat(rendered.containsKey("$self"), is(false));
        assertThat(rendered.containsKey("jsonSchemaDialect"), is(false));

        Map<?, ?> staticPath = map(map(rendered, "paths"), "/static/{id}");
        assertThat(staticPath.containsKey("query"), is(false));
        assertThat(staticPath.containsKey("additionalOperations"), is(false));
        assertThat(map(map(map(staticPath, "get"), "responses"), "200").containsKey("summary"), is(false));

        Map<?, ?> search = map(map(map(rendered, "paths"), "/search"), "get");
        assertThat(search.get("parameters"), is(List.of()));

        Map<String, Object> status = schemaProperty(rendered, "StaticItem", "status");
        assertThat(status.get("type"), is("string"));
        assertThat(status.get("nullable"), is(true));
        assertThat(((List<?>) status.get("enum")).contains(null), is(false));

        Map<String, Object> mode = schemaProperty(rendered, "StaticItem", "mode");
        assertThat(mode.containsKey("const"), is(false));
        assertThat(mode.get("enum"), is(List.of("modern")));

        assertThat(schemaPropertyValue(rendered, "StaticItem", "payload"), is(Map.of()));

        Map<?, ?> securitySchemes = map(map(rendered, "components"), "securitySchemes");
        Map<?, ?> oauthFlows = map(map(securitySchemes, "oauthDevice"), "flows");
        assertThat(oauthFlows.containsKey("deviceAuthorization"), is(false));
        assertThat(oauthFlows.containsKey("authorizationCode"), is(true));
    }

    @Test
    void rejectsOpenApi32DeviceAuthorizationWhenRenderingOpenApi31() {
        OpenApi32Version parseVersion = OpenApi32Version.create();
        OpenApiDocument document = parseVersion.parse(context(parseVersion), static32(), MediaTypes.APPLICATION_OPENAPI_YAML);

        OpenApi31Version renderVersion = OpenApi31Version.create();
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                                                   () -> renderVersion.render(context(renderVersion), document));

        assertThat(thrown.getMessage(), containsString("deviceAuthorization"));
    }

    @Test
    void rejectsOpenApi32DeviceAuthorizationWhenRenderingOpenApi30() {
        OpenApi32Version parseVersion = OpenApi32Version.create();
        OpenApiDocument document = parseVersion.parse(context(parseVersion), static32(), MediaTypes.APPLICATION_OPENAPI_YAML);

        OpenApi30Version renderVersion = OpenApi30Version.create();
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                                                   () -> renderVersion.render(context(renderVersion), document));

        assertThat(thrown.getMessage(), containsString("deviceAuthorization"));
    }

    @Test
    void arbitraryHttpMethodUsesAdditionalOperations() {
        OpenApiDocument document = OpenApiDocument.builder()
                .info("Generated API", "1.0.0")
                .path("/static/{id}",
                      path -> path.operation("COPY",
                                             operation -> operation.operationId("copyStatic")
                                                     .response("200", "Copied.")))
                .build();

        OpenApi32Version version32 = OpenApi32Version.create();
        Map<String, Object> rendered32 = parse(version32.render(context(version32), document));
        Map<?, ?> path32 = map(map(rendered32, "paths"), "/static/{id}");
        assertThat(path32.containsKey("copy"), is(false));
        assertThat(map(path32, "additionalOperations").containsKey("COPY"), is(true));

        OpenApi30Version version30 = OpenApi30Version.create();
        Map<String, Object> rendered30 = parse(version30.render(context(version30), document));
        Map<?, ?> path30 = map(map(rendered30, "paths"), "/static/{id}");
        assertThat(path30.containsKey("copy"), is(false));
        assertThat(path30.containsKey("additionalOperations"), is(false));
    }

    @Test
    void parsesOnlyOpenApi32Documents() {
        OpenApi32Version version = OpenApi32Version.create();

        assertThrows(IllegalStateException.class,
                     () -> version.parse(context(version),
                                         """
                                         openapi: 3.1.0
                                         info:
                                           title: Static API
                                           version: 1.0.0
                                         """,
                                         MediaTypes.APPLICATION_OPENAPI_YAML));
    }

    @Test
    void rejectsNullArguments() {
        OpenApi32Version version = OpenApi32Version.create();
        OpenApiDocumentContext context = context(version);
        OpenApiDocument document = OpenApiDocument.builder().build();

        assertThrows(NullPointerException.class, () -> OpenApi32Version.create((OpenApi32VersionConfig) null));
        assertThrows(NullPointerException.class, () -> version.parse(null, "", MediaTypes.APPLICATION_OPENAPI_YAML));
        assertThrows(NullPointerException.class, () -> version.parse(context, null, MediaTypes.APPLICATION_OPENAPI_YAML));
        assertThrows(NullPointerException.class, () -> version.parse(context, "", null));
        assertThrows(NullPointerException.class, () -> version.render(null, document));
        assertThrows(NullPointerException.class, () -> version.render(context, null));
    }

    @Test
    void validatesConfiguredVersion() {
        assertThat(OpenApi32Version.builder().version("3.2.99").build().version(), is("3.2.99"));

        for (String invalidVersion : List.of("3.2", "3.2.", "3.2.not-a-version", "3.2.1.0", "3.1.0")) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                                                       () -> OpenApi32Version.builder()
                                                               .version(invalidVersion)
                                                               .build(),
                                                       invalidVersion);
            assertThat(invalidVersion, ex.getMessage(), containsString("3.2"));
            assertThat(invalidVersion, ex.getMessage(), containsString(invalidVersion));
        }
    }

    @Test
    void serviceLoaderDiscoversProvider() {
        boolean found = ServiceLoader.load(OpenApiVersionProvider.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .anyMatch(provider -> "3.2".equals(provider.configKey()));

        assertThat(found, is(true));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(String content) {
        return new Yaml().load(content);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> schemaProperty(Map<String, Object> document, String schemaName, String propertyName) {
        return (Map<String, Object>) schemaPropertyValue(document, schemaName, propertyName);
    }

    private static Object schemaPropertyValue(Map<String, Object> document, String schemaName, String propertyName) {
        return map(map(map(map(document, "components"), "schemas"), schemaName), "properties")
                .get(propertyName);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Map<?, ?> map, String name) {
        return (Map<String, Object>) map.get(name);
    }

    private static OpenApiDocumentContext context(OpenApiVersion version) {
        return new TestOpenApiDocumentContext(version);
    }

    private record TestOpenApiDocumentContext(OpenApiVersion openApiVersion) implements OpenApiDocumentContext {
        @Override
        public String featureName() {
            return "openapi";
        }

        @Override
        public String webContext() {
            return "/openapi";
        }

        @Override
        public String listener() {
            return "default";
        }

        @Override
        public OpenApiGeneratedMode generatedMode() {
            return OpenApiGeneratedMode.STATIC_ONLY;
        }
    }

    private static String static32() {
        try (InputStream is = OpenApi32VersionTest.class.getResourceAsStream("/static-3.2.yaml")) {
            if (is == null) {
                throw new IllegalArgumentException("Resource not found: static-3.2.yaml");
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static String static32WithoutDeviceAuthorization() {
        Map<String, Object> document = parse(static32());
        Map<String, Object> securityScheme = map(map(map(document, "components"), "securitySchemes"), "oauthDevice");
        map(securityScheme, "flows").remove("deviceAuthorization");
        return new Yaml().dump(document);
    }
}
