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

package io.helidon.security;

import java.net.URI;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class SecurityEnvironmentTest {

    @Test
    void testRequestedTargetUsesRawTargetUri() {
        SecurityEnvironment env = SecurityEnvironment.builder()
                .path("/changed")
                .targetUri(URI.create("http://example.org/my%2Fresource?b=2&a=1&admin"))
                .build();

        assertThat(env.requestedMethod(), is("GET"));
        assertThat(env.requestedTarget(), is("/my%2Fresource?b=2&a=1&admin"));
    }

    @Test
    void testExplicitRequestedTargetPreservesEmptyQueryDelimiter() {
        SecurityEnvironment env = SecurityEnvironment.builder()
                .path("/changed")
                .requestedTarget("/my%2Fresource?")
                .build();

        assertThat(env.requestedTarget(), is("/my%2Fresource?"));
    }

    @Test
    void testQueryParamsDoNotSynthesizeRequestedTarget() {
        SecurityEnvironment env = SecurityEnvironment.builder()
                .path("/resource")
                .queryParam("b", "2")
                .queryParam("a", "1")
                .build();

        assertThat(env.requestedTarget(), is("/resource"));
    }

    @Test
    void testDerivedEnvironmentRetargetsImplicitRequestedValues() {
        SecurityEnvironment inbound = SecurityEnvironment.builder()
                .method("POST")
                .path("/inbound")
                .targetUri(URI.create("http://example.org/inbound?x=1"))
                .build();

        SecurityEnvironment outbound = inbound.derive()
                .method("PUT")
                .path("/outbound")
                .targetUri(URI.create("http://example.org/outbound?y=2"))
                .build();

        assertThat(outbound.requestedMethod(), is("PUT"));
        assertThat(outbound.requestedTarget(), is("/outbound?y=2"));
    }

    @Test
    void testDerivedEnvironmentPreservesExplicitRequestedValues() {
        SecurityEnvironment inbound = SecurityEnvironment.builder()
                .method("POST")
                .path("/inbound")
                .targetUri(URI.create("http://example.org/inbound?x=1"))
                .build();

        SecurityEnvironment explicit = inbound.derive()
                .requestedMethod("PATCH")
                .requestedTarget("/explicit?")
                .method("PUT")
                .path("/outbound")
                .targetUri(URI.create("http://example.org/outbound?y=2"))
                .build();

        assertThat(explicit.requestedMethod(), is("PATCH"));
        assertThat(explicit.requestedTarget(), is("/explicit?"));
    }
}
