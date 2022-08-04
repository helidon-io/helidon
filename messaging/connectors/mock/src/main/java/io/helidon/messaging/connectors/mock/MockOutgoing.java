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

package io.helidon.messaging.connectors.mock;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.helidon.common.reactive.Single;

import org.eclipse.microprofile.reactive.messaging.Message;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;

import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Mock-able outgoing channel connected to the mock connector.
 *
 * @param <P> type of the payload
 */
public class MockOutgoing<P> {

    private final MockSubscriber mockSubscriber;

    MockOutgoing(MockSubscriber mockSubscriber) {
        this.mockSubscriber = mockSubscriber;
    }

    /**
     * Control backpressure manually and request {@code n} items from upstream.
     *
     * @param n number of items requested from upstream
     * @return this mocker
     */
    public MockOutgoing<P> request(long n) {
        if (n <= 0L) {
            throw new IllegalArgumentException("Rule §3.9 violated: non-positive requests are forbidden!");
        }
        if (mockSubscriber.upstream() == null) {
            throw new IllegalStateException("Not subscribed yet.");
        }
        mockSubscriber.upstream().request(n);
        return this;
    }

    /**
     * Request {@code unbounded} number of items from upstream and effectively turn off backpressure.
     *
     * @return this mocker
     */
    public MockOutgoing<P> requestMax() {
        return request(Long.MAX_VALUE);
    }

    /**
     * Returns single which is completed when the terminates with the complete signal.
     *
     * @return this mocker
     */
    public Single<Void> whenComplete() {
        return mockSubscriber.completed();
    }

    /**
     * Block current thread until channel gets terminated with complete signal.
     *
     * @param timeout timeout when reached before complete signal is received, Completion exception is thrown.
     * @return this mocker
     * @throws java.util.concurrent.CancellationException if the future was cancelled
     * @throws java.util.concurrent.CompletionException   if the future completed exceptionally,
     *                                                    was interrupted while waiting or the wait timed out
     */
    public MockOutgoing<P> awaitComplete(Duration timeout) {
        whenComplete().await(timeout);
        return this;
    }

    /**
     * Block current thread until expected number of items is received.
     *
     * @param count number of items to be received for releasing current thread
     * @param timeout timeout when reached before specified number of items is received, Completion exception is thrown.
     * @return this mocker
     * @throws java.util.concurrent.CancellationException if the future was cancelled
     * @throws java.util.concurrent.CompletionException   if the future completed exceptionally,
     *                                                    was interrupted while waiting or the wait timed out
     */
    public MockOutgoing<P> awaitCount(Duration timeout, int count) {
        CompletableFuture<Void> latch = new CompletableFuture<>();
        Consumer<Integer> counter = l -> {
            if (l >= count) {
                latch.complete(null);
            }
        };
        mockSubscriber.counters().add(counter);
        counter.accept(mockSubscriber.data().size());
        Single.create(latch, true).await(timeout);
        return this;
    }

    /**
     * Block current thread until expected message is received.
     *
     * @param waitingFor function for comparing each received message, when true is returned, wait is over.
     * @param timeout timeout when reached before expected item is received, Completion exception is thrown.
     * @return this mocker
     * @throws java.util.concurrent.CancellationException if the future was cancelled
     * @throws java.util.concurrent.CompletionException   if the future completed exceptionally,
     *                                                    was interrupted while waiting or the wait timed out
     */
    @SuppressWarnings("unchecked")
    public MockOutgoing<P> awaitMessage(Duration timeout, Function<Message<P>, Boolean> waitingFor) {
        this.request(1);
        CompletableFuture<Void> latch = new CompletableFuture<>();
        Consumer<Message<?>> checker = m -> {
            if (waitingFor.apply((Message<P>) m)) {
                latch.complete(null);
            } else {
                this.request(1);
            }
        };
        mockSubscriber.checkers().add(checker);
        mockSubscriber.data().forEach(checker);
        Single.create(latch, true).await(timeout);
        return this;
    }

    /**
     * Block current thread until expected messages are received.
     *
     * @param mapper to be used for unwrapping the message for comparison with expected values
     * @param expectedItems one or more expected values
     * @param timeout timeout when reached before expected item is received, Completion exception is thrown.
     * @return this mocker
     * @throws java.util.concurrent.CancellationException if the future was cancelled
     * @throws java.util.concurrent.CompletionException   if the future completed exceptionally,
     *                                                    was interrupted while waiting or the wait timed out
     */
    @SuppressWarnings("unchecked")
    public MockOutgoing<P> awaitData(Duration timeout, Function<Message<P>, P> mapper, P... expectedItems) {
        this.request(expectedItems.length);
        try {
            this.awaitCount(timeout, expectedItems.length);
        } catch (CompletionException e) {
            if (e.getCause() instanceof TimeoutException) {
                assertThat("Not all " + expectedItems.length + " items delivered in time.",
                        mockSubscriber.data().stream()
                                .map(message -> mapper.apply((Message<P>) message))
                                .toList(),
                        Matchers.contains(expectedItems));
            } else {
                throw e;
            }

        }
        assertThat(mockSubscriber.data().stream()
                .map(message -> mapper.apply((Message<P>) message))
                .collect(Collectors.toList()), Matchers.contains(expectedItems));
        return this;
    }

    /**
     * Block current thread until expected payloads are received.
     *
     * @param expectedItems one or more expected payload values
     * @param timeout timeout when reached before expected item is received, Completion exception is thrown.
     * @return this mocker
     * @throws java.util.concurrent.CancellationException if the future was cancelled
     * @throws java.util.concurrent.CompletionException   if the future completed exceptionally,
     *                                                    was interrupted while waiting or the wait timed out
     */
    @SafeVarargs
    public final MockOutgoing<P> awaitPayloads(Duration timeout, P... expectedItems) {
        return awaitData(timeout, Message::getPayload, expectedItems);
    }

    /**
     * Assert if matching data has been received.
     *
     * @param mapper  to be used for unwrapping the messages for matching
     * @param matcher matcher to be used for asserting whole collection of the received and with provided mapper unwrapped data.
     * @param <T> matching type
     * @return this mocker
     * @throws java.util.concurrent.CancellationException if the future was cancelled
     * @throws java.util.concurrent.CompletionException   if the future completed exceptionally,
     *                                                    was interrupted while waiting or the wait timed out
     */
    @SuppressWarnings("unchecked")
    public <T> MockOutgoing<P> assertData(Function<Message<P>, P> mapper, Matcher<? super T> matcher) {
        T result = (T) mockSubscriber.data().stream()
                .map(message -> mapper.apply((Message<P>) message))
                .toList();
        assertThat(result, matcher);
        return this;
    }

    /**
     * Assert if matching payloads has been received.
     *
     * @param matcher applied on the whole collection of the received payloads
     * @param <T>     matching type
     * @return this mocker
     */
    public <T> MockOutgoing<P> assertPayloads(Matcher<? super T> matcher) {
        return assertData(Message::getPayload, matcher);
    }

    /**
     * Assert if matching payloads has been received.
     *
     * @param expected applied on the whole collection of the received payloads
     * @return this mocker
     */
    @SafeVarargs
    public final MockOutgoing<P> assertPayloads(P... expected) {
        return assertPayloads(Matchers.contains(expected));
    }

    /**
     * Assert if matching messages has been received.
     *
     * @param mapper to be used for unwrapping each message before comparison
     * @param items  applied on the whole collection of the received payloads
     * @return this mocker
     */
    @SafeVarargs
    public final MockOutgoing<P> assertData(Function<Message<P>, P> mapper, P... items) {
        return assertData(Message::getPayload, Matchers.contains(items));
    }

    /**
     * Get all received data at this moment.
     *
     * @return All received data at this moment in unmodifiable list
     */
    @SuppressWarnings("unchecked")
    public List<Message<P>> data() {
        return mockSubscriber.data().stream().map(m -> (Message<P>) m).toList();
    }

    MockSubscriber subscriber(){
        return mockSubscriber;
    }
}
