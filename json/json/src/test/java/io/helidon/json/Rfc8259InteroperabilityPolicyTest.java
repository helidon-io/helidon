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

package io.helidon.json;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * RFC 8259 interoperability-sensitive behaviors kept as explicit Helidon policy tests.
 */
class Rfc8259InteroperabilityPolicyTest {

    /**
     * RFC 8259 §4
     * Quote: "Many implementations report the last name/value pair only."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-4
     */
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void testDuplicateObjectNamesUseLastValue(ParserMethod parserMethod) {
        JsonParser parser = parserMethod.createParser("{\"a\":1,\"a\":2,\"a\":3}");
        JsonObject jsonObject = parser.readJsonObject();

        assertThat(jsonObject.intValue("a").orElseThrow(), is(3));
        if (!parser.hasNext()) {
            return;
        }
        try {
            byte token = parser.nextToken();
            fail("Unexpected trailing token: " + Parsers.toPrintableForm(token));
        } catch (JsonException expected) {
            // Remaining input was parser-recognized trailing whitespace only.
        }
    }
}
