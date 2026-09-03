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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.json.JsonObject;
import io.helidon.json.JsonString;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenApiDocumentTest {
    @Test
    void allowsEmptyInfoStrings() {
        OpenApiDocument.Info info = OpenApiDocument.Info.builder()
                .title("")
                .version(" ")
                .build();

        assertThat(info.title(), is(""));
        assertThat(info.version(), is(" "));
    }

    @Test
    void allowsEmptyRequiredNames() {
        assertDoesNotThrow(() -> OpenApiDocument.License.builder().name("").build());
        assertDoesNotThrow(() -> OpenApiDocument.Tag.builder().name(" ").build());
        assertDoesNotThrow(() -> OpenApiDocument.Parameter.builder().name("").in("query").build());
    }

    @Test
    void buildersAllowPartialObjects() {
        assertDoesNotThrow(() -> OpenApiDocument.Info.builder().build());
        assertDoesNotThrow(() -> OpenApiDocument.License.builder().build());
        assertDoesNotThrow(() -> OpenApiDocument.ExternalDocs.builder().build());
        assertDoesNotThrow(() -> OpenApiDocument.Server.builder().build());
        assertDoesNotThrow(() -> OpenApiDocument.ServerVariable.builder().build());
        assertDoesNotThrow(() -> OpenApiDocument.Tag.builder().build());
        assertDoesNotThrow(() -> OpenApiDocument.Parameter.builder().build());
        assertDoesNotThrow(() -> OpenApiDocument.RequestBody.builder().build());
        assertDoesNotThrow(() -> OpenApiDocument.SecurityScheme.builder().build());
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

    @Test
    void validatesHttpMethodTokens() {
        OpenApiDocument.Operation operation = OpenApiDocument.Operation.builder().build();
        List<String> invalidMethods = List.of("", "BAD METHOD", "BAD:METHOD", "BAD\u0007METHOD", "M\u00c9THOD");

        for (String method : invalidMethods) {
            assertThrows(IllegalArgumentException.class,
                         () -> OpenApiDocument.PathItem.builder().operation(method, operation),
                         "operation(Operation) should reject invalid method");
            assertThrows(IllegalArgumentException.class,
                         () -> OpenApiDocument.PathItem.builder().operation(method, _ -> { }),
                         "operation(Consumer) should reject invalid method");
            assertThrows(IllegalArgumentException.class,
                         () -> OpenApiDocument.PathItem.builder().additionalOperation(method, operation),
                         "additionalOperation(Operation) should reject invalid method");
            assertThrows(IllegalArgumentException.class,
                         () -> OpenApiDocument.PathItem.builder().additionalOperation(method, _ -> { }),
                         "additionalOperation(Consumer) should reject invalid method");
        }

        OpenApiDocument.PathItem pathItem = OpenApiDocument.PathItem.builder()
                .operation("M-SEARCH", operation)
                .additionalOperation("!#$%&'*+-.^_`|~", operation)
                .build();

        assertThat(pathItem.additionalOperations().keySet(), is(Set.of("M-SEARCH", "!#$%&'*+-.^_`|~")));
    }

    @Test
    void indexesNamedTagsInstalledByMerge() {
        AtomicInteger iteratorCalls = new AtomicInteger();
        List<Object> initialTags = new ArrayList<>() {
            @Override
            public Iterator<Object> iterator() {
                iteratorCalls.incrementAndGet();
                return super.iterator();
            }
        };
        initialTags.add(Map.of("name", "first"));
        Map<String, Object> initialDocument = new LinkedHashMap<>();
        initialDocument.put("tags", initialTags);

        OpenApiDocument.Builder builder = OpenApiDocument.builder().mergeNode(initialDocument);
        iteratorCalls.set(0);
        Map<String, Object> nextDocument = new LinkedHashMap<>();
        nextDocument.put("tags", List.of(Map.of("name", "second")));
        builder.mergeNode(nextDocument);

        assertThat("Merging a named tag should not scan tags already indexed", iteratorCalls.get(), is(0));
        assertThat(builder.build().tags().stream().map(OpenApiDocument.Tag::name).toList(),
                   is(List.of("first", "second")));
    }

    @Test
    void deduplicatesNamedTagsInstalledByFirstMerge() {
        Map<String, Object> first = Map.of("name", "first", "description", "First");
        Map<String, Object> initialDocument = new LinkedHashMap<>();
        initialDocument.put("tags", List.of(first,
                                            Map.of("name", "first", "description", "First"),
                                            Map.of("name", "second")));

        OpenApiDocument document = OpenApiDocument.builder()
                .mergeNode(initialDocument)
                .build();

        assertThat(document.tags().stream().map(OpenApiDocument.Tag::name).toList(),
                   is(List.of("first", "second")));
        assertThat(document.tags().getFirst().description().orElseThrow(), is("First"));
    }

    @Test
    void rejectsConflictingNamedTagsInstalledByFirstMerge() {
        Map<String, Object> initialDocument = new LinkedHashMap<>();
        initialDocument.put("tags", List.of(Map.of("name", "first", "description", "First"),
                                            Map.of("name", "first", "description", "Conflicting")));

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                                                     () -> OpenApiDocument.builder().mergeNode(initialDocument));

        assertThat(thrown.getMessage(), is("Conflicting OpenAPI tag at tags.first"));
    }

    @Test
    void deduplicatesDirectNamedTagsAndRejectsConflicts() {
        OpenApiDocument.Tag first = OpenApiDocument.Tag.builder()
                .name("first")
                .description("First")
                .build();
        OpenApiDocument.Builder builder = OpenApiDocument.builder()
                .tag(first)
                .tag(OpenApiDocument.Tag.builder()
                             .name("first")
                             .description("First")
                             .build());

        assertThat(builder.build().tags().size(), is(1));

        OpenApiDocument.Tag conflicting = OpenApiDocument.Tag.builder()
                .name("first")
                .description("Conflicting")
                .build();
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> builder.tag(conflicting));

        assertThat(thrown.getMessage(), is("Conflicting OpenAPI tag at tags.first"));
    }

    @Test
    void indexesTagsAddedDirectlyToBuilder() {
        OpenApiDocument.Tag first = OpenApiDocument.Tag.builder()
                .name("first")
                .description("First")
                .build();
        OpenApiDocument.Builder builder = OpenApiDocument.builder()
                .tag(first)
                .merge(OpenApiDocument.builder().tag(first).build());

        assertThat(builder.build().tags().size(), is(1));

        OpenApiDocument conflicting = OpenApiDocument.builder()
                .tag("first", "Conflicting")
                .build();
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> builder.merge(conflicting));
        assertThat(thrown.getMessage(), is("Conflicting OpenAPI tag at tags.first"));
    }

    @Test
    void mergesMatchingPathExtensionsAndRejectsConflicts() {
        OpenApiDocument shared = OpenApiDocument.builder()
                .pathExtension("x-routing", JsonString.create("shared"))
                .build();
        OpenApiDocument.Builder builder = OpenApiDocument.builder()
                .merge(shared)
                .merge(shared);
        OpenApiDocument conflicting = OpenApiDocument.builder()
                .pathExtension("x-routing", JsonString.create("conflicting"))
                .build();

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> builder.merge(conflicting));

        assertThat(thrown.getMessage(), is("Conflicting OpenAPI document value at paths.x-routing"));
    }

    @Test
    void mergesMatchingObjectPathExtensionsAndRejectsConflicts() {
        OpenApiDocument shared = OpenApiDocument.builder()
                .pathExtension("x-routing", JsonObject.builder().set("get", "shared").build())
                .build();
        OpenApiDocument.Builder builder = OpenApiDocument.builder().merge(shared);

        assertDoesNotThrow(() -> builder.merge(shared));

        OpenApiDocument compatible = OpenApiDocument.builder()
                .pathExtension("x-routing", JsonObject.builder().set("post", "compatible").build())
                .build();
        assertDoesNotThrow(() -> builder.merge(compatible));

        OpenApiDocument conflicting = OpenApiDocument.builder()
                .pathExtension("x-routing", JsonObject.builder().set("get", "conflicting").build())
                .build();
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> builder.merge(conflicting));

        assertThat(thrown.getMessage(), is("Conflicting OpenAPI document value at paths.x-routing.get"));
    }
}
