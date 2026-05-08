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

package io.helidon.config.metadata.docs;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;

/**
 * Tests {@link CmDocNames}.
 */
class CmDocNamesTest {

    @Test
    void testTypeNamesRespectReservedNames() {
        var names = new CmDocNames();

        names.reserveTypeName("io.helidon.ServerConfig");

        assertThat(names.typeName("io.helidon.ServerConfig"), is("io.helidon.ServerConfig2"));
        assertThat(names.typeName("io.helidon.ServerConfig"), is("io.helidon.ServerConfig3"));
    }

    @Test
    void testSyntheticTypeNamesFollowPathRules() {
        var names = new CmDocNames();

        assertThat(names.syntheticTypeName("server"), is("io.helidon.ServerConfig"));
        assertThat(names.syntheticTypeName("server.tls"), is("io.helidon.server.TlsConfig"));
        assertThat(names.syntheticTypeName("io.helidon.data.sources"), is("io.helidon.data.SourcesConfig"));
        assertThat(names.syntheticTypeName("*.rest-client"), is("io.helidon.RestClientConfig"));
    }

    @Test
    void testSyntheticTypeNamesUseNumericSuffixes() {
        var names = new CmDocNames();

        assertThat(names.syntheticTypeName("foo-bar"), is("io.helidon.FooBarConfig"));
        assertThat(names.syntheticTypeName("foo_bar"), is("io.helidon.FooBarConfig2"));
    }

    @Test
    void testFileNamesNormalizeAndResolveCollisions() {
        var names = new CmDocNames("initial.html");

        assertThat(names.fileName("alpha.beta", ".html"), is("alpha_beta.html"));
        assertThat(names.fileName("alpha/beta", ".html"), is("alpha_beta_2.html"));
        assertThat(names.fileName("initial", ".html"), is("initial_2.html"));
    }

    @Test
    void testAnchorsAreStableAndSanitized() {
        var names = new CmDocNames();

        var anchor = names.anchor("com_acme_AcmeServerConfig.adoc", "cert.alias", "com.acme.AcmeServerConfig");

        assertThat(anchor, startsWith("a"));
        assertThat(anchor, containsString("-cert-alias"));
        assertThat(anchor.matches("a[0-9a-f]{8}-cert-alias"), is(true));
        assertThat(names.anchor("com_acme_AcmeServerConfig.adoc", "cert.alias", "com.acme.AcmeServerConfig"),
                is(anchor));
    }
}
