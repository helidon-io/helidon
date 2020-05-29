/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

package io.helidon.webserver;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.helidon.common.http.AlreadyCompletedException;
import io.helidon.common.http.HashParameters;
import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.common.http.Parameters;
import io.helidon.common.http.SetCookie;
import io.helidon.common.http.Utils;
import io.helidon.common.reactive.Single;

/**
 * A {@link ResponseHeaders} implementation on top of {@link HashParameters}.
 */
class HashResponseHeaders extends HashParameters implements ResponseHeaders {

    private static final String COMPLETED_EXCEPTION_MESSAGE = "Response headers are already completed (sent to the client)!";

    // status is by default null, so we can check if it was explicitly set
    private volatile Http.ResponseStatus httpStatus;
    private final CompletionSupport completable;
    private final CompletableFuture<ResponseHeaders> completionStage = new CompletableFuture<>();

    /**
     * Creates a new instance.
     *
     * @param bareResponse the bare response to send headers
     */
    HashResponseHeaders(BareResponse bareResponse) {
        this.completable = new CompletionSupport(bareResponse);
        if (bareResponse != null) {
            bareResponse.whenHeadersCompleted()
                    .thenRun(() -> completionStage.complete(this))
                    .exceptionally(thr -> {
                        completionStage.completeExceptionally(thr);
                        return null;
                    });
        }
        // Set standard headers
        this.put(Http.Header.DATE, ZonedDateTime.now().format(Http.DateTime.RFC_1123_DATE_TIME));
    }

    @Override
    public List<MediaType> acceptPatches() {
        List<MediaType> result = all(Http.Header.ACCEPT_PATCH).stream()
                .flatMap(h -> Utils.tokenize(',', "\"", false, h).stream())
                .map(String::trim)
                .map(MediaType::parse)
                .collect(Collectors.toList());
        return Collections.unmodifiableList(result);
    }

    @Override
    public void addAcceptPatches(MediaType... acceptableMediaTypes) {
        if (acceptableMediaTypes == null) {
            return;
        }
        for (MediaType mt : acceptableMediaTypes) {
            add(Http.Header.ACCEPT_PATCH, mt.toString());
        }
    }

    @Override
    public Optional<MediaType> contentType() {
        return first(Http.Header.CONTENT_TYPE).map(MediaType::parse);
    }

    @Override
    public void contentType(MediaType contentType) {
        if (contentType == null) {
            remove(Http.Header.CONTENT_TYPE);
        } else {
            put(Http.Header.CONTENT_TYPE, contentType.toString());
        }
    }

    @Override
    public OptionalLong contentLength() {
        return first(Http.Header.CONTENT_LENGTH).stream()
                .mapToLong(Long::parseLong).findFirst();
    }

    @Override
    public void contentLength(long contentLength) {
        put(Http.Header.CONTENT_LENGTH, String.valueOf(contentLength));
    }

    @Override
    public Optional<ZonedDateTime> expires() {
        return first(Http.Header.EXPIRES).map(Http.DateTime::parse);
    }

    @Override
    public void expires(ZonedDateTime dateTime) {
        if (dateTime == null) {
            remove(Http.Header.EXPIRES);
        } else {
            put(Http.Header.EXPIRES, dateTime.format(Http.DateTime.RFC_1123_DATE_TIME));
        }
    }

    @Override
    public void expires(Instant dateTime) {
        if (dateTime == null) {
            remove(Http.Header.EXPIRES);
        } else {
            ZonedDateTime dt = ZonedDateTime.ofInstant(dateTime, ZoneId.systemDefault());
            put(Http.Header.EXPIRES, dt.format(Http.DateTime.RFC_1123_DATE_TIME));
        }
    }

    @Override
    public Optional<ZonedDateTime> lastModified() {
        return first(Http.Header.LAST_MODIFIED).map(Http.DateTime::parse);
    }

    @Override
    public void lastModified(ZonedDateTime dateTime) {
        if (dateTime == null) {
            remove(Http.Header.LAST_MODIFIED);
        } else {
            put(Http.Header.LAST_MODIFIED, dateTime.format(Http.DateTime.RFC_1123_DATE_TIME));
        }
    }

    @Override
    public void lastModified(Instant dateTime) {
        if (dateTime == null) {
            remove(Http.Header.LAST_MODIFIED);
        } else {
            ZonedDateTime dt = ZonedDateTime.ofInstant(dateTime, ZoneId.systemDefault());
            put(Http.Header.LAST_MODIFIED, dt.format(Http.DateTime.RFC_1123_DATE_TIME));
        }
    }

    @Override
    public Optional<URI> location() {
        return first(Http.Header.LOCATION).map(URI::create);
    }

    @Override
    public void location(URI location) {
        if (location == null) {
            remove(Http.Header.LOCATION);
        } else {
            put(Http.Header.LOCATION, location.toASCIIString());
        }
    }

    @Override
    public void addCookie(String name, String value) {
        add(Http.Header.SET_COOKIE, SetCookie.create(name, value).toString());
    }

    @Override
    public void addCookie(String name, String value, Duration maxAge) {
        add(Http.Header.SET_COOKIE,
            SetCookie.builder(name, value)
                    .maxAge(maxAge)
                    .build()
                    .toString());
    }

    @Override
    public void addCookie(SetCookie cookie) {
        Objects.requireNonNull(cookie, "Parameter 'cookie' is null!");
        add(Http.Header.SET_COOKIE, cookie.toString());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HashResponseHeaders)) {
            return false;
        }

        HashResponseHeaders that = (HashResponseHeaders) o;

        if (super.equals(that)) {
            if (httpStatus == null) {
                return that.httpStatus == null;
            }
            if (that.httpStatus == null) {
                return false;
            }
            return this.httpStatus.equals(that.httpStatus);
        }

        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), this.httpStatus);
    }

    /**
     * Returns an HTTP status code. The code is managed with headers because it has the same lifecycle.
     *
     * @return an HTTP status code.
     */
    Http.ResponseStatus httpStatus() {
        return httpStatus;
    }

    /**
     * Sets an HTTP status code. The code is managed with headers because it has the same lifecycle.
     *
     * @status an HTTP status code.
     */
    void httpStatus(Http.ResponseStatus httpStatusCode) {
        Objects.requireNonNull(httpStatusCode, "Parameter 'httpStatus' is null!");
        completable.runIfNotCompleted(() -> this.httpStatus = httpStatusCode,
                                      "Response status code and headers are already completed (sent to the client)!");
    }

    // --------------------------------------------------------------------
    // --- Limit access to HashParameters mutable methods when closed.
    // ---------------------------------------------------------------------

    @Override
    public List<String> put(String key, String... values) {
        return completable.supplyIfNotCompleted(() -> super.put(key, values), COMPLETED_EXCEPTION_MESSAGE);
    }

    @Override
    public List<String> put(String key, Iterable<String> values) {
        return completable.supplyIfNotCompleted(() -> super.put(key, values), COMPLETED_EXCEPTION_MESSAGE);
    }

    @Override
    public List<String> putIfAbsent(String key, String... values) {
        return completable.supplyIfNotCompleted(() -> super.putIfAbsent(key, values), COMPLETED_EXCEPTION_MESSAGE);
    }

    @Override
    public List<String> putIfAbsent(String key, Iterable<String> values) {
        return completable.supplyIfNotCompleted(() -> super.putIfAbsent(key, values), COMPLETED_EXCEPTION_MESSAGE);
    }

    @Override
    public List<String> computeIfAbsent(String key, Function<String, Iterable<String>> values) {
        return completable.supplyIfNotCompleted(() -> super.computeIfAbsent(key, values), COMPLETED_EXCEPTION_MESSAGE);
    }

    @Override
    public List<String> computeSingleIfAbsent(String key, Function<String, String> value) {
        return completable.supplyIfNotCompleted(() -> super.computeSingleIfAbsent(key, value), COMPLETED_EXCEPTION_MESSAGE);
    }

    @Override
    public void putAll(Parameters parameters) {
        completable.runIfNotCompleted(() -> super.putAll(parameters), COMPLETED_EXCEPTION_MESSAGE);
    }

    @Override
    public void add(String key, String... values) {
        completable.runIfNotCompleted(() -> super.add(key, values), COMPLETED_EXCEPTION_MESSAGE);
    }

    @Override
    public void add(String key, Iterable<String> values) {
        completable.runIfNotCompleted(() -> super.add(key, values), COMPLETED_EXCEPTION_MESSAGE);
    }

    @Override
    public void addAll(Parameters parameters) {
        completable.runIfNotCompleted(() -> super.addAll(parameters), COMPLETED_EXCEPTION_MESSAGE);
    }

    @Override
    public List<String> remove(String key) {
        return completable.supplyIfNotCompleted(() -> super.remove(key), COMPLETED_EXCEPTION_MESSAGE);
    }

    // ----------------------------------
    // --- COMPLETION OPERATIONS
    // ----------------------------------

    @Override
    public void beforeSend(Consumer<ResponseHeaders> headersConsumer) {
        completable.beforeComplete(headersConsumer);
    }

    @Override
    public Single<ResponseHeaders> whenSent() {
        return Single.from(completionStage);
    }

    @Override
    public Single<ResponseHeaders> send() {
        completable.doComplete(this);
        return whenSent();
    }

    /**
     * If not yet completed then ompletes and return {@code true}, otherwise returns {@code false}.
     * <p>
     * All possible exceptions are forwarded to {@link BareResponse#onError(Throwable)} method.
     *
     * @return {@code true} if this method call completes headers. If {@code false} then headers were completed.
     */
    boolean sendNow() {
        return completable.doComplete(this);
    }

    /**
     * Contains simple completion logic. It is separated just to provide some code organisation.
     */
    private static class CompletionSupport {

        private enum State {
            OPEN, COMPLETING, COMPLETED;
        }

        // A simple locking mechanism.
        private volatile State state = State.OPEN;
        private final List<Consumer<ResponseHeaders>> beforeCompleteConsumers = new ArrayList<>();
        private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
        private final BareResponse bareResponse;

        /**
         * Creates new instance.
         *
         * @param bareResponse
         */
        CompletionSupport(BareResponse bareResponse) {
            this.bareResponse = bareResponse;
        }

        /**
         * If not completed then runs provided {@link Runnable}, otherwise throws {@link AlreadyCompletedException}.
         *
         * @param runnable         to run.
         * @param exceptionMessage a detail message of potential {@link AlreadyCompletedException}.
         * @throws AlreadyCompletedException if resource is already completed.
         */
        void runIfNotCompleted(Runnable runnable, String exceptionMessage) {
            // When resource is completed, then write-lock is locked.
            // It means then if cannot lock a read-lock then provided runnable should not be executed!.
            if (rwLock.readLock().tryLock()) {
                try {
                    if (state != State.COMPLETED) { // Lock must be unlocked any-way
                        runnable.run();
                    } else {
                        throw new AlreadyCompletedException(exceptionMessage);
                    }
                } finally {
                    rwLock.readLock().unlock();
                }
            } else {
                throw new AlreadyCompletedException(exceptionMessage);
            }
        }

        /**
         * If not completed then executes provided {@link Supplier} and returns it's result,
         * otherwise throws {@link AlreadyCompletedException}.
         *
         * @param supplier         to execute.
         * @param exceptionMessage a detail message of potential {@link AlreadyCompletedException}.
         * @throws AlreadyCompletedException if resource is already completed.
         */
        <R> R supplyIfNotCompleted(Supplier<R> supplier, String exceptionMessage) {
            // When resource is completed, then write-lock is locked.
            // It means then if cannot lock a read-lock then provided runnable should not be executed!.
            if (rwLock.readLock().tryLock()) {
                try {
                    if (state != State.COMPLETED) { // Lock must be unlocked any-way
                        return supplier.get();
                    } else {
                        throw new AlreadyCompletedException(exceptionMessage);
                    }
                } finally {
                    rwLock.readLock().unlock();
                }
            } else {
                throw new AlreadyCompletedException(exceptionMessage);
            }
        }

        /**
         * Provided consumer is executed just before resource is completed.
         * <p>
         * Consumers are executed in registration order.
         *
         * @param consumer a consumer which will be executed just before response is completed.
         * @throws AlreadyCompletedException if resource is already completed.
         */
        synchronized void beforeComplete(Consumer<ResponseHeaders> consumer) {
            if (state == State.OPEN) {
                beforeCompleteConsumers.add(consumer);
            } else {
                throw new AlreadyCompletedException("Cannot accept new 'beforeComplete consumer'! Headers are sent.");
            }
        }

        /**
         * If not yet completed then completes and returns {@code true}, otherwise returns {@code false}.
         * <p>
         * All possible exceptions are forwarded to {@link BareResponse#onError(Throwable)} method.
         *
         * @param headers headers which are completed
         * @return {@code true} if this method call completes headers. If {@code false} then headers were completed.
         */
        synchronized boolean doComplete(HashResponseHeaders headers) {
            if (state != State.OPEN) {
                return false;
            }

            state = State.COMPLETING;
            try {
                // Finish all registered before complete consumers
                for (Consumer<ResponseHeaders> consumer : beforeCompleteConsumers) {
                    consumer.accept(headers);
                }
                // Lock and write response headers
                rwLock.writeLock().lock();
                try {
                    state = State.COMPLETED;
                    Http.ResponseStatus status = (null == headers.httpStatus) ? Http.Status.OK_200 : headers.httpStatus;
                    status = (null == status) ?  Http.Status.OK_200 : status;
                    Map<String, List<String>> rawHeaders = filterSpecificHeaders(headers.toMap(), status);
                    bareResponse.writeStatusAndHeaders(status, rawHeaders);
                } finally {
                    rwLock.writeLock().unlock();
                }
            } catch (Throwable th) {
                bareResponse.onError(th);
            }
            return true;
        }

        /**
         * Specific status codes requires or omits specific headers.
         *
         * @param data   mutable headers data
         * @param status response status code
         * @return filtered headers
         */
        private Map<String, List<String>> filterSpecificHeaders(Map<String, List<String>> data, Http.ResponseStatus status) {
            if (data == null) {
                return null;
            }
            if (status.code() == Http.Status.NO_CONTENT_204.code()) {
                data.remove(Http.Header.TRANSFER_ENCODING);
                data.remove(Http.Header.CONTENT_DISPOSITION);
                data.remove(Http.Header.CONTENT_ENCODING);
                data.remove(Http.Header.CONTENT_LANGUAGE);
                data.remove(Http.Header.CONTENT_LENGTH);
                data.remove(Http.Header.CONTENT_LOCATION);
                data.remove(Http.Header.CONTENT_RANGE);
                data.remove(Http.Header.CONTENT_TYPE);
            }
            return data;
        }
    }
}
