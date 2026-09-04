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

package io.helidon.webclient.api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.WritableHeaders;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class EntityWriterPreflightTest {
    private static final HeaderName X_HEADER = HeaderNames.create("X-Test");
    private static final HeaderName Y_HEADER = HeaderNames.create("Y-Test");
    private static final HeaderName Z_HEADER = HeaderNames.create("Z-Test");

    @Test
    void boundsProducerUntilOneShotAttachAndDrainsExactBytes() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            byte[] expected = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);
            EntityWriterPreflight preflight = preflight(4, (output, _) -> {
                write(output, expected);
                close(output);
            });
            preflight.prepare(headers());

            assertFalse(preflight.whenTerminated().isDone(), "producer should block after filling the fixed ring");

            ByteArrayOutputStream actual = new ByteArrayOutputStream();
            preflight.writeTo(actual);
            assertThat(actual.toByteArray(), is(expected));
            assertThat(preflight.whenTerminated().get(1, TimeUnit.SECONDS), is((Void) null));

            IllegalStateException failure = assertThrows(IllegalStateException.class,
                                                          () -> preflight.writeTo(OutputStream.nullOutputStream()));
            assertThat(failure.getMessage(), containsString("already been attached"));
        });
    }

    @Test
    void acknowledgesFlushOnlyAfterDownstreamFlushCompletes() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            CountDownLatch flushEntered = new CountDownLatch(1);
            CountDownLatch releaseFlush = new CountDownLatch(1);
            CountDownLatch producerPastFlush = new CountDownLatch(1);
            EntityWriterPreflight preflight = preflight(8, (output, _) -> {
                write(output, new byte[] {1, 2});
                flush(output);
                producerPastFlush.countDown();
                write(output, new byte[] {3});
                close(output);
            });
            preflight.prepare(headers());

            Thread consumer = Thread.ofVirtual().start(() -> {
                try {
                    preflight.writeTo(new ByteArrayOutputStream() {
                        @Override
                        public void flush() throws IOException {
                            flushEntered.countDown();
                            await(releaseFlush);
                            super.flush();
                        }
                    });
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });

            assertThat(flushEntered.await(1, TimeUnit.SECONDS), is(true));
            assertThat(producerPastFlush.getCount(), is(1L));
            releaseFlush.countDown();
            consumer.join();
            assertThat(producerPastFlush.getCount(), is(0L));
        });
    }

    @Test
    void propagatesFailuresAtTheirCommitBoundary() {
        IllegalStateException beforeCommit = new IllegalStateException("before-commit");
        EntityWriterPreflight early = preflight(4, (_, _) -> {
            throw beforeCommit;
        });
        assertThat(assertThrows(IllegalStateException.class, () -> early.prepare(headers())), is(beforeCommit));

        IllegalStateException afterCommit = new IllegalStateException("after-commit");
        EntityWriterPreflight late = preflight(4, (output, _) -> {
            write(output, new byte[] {1});
            throw afterCommit;
        });
        late.prepare(headers());
        assertThrows(ExecutionException.class, () -> late.whenTerminated().get(1, TimeUnit.SECONDS));
        ByteArrayOutputStream target = new ByteArrayOutputStream();
        assertThat(assertThrows(IllegalStateException.class, () -> late.writeTo(target)), is(afterCommit));
        assertThat(target.size(), is(0));
    }

    @Test
    void rejectsReturnWithoutCloseAndPostCommitHeaderMutation() {
        EntityWriterPreflight noClose = preflight(4, (output, _) -> write(output, new byte[] {1}));
        noClose.prepare(headers());
        assertThrows(ExecutionException.class, () -> noClose.whenTerminated().get(1, TimeUnit.SECONDS));
        IllegalStateException closeFailure = assertThrows(IllegalStateException.class,
                                                          () -> noClose.writeTo(OutputStream.nullOutputStream()));
        assertThat(closeFailure.getMessage(), containsString("without closing"));

        EntityWriterPreflight lateHeaders = preflight(4, (output, headers) -> {
            write(output, new byte[] {1});
            headers.set(X_HEADER, "too-late");
            close(output);
        });
        lateHeaders.prepare(headers());
        assertThrows(ExecutionException.class, () -> lateHeaders.whenTerminated().get(1, TimeUnit.SECONDS));
        IllegalStateException headerFailure = assertThrows(IllegalStateException.class,
                                                           () -> lateHeaders.writeTo(OutputStream.nullOutputStream()));
        assertThat(headerFailure.getMessage(), containsString("already been committed"));
    }

    @Test
    void cancellationSettlesBeforeDuringAndAfterProduction() throws Exception {
        EntityWriterPreflight before = preflight(4, (_, _) -> {
            throw new AssertionError("cancelled writer must not run");
        });
        before.cancel();
        assertThrows(ExecutionException.class, () -> before.whenTerminated().get(1, TimeUnit.SECONDS));
        assertThrows(RuntimeException.class, () -> before.prepare(headers()));

        EntityWriterPreflight blocked = preflight(2, (output, _) -> {
            write(output, new byte[] {1, 2, 3});
            close(output);
        });
        blocked.prepare(headers());
        blocked.cancelIfUnattached(new IllegalStateException("discard"));
        assertThrows(ExecutionException.class, () -> blocked.whenTerminated().get(1, TimeUnit.SECONDS));
        assertFalse(blocked.canAttach());

        EntityWriterPreflight completed = preflight(4, (output, _) -> close(output));
        completed.prepare(headers());
        completed.whenTerminated().get(1, TimeUnit.SECONDS);
        completed.cancelIfUnattached(new IllegalStateException("late-discard"));
        assertFalse(completed.canAttach());
    }

    @Test
    void rejectsAttachBeforePrepareAndCancelsOnTransferFailure() {
        EntityWriterPreflight unprepared = preflight(4, (output, _) -> close(output));
        assertThrows(IllegalStateException.class,
                     () -> unprepared.writeTo(OutputStream.nullOutputStream()));

        EntityWriterPreflight transfer = preflight(2, (output, _) -> {
            write(output, new byte[] {1, 2, 3, 4});
            close(output);
        });
        transfer.prepare(headers());
        IOException expected = new IOException("transport-failure");
        OutputStream failing = new OutputStream() {
            @Override
            public void write(int value) throws IOException {
                throw expected;
            }

            @Override
            public void write(byte[] bytes, int offset, int length) throws IOException {
                throw expected;
            }
        };
        assertThat(assertThrows(IOException.class, () -> transfer.writeTo(failing)), is(expected));
        assertThrows(ExecutionException.class, () -> transfer.whenTerminated().get(1, TimeUnit.SECONDS));
    }

    @Test
    void propagatesAndClearsRequestContext() throws Exception {
        Marker marker = new Marker();
        Context context = Context.create();
        context.register(marker);
        AtomicReference<Marker> observed = new AtomicReference<>();
        EntityWriterPreflight preflight = EntityWriterPreflight.create(4, context, (output, _) -> {
            observed.set(Contexts.context().flatMap(current -> current.get(Marker.class)).orElse(null));
            close(output);
        });

        preflight.prepare(headers());
        preflight.writeTo(OutputStream.nullOutputStream());
        preflight.whenTerminated().get(1, TimeUnit.SECONDS);

        assertThat(observed.get(), is(marker));
        assertFalse(Contexts.context().flatMap(current -> current.get(Marker.class)).isPresent());
    }

    @Test
    void replaysOperationsAgainstTargetBaselineAndRollsBackConditionally() {
        ClientRequestHeaders source = headers();
        source.set(X_HEADER, "source");
        source.set(Z_HEADER, "remove-source");
        EntityWriterPreflight.HeaderRecorder recorder = EntityWriterPreflight.record(source);
        recorder.add(X_HEADER, "writer-add");
        recorder.set(Y_HEADER, "writer-set");
        recorder.remove(Z_HEADER);

        ClientRequestHeaders target = headers();
        target.set(X_HEADER, "target");
        target.set(Z_HEADER, "remove-target");
        EntityWriterPreflight.Application application = recorder.changes().apply(target);

        assertThat(target.get(X_HEADER).allValues(), is(List.of("target", "writer-add")));
        assertThat(target.get(Y_HEADER).get(), is("writer-set"));
        assertFalse(target.contains(Z_HEADER));

        target.set(Y_HEADER, "service-replacement");
        target.add(X_HEADER, "service-add");
        application.rollback(target);
        assertThat(target.get(X_HEADER).allValues(), is(List.of("target", "service-add")));
        assertThat(target.get(Y_HEADER).get(), is("service-replacement"));
        assertThat(target.get(Z_HEADER).get(), is("remove-target"));
    }

    @Test
    void replaysCookieAddRemoveAndSetWithoutSourceManagerBaseline() {
        ClientRequestHeaders source = headers();
        source.set(HeaderNames.COOKIE, "source-manager=one");

        EntityWriterPreflight.HeaderRecorder addRecorder = EntityWriterPreflight.record(source);
        addRecorder.add(HeaderNames.COOKIE, "writer=one");
        ClientRequestHeaders addTarget = headers();
        addTarget.set(HeaderNames.COOKIE, "target-manager=two");
        EntityWriterPreflight.Application addApplication = addRecorder.changes().apply(addTarget);
        assertThat(addTarget.get(HeaderNames.COOKIE).allValues(),
                   is(List.of("target-manager=two", "writer=one")));
        addTarget.add(HeaderNames.COOKIE, "service=three");
        addApplication.rollback(addTarget);
        assertThat(addTarget.get(HeaderNames.COOKIE).allValues(),
                   is(List.of("target-manager=two; service=three")));

        EntityWriterPreflight.HeaderRecorder removeRecorder = EntityWriterPreflight.record(source);
        removeRecorder.remove(HeaderNames.COOKIE);
        ClientRequestHeaders removeTarget = headers();
        removeTarget.set(HeaderNames.COOKIE, "target-manager=two");
        removeRecorder.changes().apply(removeTarget);
        assertFalse(removeTarget.contains(HeaderNames.COOKIE));

        EntityWriterPreflight.HeaderRecorder setRecorder = EntityWriterPreflight.record(source);
        setRecorder.set(HeaderNames.COOKIE, "writer=one");
        ClientRequestHeaders setTarget = headers();
        setTarget.set(HeaderNames.COOKIE, "target-manager=two");
        setRecorder.changes().apply(setTarget);
        assertThat(setTarget.get(HeaderNames.COOKIE).get(), is("writer=one"));
    }

    @Test
    void commitsFirstWriteHeadersAndDeepCopiesLazyValues() throws Exception {
        AtomicReference<String[]> values = new AtomicReference<>(new String[] {"first", "second"});
        EntityWriterPreflight preflight = preflight(4, (output, headers) -> {
            headers.set(HeaderValues.create(X_HEADER, values.get()));
            write(output, new byte[] {1});
            values.set(new String[] {"changed"});
            close(output);
        });
        ClientRequestHeaders target = headers();

        preflight.prepare(target);
        preflight.writeTo(OutputStream.nullOutputStream());

        assertThat(target.get(X_HEADER).allValues(), is(List.of("first", "second")));
    }

    private static EntityWriterPreflight preflight(int capacity,
                                                   BiConsumer<OutputStream, WritableHeaders<?>> writer) {
        return EntityWriterPreflight.create(capacity, Context.create(), writer);
    }

    private static ClientRequestHeaders headers() {
        return ClientRequestHeaders.create(WritableHeaders.create());
    }

    private static void write(OutputStream outputStream, byte[] bytes) {
        try {
            outputStream.write(bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void flush(OutputStream outputStream) {
        try {
            outputStream.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void close(OutputStream outputStream) {
        try {
            outputStream.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void await(CountDownLatch latch) throws IOException {
        try {
            if (!latch.await(1, TimeUnit.SECONDS)) {
                throw new IOException("Timed out waiting for test latch");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted waiting for test latch", e);
        }
    }

    private static final class Marker {
    }
}
