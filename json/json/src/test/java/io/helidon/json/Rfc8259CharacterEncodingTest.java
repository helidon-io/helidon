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

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Strict RFC 8259 parser conformance tests for UTF-8 character encoding.
 */
class Rfc8259CharacterEncodingTest {

    private static final int STREAM_BUFFER_SIZE = 6;

    /**
     * RFC 8259 §7
     * Quote: "All Unicode characters may be placed within the quotation marks,"
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-7
     */
    @ParameterizedTest
    @EnumSource(Utf8InputMethod.class)
    void testAcceptsDirectUtf8InObjectNamesAndStringValues(Utf8InputMethod inputMethod) {
        byte[] json = "{\"€\":\"😀\",\"©\":\"žluťoučký\"}".getBytes(StandardCharsets.UTF_8);

        runByteTextScenario(inputMethod, json, STREAM_BUFFER_SIZE, parser -> {
            JsonObject jsonObject = parser.readJsonObject();

            assertThat(jsonObject.stringValue("€").orElseThrow(), is("😀"));
            assertThat(jsonObject.stringValue("©").orElseThrow(), is("žluťoučký"));
        }, false);
    }

    /**
     * RFC 8259 §7
     * Quote: "All Unicode characters may be placed within the quotation marks,"
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-7
     */
    @ParameterizedTest
    @EnumSource(Utf8InputMethod.class)
    void testAcceptsUtf8BoundaryUnicodeScalarValues(Utf8InputMethod inputMethod) {
        byte[] json = "\"\u0080\u07FF\u0800\uD7FF\uE000\uD800\uDC00\"".getBytes(StandardCharsets.UTF_8);

        runByteTextScenario(inputMethod, json, STREAM_BUFFER_SIZE, parser -> {
            assertThat(parser.readString(), is("\u0080\u07FF\u0800\uD7FF\uE000\uD800\uDC00"));
        }, false);
    }

    /**
     * RFC 8259 §7
     * Quote: "All Unicode characters may be placed within the quotation marks,"
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-7
     */
    @ParameterizedTest
    @EnumSource(Utf8InputMethod.class)
    void testAcceptsUtf8EncodedByteOrderMarkCharacterInsideString(Utf8InputMethod inputMethod) {
        byte[] json = "\"\uFEFFdata\"".getBytes(StandardCharsets.UTF_8);

        runByteTextScenario(inputMethod, json, STREAM_BUFFER_SIZE, parser -> {
            assertThat(parser.readString(), is("\uFEFFdata"));
        }, false);
    }

    /**
     * RFC 8259 §8.1
     * Quote: "JSON text exchanged between systems that are not part of a closed ecosystem MUST be encoded using UTF-8"
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-8.1
     */
    @Test
    void testAcceptsUtf8SequenceSplitAcrossStreamBufferBoundary() {
        byte[] json = "\"1234😀\"".getBytes(StandardCharsets.UTF_8);

        runByteTextScenario(Utf8InputMethod.STREAM, json, STREAM_BUFFER_SIZE, parser -> {
            assertThat(parser.readString(), is("1234😀"));
        }, false);
    }

    /**
     * RFC 8259 §8.1
     * Quote: "JSON text exchanged between systems that are not part of a closed ecosystem MUST be encoded using UTF-8"
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-8.1
     */
    @Test
    void testAcceptsTwoAndThreeByteUtf8SequencesSplitAcrossStreamBufferBoundary() {
        assertAll(
                () -> runByteTextScenario(Utf8InputMethod.STREAM,
                                          "\"1234©\"".getBytes(StandardCharsets.UTF_8),
                                          STREAM_BUFFER_SIZE,
                                          parser -> assertThat(parser.readString(), is("1234©")),
                                          false),
                () -> runByteTextScenario(Utf8InputMethod.STREAM,
                                          "\"123€\"".getBytes(StandardCharsets.UTF_8),
                                          STREAM_BUFFER_SIZE,
                                          parser -> assertThat(parser.readString(), is("123€")),
                                          false)
        );
    }

    /**
     * RFC 8259 §8.1
     * Quote: "JSON text exchanged between systems that are not part of a closed ecosystem MUST be encoded using UTF-8"
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-8.1
     */
    @ParameterizedTest
    @EnumSource(Utf8InputMethod.class)
    void testRejectsIsolatedUtf8ContinuationByte(Utf8InputMethod inputMethod) {
        byte[] json = new byte[] {'"', (byte) 0x80, '"'};

        runByteTextScenario(inputMethod, json, STREAM_BUFFER_SIZE, JsonParser::readString, true);
    }

    /**
     * RFC 8259 §8.1
     * Quote: "JSON text exchanged between systems that are not part of a closed ecosystem MUST be encoded using UTF-8"
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-8.1
     */
    @ParameterizedTest
    @EnumSource(Utf8InputMethod.class)
    void testRejectsIncompleteUtf8Sequence(Utf8InputMethod inputMethod) {
        assertAll(
                () -> runByteTextScenario(inputMethod,
                                          new byte[] {'"', (byte) 0xC2},
                                          STREAM_BUFFER_SIZE,
                                          JsonParser::readString,
                                          true),
                () -> runByteTextScenario(inputMethod,
                                          new byte[] {'"', (byte) 0xE2, (byte) 0x82},
                                          STREAM_BUFFER_SIZE,
                                          JsonParser::readString,
                                          true),
                () -> runByteTextScenario(inputMethod,
                                          new byte[] {'"', (byte) 0xF0, (byte) 0x9F, (byte) 0x98},
                                          STREAM_BUFFER_SIZE,
                                          JsonParser::readString,
                                          true)
        );
    }

    /**
     * RFC 8259 §8.1
     * Quote: "JSON text exchanged between systems that are not part of a closed ecosystem MUST be encoded using UTF-8"
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-8.1
     */
    @ParameterizedTest
    @EnumSource(Utf8InputMethod.class)
    void testRejectsInvalidUtf8ContinuationByte(Utf8InputMethod inputMethod) {
        assertAll(
                () -> runByteTextScenario(inputMethod,
                                          new byte[] {'"', (byte) 0xC2, 0x20, '"'},
                                          STREAM_BUFFER_SIZE,
                                          JsonParser::readString,
                                          true),
                () -> runByteTextScenario(inputMethod,
                                          new byte[] {'"', (byte) 0xE2, 0x28, (byte) 0xA1, '"'},
                                          STREAM_BUFFER_SIZE,
                                          JsonParser::readString,
                                          true),
                () -> runByteTextScenario(inputMethod,
                                          new byte[] {'"', (byte) 0xF0, 0x28, (byte) 0x8C, (byte) 0xBC, '"'},
                                          STREAM_BUFFER_SIZE,
                                          JsonParser::readString,
                                          true)
        );
    }

    /**
     * RFC 8259 §8.1
     * Quote: "JSON text exchanged between systems that are not part of a closed ecosystem MUST be encoded using UTF-8"
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-8.1
     */
    @ParameterizedTest
    @EnumSource(Utf8InputMethod.class)
    void testRejectsOverlongUtf8Sequences(Utf8InputMethod inputMethod) {
        assertAll(
                () -> runByteTextScenario(inputMethod,
                                          new byte[] {'"', (byte) 0xC0, (byte) 0xAF, '"'},
                                          STREAM_BUFFER_SIZE,
                                          JsonParser::readString,
                                          true),
                () -> runByteTextScenario(inputMethod,
                                          new byte[] {'"', (byte) 0xE0, (byte) 0x9F, (byte) 0xBF, '"'},
                                          STREAM_BUFFER_SIZE,
                                          JsonParser::readString,
                                          true),
                () -> runByteTextScenario(inputMethod,
                                          new byte[] {'"', (byte) 0xF0, (byte) 0x8F, (byte) 0xBF, (byte) 0xBF, '"'},
                                          STREAM_BUFFER_SIZE,
                                          JsonParser::readString,
                                          true)
        );
    }

    @ParameterizedTest
    @EnumSource(Utf8InputMethod.class)
    void testReadCharRejectsOverlongUtf8Sequence(Utf8InputMethod inputMethod) {
        byte[] json = new byte[] {'"', (byte) 0xC0, (byte) 0xAF, '"'};

        runByteTextScenario(inputMethod, json, STREAM_BUFFER_SIZE, parser -> parser.readChar(), true);
    }

    @ParameterizedTest
    @EnumSource(Utf8InputMethod.class)
    void testReadJsonStringValueRejectsOverlongUtf8Sequence(Utf8InputMethod inputMethod) {
        byte[] json = new byte[] {'"', (byte) 0xC0, (byte) 0xAF, '"'};

        runByteTextScenario(inputMethod, json, STREAM_BUFFER_SIZE, parser -> parser.readJsonString().value(), true);
    }

    @ParameterizedTest
    @EnumSource(Utf8InputMethod.class)
    void testReadStringAsHashRejectsInvalidUtf8ContinuationByte(Utf8InputMethod inputMethod) {
        assertAll(
                () -> runByteTextScenario(inputMethod,
                                          new byte[] {'"', (byte) 0xC2, 0x20, '"'},
                                          STREAM_BUFFER_SIZE,
                                          JsonParser::readStringAsHash,
                                          true),
                () -> runByteTextScenario(inputMethod,
                                          new byte[] {'"', (byte) 0xE2, 0x28, (byte) 0xA1, '"'},
                                          STREAM_BUFFER_SIZE,
                                          JsonParser::readStringAsHash,
                                          true)
        );
    }

    @ParameterizedTest
    @EnumSource(Utf8InputMethod.class)
    void testReadStringAsHashRejectsOverlongUtf8Sequence(Utf8InputMethod inputMethod) {
        assertAll(
                () -> runByteTextScenario(inputMethod,
                                          new byte[] {'"', (byte) 0xC0, (byte) 0xAF, '"'},
                                          STREAM_BUFFER_SIZE,
                                          JsonParser::readStringAsHash,
                                          true),
                () -> runByteTextScenario(inputMethod,
                                          new byte[] {'"', (byte) 0xE0, (byte) 0x9F, (byte) 0xBF, '"'},
                                          STREAM_BUFFER_SIZE,
                                          JsonParser::readStringAsHash,
                                          true),
                () -> runByteTextScenario(inputMethod,
                                          new byte[] {'"', (byte) 0xF0, (byte) 0x8F, (byte) 0xBF, (byte) 0xBF, '"'},
                                          STREAM_BUFFER_SIZE,
                                          JsonParser::readStringAsHash,
                                          true)
        );
    }

    /**
     * RFC 8259 §8.1
     * Quote: "JSON text exchanged between systems that are not part of a closed ecosystem MUST be encoded using UTF-8"
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-8.1
     */
    @ParameterizedTest
    @EnumSource(Utf8InputMethod.class)
    void testRejectsImpossibleUtf8LeadingBytes(Utf8InputMethod inputMethod) {
        assertAll(
                () -> runByteTextScenario(inputMethod,
                                          new byte[] {'"', (byte) 0xF8, (byte) 0x88, (byte) 0x80,
                                                  (byte) 0x80, (byte) 0x80, '"'},
                                          STREAM_BUFFER_SIZE,
                                          JsonParser::readString,
                                          true),
                () -> runByteTextScenario(inputMethod,
                                          new byte[] {'"', (byte) 0xFF, '"'},
                                          STREAM_BUFFER_SIZE,
                                          JsonParser::readString,
                                          true)
        );
    }

    /**
     * RFC 8259 §7
     * Quote: "All Unicode characters may be placed within the quotation marks,"
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-7
     */
    @ParameterizedTest
    @EnumSource(Utf8InputMethod.class)
    void testAcceptsMaximumUnicodeCodePointAsUtf8(Utf8InputMethod inputMethod) {
        byte[] json = "\"\uDBFF\uDFFF\"".getBytes(StandardCharsets.UTF_8);

        runByteTextScenario(inputMethod, json, STREAM_BUFFER_SIZE, parser -> {
            assertThat(parser.readString(), is("\uDBFF\uDFFF"));
        }, false);
    }

    /**
     * RFC 8259 §8.1
     * Quote: "JSON text exchanged between systems that are not part of a closed ecosystem MUST be encoded using UTF-8"
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-8.1
     */
    @ParameterizedTest
    @EnumSource(Utf8InputMethod.class)
    void testRejectsUtf8EncodedSurrogateCodePoints(Utf8InputMethod inputMethod) {
        assertAll(
                () -> runByteTextScenario(inputMethod,
                                          new byte[] {'"', (byte) 0xED, (byte) 0xA0, (byte) 0x80, '"'},
                                          STREAM_BUFFER_SIZE,
                                          JsonParser::readString,
                                          true),
                () -> runByteTextScenario(inputMethod,
                                          new byte[] {'"', (byte) 0xED, (byte) 0xB0, (byte) 0x80, '"'},
                                          STREAM_BUFFER_SIZE,
                                          JsonParser::readString,
                                          true)
        );
    }

    /**
     * RFC 8259 §8.1
     * Quote: "JSON text exchanged between systems that are not part of a closed ecosystem MUST be encoded using UTF-8"
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-8.1
     */
    @ParameterizedTest
    @EnumSource(Utf8InputMethod.class)
    void testRejectsUtf8CodePointsBeyondUnicodeRange(Utf8InputMethod inputMethod) {
        byte[] json = new byte[] {'"', (byte) 0xF4, (byte) 0x90, (byte) 0x80, (byte) 0x80, '"'};

        runByteTextScenario(inputMethod, json, STREAM_BUFFER_SIZE, JsonParser::readString, true);
    }

    /**
     * RFC 8259 §8.1
     * Quote: "JSON text exchanged between systems that are not part of a closed ecosystem MUST be encoded using UTF-8"
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-8.1
     */
    @ParameterizedTest
    @EnumSource(Utf8InputMethod.class)
    void testRejectsUtf16AndUtf32EncodedJsonText(Utf8InputMethod inputMethod) {
        assertAll(
                () -> runByteTextScenario(inputMethod,
                                          "{}".getBytes(StandardCharsets.UTF_16BE),
                                          STREAM_BUFFER_SIZE,
                                          JsonParser::readJsonObject,
                                          true),
                () -> runByteTextScenario(inputMethod,
                                          "{}".getBytes(StandardCharsets.UTF_16LE),
                                          STREAM_BUFFER_SIZE,
                                          JsonParser::readJsonObject,
                                          true),
                () -> runByteTextScenario(inputMethod,
                                          new byte[] {0x00, 0x00, 0x00, 0x7B, 0x00, 0x00, 0x00, 0x7D},
                                          STREAM_BUFFER_SIZE,
                                          JsonParser::readJsonObject,
                                          true),
                () -> runByteTextScenario(inputMethod,
                                          new byte[] {0x7B, 0x00, 0x00, 0x00, 0x7D, 0x00, 0x00, 0x00},
                                          STREAM_BUFFER_SIZE,
                                          JsonParser::readJsonObject,
                                          true)
        );
    }

    private static void runByteTextScenario(Utf8InputMethod inputMethod,
                                            byte[] json,
                                            int streamBufferSize,
                                            Consumer<JsonParser> parserAssertions,
                                            boolean expectRejection) {
        String failureMessage = "Unexpected UTF-8 parser result for bytes: "
                + HexFormat.ofDelimiter(" ").withUpperCase().formatHex(json);
        try {
            JsonParser parser = inputMethod.createParser(json, streamBufferSize);
            parserAssertions.accept(parser);
            if (!parser.hasNext()) {
                if (!expectRejection) {
                    return;
                }
                fail(failureMessage);
            }
            try {
                byte token = parser.nextToken();
                if (expectRejection) {
                    return;
                }
                fail("Unexpected trailing token: " + Parsers.toPrintableForm(token));
            } catch (JsonException expectedTrailingWhitespaceOnly) {
                if (!expectRejection) {
                    return;
                }
                fail(failureMessage);
            }
        } catch (JsonException expected) {
            if (!expectRejection) {
                throw expected;
            }
        } catch (RuntimeException unexpected) {
            if (!expectRejection) {
                throw unexpected;
            }
            fail(failureMessage + " (parser crashed with " + unexpected.getClass().getSimpleName() + ")", unexpected);
        }
    }
}
