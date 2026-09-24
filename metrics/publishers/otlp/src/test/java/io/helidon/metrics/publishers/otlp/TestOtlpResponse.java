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

package io.helidon.metrics.publishers.otlp;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import io.helidon.json.JsonException;
import io.helidon.json.JsonParser;
import io.helidon.json.binding.JsonBinding;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestOtlpResponse {
    private static final JsonBinding JSON_BINDING = JsonBinding.create();

    @BeforeAll
    static void initializeBinding() {
        var response = parse("{\"partialSuccess\":{\"rejectedDataPoints\":1}}");
        assertThat(response.partialSuccess().rejectedDataPoints(), is(1L));
    }

    @Test
    void exactSignedInt64ValuesAreAcceptedAsNumbersAndStrings() {
        List<NumberCase> values = List.of(
                new NumberCase("0", 0),
                new NumberCase("-0", 0),
                new NumberCase("42", 42),
                new NumberCase("-42", -42),
                new NumberCase("9007199254740993", 9007199254740993L),
                new NumberCase("9223372036854775807", Long.MAX_VALUE),
                new NumberCase("-9223372036854775808", Long.MIN_VALUE),
                new NumberCase("1e2", 100),
                new NumberCase("1E+2", 100),
                new NumberCase("1200e-2", 12),
                new NumberCase("-1e2", -100),
                new NumberCase("9.223372036854775807e18", Long.MAX_VALUE),
                new NumberCase("-9.223372036854775808e18", Long.MIN_VALUE),
                new NumberCase("1.000", 1),
                new NumberCase("-1.000", -1),
                new NumberCase("9223372036854775807.0", Long.MAX_VALUE),
                new NumberCase("-9223372036854775808.0", Long.MIN_VALUE));
        for (var value : values) {
            for (String jsonValue : List.of(value.value(), "\"" + value.value() + "\"")) {
                var response = parse("{\"partialSuccess\":{\"rejectedDataPoints\":" + jsonValue + "}}");
                assertThat("Exact rejected count for " + jsonValue,
                           response.partialSuccess().rejectedDataPoints(), is(value.expected()));
                assertThat("Default error message for " + jsonValue, response.partialSuccess().errorMessage(), is(""));
            }
        }
    }

    @Test
    void missingAndNullCountsDefaultToZero() {
        for (String partialSuccess : List.of("{}", "{\"rejectedDataPoints\":null}",
                                             "{\"rejectedDataPoints\":null,\"errorMessage\":null}")) {
            var response = parse("{\"partialSuccess\":" + partialSuccess + "}");
            assertThat("Default rejected count for " + partialSuccess,
                       response.partialSuccess().rejectedDataPoints(), is(0L));
            assertThat("Default error message for " + partialSuccess, response.partialSuccess().errorMessage(), is(""));
        }
    }

    @Test
    void fractionalAndOverflowCountsAreRejectedAsNumbersAndStrings() {
        for (String value : List.of("1.5", "-1.5", "1e-1", "9223372036854775808", "-9223372036854775809",
                                    "9.223372036854775808e18", "-9.223372036854775809e18", "1e1000000")) {
            for (String jsonValue : List.of(value, "\"" + value + "\"")) {
                String json = "{\"partialSuccess\":{\"rejectedDataPoints\":" + jsonValue + "}}";
                assertThrows(JsonException.class, () -> parse(json), "Invalid rejected count " + jsonValue);
            }
        }
    }

    @Test
    void invalidCountTypesAndStringsAreRejected() {
        for (String value : List.of("true", "false", "[]", "{}", "\"\"", "\"NaN\"", "\"Infinity\"", "\"1e\"")) {
            String json = "{\"partialSuccess\":{\"rejectedDataPoints\":" + value + "}}";
            assertThrows(JsonException.class, () -> parse(json), "Invalid rejected count " + value);
        }
    }

    @Test
    void fieldsFollowingTheCountAreParsed() {
        for (String value : List.of("42", "4.2e1", "42.000", "\"42\"", "\"4.2e1\"", "\"42.000\"", "null")) {
            String json = """
                    {"partialSuccess":{"rejectedDataPoints":%s \t\r\n,
                     "future":{"nested":[true,null,1,"ignored"]},"errorMessage":"receiver warning"},
                     "future":[{"ignored":true}]}
                    """.formatted(value);
            var response = parse(json);
            assertThat("Rejected count before following fields for " + value,
                       response.partialSuccess().rejectedDataPoints(), is(value.equals("null") ? 0L : 42L));
            assertThat("Error message following rejected count " + value,
                       response.partialSuccess().errorMessage(), is("receiver warning"));
        }
    }

    @Test
    void numericRepresentationLengthIsBounded() {
        String atLimit = "1." + "0".repeat(126);
        String overLimit = atLimit + "0";
        for (String value : List.of(atLimit, "\"" + atLimit + "\"")) {
            var response = parse("{\"partialSuccess\":{\"rejectedDataPoints\":" + value + "}}");
            assertThat("Integral decimal at the representation limit " + value,
                       response.partialSuccess().rejectedDataPoints(), is(1L));
        }
        for (String value : List.of(overLimit, "\"" + overLimit + "\"")) {
            String json = "{\"partialSuccess\":{\"rejectedDataPoints\":" + value + "}}";
            assertThrows(JsonException.class, () -> parse(json), "Integral decimal beyond the representation limit " + value);
        }
    }

    @Test
    void countAndFollowingFieldsSurviveStreamBufferRefills() {
        String atLimit = "1." + "0".repeat(126);
        for (String value : List.of(atLimit, "\"" + atLimit + "\"")) {
            String json = """
                    {"partialSuccess":{"rejectedDataPoints":%s,
                     "future":{"nested":[true,null,1,"ignored"]},"errorMessage":"receiver warning"}}
                    """.formatted(value);
            var input = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
            var parser = JsonParser.create(input, 8);
            var response = JSON_BINDING.deserialize(parser, OtlpResponse.class);
            assertThat("Rejected count spanning stream buffers for " + value,
                       response.partialSuccess().rejectedDataPoints(), is(1L));
            assertThat("Error message following stream buffer refills for " + value,
                       response.partialSuccess().errorMessage(), is("receiver warning"));
        }
    }

    @Test
    @Timeout(5)
    void oversizedBareCountIsRejectedPromptly() {
        String json = "{\"partialSuccess\":{\"rejectedDataPoints\":" + "9".repeat(1_000_000) + "}}";
        assertThrows(JsonException.class, () -> parse(json));
    }

    @Test
    @Timeout(5)
    void oversizedQuotedCountIsRejectedPromptly() {
        String json = "{\"partialSuccess\":{\"rejectedDataPoints\":\"" + "9".repeat(1_000_000) + "\"}}";
        assertThrows(JsonException.class, () -> parse(json));
    }

    private static OtlpResponse parse(String json) {
        var parser = JsonParser.create(json);
        return JSON_BINDING.deserialize(parser, OtlpResponse.class);
    }

    private record NumberCase(String value, long expected) {
    }
}
