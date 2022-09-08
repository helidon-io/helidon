/*
 * Copyright (c) 2017, 2022 Oracle and/or its affiliates.
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

package io.helidon.reactive.webserver;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.common.http.Http;
import io.helidon.common.http.HttpMediaType;
import io.helidon.common.http.ServerResponseHeaders;
import io.helidon.common.http.SetCookie;
import io.helidon.common.reactive.Single;

/**
 * A {@link ResponseHeaders} implementation.
 */
class HashResponseHeaders implements ResponseHeaders {

    private static final String COMPLETED_EXCEPTION_MESSAGE = "Response headers are already completed (sent to the client)!";

    private static final LazyValue<ZonedDateTime> START_OF_YEAR_1970 = LazyValue.create(
            () -> ZonedDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneId.of("GMT+0")));

    // status is by default null, so we can check if it was explicitly set
    private volatile Http.Status httpStatus;
    private final CompletionSupport completable;
    private final CompletableFuture<ResponseHeaders> completionStage = new CompletableFuture<>();
    private final ServerResponseHeaders headers = ServerResponseHeaders.create();

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
        this.set(Http.Header.DATE, ZonedDateTime.now().format(Http.DateTime.RFC_1123_DATE_TIME));
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
    Http.Status httpStatus() {
        return httpStatus;
    }

    /**
     * Sets an HTTP status code. The code is managed with headers because it has the same lifecycle.
     *
     * @param httpStatusCode an HTTP status code.
     */
    void httpStatus(Http.Status httpStatusCode) {
        Objects.requireNonNull(httpStatusCode, "Parameter 'httpStatus' is null!");
        completable.runIfNotCompleted(() -> this.httpStatus = httpStatusCode,
                                      "Response status code and headers are already completed (sent to the client)!");
    }

    // --------------------------------------------------------------------
    // --- Limit access to HashParameters mutable methods when closed.
    // ---------------------------------------------------------------------

    @Override
    public List<String> all(Http.HeaderName name, Supplier<List<String>> defaultSupplier) {
        return headers.all(name, defaultSupplier);
    }

    @Override
    public boolean contains(Http.HeaderName name) {
        return headers.contains(name);
    }

    @Override
    public boolean contains(Http.HeaderValue value) {
        return headers.contains(value);
    }

    @Override
    public Http.HeaderValue get(Http.HeaderName name) {
        return headers.get(name);
    }

    @Override
    public int size() {
        return headers.size();
    }

    @Override
    public ServerResponseHeaders addCookie(SetCookie cookie) {
        completable.runIfNotCompleted(() -> headers.addCookie(cookie), COMPLETED_EXCEPTION_MESSAGE);
        return this;
    }

    @Override
    public ServerResponseHeaders clearCookie(String name) {
        completable.runIfNotCompleted(() -> headers.clearCookie(name), COMPLETED_EXCEPTION_MESSAGE);
        return this;
    }

    @Override
    public ServerResponseHeaders setIfAbsent(Http.HeaderValue header) {
        completable.runIfNotCompleted(() -> headers.setIfAbsent(header), COMPLETED_EXCEPTION_MESSAGE);
        return this;
    }

    @Override
    public ServerResponseHeaders add(Http.HeaderValue header) {
        completable.runIfNotCompleted(() -> headers.add(header), COMPLETED_EXCEPTION_MESSAGE);
        return this;
    }

    @Override
    public ServerResponseHeaders remove(Http.HeaderName name) {
        completable.runIfNotCompleted(() -> headers.remove(name), COMPLETED_EXCEPTION_MESSAGE);
        return this;
    }

    @Override
    public ServerResponseHeaders remove(Http.HeaderName name, Consumer<Http.HeaderValue> removedConsumer) {
        completable.runIfNotCompleted(() -> headers.remove(name, removedConsumer), COMPLETED_EXCEPTION_MESSAGE);
        return this;
    }

    @Override
    public ServerResponseHeaders set(Http.HeaderValue header) {
        completable.runIfNotCompleted(() -> headers.set(header), COMPLETED_EXCEPTION_MESSAGE);
        return this;
    }

    @Override
    public ResponseHeaders clear() {
        completable.runIfNotCompleted(headers::clear, COMPLETED_EXCEPTION_MESSAGE);
        return this;
    }

    @Override
    public Iterator<Http.HeaderValue> iterator() {
        return headers.iterator();
    }

    @Override
    public List<HttpMediaType> acceptedTypes() {
        return List.of();
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
        return Single.create(completionStage);
    }

    @Override
    public Single<ResponseHeaders> send() {
        completable.doComplete(this);
        return whenSent();
    }

    /**
     * If not yet completed then completes and return {@code true}, otherwise returns {@code false}.
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
            OPEN, COMPLETING, COMPLETED
        }

        // A simple locking mechanism.
        private volatile State state = State.OPEN;
        private final List<Consumer<ResponseHeaders>> beforeCompleteConsumers = new ArrayList<>();
        private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
        private final BareResponse bareResponse;

        /**
         * Creates new instance.
         *
         * @param bareResponse bare response
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
                    Http.Status status = (null == headers.httpStatus) ? Http.Status.OK_200 : headers.httpStatus;
                    status = (null == status) ?  Http.Status.OK_200 : status;
                    Map<String, List<String>> rawHeaders = filterSpecificHeaders(headers, status);
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
         * @param headers
         * @param status  response status code
         * @return filtered headers
         */
        private Map<String, List<String>> filterSpecificHeaders(HashResponseHeaders headers, Http.Status status) {
            Map<String, List<String>> data = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

            headers.headers.iterator()
                    .forEachRemaining(it -> data.put(it.name(), it.allValues()));

            if (status.code() == Http.Status.NO_CONTENT_204.code()) {
                data.remove(Http.Header.TRANSFER_ENCODING.defaultCase());
                data.remove(Http.Header.CONTENT_DISPOSITION.defaultCase());
                data.remove(Http.Header.CONTENT_ENCODING.defaultCase());
                data.remove(Http.Header.CONTENT_LANGUAGE.defaultCase());
                data.remove(Http.Header.CONTENT_LENGTH.defaultCase());
                data.remove(Http.Header.CONTENT_LOCATION.defaultCase());
                data.remove(Http.Header.CONTENT_RANGE.defaultCase());
                data.remove(Http.Header.CONTENT_TYPE.defaultCase());
            }
            return data;
        }
    }
}
