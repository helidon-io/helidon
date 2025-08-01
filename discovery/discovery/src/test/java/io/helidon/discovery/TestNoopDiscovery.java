/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.discovery;

import java.net.URI;
import java.util.SequencedSet;

import io.helidon.service.registry.Services;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static java.net.URI.create;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class TestNoopDiscovery {

    private Discovery d;

    TestNoopDiscovery() {
        super();
    }

    @BeforeEach
    void acquireDiscovery() {
        this.d = Services.get(Discovery.class);
    }

    @AfterEach
    void closeDiscovery() {
        if (this.d != null) {
            this.d.close();
            this.d = null;
        }
    }

    @Test
    void testDiscoveredSetIsNotEmpty() {
        assertThat(d.uris("example", create("https://example.com")).size(), is(1));
    }

    @Test
    void testDiscoveredSetIsUnmodifiable() {
        assertThrows(UnsupportedOperationException.class, d.uris("example", create("https://example.com"))::clear);
    }

    @Test
    void testDefaultValueIsLastInDiscoveredSet() {
        URI defaultUri = create("https://example.com");
        assertThat(d.uris("example", create("https://example.com")).getLast().uri(), is(defaultUri));
    }

    @Test
    void testDefaultValueRequired() {
        assertThrows(NullPointerException.class, () -> d.uris("example", null));
    }

    @Test
    void testNameRequired() {
        assertThrows(NullPointerException.class, () -> d.uris(null, create("https://example.com")));
    }

}
