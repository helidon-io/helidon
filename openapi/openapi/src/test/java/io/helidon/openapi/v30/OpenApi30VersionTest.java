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

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenApi30VersionTest {
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
