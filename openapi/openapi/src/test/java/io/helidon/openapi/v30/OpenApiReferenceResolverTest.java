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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;

class OpenApiReferenceResolverTest {
    private static final int CHAIN_LENGTH = 100;
    private static final Map<String, Object> TARGET = Map.of("name", "query", "in", "querystring");

    @Test
    void resolvesInlineChainedAndEncodedComponentReferences() {
        OpenApiReferenceResolver resolver = resolver(Map.of(
                "Query.String", TARGET,
                "Alias", reference("#/components/parameters/Query%2EString")));

        assertResolution(resolver.resolveComponent(TARGET, "parameters"),
                         OpenApiReferenceResolver.Status.RESOLVED,
                         TARGET);
        assertResolution(resolver.resolveComponent(reference("#/components/parameters/Alias"), "parameters"),
                         OpenApiReferenceResolver.Status.RESOLVED,
                         TARGET);
    }

    @Test
    void identifiesIpvFutureHosts() {
        assertThat(OpenApiReferenceResolver.hasIpvFutureHost(
                           "http://[v1.fe]/description.yaml#/components/examples/Example"),
                   is(true));
        assertThat(OpenApiReferenceResolver.hasIpvFutureHost("//user@[Vf.a:b]:8443/description.yaml"), is(true));
        assertThat(OpenApiReferenceResolver.hasIpvFutureHost("http://[2001:db8::1]/description.yaml"), is(false));
        assertThat(OpenApiReferenceResolver.hasIpvFutureHost("description/[v1.fe].yaml"), is(false));
        assertThat(OpenApiReferenceResolver.hasIpvFutureHost("http://[v.fe]/description.yaml"), is(false));
    }

    @Test
    void reportsUnresolvedComponentReferences() {
        OpenApiReferenceResolver resolver = resolver(Map.of(
                "First", reference("#/components/parameters/Second"),
                "Second", reference("#/components/parameters/First")));

        assertStatus(resolver.resolveComponent(reference("https://example.test/parameter"), "parameters"),
                     OpenApiReferenceResolver.Status.EXTERNAL);
        assertStatus(resolver.resolveComponent(reference("#/components/parameters/Missing"), "parameters"),
                     OpenApiReferenceResolver.Status.MISSING);
        assertStatus(resolver.resolveComponent(reference("#not-a-pointer"), "parameters"),
                     OpenApiReferenceResolver.Status.MALFORMED);
        assertStatus(resolver.resolveComponent(reference("#/components/parameters/Bad~2Name"), "parameters"),
                     OpenApiReferenceResolver.Status.MALFORMED);
        assertStatus(resolver.resolveComponent(reference("#/components/parameters/First"), "parameters"),
                     OpenApiReferenceResolver.Status.CYCLIC);
        assertStatus(resolver.resolveComponent(reference("#/components/parameters/Second"), "parameters"),
                     OpenApiReferenceResolver.Status.CYCLIC);
    }

    @Test
    void resolvesGeneralLocalReferences() {
        Map<String, Object> pathItem = Map.of("get", Map.of("operationId", "getItems"));
        Map<String, Object> alias = reference("#/paths/~1items");
        OpenApiReferenceResolver resolver = OpenApiReferenceResolver.create(Map.of(
                "paths", Map.of("/items", pathItem),
                "components", Map.of("pathItems", Map.of(
                        "Alias", alias,
                        "Encoded~Name", pathItem))));

        assertResolution(resolver.resolveReference(reference("#/components/pathItems/Alias")),
                         OpenApiReferenceResolver.Status.RESOLVED,
                         alias);
        assertResolution(resolver.resolveReference(alias),
                         OpenApiReferenceResolver.Status.RESOLVED,
                         pathItem);
        assertResolution(resolver.resolveReference(reference("#/components/pathItems/Encoded~0Name")),
                         OpenApiReferenceResolver.Status.RESOLVED,
                         pathItem);
    }

    @Test
    void cachesSelfQualifiedComponentReferenceChains() {
        CountingMap parameters = new CountingMap();
        List<Map<String, Object>> chain = new ArrayList<>();
        for (int i = 0; i < CHAIN_LENGTH; i++) {
            Map<String, Object> value = i + 1 == CHAIN_LENGTH
                    ? TARGET
                    : reference("https://example.test/api#/components/parameters/Alias" + (i + 1));
            parameters.put("Alias" + i, value);
            chain.add(value);
        }
        OpenApiReferenceResolver resolver = OpenApiReferenceResolver.create(Map.of(
                "$self", "https://example.test/api",
                "components", Map.of("parameters", parameters)));

        chain.forEach(value -> assertResolution(resolver.resolveComponent(value, "parameters"),
                                                OpenApiReferenceResolver.Status.RESOLVED,
                                                TARGET));

        assertThat(parameters.lookups(), lessThanOrEqualTo(CHAIN_LENGTH));
    }

    @Test
    void cachesGeneralReferenceChains() {
        CountingMap parameters = new CountingMap();
        List<Map<String, Object>> chain = new ArrayList<>();
        for (int i = 0; i < CHAIN_LENGTH; i++) {
            Map<String, Object> value = i + 1 == CHAIN_LENGTH
                    ? TARGET
                    : reference("#/components/parameters/Alias" + (i + 1));
            parameters.put("Alias" + i, value);
            chain.add(value);
        }
        OpenApiReferenceResolver resolver = resolver(parameters);

        chain.forEach(value -> assertResolution(resolver.resolveReferenceChain(value),
                                                OpenApiReferenceResolver.Status.RESOLVED,
                                                TARGET));

        assertThat(parameters.lookups(), lessThanOrEqualTo(CHAIN_LENGTH));
    }

    @Test
    void isolatesComponentResolutionCachesByTypeAndIdentity() {
        Map<String, Object> first = new LinkedHashMap<>(TARGET);
        Map<String, Object> second = new LinkedHashMap<>(TARGET);
        Map<String, Object> alias = reference("#/components/parameters/Target");
        OpenApiReferenceResolver resolver = OpenApiReferenceResolver.create(Map.of(
                "components", Map.of("parameters", Map.of("Target", first))));

        assertResolution(resolver.resolveComponent(alias, "parameters"),
                         OpenApiReferenceResolver.Status.RESOLVED,
                         first);
        assertStatus(resolver.resolveComponent(alias, "schemas"),
                     OpenApiReferenceResolver.Status.MISSING);
        assertThat(resolver.resolveComponent(first, "parameters").value(), sameInstance(first));
        assertThat(resolver.resolveComponent(second, "parameters").value(), sameInstance(second));
    }

    @Test
    void rejectsInvalidArrayIndices() {
        OpenApiReferenceResolver resolver = OpenApiReferenceResolver.create(Map.of("values", List.of(TARGET)));

        assertResolution(resolver.resolveReference(reference("#/values/0")),
                         OpenApiReferenceResolver.Status.RESOLVED,
                         TARGET);
        assertStatus(resolver.resolveReference(reference("#/values/00")),
                     OpenApiReferenceResolver.Status.MISSING);
        assertStatus(resolver.resolveReference(reference("#/values/+0")),
                     OpenApiReferenceResolver.Status.MISSING);
        assertStatus(resolver.resolveReference(reference("#/values/-0")),
                     OpenApiReferenceResolver.Status.MISSING);
        assertStatus(resolver.resolveReference(reference("#/values/2147483648")),
                     OpenApiReferenceResolver.Status.MISSING);
        assertStatus(resolver.resolveReference(reference("#/values/1")),
                     OpenApiReferenceResolver.Status.MISSING);
    }

    @Test
    void resolvesReferencesToSelfDocument() {
        OpenApiReferenceResolver resolver = OpenApiReferenceResolver.create(Map.of(
                "$self", "https://example.test/api",
                "components", Map.of("parameters", Map.of("Query", TARGET))));

        assertResolution(resolver.resolveReference(
                                 reference("https://example.test/api#/components/parameters/Query")),
                         OpenApiReferenceResolver.Status.RESOLVED,
                         TARGET);
        assertResolution(resolver.resolveReference(reference("/api#/components/parameters/Query")),
                         OpenApiReferenceResolver.Status.RESOLVED,
                         TARGET);
        assertStatus(resolver.resolveReference(
                             reference("https://example.test/other#/components/parameters/Query")),
                     OpenApiReferenceResolver.Status.EXTERNAL);
    }

    @Test
    void resolvesOneLocalReferenceAtATime() {
        Map<String, Object> terminal = Map.of("get", Map.of("operationId", "getItems"));
        Map<String, Object> intermediate = reference("#/components/pathItems/Terminal");
        OpenApiReferenceResolver resolver = OpenApiReferenceResolver.create(Map.of(
                "components", Map.of("pathItems", Map.of(
                        "Intermediate", intermediate,
                        "Terminal", terminal))));

        assertResolution(resolver.resolveComponent(reference("#/components/pathItems/Intermediate"), "pathItems"),
                         OpenApiReferenceResolver.Status.RESOLVED,
                         terminal);
        assertResolution(resolver.resolveReference(reference("#/components/pathItems/Intermediate")),
                         OpenApiReferenceResolver.Status.RESOLVED,
                         intermediate);
        assertResolution(resolver.resolveReference(intermediate),
                         OpenApiReferenceResolver.Status.RESOLVED,
                         terminal);
        assertResolution(resolver.resolveReference(terminal),
                         OpenApiReferenceResolver.Status.RESOLVED,
                         terminal);
    }

    @Test
    void reportsUnresolvedGeneralLocalReferences() {
        OpenApiReferenceResolver resolver = OpenApiReferenceResolver.create(Map.of("components", Map.of()));

        assertStatus(resolver.resolveReference(reference("https://example.test/path-item")),
                     OpenApiReferenceResolver.Status.EXTERNAL);
        assertStatus(resolver.resolveReference(reference("#/components/pathItems/Missing")),
                     OpenApiReferenceResolver.Status.MISSING);
        assertStatus(resolver.resolveReference(reference("#not-a-pointer")),
                     OpenApiReferenceResolver.Status.MALFORMED);
    }

    private static OpenApiReferenceResolver resolver(Map<String, Object> parameters) {
        return OpenApiReferenceResolver.create(Map.of("components", Map.of("parameters", parameters)));
    }

    private static Map<String, Object> reference(String ref) {
        return Map.of("$ref", ref);
    }

    private static void assertResolution(OpenApiReferenceResolver.Resolution resolution,
                                         OpenApiReferenceResolver.Status status,
                                         Map<String, Object> value) {
        assertThat(resolution.status(), is(status));
        assertThat(resolution.value(), is(value));
    }

    private static void assertStatus(OpenApiReferenceResolver.Resolution resolution,
                                     OpenApiReferenceResolver.Status status) {
        assertThat(resolution.status(), is(status));
        assertThat(resolution.value(), is(Map.of()));
    }

    private static final class CountingMap extends LinkedHashMap<String, Object> {
        private int lookups;

        @Override
        public Object get(Object key) {
            lookups++;
            return super.get(key);
        }

        private int lookups() {
            return lookups;
        }
    }
}
