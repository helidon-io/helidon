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
import static org.hamcrest.Matchers.is;

/**
 * Tests {@link CodeBreaks}.
 */
class CodeBreaksTest {

    @Test
    void testFullyQualifiedJavaType() {
        assertThat(CodeBreaks.code("io.helidon.webserver.WebServer"),
                   is("io.<wbr>helidon.<wbr>webserver.<wbr>WebServer"));
    }

    @Test
    void testGenericType() {
        assertThat(CodeBreaks.code("Map<String, AcmeListenerConfig>"),
                   is("Map&lt;<wbr>String,<wbr> Acme<wbr>Listener<wbr>Config&gt;"));
    }

    @Test
    void testMethodReference() {
        assertThat(CodeBreaks.code("Type.Builder#method(java.lang.String)"),
                   is("Type.<wbr>Builder#<wbr>method(<wbr>java.<wbr>lang.<wbr>String)"));
    }

    @Test
    void testUrlAndPath() {
        assertThat(CodeBreaks.code("https://example.com/acme-service/v1?region=us-phoenix-1&debug=true"),
                   is("https:<wbr>//example.<wbr>com/<wbr>acme-<wbr>service/<wbr>v1?region=<wbr>"
                              + "us-phoenix-<wbr>1&amp;debug=<wbr>true"));
        assertThat(CodeBreaks.code("C:\\Program Files\\Acme_Config"),
                   is("C:<wbr>\\Program Files\\<wbr>Acme_<wbr>Config"));
    }

    @Test
    void testCamelCaseAndAcronymBoundaries() {
        assertThat(CodeBreaks.code("HTTPServerOpenAIConfig"),
                   is("HTTP<wbr>Server<wbr>Open<wbr>AIConfig"));
    }

    @Test
    void testMinimumGapBetweenBreaks() {
        assertThat(CodeBreaks.code("a-b.c-d"),
                   is("a-<wbr>b.c-<wbr>d"));
    }

    @Test
    void testEdgeSeparatorsDoNotBreak() {
        assertThat(CodeBreaks.code("/"), is("/"));
        assertThat(CodeBreaks.code("-"), is("-"));
        assertThat(CodeBreaks.code("/openapi"), is("&#8288;/&#8288;openapi"));
        assertThat(CodeBreaks.code("/oidc/redirect"), is("&#8288;/&#8288;oidc/<wbr>redirect"));
        assertThat(CodeBreaks.code("-1"), is("&#8288;-&#8288;1"));
        assertThat(CodeBreaks.code("-1.0"), is("&#8288;-&#8288;1.<wbr>0"));
        assertThat(CodeBreaks.code("value-"), is("value-"));
    }

    @Test
    void testShortSeparatorValueStillBreaks() {
        assertThat(CodeBreaks.code("api-version"), is("api-<wbr>version"));
    }

    @Test
    void testHtmlEscaping() {
        assertThat(CodeBreaks.code("Fish & Chips <T> \"name\" 'value'"),
                   is("Fish &amp;<wbr> Chips &lt;<wbr>T&gt; &quot;name&quot; &#39;value&#39;"));
    }

    @Test
    void testHtmlRewritesOnlyCodeContents() {
        assertThat(CodeBreaks.html("<p><span>AcmeListenerConfig</span> <code>AcmeListenerConfig</code></p>"),
                   is("<p><span>AcmeListenerConfig</span> <code>Acme<wbr>Listener<wbr>Config</code></p>"));
        assertThat(CodeBreaks.html("<p><code>/</code></p>"),
                   is("<p><code>/</code></p>"));
        assertThat(CodeBreaks.html("<p><code>-</code></p>"),
                   is("<p><code>-</code></p>"));
        assertThat(CodeBreaks.html("<p><code>/openapi</code></p>"),
                   is("<p><code>&#8288;/&#8288;openapi</code></p>"));
    }

    @Test
    void testHtmlHandlesExistingEntitiesInCodeContents() {
        assertThat(CodeBreaks.html("<p><code>Map&lt;String, AcmeListenerConfig&gt;</code> &amp; <em>keep</em></p>"),
                   is("<p><code>Map&lt;<wbr>String,<wbr> Acme<wbr>Listener<wbr>Config&gt;</code>"
                              + " &amp; <em>keep</em></p>"));
        assertThat(CodeBreaks.html("<p><code>a&amp;b-c</code></p>"),
                   is("<p><code>a&amp;<wbr>b-c</code></p>"));
        assertThat(CodeBreaks.html("<p><code>Fish&copy;Chips</code></p>"),
                   is("<p><code>Fish&copy;Chips</code></p>"));
    }

    @Test
    void testNoFixedLengthBreaks() {
        var token = "abcdefghijklmnopqrstuvwxyz0123456789abcdefghijklmnopqrstuvwxyz0123456789";

        assertThat(CodeBreaks.code(token), is(token));
    }
}
