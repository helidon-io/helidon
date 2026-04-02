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

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * RFC 8259 character-encoding behaviors kept as explicit Helidon policy tests.
 */
class Rfc8259CharacterEncodingPolicyTest {

    /**
     * RFC 8259 §8.1
     * Quote: "implementations that parse JSON texts MAY ignore the presence of a byte order mark rather than treating it as an error."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-8.1
     */
    @ParameterizedTest
    @EnumSource(Utf8InputMethod.class)
    void testLeadingUtf8BomIsRejectedPerCurrentHelidonPolicy(Utf8InputMethod inputMethod) {
        byte[] json = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, '{', '}'};
        JsonParser parser = inputMethod.createParser(json, 6);

        assertThrows(JsonException.class, parser::readJsonValue);
    }
}
