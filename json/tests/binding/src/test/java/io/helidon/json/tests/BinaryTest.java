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

import java.util.Base64;

import io.helidon.json.binding.Json;
import io.helidon.json.binding.JsonBinding;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

@Testing.Test
public class BinaryTest {
    private final JsonBinding jsonBinding;

    BinaryTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    @Test
    public void testRoundTripEmpty() {
        assertRoundTrip(new byte[0]);
    }

    @Test
    public void testRoundTripSingleByte() {
        assertRoundTrip(new byte[] {42});
    }

    @Test
    public void testRoundTripTwoBytes() {
        assertRoundTrip(new byte[] {1, 2});
    }

    @Test
    public void testRoundTripThreeBytes() {
        assertRoundTrip(new byte[] {1, 2, 3});
    }

    @Test
    public void testRoundTripAllByteValues() {
        byte[] value = new byte[256];
        for (int i = 0; i < 256; i++) {
            value[i] = (byte) i;
        }
        assertRoundTrip(value);
    }

    @Test
    public void testRoundTripLargeBinary() {
        byte[] value = new byte[8192];
        for (int i = 0; i < value.length; i++) {
            value[i] = (byte) ((i * 31) & 0xFF);
        }
        assertRoundTrip(value);
    }

    @Test
    public void testSerializedAsBase64String() {
        byte[] value = new byte[] {-1, 0, 1, 2, 127};
        String json = jsonBinding.serialize(new BinaryModel(value));

        assertThat(json, is("{\"binary\":\"" + Base64.getEncoder().encodeToString(value) + "\"}"));
    }

    @Test
    public void testDeserializeKnownBase64() {
        String json = "{\"binary\":\"AQIDBAU=\"}";
        BinaryModel model = jsonBinding.deserialize(json, BinaryModel.class);

        assertArrayEquals(new byte[] {1, 2, 3, 4, 5}, model.binary());
    }

    @Test
    public void testMultipleBinaryFieldsRoundTrip() {
        byte[] first = new byte[] {10, 11, 12};
        byte[] second = new byte[] {-1, -2, -3, -4};
        MultiBinaryModel original = new MultiBinaryModel(first, second);

        String json = jsonBinding.serialize(original);
        MultiBinaryModel deserialized = jsonBinding.deserialize(json, MultiBinaryModel.class);

        assertArrayEquals(first, deserialized.binary1());
        assertArrayEquals(second, deserialized.binary2());
    }

    private void assertRoundTrip(byte[] value) {
        BinaryModel original = new BinaryModel(value);
        String json = jsonBinding.serialize(original);
        BinaryModel deserialized = jsonBinding.deserialize(json, BinaryModel.class);
        assertArrayEquals(value, deserialized.binary());
    }

    @Json.Entity
    record BinaryModel(byte[] binary) {
    }

    @Json.Entity
    record MultiBinaryModel(byte[] binary1, byte[] binary2) {
    }
}
