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

import static io.helidon.config.metadata.docs.CmDocNames.shortPackageName;
import static io.helidon.config.metadata.docs.CmDocNames.shortTypeName;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Tests {@link CmDocNames}.
 */
class CmDocNamesTest {

    @Test
    void testShortPackageName() {
        assertThat(shortPackageName(""), is(""));
        assertThat(shortPackageName("io.helidon.webclient.api"), is("i.h.w.a"));
        assertThat(shortPackageName("io.helidon.webclient."), is("i.h.w"));
        assertThat(shortPackageName(".helidon.webclient.api"), is("h.w.a"));
        assertThat(shortPackageName("io..webclient.api"), is("i.w.a"));
    }

    @Test
    void testShortTypeName() {
        assertThat(shortTypeName("WebClient"), is("WebClient"));
        assertThat(shortTypeName("io.helidon.webclient.api.WebClient"), is("i.h.w.a.WebClient"));
        assertThat(shortTypeName("java.lang.String"), is("String"));
        assertThat(shortTypeName("java.time.Duration"), is("Duration"));
        assertThat(shortTypeName("io.helidon.integrations.langchain4j.providers.gemini.GoogleAiGeminiStreamingChatModelConfig"),
                is("i.h.i.l.p.g.GoogleAiGeminiStreamingChatModelConfig"));
    }
}
