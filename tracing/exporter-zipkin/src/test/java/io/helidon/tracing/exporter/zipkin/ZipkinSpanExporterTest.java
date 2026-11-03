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

package io.helidon.tracing.exporter.zipkin;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import zipkin2.reporter.BytesMessageSender;
import zipkin2.reporter.Encoding;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@SuppressWarnings("removal")
class ZipkinSpanExporterTest {
    @Test
    void closesCustomSenderExactlyOnceAndRejectsExportsAfterShutdown() {
        TestSender sender = new TestSender();
        var exporter = ZipkinSpanExporter.builder().sender(sender).build();
        assertThat(exporter.shutdown().isSuccess(), is(true));
        assertThat(exporter.shutdown().isSuccess(), is(true));
        assertThat(sender.closes.get(), is(1));
        assertThat(exporter.export(List.of()).isSuccess(), is(false));
        assertThat(exporter.flush().isSuccess(), is(true));
    }

    @Test
    void failedShutdownCanBeRetriedWithoutReopeningAdmission() {
        TestSender sender = new TestSender() {
            @Override
            public void close() throws IOException {
                if (closes.incrementAndGet() == 1) {
                    throw new IOException("sensitive sender details");
                }
            }
        };
        var exporter = ZipkinSpanExporter.builder().sender(sender).build();
        assertThat(exporter.shutdown().isSuccess(), is(false));
        assertThat(exporter.export(List.of()).isSuccess(), is(false));
        assertThat(exporter.shutdown().isSuccess(), is(true));
        assertThat(exporter.shutdown().isSuccess(), is(true));
        assertThat(sender.closes.get(), is(2));
    }

    @Test
    void failedSendDoesNotStopExporter() {
        AtomicInteger attempts = new AtomicInteger();
        TestSender sender = new TestSender() {
            @Override
            public void send(List<byte[]> spans) throws IOException {
                if (attempts.getAndIncrement() == 0) {
                    throw new IOException("sensitive endpoint");
                }
            }
        };
        try (var exporter = ZipkinSpanExporter.builder().sender(sender).build()) {
            assertThat(exporter.export(List.of()).isSuccess(), is(false));
            assertThat(exporter.export(List.of()).isSuccess(), is(true));
        }
    }

    @Test
    void encodingFailureReleasesExportOwnership() {
        try (var exporter = ZipkinSpanExporter.builder().sender(new TestSender()).encoder(new zipkin2.reporter.BytesEncoder<>() {
            @Override
            public Encoding encoding() {
                return Encoding.JSON;
            }

            @Override
            public int sizeInBytes(zipkin2.Span span) {
                return 0;
            }

            @Override
            public byte[] encode(zipkin2.Span span) {
                throw new IllegalArgumentException("sensitive span");
            }
        }).build()) {
            assertThat(exporter.export(List.of(ZipkinTestSpans.span().build())).isSuccess(), is(false));
            assertThat(exporter.shutdown().isSuccess(), is(true));
        }
    }

    @Test
    void shutdownDrainsActualExportBeforeClosingAndRejectsNewWork() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        TestSender sender = blockingSender(started, release);
        var exporter = ZipkinSpanExporter.builder().sender(sender).build();
        AtomicBoolean exportSuccess = new AtomicBoolean();
        AtomicBoolean shutdownSuccess = new AtomicBoolean();
        Thread export = Thread.ofVirtual().start(() -> exportSuccess.set(exporter.export(List.of()).isSuccess()));
        assertThat(started.await(5, TimeUnit.SECONDS), is(true));
        Thread shutdown = Thread.ofVirtual().start(() -> shutdownSuccess.set(exporter.shutdown().isSuccess()));
        try {
            awaitWaiting(shutdown);
            assertThat(exporter.export(List.of()).isSuccess(), is(false));
            assertThat(sender.closes.get(), is(0));
        } finally {
            release.countDown();
        }
        assertThat(export.join(java.time.Duration.ofSeconds(5)), is(true));
        assertThat(shutdown.join(java.time.Duration.ofSeconds(5)), is(true));
        assertThat(exportSuccess.get(), is(true));
        assertThat(shutdownSuccess.get(), is(true));
        assertThat(sender.closes.get(), is(1));
    }

    @Test
    void interruptedShutdownRetainsSenderUntilExportExitsAndAllowsRetry() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        TestSender sender = blockingSender(started, release);
        var exporter = ZipkinSpanExporter.builder().sender(sender).build();
        Thread export = Thread.ofVirtual().start(() -> exporter.export(List.of()));
        assertThat(started.await(5, TimeUnit.SECONDS), is(true));
        AtomicBoolean interrupted = new AtomicBoolean();
        AtomicBoolean failed = new AtomicBoolean();
        Thread shutdown = Thread.ofVirtual().start(() -> {
            failed.set(!exporter.shutdown().isSuccess());
            interrupted.set(Thread.currentThread().isInterrupted());
        });
        try {
            awaitWaiting(shutdown);
            shutdown.interrupt();
            assertThat(shutdown.join(java.time.Duration.ofSeconds(5)), is(true));
            assertThat(failed.get(), is(true));
            assertThat(interrupted.get(), is(true));
            assertThat(sender.closes.get(), is(0));
            assertThat(exporter.export(List.of()).isSuccess(), is(false));
        } finally {
            release.countDown();
        }
        assertThat(export.join(java.time.Duration.ofSeconds(5)), is(true));
        assertThat(exporter.shutdown().isSuccess(), is(true));
        assertThat(sender.closes.get(), is(1));
    }

    @Test
    void concurrentShutdownCallsSerializeSenderClose() throws Exception {
        CountDownLatch closing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        TestSender sender = new TestSender() {
            @Override
            public void close() throws IOException {
                closes.incrementAndGet();
                closing.countDown();
                await(release);
            }
        };
        var exporter = ZipkinSpanExporter.builder().sender(sender).build();
        AtomicInteger successes = new AtomicInteger();
        Thread first = Thread.ofVirtual().start(() -> {
            if (exporter.shutdown().isSuccess()) {
                successes.incrementAndGet();
            }
        });
        assertThat(closing.await(5, TimeUnit.SECONDS), is(true));
        Thread second = Thread.ofVirtual().start(() -> {
            if (exporter.shutdown().isSuccess()) {
                successes.incrementAndGet();
            }
        });
        try {
            awaitWaiting(second);
            assertThat(sender.closes.get(), is(1));
        } finally {
            release.countDown();
        }
        assertThat(first.join(java.time.Duration.ofSeconds(5)), is(true));
        assertThat(second.join(java.time.Duration.ofSeconds(5)), is(true));
        assertThat(successes.get(), is(2));
        assertThat(sender.closes.get(), is(1));
    }

    private static TestSender blockingSender(CountDownLatch started, CountDownLatch release) {
        return new TestSender() {
            @Override
            public void send(List<byte[]> spans) throws IOException {
                started.countDown();
                await(release);
            }
        };
    }

    private static void await(CountDownLatch latch) throws IOException {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IOException("Test operation timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Test operation interrupted");
        }
    }

    private static void awaitWaiting(Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (thread.getState() != Thread.State.WAITING && thread.isAlive() && System.nanoTime() < deadline) {
            Thread.sleep(1);
        }
        assertThat(thread.getState(), is(Thread.State.WAITING));
    }

    static class TestSender extends BytesMessageSender.Base {
        final AtomicInteger closes = new AtomicInteger();

        TestSender() {
            super(Encoding.JSON);
        }

        @Override
        public int messageMaxBytes() {
            return 500_000;
        }

        @Override
        public void send(List<byte[]> spans) throws IOException {
        }

        @Override
        public void close() throws IOException {
            closes.incrementAndGet();
        }
    }
}
