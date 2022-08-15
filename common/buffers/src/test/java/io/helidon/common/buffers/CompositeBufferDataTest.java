/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.common.buffers;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class CompositeBufferDataTest {
    static Stream<TestContext> initParams() {
        BufferData array = BufferData.create(BufferData.create("0123456"), BufferData.create("7890"));
        BufferData list = BufferData.create(List.of(BufferData.create("0123456"), BufferData.create("7890")));

        return Stream.of(new TestContext("array", array),
                         new TestContext("list", list));
    }

    @ParameterizedTest
    @MethodSource("initParams")
    void testGet(TestContext context) {
        char[] expected = "01234567890".toCharArray();

        for (int i = 0; i < 11; i++) {
            char found = (char) context.bufferData().get(i);
            assertThat(found, is(expected[i]));
        }
    }

    @ParameterizedTest
    @MethodSource("initParams")
    void testReadString(TestContext context) {
        BufferData combined = context.bufferData();
        combined.read();
        String result = combined.readString(combined.indexOf((byte) '0'));
        assertThat(result, is("123456789"));
    }

    private record TestContext(String name, BufferData bufferData) {
        @Override
        public String toString() {
            return name;
        }
    }
}