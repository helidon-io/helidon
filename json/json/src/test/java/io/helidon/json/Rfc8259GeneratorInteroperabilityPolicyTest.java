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

/**
 * RFC 8259 generator behaviors kept as explicit Helidon policy tests.
 */
class Rfc8259GeneratorInteroperabilityPolicyTest {

    /**
     * RFC 8259 §4
     * Quote: "The names within an object SHOULD be unique."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-4
     */
    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    void testDuplicateObjectNamesAreWrittenAsProvided(GeneratorMethod generatorMethod) {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.writeObjectStart()
                    .write("a", 1)
                    .write("a", 2)
                    .write("a", 3)
                    .writeObjectEnd();
        }

        assertThat(target.generatedJson(), is("{\"a\":1,\"a\":2,\"a\":3}"));
    }

    /**
     * RFC 8259 §6
     * Quote: "Numeric values that cannot be represented in the grammar below (such as Infinity and NaN) are not permitted."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-6
     */
    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    void testNonFiniteNumbersAreWrittenAsJsonStrings(GeneratorMethod generatorMethod) {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.writeObjectStart()
                    .write("floatNaN", Float.NaN)
                    .write("floatPositiveInfinity", Float.POSITIVE_INFINITY)
                    .write("floatNegativeInfinity", Float.NEGATIVE_INFINITY)
                    .write("doubleNaN", Double.NaN)
                    .write("doublePositiveInfinity", Double.POSITIVE_INFINITY)
                    .write("doubleNegativeInfinity", Double.NEGATIVE_INFINITY)
                    .writeObjectEnd();
        }

        assertThat(target.generatedJson(),
                   is("{\"floatNaN\":\"NaN\","
                              + "\"floatPositiveInfinity\":\"Infinity\","
                              + "\"floatNegativeInfinity\":\"-Infinity\","
                              + "\"doubleNaN\":\"NaN\","
                              + "\"doublePositiveInfinity\":\"Infinity\","
                              + "\"doubleNegativeInfinity\":\"-Infinity\"}"));
    }
}
