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
package io.helidon.media.common;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import io.helidon.common.GenericType;
import io.helidon.common.http.DataChunk;
import io.helidon.common.http.ReadOnlyHeaders;
import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessageBodyReaderContextBudgetTest {
    private static final GenericType<byte[]> BYTES = GenericType.create(byte[].class);
    private static final GenericType<String> STRING = GenericType.create(String.class);

    private final MessageBodyReaderContext context = MediaContext.builder()
            .maxBufferedEntitySize(4)
            .build()
            .readerContext();

    @Test
    void defaultBudgetLimitsOrdinaryByteArrayConversion() {
        MessageBodyReaderContext defaults = MediaContext.create().readerContext();
        byte[] bytes = new byte[1_048_577];
        assertLimitFailure(defaults.unmarshall(Multi.just(DataChunk.create(bytes)), BYTES));
    }

    @Test
    void defaultBudgetAllowsOneMiB() throws Exception {
        MessageBodyReaderContext defaults = MediaContext.create().readerContext();
        byte[] bytes = new byte[1_048_576];
        assertThat(defaults.unmarshall(Multi.just(DataChunk.create(bytes)), BYTES).get(5, TimeUnit.SECONDS), is(bytes));
    }

    @Test
    void bytesAtLimitAreAccepted() throws Exception {
        assertThat(context.unmarshall(payload("ab", "cd"), BYTES).get(5, TimeUnit.SECONDS),
                   is("abcd".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void ordinaryStringsAreBounded() {
        assertLimitFailure(context.unmarshall(payload("abc", "de"), STRING));
    }

    @Test
    void budgetCountsDecodedOutputRatherThanRawInput() {
        context.registerFilter(chunks -> Multi.create(chunks).map(chunk -> {
            chunk.release();
            return DataChunk.create("decoded".getBytes(StandardCharsets.UTF_8));
        }));
        assertLimitFailure(context.unmarshall(payload("x"), STRING));
    }

    @Test
    void customMaterializingReadersAreBounded() {
        TestReader reader = new TestReader(chunks -> ContentReaders.readString(chunks, StandardCharsets.UTF_8), false);
        assertLimitFailure(context.unmarshall(payload("abcde"), reader, STRING));
    }

    @Test
    void customReaderCannotSuppressFailureWithValue() {
        TestReader reader = new TestReader(chunks -> ContentReaders.readString(chunks, StandardCharsets.UTF_8)
                .onErrorResume(error -> "fallback"), false);
        assertLimitFailure(context.unmarshall(payload("abcde"), reader, STRING));
    }

    @Test
    void customReaderCannotSuppressFailureWithEmptyCompletion() {
        TestReader reader = new TestReader(chunks -> ContentReaders.readString(chunks, StandardCharsets.UTF_8)
                .onErrorResumeWithSingle(error -> Single.empty()), false);
        assertLimitFailure(context.unmarshall(payload("abcde"), reader, STRING));
    }

    @Test
    void customReaderCannotWrapFailure() {
        TestReader reader = new TestReader(chunks -> ContentReaders.readString(chunks, StandardCharsets.UTF_8)
                .onErrorResumeWithSingle(error -> Single.error(new IllegalArgumentException("wrapped", error))), false);
        assertLimitFailure(context.unmarshall(payload("abcde"), reader, STRING));
    }

    @Test
    void synchronousReaderCannotWrapFailure() {
        TestReader reader = new TestReader(chunks -> {
            ContentReaders.readBytes(chunks).toStage().toCompletableFuture().join();
            return Single.just("converted");
        }, false);
        assertLimitFailure(context.unmarshall(payload("abcde"), reader, STRING));
    }

    @Test
    void directInputStreamsRemainUnlimited() throws Exception {
        try (InputStream stream = context.unmarshall(payload("unlimited"), GenericType.create(InputStream.class))
                .get(5, TimeUnit.SECONDS)) {
            assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8), is("unlimited"));
        }
    }

    @Test
    void readersDeclaringStreamingRemainUnlimited() throws Exception {
        TestReader reader = new TestReader(chunks -> Multi.create(chunks).reduce(() -> 0L, (total, chunk) -> {
            try {
                return total + chunk.remaining();
            } finally {
                chunk.release();
            }
        }).map(String::valueOf), true);
        assertThat(context.unmarshall(payload("streaming"), reader, STRING).get(5, TimeUnit.SECONDS), is("9"));
    }

    @Test
    void zeroBudgetAllowsEmptyInput() throws Exception {
        context.maxBufferedEntitySize(0);
        assertThat(context.unmarshall(Multi.empty(), BYTES).get(5, TimeUnit.SECONDS), is(new byte[0]));
        assertLimitFailure(context.unmarshall(payload("a"), BYTES));
    }

    @Test
    void negativeBudgetIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> context.maxBufferedEntitySize(-1));
    }

    @Test
    void eachSubscriptionGetsItsOwnBudget() throws Exception {
        Single<byte[]> conversion = context.unmarshall(payload("abcd"), BYTES);
        assertThat(conversion.get(5, TimeUnit.SECONDS), is("abcd".getBytes(StandardCharsets.UTF_8)));
        assertThat(conversion.get(5, TimeUnit.SECONDS), is("abcd".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void childContextsInheritLimitAndFailurePolicy() {
        IllegalArgumentException limit = new IllegalArgumentException("request limit");
        context.maxBufferedEntitySize(4, () -> limit);
        MessageBodyReaderContext child = MessageBodyReaderContext.create(context);
        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> child.unmarshall(payload("abcde"), BYTES).get(5, TimeUnit.SECONDS));
        assertThat(failure.getCause(), sameInstance(limit));
    }

    @Test
    void oversizedChunkIsReleasedAndUpstreamCancelled() {
        AtomicBoolean released = new AtomicBoolean();
        AtomicBoolean cancelled = new AtomicBoolean();
        DataChunk chunk = DataChunk.create(false, () -> released.set(true), ByteBuffer.wrap(new byte[5]));
        assertLimitFailure(context.unmarshall(Multi.just(chunk).onCancel(() -> cancelled.set(true)), BYTES));
        assertThat(released.get(), is(true));
        assertThat(cancelled.get(), is(true));
    }

    @Test
    void sizeIsCountedAcrossBuffersInOneChunk() {
        DataChunk chunk = DataChunk.create(ByteBuffer.wrap(new byte[3]), ByteBuffer.wrap(new byte[2]));
        assertLimitFailure(context.unmarshall(Multi.just(chunk), BYTES));
    }

    @Test
    void builderAndConfigurationExposeTheSameBudget() throws Exception {
        Config config = Config.create(ConfigSources.create(Map.of("max-buffered-entity-size", "4")));
        MessageBodyReaderContext configured = MediaContext.create(config).readerContext();
        assertThat(configured.unmarshall(payload("abcd"), BYTES).get(5, TimeUnit.SECONDS),
                   is("abcd".getBytes(StandardCharsets.UTF_8)));
        assertLimitFailure(configured.unmarshall(payload("abcde"), BYTES));
    }

    @Test
    void reusingBuilderDoesNotChangePreviouslyBuiltBudget() throws Exception {
        MediaContext.Builder builder = MediaContext.builder().maxBufferedEntitySize(4);
        MessageBodyReaderContext original = builder.build().readerContext();
        builder.maxBufferedEntitySize(8);

        assertThat(original.maxBufferedEntitySize(), is(4L));
        assertLimitFailure(original.unmarshall(payload("abcde"), BYTES));

        MessageBodyReaderContext updated = builder.build().readerContext();
        assertThat(updated.maxBufferedEntitySize(), is(8L));
        assertThat(updated.unmarshall(payload("abcde"), BYTES).get(5, TimeUnit.SECONDS),
                   is("abcde".getBytes(StandardCharsets.UTF_8)));
        assertLimitFailure(original.unmarshall(payload("abcde"), BYTES));
    }

    @Test
    void budgetFailureNotifiesReadErrorListener() {
        List<MessageBodyContext.Event> events = new ArrayList<>();
        MessageBodyReaderContext observed = MessageBodyReaderContext.create(MediaContext.create(),
                                                                            events::add,
                                                                            ReadOnlyHeaders.empty(),
                                                                            Optional.empty())
                .maxBufferedEntitySize(4);
        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> observed.unmarshall(payload("abcde"), BYTES).get(5, TimeUnit.SECONDS));

        List<MessageBodyContext.Event> errors = events.stream()
                .filter(event -> event.eventType() == MessageBodyContext.EventType.AFTER_ONERROR)
                .toList();
        assertThat(errors.size(), is(1));
        assertThat(errors.get(0).asErrorEvent().error(), sameInstance(failure.getCause()));
    }

    private static Multi<DataChunk> payload(String... chunks) {
        return Multi.create(Arrays.asList(chunks))
                .map(value -> DataChunk.create(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static void assertLimitFailure(Single<?> conversion) {
        ExecutionException failure = assertThrows(ExecutionException.class, () -> conversion.get(5, TimeUnit.SECONDS));
        assertThat(failure.getCause(), instanceOf(IllegalStateException.class));
        assertThat(failure.getCause().getMessage(), is("Maximum materialized entity size exceeded"));
    }

    private static final class TestReader implements MessageBodyReader<String> {
        private final Function<Publisher<DataChunk>, Single<String>> action;
        private final boolean streaming;

        private TestReader(Function<Publisher<DataChunk>, Single<String>> action, boolean streaming) {
            this.action = action;
            this.streaming = streaming;
        }

        @Override
        public boolean streaming() {
            return streaming;
        }

        @Override
        public PredicateResult accept(GenericType<?> type, MessageBodyReaderContext readerContext) {
            return PredicateResult.supports(String.class, type);
        }

        @Override
        public <U extends String> Single<U> read(Publisher<DataChunk> chunks,
                                                GenericType<U> type,
                                                MessageBodyReaderContext readerContext) {
            return action.apply(chunks).map(type::cast);
        }
    }
}
