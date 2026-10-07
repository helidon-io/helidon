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

package io.helidon.http.benchmark.jmh;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

import io.helidon.common.GenericType;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.parameters.Parameters;
import io.helidon.http.HeaderNames;
import io.helidon.http.Headers;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.EntityReader;
import io.helidon.http.media.MediaContext;
import io.helidon.http.media.ReadableEntity;
import io.helidon.http.media.ReadableEntityBase;
import io.helidon.http.media.json.JsonSupport;
import io.helidon.json.JsonObject;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import static java.util.concurrent.TimeUnit.MICROSECONDS;

/**
 * Compares unbounded and bounded byte-array conversion and String, form, and JSON media readers.
 * Each media-reader pair uses the same request stream, payload, reader, and stream lifecycle;
 * only the materialization budget differs. Payload generation and reader selection are outside measured operations.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(MICROSECONDS)
public class MaterializedEntityJmhBenchmark {
    private static final GenericType<Parameters> FORM_TYPE = GenericType.create(Parameters.class);
    private static final GenericType<JsonObject> JSON_TYPE = GenericType.create(JsonObject.class);
    private static final Headers STRING_HEADERS = WritableHeaders.create()
            .set(HeaderNames.CONTENT_TYPE, "text/plain; charset=utf-8");
    private static final Headers FORM_HEADERS = WritableHeaders.create()
            .set(HeaderNames.CONTENT_TYPE, "application/x-www-form-urlencoded; charset=utf-8");
    private static final Headers JSON_HEADERS = WritableHeaders.create()
            .set(HeaderNames.CONTENT_TYPE, "application/json");

    @Param({"1024", "65536"})
    private int bytes;

    private byte[] payload;
    private byte[] stringPayload;
    private byte[] formPayload;
    private byte[] jsonPayload;
    private EntityReader<String> stringReader;
    private EntityReader<Parameters> formReader;
    private EntityReader<JsonObject> jsonReader;

    @Setup
    public void setup() {
        payload = new byte[bytes];
        stringPayload = "A".repeat(bytes).getBytes(StandardCharsets.UTF_8);
        formPayload = ("payload=" + "A".repeat(bytes - 8)).getBytes(StandardCharsets.UTF_8);
        jsonPayload = ("{\"payload\":\"" + "A".repeat(bytes - 14) + "\"}").getBytes(StandardCharsets.UTF_8);
        MediaContext context = MediaContext.builder()
                .mediaSupportsDiscoverServices(false)
                .addMediaSupport(JsonSupport.create())
                .build();
        stringReader = context.reader(GenericType.STRING, STRING_HEADERS);
        formReader = context.reader(FORM_TYPE, FORM_HEADERS);
        jsonReader = context.reader(JSON_TYPE, JSON_HEADERS);
        String string = verifyBudget(stringPayload, stringReader, GenericType.STRING, STRING_HEADERS);
        Parameters form = verifyBudget(formPayload, formReader, FORM_TYPE, FORM_HEADERS);
        JsonObject json = verifyBudget(jsonPayload, jsonReader, JSON_TYPE, JSON_HEADERS);
        if (string.length() != bytes
                || form.first("payload").orElseThrow().length() != bytes - 8
                || json.stringValue("payload", "").length() != bytes - 14) {
            throw new IllegalStateException("Media reader did not materialize the complete benchmark payload");
        }
    }

    @Benchmark
    public byte[] unbounded() {
        return new TestEntity(payload).readUnbounded();
    }

    @Benchmark
    public byte[] bounded() {
        return new TestEntity(payload).as(byte[].class);
    }

    /**
     * Read a String without the materialization budget.
     *
     * @return materialized String
     */
    @Benchmark
    public String unboundedString() {
        return new TestEntity(stringPayload).readMedia(stringReader, GenericType.STRING, STRING_HEADERS, false);
    }

    /**
     * Read a String with the materialization budget.
     *
     * @return materialized String
     */
    @Benchmark
    public String boundedString() {
        return new TestEntity(stringPayload).readMedia(stringReader, GenericType.STRING, STRING_HEADERS, true);
    }

    /**
     * Read URL-encoded form parameters without the materialization budget.
     *
     * @return parsed form parameters
     */
    @Benchmark
    public Parameters unboundedForm() {
        return new TestEntity(formPayload).readMedia(formReader, FORM_TYPE, FORM_HEADERS, false);
    }

    /**
     * Read URL-encoded form parameters with the materialization budget.
     *
     * @return parsed form parameters
     */
    @Benchmark
    public Parameters boundedForm() {
        return new TestEntity(formPayload).readMedia(formReader, FORM_TYPE, FORM_HEADERS, true);
    }

    /**
     * Read a JSON object without the materialization budget.
     *
     * @return parsed JSON object
     */
    @Benchmark
    public JsonObject unboundedJson() {
        return new TestEntity(jsonPayload).readMedia(jsonReader, JSON_TYPE, JSON_HEADERS, false);
    }

    /**
     * Read a JSON object with the materialization budget.
     *
     * @return parsed JSON object
     */
    @Benchmark
    public JsonObject boundedJson() {
        return new TestEntity(jsonPayload).readMedia(jsonReader, JSON_TYPE, JSON_HEADERS, true);
    }

    private static <T> T verifyBudget(byte[] payload, EntityReader<T> reader, GenericType<T> type, Headers headers) {
        if (reader.streaming()) {
            throw new IllegalStateException("Expected a materializing reader for " + type);
        }
        // Both paths retain the same configured budget; only the unbounded path bypasses its stream wrapper.
        T result = new TestEntity(payload, payload.length - 1).readMedia(reader, type, headers, false);
        boolean rejected = false;
        try {
            new TestEntity(payload, payload.length - 1).readMedia(reader, type, headers, true);
        } catch (IllegalStateException e) {
            if (!"Maximum materialized entity size exceeded".equals(e.getMessage())) {
                throw e;
            }
            rejected = true;
        }
        if (!rejected) {
            throw new IllegalStateException("Materialization budget was not enforced for " + type);
        }
        return result;
    }

    private static final class TestEntity extends ReadableEntityBase {
        private TestEntity(byte[] payload) {
            this(payload, 1024 * 1024);
        }

        private TestEntity(byte[] payload, long maxSize) {
            super(new ByteSource(payload), () -> { }, maxSize);
        }

        @Override
        public ReadableEntity copy(Runnable entityProcessedRunnable) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected <T> T entityAs(GenericType<T> type) {
            throw new UnsupportedOperationException();
        }

        private byte[] readUnbounded() {
            try {
                return inputStream().readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private <T> T readMedia(EntityReader<T> reader, GenericType<T> type, Headers headers, boolean bounded) {
            if (bounded) {
                return readEntity(reader, type, headers);
            }
            return reader.read(type, inputStream(), headers);
        }
    }

    private static final class ByteSource implements Function<Integer, BufferData> {
        private final byte[] payload;
        private boolean sent;

        private ByteSource(byte[] payload) {
            this.payload = payload;
        }

        @Override
        public BufferData apply(Integer estimate) {
            if (sent) {
                return BufferData.empty();
            }
            sent = true;
            return BufferData.create(payload);
        }
    }
}
