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

package io.helidon.openapi;

import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenApiDocumentTest {
    @Test
    void queryStringParameterRequiresNonblankName() {
        IllegalStateException missingName = assertThrows(IllegalStateException.class,
                                                         () -> OpenApiDocument.Parameter.builder()
                                                                 .in("querystring")
                                                                 .build());
        assertThat(missingName.getMessage(), is("OpenAPI Parameter requires name"));

        IllegalStateException blankName = assertThrows(IllegalStateException.class,
                                                       () -> OpenApiDocument.Parameter.builder()
                                                               .name(" ")
                                                               .in("querystring")
                                                               .build());
        assertThat(blankName.getMessage(), is("OpenAPI Parameter requires name"));
    }

    @Test
    void rejectsFixedMethodAsAdditionalOperation() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                                                       () -> OpenApiDocument.PathItem.builder()
                                                               .additionalOperation("POST",
                                                                                    OpenApiDocument.Operation.builder()
                                                                                            .build()));

        assertThat(thrown.getMessage(), containsString("fixed-field HTTP method: POST"));
    }

    @Test
    void normalizesUppercaseFixedOperation() {
        OpenApiDocument.PathItem pathItem = OpenApiDocument.PathItem.builder()
                .operation("POST", OpenApiDocument.Operation.builder().build())
                .build();

        assertThat(pathItem.operations().keySet(), is(Set.of("post")));
        assertThat(pathItem.additionalOperations().isEmpty(), is(true));
    }

    @Test
    void preservesCaseSensitiveCustomAdditionalOperations() {
        OpenApiDocument.Operation operation = OpenApiDocument.Operation.builder().build();
        OpenApiDocument.PathItem pathItem = OpenApiDocument.PathItem.builder()
                .additionalOperation("post", operation)
                .additionalOperation("PoSt", operation)
                .build();

        assertThat(pathItem.additionalOperations().keySet(), is(Set.of("post", "PoSt")));
    }

    @Test
    void preservesCaseSensitiveCustomOperations() {
        OpenApiDocument.Operation operation = OpenApiDocument.Operation.builder().build();
        OpenApiDocument.PathItem pathItem = OpenApiDocument.PathItem.builder()
                .operation("post", operation)
                .operation("PoSt", operation)
                .build();

        assertThat(pathItem.operations().isEmpty(), is(true));
        assertThat(pathItem.additionalOperations().keySet(), is(Set.of("post", "PoSt")));
    }
}
