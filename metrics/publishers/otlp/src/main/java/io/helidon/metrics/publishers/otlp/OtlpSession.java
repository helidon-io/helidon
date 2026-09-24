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

package io.helidon.metrics.publishers.otlp;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.task.DeadlineGuard;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.json.JsonException;
import io.helidon.json.JsonParser;
import io.helidon.json.binding.JsonBinding;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.providers.helidon.HelidonMetricsPublisher;
import io.helidon.webclient.api.RuntimeUnknownHostException;
import io.helidon.webclient.http1.Http1Client;

import static java.lang.System.Logger.Level.WARNING;

final class OtlpSession implements HelidonMetricsPublisher.Session {
    private static final System.Logger LOGGER = System.getLogger(OtlpSession.class.getName());
    private static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;
    private static final long INITIAL_BACKOFF_NANOS = TimeUnit.MILLISECONDS.toNanos(100);
    private static final long MAX_BACKOFF_NANOS = TimeUnit.SECONDS.toNanos(5);
    private static final Duration SHUTDOWN_GRACE = Duration.ofMillis(100);

    private final OtlpPublisherConfig config;
    private final OtlpEncoder encoder;
    private final Http1Client client;
    private final JsonBinding jsonBinding = JsonBinding.create();
    private final List<Header> headers;
    private final Thread worker;
    private final Semaphore wakeup = new Semaphore(0);
    private final AtomicReference<Long> closeStarted = new AtomicReference<>();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final ReentrantLock interruptLock = new ReentrantLock();
    private final long timeoutNanos;
    private final int maxRequestBytes;

    OtlpSession(MeterRegistry registry, MetricsConfig metricsConfig, OtlpPublisherConfig config) {
        this.config = config;
        this.timeoutNanos = config.timeout().toNanos();
        this.maxRequestBytes = Math.toIntExact(config.maxRequestSize().toBytes());
        this.headers = config.headers().entrySet().stream()
                .map(entry -> HeaderValues.create(entry.getKey(), entry.getValue()))
                .toList();
        var attributes = new HashMap<>(config.resourceAttributes());
        attributes.putIfAbsent("service.name", config.serviceName());
        this.encoder = new OtlpEncoder(registry, metricsConfig, attributes);
        try {
            this.client = Http1Client.builder()
                    .servicesDiscoverServices(false)
                    .shareConnectionCache(false)
                    .followRedirects(false)
                    .connectTimeout(config.timeout())
                    .readTimeout(config.timeout())
                    .build();
        } catch (RuntimeException | Error e) {
            encoder.close();
            throw e;
        }
        this.worker = Thread.ofVirtual()
                .name("helidon-metrics-otlp-" + config.name().orElse(OtlpPublisherProvider.TYPE))
                .inheritInheritableThreadLocals(false)
                .unstarted(this::run);
        try {
            worker.start();
        } catch (RuntimeException | Error e) {
            client.closeResource();
            encoder.close();
            throw e;
        }
    }

    @Override
    public void close() {
        if (closeStarted.compareAndSet(null, System.nanoTime())) {
            wakeup.release();
        }
        if (Thread.currentThread() == worker) {
            return;
        }
        boolean interrupted = false;
        try {
            long remaining = remainingShutdownNanos();
            if (remaining > 0 && worker.join(Duration.ofNanos(remaining))) {
                return;
            }
        } catch (InterruptedException _) {
            interrupted = true;
        }
        cancelled.set(true);
        worker.interrupt();
        try {
            client.closeResource();
        } finally {
            try {
                if (!worker.join(SHUTDOWN_GRACE)) {
                    LOGGER.log(WARNING, "OTLP metrics worker did not terminate after cancellation");
                }
            } catch (InterruptedException _) {
                interrupted = true;
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private static long retryDelay(Optional<String> retryAfter, long backoff) {
        if (retryAfter.isPresent()) {
            String value = retryAfter.get().strip();
            try {
                return Math.max(0, Math.multiplyExact(Long.parseLong(value), TimeUnit.SECONDS.toNanos(1)));
            } catch (ArithmeticException _) {
                return Long.MAX_VALUE;
            } catch (NumberFormatException _) {
                try {
                    return Math.max(0, Duration.between(Instant.now(),
                                                       ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                                                               .toInstant()).toNanos());
                } catch (ArithmeticException _) {
                    return Long.MAX_VALUE;
                } catch (DateTimeParseException _) {
                    // Invalid Retry-After values use the regular backoff.
                }
            }
        }
        return ThreadLocalRandom.current().nextLong(backoff / 2, backoff + backoff / 2);
    }

    private void run() {
        try {
            while (closeStarted.get() == null) {
                if (wakeup.tryAcquire(config.interval().toNanos(), TimeUnit.NANOSECONDS)
                        || closeStarted.get() != null) {
                    break;
                }
                export();
            }
            if (!cancelled.get() && remainingShutdownNanos() > 0) {
                export();
            }
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        } finally {
            try {
                client.closeResource();
            } finally {
                encoder.close();
            }
        }
    }

    private void export() {
        long budget = Math.min(timeoutNanos, remainingShutdownNanos());
        if (budget <= 0) {
            return;
        }
        long started = System.nanoTime();
        var active = new AtomicBoolean(true);
        try (var guard = DeadlineGuard.create(Duration.ofNanos(budget), () -> {
            interruptLock.lock();
            try {
                if (active.get()) {
                    worker.interrupt();
                }
            } finally {
                interruptLock.unlock();
            }
        })) {
            var request = encoder.collect();
            if (!request.resourceMetrics().isEmpty()) {
                // Count UTF-8 bytes without buffering the payload or sampling the registry again.
                var counter = new CountingOutputStream();
                jsonBinding.serialize(counter, request);
                long size = counter.count;
                if (size > maxRequestBytes) {
                    LOGGER.log(WARNING,
                               "Discarding OTLP metrics request (serialized size: {0} bytes, maximum: {1} bytes)",
                               size, maxRequestBytes);
                } else {
                    send(request, size, started, budget);
                }
            }
            if (guard.timedOut()) {
                LOGGER.log(WARNING, "OTLP metrics export exceeded its timeout");
            }
        } catch (RuntimeException e) {
            LOGGER.log(WARNING, "OTLP metrics export failed", e);
        } finally {
            interruptLock.lock();
            try {
                active.set(false);
                // The next interval and the final export must not inherit this export's timeout interruption.
                Thread.interrupted();
            } finally {
                interruptLock.unlock();
            }
        }
    }

    private void send(OtlpRequest payload, long size, long started, long budget) {
        long backoff = INITIAL_BACKOFF_NANOS;
        while (!cancelled.get() && !Thread.currentThread().isInterrupted()) {
            long remaining = Math.min(budget - (System.nanoTime() - started), remainingShutdownNanos());
            if (remaining <= 0) {
                return;
            }
            long delay;
            var request = client.post()
                    .uri(config.endpoint())
                    .contentType(MediaTypes.APPLICATION_JSON)
                    .accept(MediaTypes.APPLICATION_JSON)
                    .header(HeaderNames.CONTENT_LENGTH, Long.toString(size))
                    .readTimeout(Duration.ofNanos(remaining));
            headers.forEach(request::header);
            try (var response = request.outputStream(output -> {
                try (output) {
                    jsonBinding.serialize(output, payload);
                }
            })) {
                int status = response.status().code();
                if (status == 200) {
                    if (!response.headers().contentType().map(type -> type.test(MediaTypes.APPLICATION_JSON)).orElse(false)) {
                        LOGGER.log(WARNING, "OTLP metrics receiver returned an unexpected content type");
                        return;
                    }
                    byte[] body = response.inputStream().readNBytes(MAX_RESPONSE_BYTES + 1);
                    if (body.length > MAX_RESPONSE_BYTES) {
                        LOGGER.log(WARNING, "OTLP metrics receiver returned a response larger than 4 MiB");
                        return;
                    }
                    int length = body.length;
                    while (length > 0 && (body[length - 1] == ' ' || body[length - 1] == '\t'
                            || body[length - 1] == '\r' || body[length - 1] == '\n')) {
                        length--;
                    }
                    var parser = JsonParser.create(body, 0, length);
                    var responseMessage = jsonBinding.deserialize(parser, OtlpResponse.class);
                    if (responseMessage == null || parser.hasNext()) {
                        throw new JsonException("Expected an OTLP response object");
                    }
                    if (responseMessage.partialSuccess() != null) {
                        var partial = responseMessage.partialSuccess();
                        if (partial.rejectedDataPoints() > 0 || !partial.errorMessage().isEmpty()) {
                            LOGGER.log(WARNING, "OTLP metrics receiver reported partial success: {0} rejected data points; {1}",
                                       partial.rejectedDataPoints(), partial.errorMessage());
                        }
                    }
                    return;
                }
                if (status != 429 && status != 502 && status != 503 && status != 504) {
                    LOGGER.log(WARNING, "OTLP metrics export rejected with HTTP status {0}", status);
                    return;
                }
                delay = retryDelay(response.headers().first(HeaderNames.RETRY_AFTER), backoff);
            } catch (JsonException _) {
                // JSON parser exceptions can contain response bytes; keep payloads out of diagnostics.
                LOGGER.log(WARNING, "OTLP metrics receiver returned invalid JSON");
                return;
            } catch (UncheckedIOException | RuntimeUnknownHostException | IOException e) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                LOGGER.log(WARNING, "OTLP metrics transport failed; retrying within the export timeout", e);
                delay = retryDelay(Optional.empty(), backoff);
            }
            remaining = Math.min(budget - (System.nanoTime() - started), remainingShutdownNanos());
            if (delay >= remaining) {
                LOGGER.log(WARNING, "OTLP metrics export could not complete within its timeout");
                return;
            }
            try {
                TimeUnit.NANOSECONDS.sleep(delay);
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
                return;
            }
            backoff = Math.min(backoff * 2, MAX_BACKOFF_NANOS);
        }
    }

    private long remainingShutdownNanos() {
        Long started = closeStarted.get();
        return started == null ? timeoutNanos : timeoutNanos - (System.nanoTime() - started);
    }

    private static final class CountingOutputStream extends OutputStream {
        private long count;

        @Override
        public void write(int value) {
            count++;
        }

        @Override
        public void write(byte[] bytes, int offset, int length) {
            count += length;
        }
    }
}
