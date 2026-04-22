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

package io.helidon.json.tests;

import java.util.Arrays;

import io.helidon.json.binding.Json;
import io.helidon.json.binding.JsonBinding;
import io.helidon.json.smile.SmileConfig;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Tests for Smile format binary data (7-bit encoded and raw binary).
 * Tests Smile binary format serialization/deserialization using JsonBinding.
 * Note: Binary data support requires special handling and may not be available
 * through standard JsonBinding serialization methods.
 *
 * <p>Spec-trace comments quote exact Smile spec section titles and then paraphrase the exercised rule.</p>
 */
@Testing.Test
public class SmileBinaryTest {

    private final JsonBinding jsonBinding;

    SmileBinaryTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    /*
     * Spec: "Token class: Misc; binary / text / structure markers".
     * Rule: default Smile binary uses the non-raw 7-bit-safe binary form, so arbitrary byte patterns must still
     * round-trip.
     */
    @Test
    public void testByteArrayHandling() {
        byte[] data = {1, 2, 3, 4, 5};
        BinaryModel model = new BinaryModel(data);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        BinaryModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, BinaryModel.class);
        assertArrayEquals(data, result.binary());
    }

    @Test
    public void testEmptyByteArray() {
        byte[] data = new byte[0];
        BinaryModel model = new BinaryModel(data);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        BinaryModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, BinaryModel.class);
        assertArrayEquals(data, result.binary());
    }

    @Test
    public void testLargeByteArray() {
        byte[] data = new byte[1024];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 256);
        }
        BinaryModel model = new BinaryModel(data);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        BinaryModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, BinaryModel.class);
        assertArrayEquals(data, result.binary());
    }

    @Test
    public void testMultipleByteArrays() {
        byte[] data1 = {1, 2, 3};
        byte[] data2 = {4, 5, 6, 7};
        MultiBinaryModel model = new MultiBinaryModel(data1, data2);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        MultiBinaryModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, MultiBinaryModel.class);
        assertArrayEquals(data1, result.binary1());
        assertArrayEquals(data2, result.binary2());
    }

    @Test
    public void testRoundTripSizesAndPatterns() {
        int[] sizes = {0, 1, 2, 3, 4, 5, 7, 8, 15, 16, 31, 32, 33, 63, 64, 65, 127, 128, 255, 256, 1023, 1024};
        for (int size : sizes) {
            assertRoundTrip(createPattern(size, 1));
            assertRoundTrip(createPattern(size, 7));
            assertRoundTrip(createPattern(size, 31));
        }
    }

    @Test
    public void testAllByteValues() {
        byte[] data = new byte[256];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }
        assertRoundTrip(data);
    }

    @Test
    public void testConstantPatternData() {
        byte[] zeros = new byte[300];
        byte[] ff = new byte[300];
        Arrays.fill(ff, (byte) 0xFF);

        assertRoundTrip(zeros);
        assertRoundTrip(ff);
    }

    /*
     * Spec: "High-level format" and "Token class: Misc; binary / text / structure markers".
     * Rule: header bit `0x04` gates whether `"raw binary"` may appear, and raw payloads use token `0xFD`.
     */
    @Test
    public void testSmileHeaderRawBinaryFlagDisabledByDefault() {
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, new BinaryModel(new byte[] {1, 2, 3}));

        assertThat((smileData[3] & 0x04), is(0));
        assertThat(findBinaryToken(smileData), is((byte) 0xE8));
    }

    @Test
    public void testSmileHeaderRawBinaryFlagEnabled() {
        SmileConfig config = SmileConfig.builder()
                .rawBinaryEnabled(true)
                .build();
        byte[] smileData =
                SmileBindingSupport.serializeSmile(jsonBinding, new BinaryModel(new byte[] {1, 2, 3}), config);

        assertThat((smileData[3] & 0x04), is(0x04));
        assertThat(findBinaryToken(smileData), is((byte) 0xFD));
    }

    @Test
    public void testRawBinarySmileRoundTrip() {
        byte[] data = createPattern(512, 19);
        SmileConfig config = SmileConfig.builder()
                .rawBinaryEnabled(true)
                .build();

        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, new BinaryModel(data), config);
        BinaryModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, BinaryModel.class);

        assertArrayEquals(data, result.binary());
    }

    private void assertRoundTrip(byte[] data) {
        BinaryModel model = new BinaryModel(data);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        BinaryModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, BinaryModel.class);
        assertArrayEquals(data, result.binary());
    }

    private byte[] createPattern(int size, int step) {
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
            data[i] = (byte) ((i * step + 13) & 0xFF);
        }
        return data;
    }

    private byte findBinaryToken(byte[] data) {
        for (byte datum : data) {
            if (datum == (byte) 0xE8 || datum == (byte) 0xFD) {
                return datum;
            }
        }
        throw new AssertionError("No Smile binary token found in serialized bytes");
    }

    @Json.Entity
    record BinaryModel(byte[] binary) {
    }

    @Json.Entity
    record MultiBinaryModel(byte[] binary1, byte[] binary2) {
    }
}
