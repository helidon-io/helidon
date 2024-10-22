/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.integrations.db.h2;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit tests for the BitsSubstitution class.
 */
class BitsSubstitutionTest {

    /**
     * Test the compareNotNull method with char arrays.
     */
    @Test
    void testCompareNotNull() {
        char[] data1 = {'a', 'b', 'c'};
        char[] data2 = {'a', 'b', 'c'};
        char[] data3 = {'a', 'b', 'd', 'e'};
        assertThat(BitsSubstitution.compareNotNull(data1, data2), is(0));
        assertThat(BitsSubstitution.compareNotNull(data1, data3), is(-1));
        assertThat(BitsSubstitution.compareNotNull(data3, data1), is(1));
        assertThat(BitsSubstitution.compareNotNull(data2, data3), is(-1));
    }

    /**
     * Test the compareNotNullSigned method with byte arrays.
     */
    @Test
    void testCompareNotNullSigned() {
        byte[] data1 = {1, 2, 3};
        byte[] data2 = {1, 2, 3};
        byte[] data3 = {1, 2, 4};
        assertThat(BitsSubstitution.compareNotNullSigned(data1, data2), is(0));
        assertThat(BitsSubstitution.compareNotNullSigned(data1, data3), is(-1));
        assertThat(BitsSubstitution.compareNotNullSigned(data3, data1), is(1));
    }

    /**
     * Test the compareNotNullUnsigned method with byte arrays.
     */
    @Test
    void testCompareNotNullUnsigned() {
        byte[] data1 = {1, 2, 3};
        byte[] data2 = {1, 2, 3};
        byte[] data3 = {1, 2, 4};
        assertThat(BitsSubstitution.compareNotNullUnsigned(data1, data2), is(0));
        assertThat(BitsSubstitution.compareNotNullUnsigned(data1, data3), is(-1));
        assertThat(BitsSubstitution.compareNotNullUnsigned(data3, data1), is(1));
    }

    /**
     * Test the readInt method.
     */
    @Test
    void testReadInt() {
        byte[] data = {0, 0, 0, 1};
        assertThat(BitsSubstitution.readInt(data, 0), is(1));
    }

    /**
     * Test the readIntLE method.
     */
    @Test
    void testReadIntLE() {
        byte[] data = {1, 0, 0, 0};
        assertThat(BitsSubstitution.readIntLE(data, 0), is(1));
    }

    /**
     * Test the uuidToBytes method.
     */
    @Test
    void testUuidToBytes() {
        UUID uuid = UUID.randomUUID();
        byte[] bytes = BitsSubstitution.uuidToBytes(uuid);
        assertNotNull(bytes);
        assertThat(bytes.length, is(16));
    }

    /**
     * Test the writeInt method.
     */
    @Test
    void testWriteInt() {
        byte[] data = new byte[4];
        BitsSubstitution.writeInt(data, 0, 1);
        assertThat(data, is(new byte[]{0, 0, 0, 1}));
    }

    /**
     * Test the writeLong method.
     */
    @Test
    void testWriteLong() {
        byte[] data = new byte[8];
        BitsSubstitution.writeLong(data, 0, 1L);
        assertThat(data, is(new byte[]{0, 0, 0, 0, 0, 0, 0, 1}));
    }

    /**
     * Test the writeDouble method.
     */
    @Test
    void testWriteDouble() {
        byte[] data = new byte[8];
        BitsSubstitution.writeDouble(data, 0, 1.0);
        assertThat(data, is(new byte[]{63, -16, 0, 0, 0, 0, 0, 0}));
    }
}