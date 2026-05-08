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

package io.helidon.json.smile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;

import io.helidon.json.JsonArray;
import io.helidon.json.JsonBoolean;
import io.helidon.json.JsonGenerator;
import io.helidon.json.JsonKey;
import io.helidon.json.JsonNull;
import io.helidon.json.JsonNumber;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonString;
import io.helidon.json.JsonValue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Verification of Smile generator output against the Smile specification (smile-specification.md).
 *
 * <p>The tests instantiate the Smile generator via
 * {@link io.helidon.json.smile.SmileGenerator#create(java.io.OutputStream)} and assert exact byte-for-byte output
 * including the 4-byte header and the optional end-of-content marker (0xFF).
 * Each case targets a specific rule from the Smile spec to ensure no "ish" validations sneak in.</p>
 *
 * <p>Spec-trace comments quote exact Smile spec section titles and then paraphrase the exercised rule.</p>
 */
class SmileGeneratorTest {

    private static final byte HEADER_0 = 0x3A;
    private static final byte HEADER_1 = 0x29;
    private static final byte HEADER_2 = 0x0A;
    private static final byte HEADER_FEATURES = 0x01;

    /*
     * Spec: "High-level format".
     * Rule: Smile output starts with the 4-byte header, and top-level output may end with the
     * "optional end marker, 0xFF".
     */
    @Test
    void writesHeaderAndEndMarkerOnly() {
        byte[] actual = generateBytes(gen -> {
        });

        byte[] expected = bytes(HEADER_0, HEADER_1, HEADER_2, HEADER_FEATURES);
        assertArrayEquals(expected, actual, "Header + end-of-content marker must be present");
    }

    /*
     * Spec: "Token class: Small integers".
     * Rule: values from -16 to +15 are encoded inline in a single token byte after zigzag transformation.
     */
    @Test
    void encodesSmallIntegerInline() {
        // value 1 → zigzag 2 → token 0xC0 | 0x02 = 0xC2
        byte[] actual = generateBytes(gen -> gen.write(1));

        byte[] expected = bytes(HEADER_0, HEADER_1, HEADER_2, HEADER_FEATURES, (byte) 0xC2);
        assertArrayEquals(expected, actual);
    }

    @Test
    void encodesSmallIntegerLowerAndUpperBounds() {
        // -16 -> zigzag 31 -> 0xC0 | 0x1F
        byte[] actualMin = generateBytes(gen -> gen.write(-16));
        byte[] expectedMin = bytes(HEADER_0, HEADER_1, HEADER_2, HEADER_FEATURES, (byte) 0xDF);
        assertArrayEquals(expectedMin, actualMin);

        // +15 -> zigzag 30 -> 0xC0 | 0x1E
        byte[] actualMax = generateBytes(gen -> gen.write(15));
        byte[] expectedMax = bytes(HEADER_0, HEADER_1, HEADER_2, HEADER_FEATURES, (byte) 0xDE);
        assertArrayEquals(expectedMax, actualMax);
    }

    /*
     * Spec: "Token class: Simple literals, numbers".
     * Rule: larger integers use explicit int tokens plus signed VInt payloads; `BigInteger` and `BigDecimal` switch to
     * length-prefixed 7-bit-safe binary forms.
     */
    @Test
    void encodesInt32WithVInt() {
        // value 300 → zigzag 600 (0x258)
        // VInt (MSB-first): digits LSB-first = [0x98, 0x09] → write reversed [0x09, 0x98]
        byte[] actual = generateBytes(gen -> gen.write(300));

        byte[] expected = bytes(HEADER_0, HEADER_1, HEADER_2, HEADER_FEATURES,
                                (byte) 0x24, // INT32 token
                                (byte) 0x09, (byte) 0x98);
        assertArrayEquals(expected, actual);
    }

    @Test
    void encodesInt32MaxValue() {
        byte[] actual = generateBytes(gen -> gen.write(Integer.MAX_VALUE));

        // token 0x24 + VInt(zigzag(MAX_VALUE)) => zigzag = & 0xFFFFFFFFL
        byte[] expected = concat(bytes(HEADER_0, HEADER_1, HEADER_2, HEADER_FEATURES, (byte) 0x24),
                                 encodeVInt(zigzagInt(Integer.MAX_VALUE) & 0xFFFFFFFFL));
        assertArrayEquals(expected, actual);
    }


    @Test
    void encodesBigIntegerUsingSafeBinary() {
        BigInteger value = new BigInteger("123456789012345678901234567890");
        byte[] magnitude = value.toByteArray();

        byte[] actual = generateBytes(gen -> gen.write(value));

        byte[] expected = concat(bytes(HEADER_0, HEADER_1, HEADER_2, HEADER_FEATURES, (byte) 0x26),
                                 concat(encodeVInt(magnitude.length), encode7Bit(magnitude)));
        assertArrayEquals(expected, actual);
    }

    @Test
    void encodesBigDecimalUsingScaleAndSafeBinary() {
        BigDecimal value = new BigDecimal("1234567890.12345");
        byte[] magnitude = value.unscaledValue().toByteArray();

        byte[] actual = generateBytes(gen -> gen.write(value));

        byte[] expected = concat(bytes(HEADER_0, HEADER_1, HEADER_2, HEADER_FEATURES, (byte) 0x2A),
                                 concat(encodeVInt(zigzagInt(value.scale()) & 0xFFFFFFFFL),
                                 concat(encodeVInt(magnitude.length), encode7Bit(magnitude))));
        assertArrayEquals(expected, actual);
    }

    @Test
    void encodesNegativeScaleBigDecimalUsingDecimalToken() {
        BigDecimal value = new BigDecimal("1E+3");
        byte[] magnitude = value.unscaledValue().toByteArray();

        byte[] actual = generateBytes(gen -> gen.write(value));

        byte[] expected = concat(bytes(HEADER_0, HEADER_1, HEADER_2, HEADER_FEATURES, (byte) 0x2A),
                                 concat(encodeVInt(zigzagInt(value.scale()) & 0xFFFFFFFFL),
                                 concat(encodeVInt(magnitude.length), encode7Bit(magnitude))));
        assertArrayEquals(expected, actual);
    }

    /*
     * Spec: "Shared value Strings" and "Avoiding references 0x??FE and 0x??FF".
     * Rule: shared-value references use a 1024-entry window, must not encode forbidden low bytes, and reset cleanly
     * when the window fills.
     */
    @Test
    void encodesLongSharedValueReferenceSkippingForbiddenLowBytes() {
        byte[] actual = generateSharedValueBytes(gen -> {
            gen.writeArrayStart();
            for (int i = 0; i <= 256; i++) {
                gen.write("v" + i);
            }
            gen.write("v256");
            gen.writeArrayEnd();
        });

        int refPos = indexOf(actual, new byte[] {(byte) 0xED, 0x00});
        assertThat("expected long shared value reference for index 256", refPos >= 0, is(true));
        assertThat(actual[refPos - 1], is((byte) '6'));
    }

    @Test
    void doesNotEncodeLongSharedValueReferenceForForbiddenLowByteIndex254() {
        byte[] actual = generateSharedValueBytes(gen -> {
            gen.writeArrayStart();
            for (int i = 0; i <= 254; i++) {
                gen.write("v" + i);
            }
            gen.write("v254");
            gen.writeArrayEnd();
        });

        assertThat("generator must avoid long shared value references with low byte 0xFE",
                   indexOf(actual, new byte[] {(byte) 0xEC, (byte) 0xFE}) >= 0,
                   is(false));
    }

    @Test
    void resetsSharedValueReferenceWindowAfter1024Entries() {
        byte[] actual = generateSharedValueBytes(gen -> {
            gen.writeArrayStart();
            for (int i = 0; i < 1024; i++) {
                gen.write("v" + i);
            }
            gen.write("v0");
            gen.writeArrayEnd();
        });

        byte[] first = generateSharedValueBytes(gen -> gen.write("v0"));
        byte[] second = generateSharedValueBytes(gen -> {
            gen.writeArrayStart();
            for (int i = 0; i < 1024; i++) {
                gen.write("v" + i);
            }
            gen.write("v0");
            gen.writeArrayEnd();
        });

        assertThat("after the 1024-entry reset, the repeated value should be encoded as a fresh plain string like a "
                           + "first write",
                   indexOf(second, slice(first, 4, first.length)) >= 0,
                   is(true));
    }

    /*
     * Spec: "Shared key name Strings" and "Avoiding references 0x??FE and 0x??FF".
     * Rule: object-key references follow the same 1024-entry window and forbidden-low-byte rules as shared values.
     */
    @Test
    void encodesLongSharedKeyReferenceSkippingForbiddenLowBytes() {
        byte[] actual = generateBytes(gen -> {
            gen.writeObjectStart();
            for (int i = 0; i <= 256; i++) {
                gen.write("k" + i, i);
            }
            gen.write("k256", 999);
            gen.writeObjectEnd();
        });

        int refPos = indexOf(actual, new byte[] {0x31, 0x00});
        assertThat("expected long shared key reference for index 256", refPos >= 0, is(true));
        assertThat(actual[refPos + 2], is((byte) 0x24));
    }

    @Test
    void doesNotEncodeLongSharedKeyReferenceForForbiddenLowByteIndex254() {
        byte[] actual = generateBytes(gen -> {
            gen.writeObjectStart();
            for (int i = 0; i <= 254; i++) {
                gen.write("k" + i, i);
            }
            gen.write("k254", 999);
            gen.writeObjectEnd();
        });

        assertThat("generator must avoid long shared key references with low byte 0xFE",
                   indexOf(actual, new byte[] {0x30, (byte) 0xFE}) >= 0
                           || indexOf(actual, new byte[] {0x31, (byte) 0xFE}) >= 0
                           || indexOf(actual, new byte[] {0x32, (byte) 0xFE}) >= 0
                           || indexOf(actual, new byte[] {0x33, (byte) 0xFE}) >= 0,
                   is(false));
    }

    @Test
    void resetsSharedKeyReferenceWindowAfter1024Entries() {
        byte[] actual = generateBytes(gen -> {
            gen.writeObjectStart();
            for (int i = 0; i < 1024; i++) {
                gen.write("k" + i, i);
            }
            gen.write("k0", 999);
            gen.writeObjectEnd();
        });

        byte[] first = generateBytes(gen -> gen.writeObjectStart().write("k0", 999).writeObjectEnd());
        assertThat("after the 1024-entry reset, the repeated key should be encoded as a fresh plain key/value pair "
                           + "like a first write",
                   indexOf(actual, slice(first, 4, first.length - 4)) >= 0,
                   is(true));
    }

    /*
     * Spec: "Low-level Format" and "Token class: Simple literals, numbers".
     * Rule: `float` and `double` payload bits are emitted in the fixed big-endian 7-bit chunk layout described by the
     * spec examples.
     */
    @Test
    void encodesFloatExactlyAsSpecifiedExample() {
        // Spec example: 29.9510f => raw bits 0x41ef9ba6 => encoded 0x04 0x0F 0x3E 0x37 0x26
        byte[] actual = generateBytes(gen -> gen.write(29.9510f));

        byte[] expected = bytes(HEADER_0, HEADER_1, HEADER_2, HEADER_FEATURES,
                                (byte) 0x28, // float token
                                (byte) 0x04, (byte) 0x0F, (byte) 0x3E, (byte) 0x37, (byte) 0x26);
        assertArrayEquals(expected, actual);
    }

    @Test
    void encodesDoubleExactlyAsSpecifiedExample() {
        // Spec example: -29.9510d => raw bits 0xc03df374bc6a7efa => encoded bytes below
        byte[] actual = generateBytes(gen -> gen.write(-29.9510d));

        byte[] expected = bytes(HEADER_0, HEADER_1, HEADER_2, HEADER_FEATURES,
                                (byte) 0x29, // double token
                                (byte) 0x01, (byte) 0x40, (byte) 0x1e, (byte) 0x7c, (byte) 0x6e,
                                (byte) 0x4b, (byte) 0x63, (byte) 0x29, (byte) 0x7d, (byte) 0x7a);
        assertArrayEquals(expected, actual);
    }

    @Test
    void encodesFloatAndDoubleEdgeCases() {
        // +0.0f
        byte[] actualPosZero = generateBytes(gen -> gen.write(0.0f));
        byte[] expectedPosZero = concat(bytes(HEADER_0, HEADER_1, HEADER_2, HEADER_FEATURES, (byte) 0x28),
                                        encodeFloatBits(Float.floatToRawIntBits(0.0f)));
        assertArrayEquals(expectedPosZero, actualPosZero);

        // NaN double
        byte[] actualNaN = generateBytes(gen -> gen.write(Double.NaN));
        byte[] expectedNaN = concat(bytes(HEADER_0, HEADER_1, HEADER_2, HEADER_FEATURES, (byte) 0x29),
                                    encodeDoubleBits(Double.doubleToRawLongBits(Double.NaN)));
        assertArrayEquals(expectedNaN, actualNaN);
    }

    /*
     * Spec: "Token classes: Tiny ASCII, Short ASCII", "Token classes: Tiny Unicode, Short Unicode",
     * and "Token class: Misc; binary / text / structure markers".
     * Rule: short strings are classified by UTF-8 byte length, while long strings switch to the long-text token family
     * terminated by `0xFC`.
     */
    @Test
    void encodesTinyAsciiString() {
        // "Hello" length=5 → Tiny ASCII token 0x40 | (5-1) = 0x44
        byte[] actual = generateBytes(gen -> gen.write("Hello"));

        byte[] expected = concat(bytes(HEADER_0, HEADER_1, HEADER_2, HEADER_FEATURES, (byte) 0x44),
                                 "Hello".getBytes(StandardCharsets.UTF_8));
        assertArrayEquals(expected, actual);
    }

    @Test
    void encodesTinyAsciiMaxBoundaryLength32() {
        String stringValue = "d".repeat(32);
        byte[] ascii = stringValue.getBytes(StandardCharsets.UTF_8);
        byte[] actual = generateBytes(gen -> gen.write(stringValue));

        byte[] expected = concat(bytes(HEADER_0, HEADER_1, HEADER_2, HEADER_FEATURES,
                                       (byte) (0x40 | (32 - 1))), ascii);
        assertArrayEquals(expected, actual);
    }

    @Test
    void encodesShortAsciiBoundaryLength33() {
        // length=33 ASCII → Short ASCII token 0x60 | (33-33) = 0x60
        String stringValue = "a".repeat(33);
        byte[] ascii = stringValue.getBytes(StandardCharsets.UTF_8);
        byte[] actual = generateBytes(gen -> gen.write(stringValue));

        byte[] expected = concat(bytes(HEADER_0, HEADER_1, HEADER_2, HEADER_FEATURES, (byte) 0x60), ascii);
        assertArrayEquals(expected, actual);
    }

    @Test
    void encodesShortAsciiMaxBoundaryLength64() {
        String stringValue = "c".repeat(64);
        byte[] ascii = stringValue.getBytes(StandardCharsets.UTF_8);
        byte[] actual = generateBytes(gen -> gen.write(stringValue));

        byte[] expected = concat(bytes(HEADER_0, HEADER_1, HEADER_2, HEADER_FEATURES,
                                       (byte) (0x60 | (64 - 33))), ascii);
        assertArrayEquals(expected, actual);
    }

    @Test
    void encodesLongAsciiWithTerminator() {
        // length=70 ASCII → Long ASCII token 0xE0, raw bytes, terminator 0xFC
        byte[] ascii = "b".repeat(70).getBytes(StandardCharsets.UTF_8);
        byte[] actual = generateBytes(gen -> gen.write(new String(ascii, StandardCharsets.US_ASCII)));

        byte[] expected = concat(bytes(HEADER_0, HEADER_1, HEADER_2, HEADER_FEATURES, (byte) 0xE0),
                                 ascii,
                                 new byte[] {(byte) 0xFC});
        assertArrayEquals(expected, actual);
    }

    @Test
    void encodesLongUnicodeString() {
        String longUnicode = "世界".repeat(20); // each is 3 bytes UTF-8, total 120 bytes > 65
        byte[] utf8 = longUnicode.getBytes(StandardCharsets.UTF_8);
        byte[] actual = generateBytes(gen -> gen.write(longUnicode));

        byte[] expected = concat(bytes(HEADER_0, HEADER_1, HEADER_2, HEADER_FEATURES, (byte) 0xE4),
                                 utf8,
                                 new byte[] {(byte) 0xFC});
        assertArrayEquals(expected, actual);
    }

    @Test
    void encodesTinyUnicodeString() {
        // "€" UTF-8 = E2 82 AC, length=3 bytes → Tiny Unicode token 0x80 | (3-2) = 0x81
        byte[] utf8 = "€".getBytes(StandardCharsets.UTF_8);
        byte[] actual = generateBytes(gen -> gen.write("€"));

        byte[] expected = concat(bytes(HEADER_0, HEADER_1, HEADER_2, HEADER_FEATURES, (byte) 0x81),
                                 utf8);
        assertArrayEquals(expected, actual);
    }

    @Test
    void encodesShortUnicodeMaxBoundaryLength65() {
        // build 65-byte UTF-8 string: 32 x 'é' (2 bytes) + 'a' (1 byte) => 65 bytes
        String value = "é".repeat(32) + "a";
        byte[] utf8 = value.getBytes(StandardCharsets.UTF_8); // length 65
        byte[] actual = generateBytes(gen -> gen.write(value));

        byte[] expected = concat(bytes(HEADER_0, HEADER_1, HEADER_2, HEADER_FEATURES, (byte) (0xA0 | (65 - 34))),
                                 utf8);
        assertArrayEquals(expected, actual);
    }

    /*
     * Spec: "Tokens: key mode".
     * Rule: object field names use the dedicated key-mode token families, not the value-mode string token families.
     */
    @Test
    void encodesObjectWithAsciiKeyAndValue() {
        // Object { "name": "x" }
        // START_OBJECT 0xFA
        // key "name" length=4 ASCII -> key token 0x80 | (4-1)=0x83
        // value "x" length=1 ASCII -> Tiny ASCII token 0x40 |0=0x40 + 'x'
        // END_OBJECT 0xFB, then 0xFF
        byte[] actual = generateBytes(gen -> gen.writeObjectStart()
                .write("name", "x")
                .writeObjectEnd());

        byte[] expected = concat(bytes(HEADER_0, HEADER_1, HEADER_2, HEADER_FEATURES,
                                       (byte) 0xFA, // start object
                                       (byte) 0x83), // key name token
                                 concat("name".getBytes(StandardCharsets.UTF_8),
                                        bytes((byte) 0x40, (byte) 'x', (byte) 0xFB)));
        assertArrayEquals(expected, actual);
    }

    @Test
    void encodesPrecomputedJsonKeyInKeyMode() {
        JsonKey key = JsonKey.create("value");

        byte[] actual = generateBytes(gen -> gen.writeObjectStart()
                .write(key, true)
                .writeObjectEnd());

        byte[] expected = concat(bytes(HEADER_0, HEADER_1, HEADER_2, HEADER_FEATURES,
                                       (byte) 0xFA,
                                       (byte) 0x84),
                                 concat("value".getBytes(StandardCharsets.UTF_8),
                                        bytes((byte) 0x23, (byte) 0xFB)));
        assertArrayEquals(expected, actual);
    }

    /*
     * Spec: "Shared value Strings".
     * Rule: value-string sharing is feature-flagged, limited to the shareable token classes, and excludes 65-byte
     * Unicode values from being referenced.
     */
    @Test
    void encodesSharedValueStringReference() {
        byte[] actual = generateSharedValueBytes(
                gen -> gen.writeArrayStart()
                        .write("repeat")
                        .write("repeat")
                        .writeArrayEnd());

        byte[] expected = bytes(HEADER_0, HEADER_1, HEADER_2, (byte) 0x03,
                                (byte) 0xF8,
                                (byte) 0x45,
                                (byte) 'r', (byte) 'e', (byte) 'p', (byte) 'e', (byte) 'a', (byte) 't',
                                (byte) 0x01,
                                (byte) 0xF9);
        assertArrayEquals(expected, actual);
    }

    @Test
    void doesNotShare65ByteUnicodeValueStrings() {
        String value = "é".repeat(32) + "a";
        byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);

        byte[] actual = generateSharedValueBytes(
                gen -> gen.writeArrayStart()
                        .write(value)
                        .write(value)
                        .writeArrayEnd());

        byte[] expected = concat(bytes(HEADER_0, HEADER_1, HEADER_2, (byte) 0x03,
                                       (byte) 0xF8,
                                       (byte) (0xA0 | (utf8.length - 34))),
                                 concat(utf8,
                                        concat(bytes((byte) (0xA0 | (utf8.length - 34))),
                                               concat(utf8, bytes((byte) 0xF9)))));
        assertArrayEquals(expected, actual);
    }

    @Test
    void disablesSharedValueStringReferencesViaConstructorFlag() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (JsonGenerator generator = SmileGenerator.create(baos)) {
            generator.writeArrayStart()
                    .write("repeat")
                    .write("repeat")
                    .writeArrayEnd();
        }
        byte[] actual = baos.toByteArray();

        byte[] expected = bytes(HEADER_0, HEADER_1, HEADER_2, (byte) 0x01,
                                (byte) 0xF8,
                                (byte) 0x45,
                                (byte) 'r', (byte) 'e', (byte) 'p', (byte) 'e', (byte) 'a', (byte) 't',
                                (byte) 0x45,
                                (byte) 'r', (byte) 'e', (byte) 'p', (byte) 'e', (byte) 'a', (byte) 't',
                                (byte) 0xF9);
        assertArrayEquals(expected, actual);
    }

    /*
     * Spec: "Shared key name Strings".
     * Rule: key-name sharing is independently feature-flagged and applies only to key-mode tokens.
     */
    @Test
    void encodesSharedKeyReference() {
        byte[] actual = generateBytes(gen -> gen.writeObjectStart()
                .write("name", "a")
                .write("name", "b")
                .writeObjectEnd());

        byte[] expected = bytes(HEADER_0, HEADER_1, HEADER_2, HEADER_FEATURES,
                                (byte) 0xFA,
                                (byte) 0x83, (byte) 'n', (byte) 'a', (byte) 'm', (byte) 'e',
                                (byte) 0x40, (byte) 'a',
                                (byte) 0x40,
                                (byte) 0x40, (byte) 'b',
                                (byte) 0xFB);
        assertArrayEquals(expected, actual);
    }

    @Test
    void disablesSharedKeyReferencesViaConfig() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SmileConfig config = SmileConfig.builder()
                .sharedKeyStrings(false)
                .build();
        try (JsonGenerator generator = SmileGenerator.create(baos, config)) {
            generator.writeObjectStart()
                    .write("name", "a")
                    .write("name", "b")
                    .writeObjectEnd();
        }

        byte[] actual = baos.toByteArray();
        byte[] expected = bytes(HEADER_0, HEADER_1, HEADER_2, (byte) 0x00,
                                (byte) 0xFA,
                                (byte) 0x83, (byte) 'n', (byte) 'a', (byte) 'm', (byte) 'e',
                                (byte) 0x40, (byte) 'a',
                                (byte) 0x83, (byte) 'n', (byte) 'a', (byte) 'm', (byte) 'e',
                                (byte) 0x40, (byte) 'b',
                                (byte) 0xFB);
        assertArrayEquals(expected, actual);
    }

    @Test
    void encodesObjectWithUnicodeKeyAndValue() {
        // { "ключ": "знач" }
        String key = "ключ"; // UTF-8 len > 4, non-ASCII
        String val = "знач";
        byte[] keyUtf8 = key.getBytes(StandardCharsets.UTF_8);
        byte[] valUtf8 = val.getBytes(StandardCharsets.UTF_8);
        byte keyToken = (byte) (0xC0 | (keyUtf8.length - 2)); // short unicode key
        byte valToken = (byte) (0x80 | (valUtf8.length - 2)); // tiny unicode value

        byte[] actual = generateBytes(gen -> gen.writeObjectStart()
                .write(key, val)
                .writeObjectEnd());

        byte[] expected = concat(bytes(HEADER_0, HEADER_1, HEADER_2, HEADER_FEATURES,
                                       (byte) 0xFA, // start object
                                       keyToken),
                                 concat(keyUtf8,
                                        concat(bytes(valToken),
                                               concat(valUtf8, bytes((byte) 0xFB)))));
        assertArrayEquals(expected, actual);
    }

    /*
     * Spec: "High-level format".
     * Rule: start/end object and array markers compose recursively, so mixed nested structures have deterministic
     * framing.
     */
    @Test
    void encodesArrayWithMixedValues() {
        // [ true, null, -1 ]
        // START_ARRAY 0xF8, TRUE 0x23, NULL 0x21, small int -1 → zigzag 1 => 0xC1, END_ARRAY 0xF9
        byte[] actual = generateBytes(gen -> gen.writeArrayStart()
                .write(true)
                .writeNull()
                .write(-1)
                .writeArrayEnd());

        byte[] expected = bytes(HEADER_0, HEADER_1, HEADER_2, HEADER_FEATURES,
                                (byte) 0xF8, // start array
                                (byte) 0x23, // true
                                (byte) 0x21, // null
                                (byte) 0xC1, // small int -1
                                (byte) 0xF9); // end array
        assertArrayEquals(expected, actual);
    }

    @Test
    void encodesNestedArrayAndObject() {
        byte[] actual = generateBytes(gen -> gen.writeArrayStart()
                .writeObjectStart()
                .write("n", 1)
                .writeObjectEnd()
                .writeArrayStart()
                .write(false)
                .writeArrayEnd()
                .writeArrayEnd());

        byte[] expected = bytes(HEADER_0, HEADER_1, HEADER_2, HEADER_FEATURES,
                                (byte) 0xF8, // root array
                                (byte) 0xFA, // object start
                                (byte) 0x80, // key len=1 ASCII
                                (byte) 'n',
                                (byte) 0xC2, // small int 1
                                (byte) 0xFB, // object end
                                (byte) 0xF8, // nested array
                                (byte) 0x22, // false
                                (byte) 0xF9, // nested array end
                                (byte) 0xF9); // root array end
        assertArrayEquals(expected, actual);
    }

    // ---------------------------------------------------------------------
    // JsonValue coverage (write(JsonValue) path)
    // ---------------------------------------------------------------------

    /*
     * Spec: "High-level format" together with the scalar token sections above.
     * Rule: generic `JsonValue` dispatch must land on the same on-wire Smile tokens as the typed generator methods.
     */
    @Test
    void encodesJsonValueString() {
        JsonValue value = JsonString.create("hi");
        byte[] actual = generateBytes(gen -> gen.write(value));

        byte[] expected = concat(bytes(HEADER_0, HEADER_1, HEADER_2, HEADER_FEATURES, (byte) 0x41),
                                 "hi".getBytes(StandardCharsets.UTF_8));
        assertArrayEquals(expected, actual);
    }

    @Test
    void encodesJsonValueNumberPositiveAndNegative() {
        // Positive 42 -> zigzag 84 -> INT32 + VInt(84) => bytes [0x01, 0x94]
        byte[] actual = generateBytes(gen -> gen.write(JsonNumber.create(BigDecimal.valueOf(42))));

        byte[] expected = concat(bytes(HEADER_0, HEADER_1, HEADER_2, HEADER_FEATURES, (byte) 0x24),
                                 encodeVInt(zigzagInt(42)));
        assertArrayEquals(expected, actual);

        // Negative -17 -> zigzag 33 -> INT32 + VInt(33) => single byte 0xA1
        byte[] actualNeg = generateBytes(gen -> gen.write(JsonNumber.create(BigDecimal.valueOf(-17))));

        byte[] expectedNeg = concat(bytes(HEADER_0, HEADER_1, HEADER_2, HEADER_FEATURES, (byte) 0x24),
                                    encodeVInt(zigzagInt(-17)));
        assertArrayEquals(expectedNeg, actualNeg);
    }

    @Test
    void encodesJsonValueBooleanAndNull() {
        byte[] actualTrue = generateBytes(gen -> gen.write(JsonBoolean.TRUE));
        byte[] expectedTrue = bytes(HEADER_0, HEADER_1, HEADER_2, HEADER_FEATURES,
                                    (byte) 0x23);
        assertArrayEquals(expectedTrue, actualTrue);

        byte[] actualFalse = generateBytes(gen -> gen.write(JsonBoolean.FALSE));
        byte[] expectedFalse = bytes(HEADER_0, HEADER_1, HEADER_2, HEADER_FEATURES,
                                     (byte) 0x22);
        assertArrayEquals(expectedFalse, actualFalse);

        byte[] actualNull = generateBytes(gen -> gen.write(JsonNull.instance()));
        byte[] expectedNull = bytes(HEADER_0, HEADER_1, HEADER_2, HEADER_FEATURES,
                                    (byte) 0x21);
        assertArrayEquals(expectedNull, actualNull);
    }

    @Test
    void encodesJsonValueArray() {
        // JsonArray [1, "a"]
        JsonArray array = JsonArray.create(java.util.List.of(JsonNumber.create(BigDecimal.valueOf(1)),
                                                             JsonString.create("a")));
        byte[] actual = generateBytes(gen -> gen.write(array));

        byte[] expected = bytes(HEADER_0, HEADER_1, HEADER_2, HEADER_FEATURES,
                                (byte) 0xF8, // start array
                                (byte) 0xC2, // small int 1 (zigzag 2)
                                (byte) 0x40, // Tiny ASCII len=1
                                (byte) 'a',
                                (byte) 0xF9); // end array
        assertArrayEquals(expected, actual);
    }

    @Test
    void encodesJsonValueObject() {
        // JsonObject { "k": 2 }
        JsonObject object = JsonObject.create(Map.of("k", JsonNumber.create(BigDecimal.valueOf(2))));
        byte[] actual = generateBytes(gen -> gen.write(object));

        byte[] expected = bytes(HEADER_0, HEADER_1, HEADER_2, HEADER_FEATURES,
                                (byte) 0xFA, // start object
                                (byte) 0x80, // key token len=1 ASCII
                                (byte) 'k',
                                (byte) 0xC4, // small int 2 (zigzag 4)
                                (byte) 0xFB); // end object
        assertArrayEquals(expected, actual);
    }

    /*
     * Spec: "High-level format" and "Token class: Misc; binary / text / structure markers".
     * Rule: binary defaults to 7-bit-safe encoding, and header bit `0x04` gates whether `"raw binary"` token `0xFD`
     * may be emitted instead.
     */
    @Test
    void encodesBinaryAs7BitWhenRawBinaryDisabled() {
        byte[] payload = new byte[] {1, 2, 3, 4, 5};
        byte[] actual = generateBytes(gen -> gen.writeBinary(payload));

        assertThat("header features should have only shared-key flag by default", actual[3], is((byte) 0x01));
        assertThat("binary token should be 7-bit binary token", actual[4], is((byte) 0xE8));
    }

    @Test
    void encodesBinaryAsRawWhenRawBinaryEnabled() {
        byte[] payload = new byte[] {1, 2, 3, 4, 5};
        SmileConfig config = SmileConfig.builder()
                .rawBinaryEnabled(true)
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (JsonGenerator generator = SmileGenerator.create(baos, config)) {
            generator.writeBinary(payload);
        }

        byte[] actual = baos.toByteArray();
        assertThat("header features should include raw binary flag", actual[3], is((byte) 0x05));
        assertThat("binary token should be raw binary token", actual[4], is((byte) 0xFD));
        assertThat("raw length 5 should be VInt encoded as 0x85", actual[5], is((byte) 0x85));
        assertThat(actual[6], is((byte) 0x01));
        assertThat(actual[7], is((byte) 0x02));
        assertThat(actual[8], is((byte) 0x03));
        assertThat(actual[9], is((byte) 0x04));
        assertThat(actual[10], is((byte) 0x05));
    }

    private static byte[] encodeVInt(long value) {
        byte[] digits = new byte[10];
        int pos = 0;
        digits[pos++] = (byte) (0x80 | (value & 0x3F));
        value >>>= 6;
        while (value != 0) {
            digits[pos++] = (byte) (value & 0x7F);
            value >>>= 7;
        }
        byte[] out = new byte[pos];
        for (int i = 0; i < pos; i++) {
            out[i] = digits[pos - 1 - i];
        }
        return out;
    }


    private static byte[] encode7Bit(byte[] raw) {
        if (raw.length == 0) {
            return new byte[0];
        }
        byte[] out = new byte[(raw.length * 8 + 6) / 7];
        int outIx = 0;
        int accumulator = 0;
        int bitsHeld = 0;
        for (byte rawByte : raw) {
            accumulator = (accumulator << 8) | (rawByte & 0xFF);
            bitsHeld += 8;
            while (bitsHeld >= 7) {
                bitsHeld -= 7;
                out[outIx++] = (byte) ((accumulator >> bitsHeld) & 0x7F);
            }
        }
        if (bitsHeld > 0) {
            out[outIx++] = (byte) (accumulator & ((1 << bitsHeld) - 1));
        }
        return out;
    }

    private static int indexOf(byte[] array, byte[] target) {
        outer:
        for (int i = 0; i <= array.length - target.length; i++) {
            for (int j = 0; j < target.length; j++) {
                if (array[i + j] != target[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static byte[] slice(byte[] array, int fromInclusive, int toExclusive) {
        byte[] result = new byte[toExclusive - fromInclusive];
        System.arraycopy(array, fromInclusive, result, 0, result.length);
        return result;
    }

    private static byte[] encodeFloatBits(int bits) {
        return bytes((byte) ((bits >>> 28) & 0x0F),
                     (byte) ((bits >>> 21) & 0x7F),
                     (byte) ((bits >>> 14) & 0x7F),
                     (byte) ((bits >>> 7) & 0x7F),
                     (byte) (bits & 0x7F));
    }

    private static byte[] encodeDoubleBits(long bits) {
        return bytes((byte) ((bits >>> 63) & 0x01),
                     (byte) ((bits >>> 56) & 0x7F),
                     (byte) ((bits >>> 49) & 0x7F),
                     (byte) ((bits >>> 42) & 0x7F),
                     (byte) ((bits >>> 35) & 0x7F),
                     (byte) ((bits >>> 28) & 0x7F),
                     (byte) ((bits >>> 21) & 0x7F),
                     (byte) ((bits >>> 14) & 0x7F),
                     (byte) ((bits >>> 7) & 0x7F),
                     (byte) (bits & 0x7F));
    }

    private static int zigzagInt(int n) {
        return (n << 1) ^ (n >> 31);
    }

    private static byte[] generateBytes(Consumer<JsonGenerator> writer) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (JsonGenerator generator = SmileGenerator.create(baos)) {
            writer.accept(generator);
        }
        return baos.toByteArray();
    }

    private static byte[] generateSharedValueBytes(Consumer<JsonGenerator> writer) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SmileConfig config = SmileConfig.builder()
                .sharedValueStrings(true)
                .build();
        try (JsonGenerator generator = SmileGenerator.create(baos, config)) {
            writer.accept(generator);
        }
        return baos.toByteArray();
    }

    private static byte[] bytes(byte... values) {
        return values;
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] out = new byte[first.length + second.length];
        System.arraycopy(first, 0, out, 0, first.length);
        System.arraycopy(second, 0, out, first.length, second.length);
        return out;
    }

    private static byte[] concat(byte[] first, byte[] second, byte[] third) {
        byte[] out = new byte[first.length + second.length + third.length];
        System.arraycopy(first, 0, out, 0, first.length);
        System.arraycopy(second, 0, out, first.length, second.length);
        System.arraycopy(third, 0, out, first.length + second.length, third.length);
        return out;
    }
}
