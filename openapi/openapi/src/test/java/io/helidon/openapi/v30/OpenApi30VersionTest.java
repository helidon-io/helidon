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

package io.helidon.openapi.v30;

import java.util.List;
import java.util.Map;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.openapi.OpenApiDocument;
import io.helidon.openapi.OpenApiDocumentContext;
import io.helidon.openapi.OpenApiGeneratedMode;
import io.helidon.openapi.spi.OpenApiVersion;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenApi30VersionTest {
    @Test
    void preservesEmptyRequiredNames() {
        OpenApi30Version version = OpenApi30Version.create();
        OpenApiDocumentContext context = context(version);
        OpenApiDocument document = version.parse(context,
                                                 """
                                                 openapi: 3.0.4
                                                 info:
                                                   title: API
                                                   version: "1"
                                                   license:
                                                     name: ""
                                                 tags:
                                                   - name: " "
                                                 paths:
                                                   /items:
                                                     get:
                                                       parameters:
                                                         - name: ""
                                                           in: query
                                                           schema: {type: string}
                                                       responses:
                                                         "200": {description: OK}
                                                 """,
                                                 MediaTypes.APPLICATION_OPENAPI_YAML);

        Map<?, ?> rendered = new Yaml().load(version.render(context, document));
        Map<?, ?> info = (Map<?, ?>) rendered.get("info");
        assertThat(((Map<?, ?>) info.get("license")).get("name"), is(""));
        assertThat(((Map<?, ?>) ((List<?>) rendered.get("tags")).getFirst()).get("name"), is(" "));
        Map<?, ?> operation = (Map<?, ?>) ((Map<?, ?>) ((Map<?, ?>) rendered.get("paths")).get("/items")).get("get");
        assertThat(((Map<?, ?>) ((List<?>) operation.get("parameters")).getFirst()).get("name"), is(""));
    }

    @Test
    void preservesEmptyInfoStrings() {
        OpenApi30Version version = OpenApi30Version.create();
        OpenApiDocumentContext context = context(version);
        OpenApiDocument document = version.parse(context,
                                                 """
                                                 openapi: 3.0.4
                                                 info:
                                                   title: ""
                                                   version: " "
                                                 paths: {}
                                                 """,
                                                 MediaTypes.APPLICATION_OPENAPI_YAML);

        assertThat(document.info().orElseThrow().title(), is(""));
        assertThat(document.info().orElseThrow().version(), is(" "));

        Map<?, ?> renderedInfo = (Map<?, ?>) new Yaml().<Map<?, ?>>load(version.render(context, document)).get("info");
        assertThat(renderedInfo.get("title"), is(""));
        assertThat(renderedInfo.get("version"), is(" "));
    }

    @Test
    void preservesEmptyRequiredUriReferences() {
        OpenApi30Version version = OpenApi30Version.create();
        OpenApiDocumentContext context = context(version);
        OpenApiDocument document = version.parse(context,
                                                 """
                                                 openapi: 3.0.4
                                                 info: {title: API, version: "1"}
                                                 paths: {}
                                                 components:
                                                   securitySchemes:
                                                     oauth:
                                                       type: oauth2
                                                       flows:
                                                         authorizationCode:
                                                           authorizationUrl: ""
                                                           tokenUrl: ""
                                                           scopes: {}
                                                     openId:
                                                       type: openIdConnect
                                                       openIdConnectUrl: ""
                                                 """,
                                                 MediaTypes.APPLICATION_OPENAPI_YAML);

        Map<?, ?> rendered = new Yaml().load(version.render(context, document));
        Map<?, ?> securitySchemes = (Map<?, ?>) ((Map<?, ?>) rendered.get("components")).get("securitySchemes");
        Map<?, ?> oauth = (Map<?, ?>) securitySchemes.get("oauth");
        Map<?, ?> authorizationCode = (Map<?, ?>) ((Map<?, ?>) oauth.get("flows")).get("authorizationCode");
        assertThat(authorizationCode.get("authorizationUrl"), is(""));
        assertThat(authorizationCode.get("tokenUrl"), is(""));
        assertThat(((Map<?, ?>) securitySchemes.get("openId")).get("openIdConnectUrl"), is(""));
    }

    @Test
    void requiresInfoWhenRendering() {
        OpenApi30Version version = OpenApi30Version.create();
        OpenApiDocument withoutInfo = OpenApiDocument.builder()
                .paths(Map.of())
                .build();

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> version.render(context(version), withoutInfo));
        assertThat(thrown.getMessage(), containsString("requires Info metadata"));
    }

    @Test
    void requiresPathsWhenRendering() {
        OpenApi30Version version = OpenApi30Version.create();
        OpenApiDocumentContext context = context(version);
        OpenApiDocument infoOnly = OpenApiDocument.builder()
                .info("Generated API", "1.0.0")
                .build();

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                                                    () -> version.render(context, infoOnly));
        assertThat(thrown.getMessage(), containsString("requires a paths field"));

        OpenApiDocument emptyPaths = version.parse(context,
                                                   """
                                                   openapi: 3.0.3
                                                   info:
                                                     title: Generated API
                                                     version: 1.0.0
                                                   paths: {}
                                                   """,
                                                   MediaTypes.APPLICATION_OPENAPI_YAML);
        Map<?, ?> rendered = new Yaml().load(version.render(context, emptyPaths));
        assertThat(rendered.containsKey("paths"), is(true));
    }

    @Test
    void validatesConfiguredVersion() {
        assertThat(OpenApi30Version.builder().version("3.0.99").build().version(), is("3.0.99"));
        assertThat(OpenApi30Version.builder().version("3.0.4-rc1").build().version(), is("3.0.4-rc1"));

        for (String invalidVersion : List.of("3.0", "3.0.", "3.0.not-a-version", "3.0.1-", "3.0.1.0", "3.1.0")) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                                                       () -> OpenApi30Version.builder()
                                                               .version(invalidVersion)
                                                               .build(),
                                                       invalidVersion);
            assertThat(invalidVersion, ex.getMessage(), containsString("3.0"));
            assertThat(invalidVersion, ex.getMessage(), containsString(invalidVersion));
        }
    }

    @Test
    void rejectsRecursiveYamlCollectionAliases() {
        OpenApi30Version version = OpenApi30Version.create();

        YAMLException thrown = assertThrows(YAMLException.class,
                                            () -> version.parse(context(version),
                                                                """
                                                                openapi: 3.0.4
                                                                info: {title: API, version: 1}
                                                                paths: {}
                                                                x-cycle: &cycle {self: *cycle}
                                                                """,
                                                                MediaTypes.APPLICATION_OPENAPI_YAML));
        assertThat(thrown.getMessage(), containsString("Recursive YAML collection alias"));
    }

    @Test
    void acceptsSharedNonRecursiveYamlCollectionAliases() {
        OpenApi30Version version = OpenApi30Version.create();
        OpenApiDocument document = version.parse(context(version),
                                                 """
                                                 openapi: 3.0.4
                                                 info: {title: API, version: "1"}
                                                 paths: {}
                                                 x-shared: &shared {value: shared}
                                                 x-first: *shared
                                                 x-second: *shared
                                                 """,
                                                 MediaTypes.APPLICATION_OPENAPI_YAML);

        Map<?, ?> rendered = new Yaml().load(version.render(context(version), document));
        assertThat(rendered.get("x-first"), is(Map.of("value", "shared")));
        assertThat(rendered.get("x-second"), is(Map.of("value", "shared")));
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
}
