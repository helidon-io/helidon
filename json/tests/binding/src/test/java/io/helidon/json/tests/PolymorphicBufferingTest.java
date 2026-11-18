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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import io.helidon.common.GenericType;
import io.helidon.json.JsonException;
import io.helidon.json.JsonGenerator;
import io.helidon.json.JsonParser;
import io.helidon.json.binding.Json;
import io.helidon.json.binding.JsonBinding;
import io.helidon.json.binding.JsonBindingConfigurator;
import io.helidon.json.binding.JsonDeserializer;
import io.helidon.json.smile.SmileGenerator;
import io.helidon.json.smile.SmileParser;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testing.Test
class PolymorphicBufferingTest {

    private final JsonBinding binding;

    PolymorphicBufferingTest(JsonBinding binding) {
        this.binding = binding;
    }

    @ParameterizedTest(name = "{0}, buffer={1}, read chunk={2}")
    @MethodSource("inputs")
    void largeSuffixDoesNotExtendDiscriminatorBuffering(Codec codec, int bufferSize, int chunkSize) {
        List<Integer> numbers = IntStream.range(0, 4096).boxed().toList();
        String array = numbers.stream().map(String::valueOf).collect(Collectors.joining(","));
        String suffix = "\"count\":2,\"nested\":{\"kind\":\"nested\",\"numbers\":[" + array + "]}}";
        for (String prefix : List.of("{\"kind\":\"sample\",\"name\":\"x\",",
                                     "{\"name\":\"x\",\"kind\":\"sample\",")) {
            byte[] bytes = codec.encode(prefix + suffix);
            ProbeInputStream input = new ProbeInputStream(bytes, chunkSize, Math.max(512, bufferSize * 2));
            List<Integer> selectionPositions = new ArrayList<>();
            JsonBinding observed = JsonBinding.builder()
                    .addDeserializer(new JsonDeserializer<PolymorphicOrderTest.Sample>() {
                        private JsonDeserializer<PayloadData> delegate;

                        @Override
                        public void configure(JsonBindingConfigurator configurator) {
                            delegate = configurator.deserializer(PayloadData.class);
                        }

                        @Override
                        public PolymorphicOrderTest.Sample deserialize(JsonParser parser) {
                            selectionPositions.add(input.position);
                            PayloadData data = delegate.deserialize(parser);
                            return new PolymorphicOrderTest.Sample(data.name(), data.count(), data.nested());
                        }

                        @Override
                        public GenericType<PolymorphicOrderTest.Sample> type() {
                            return GenericType.create(PolymorphicOrderTest.Sample.class);
                        }
                    })
                    .build();
            PolymorphicOrderTest.Base result = observed.deserialize(codec.parser(input, bufferSize),
                                                                   PolymorphicOrderTest.Base.class);
            assertThat(result, is(new PolymorphicOrderTest.Sample("x", 2,
                                                                 new PolymorphicOrderTest.Nested("nested", numbers))));
            assertThat("Subtype selection must precede reading the large suffix",
                       selectionPositions, contains(lessThan(bytes.length / 2)));
            assertThat(input.position, is(bytes.length));
        }
    }

    @ParameterizedTest(name = "{0}, buffer={1}, read chunk={2}")
    @MethodSource("inputs")
    void successiveObjectsDoNotAccumulateRetainedPrefixes(Codec codec, int bufferSize, int chunkSize) {
        List<PolymorphicOrderTest.Base> expected = new ArrayList<>();
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < 2048; i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"name\":\"item-").append(i)
                    .append("\",\"count\":").append(i).append(",\"kind\":\"sample\"}");
            expected.add(new PolymorphicOrderTest.Sample("item-" + i, i, null));
        }
        json.append(']');
        byte[] bytes = codec.encode(json.toString());
        ProbeInputStream input = new ProbeInputStream(bytes, chunkSize, Math.max(512, bufferSize * 2));
        List<PolymorphicOrderTest.Base> result = binding.deserialize(codec.parser(input, bufferSize),
                                                                    new GenericType<List<PolymorphicOrderTest.Base>>() { });
        assertThat(result, is(expected));
        assertThat(input.position, is(bytes.length));
    }

    @ParameterizedTest(name = "{0}, buffer={1}, read chunk={2}")
    @MethodSource("inputs")
    void grownBufferIsReusedAfterLongPrefixReplay(Codec codec, int bufferSize, int chunkSize) {
        String name = "ž😀\\\"\n".repeat(512);
        String first = "{\"name\":" + binding.serialize(name) + ",\"count\":9,\"kind\":\"sample\"}";
        int capacityBudget = codec.encode(first).length * 2 + bufferSize * 2;
        StringBuilder json = new StringBuilder("[").append(first);
        List<PolymorphicOrderTest.Base> expected = new ArrayList<>();
        expected.add(new PolymorphicOrderTest.Sample(name, 9, null));
        for (int i = 0; i < 2048; i++) {
            json.append(",{\"name\":\"next\",\"count\":").append(i).append(",\"kind\":\"sample\"}");
            expected.add(new PolymorphicOrderTest.Sample("next", i, null));
        }
        json.append(']');
        byte[] bytes = codec.encode(json.toString());
        ProbeInputStream input = new ProbeInputStream(bytes, chunkSize, capacityBudget);
        List<PolymorphicOrderTest.Base> result = binding.deserialize(codec.parser(input, bufferSize),
                                                                    new GenericType<List<PolymorphicOrderTest.Base>>() { });
        assertThat(result, is(expected));
        assertThat(input.position, is(bytes.length));
        assertThat("This case must exercise actual buffer growth", input.largestBuffer, greaterThan(bufferSize));
    }

    @ParameterizedTest(name = "{0}, buffer={1}, read chunk={2}")
    @MethodSource("inputs")
    void nestedReplayPreservesCompactedPrefixesAndReportsTruncation(Codec codec, int bufferSize, int chunkSize) {
        String name = "ž😀\\\"\n".repeat(512);
        String json = "{\"head\":\"before\",\"child\":{\"name\":" + binding.serialize(name)
                + ",\"count\":3,\"kind\":\"sample\"},\"tail\":\"after\",\"outer-kind\":\"outer\"}";
        byte[] bytes = codec.encode(json);
        int capacityBudget = bytes.length * 2 + bufferSize * 2;
        ProbeInputStream input = new ProbeInputStream(bytes, chunkSize, capacityBudget);
        OuterBase result = binding.deserialize(codec.parser(input, bufferSize), OuterBase.class);
        assertThat(result, is(new Outer("before", new PolymorphicOrderTest.Sample(name, 3, null), "after")));
        assertThat(input.position, is(bytes.length));

        ProbeInputStream truncated = new ProbeInputStream(Arrays.copyOf(bytes, bytes.length - 1), chunkSize, capacityBudget);
        assertThrows(JsonException.class, () -> binding.deserialize(codec.parser(truncated, bufferSize), OuterBase.class));
    }

    @ParameterizedTest(name = "{0}, buffer={1}, read chunk={2}")
    @MethodSource("inputs")
    void absentDiscriminatorReplaysWholeObjectIntoDefault(Codec codec, int bufferSize, int chunkSize) {
        String name = "ž😀\\\"\n".repeat(512);
        byte[] bytes = codec.encode("{\"name\":" + binding.serialize(name) + "}");
        ProbeInputStream input = new ProbeInputStream(bytes, chunkSize, bytes.length * 2 + bufferSize * 2);
        PolymorphicOrderTest.Base result = binding.deserialize(codec.parser(input, bufferSize), PolymorphicOrderTest.Base.class);
        assertThat(result, is(new PolymorphicOrderTest.DefaultValue(name)));
        assertThat(input.position, is(bytes.length));
    }

    private static Stream<Arguments> inputs() {
        return Stream.of(Arguments.of(Codec.JSON, 6, 1),
                         Arguments.of(Codec.JSON, 7, 3),
                         Arguments.of(Codec.JSON, 31, 17),
                         Arguments.of(Codec.JSON, 512, 1),
                         Arguments.of(Codec.SMILE, 5, 1),
                         Arguments.of(Codec.SMILE, 7, 3),
                         Arguments.of(Codec.SMILE, 31, 17),
                         Arguments.of(Codec.SMILE, 512, 1));
    }

    enum Codec {
        JSON,
        SMILE;

        byte[] encode(String json) {
            if (this == JSON) {
                return json.getBytes(StandardCharsets.UTF_8);
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (JsonGenerator generator = SmileGenerator.create(output)) {
                generator.write(JsonParser.create(json).readJsonValue());
            }
            return output.toByteArray();
        }

        JsonParser parser(InputStream input, int bufferSize) {
            return this == JSON ? JsonParser.create(input, bufferSize) : SmileParser.create(input, bufferSize);
        }
    }

    @Json.Entity
    @Json.Polymorphic(key = "outer-kind")
    @Json.Subtype(value = Outer.class, alias = "outer")
    interface OuterBase {
    }

    // Observe buffers at the InputStream boundary; no parser-private state or timing is inspected.
    private static final class ProbeInputStream extends InputStream {
        private final byte[] bytes;
        private final int chunkSize;
        private final int capacityBudget;

        private byte[] previousBuffer;
        private int position;
        private int largestBuffer;
        private long totalBufferBytes;

        private ProbeInputStream(byte[] bytes, int chunkSize, int capacityBudget) {
            this.bytes = bytes;
            this.chunkSize = chunkSize;
            this.capacityBudget = capacityBudget;
        }

        @Override
        public int read() {
            return position == bytes.length ? -1 : bytes[position++] & 0xff;
        }

        @Override
        public int read(byte[] target, int offset, int length) {
            if (target != previousBuffer) {
                previousBuffer = target;
                largestBuffer = Math.max(largestBuffer, target.length);
                totalBufferBytes += target.length;
                // Fail before a broken growth policy can exhaust the test process.
                assertThat("Input buffer must follow the replay prefix, not document length",
                           target.length, lessThanOrEqualTo(capacityBudget));
                assertThat("Buffer replacements must have a linear aggregate capacity",
                           totalBufferBytes, lessThanOrEqualTo(capacityBudget * 2L));
            }
            assertThat("Refills must have space to make progress", length, greaterThan(0));
            if (position == bytes.length) {
                return -1;
            }
            int count = Math.min(Math.min(length, chunkSize), bytes.length - position);
            System.arraycopy(bytes, position, target, offset, count);
            position += count;
            return count;
        }
    }

    @Json.Entity
    record Outer(String head, PolymorphicOrderTest.Base child, String tail) implements OuterBase {
    }

    @Json.Entity
    record PayloadData(String name, int count, PolymorphicOrderTest.Nested nested) {
    }
}
