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
import java.util.concurrent.CompletableFuture;

import io.helidon.common.reactive.BufferedEmittingPublisher;
import io.helidon.common.reactive.Single;

import org.eclipse.microprofile.reactive.messaging.Message;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Mock-able incoming channel connected to the mock connector.
 *
 * @param <P> type of the payload
 */
public class MockIncoming<P> {

    private final BufferedEmittingPublisher<Message<?>> publisher;
    private final CompletableFuture<Void> onAbortOrComplete;

    MockIncoming(BufferedEmittingPublisher<Message<?>> publisher) {
        this.publisher = publisher;
        onAbortOrComplete = new CompletableFuture<>();
        publisher.onAbort(throwable -> onAbortOrComplete.complete(null));
    }

    /**
     * Emit one or more payloads directly to the mocked channel.
     *
     * @param payload one or more payloads to send down the stream
     * @return this channel mocker
     */
    @SafeVarargs
    public final MockIncoming<P> emit(P... payload) {
        for (P item : payload) {
            publisher.emit(Message.of(item));
        }
        return this;
    }

    /**
     * Emit one or more messages directly to the mocked channel.
     *
     * @param message one or more messages to send down the stream
     * @return this channel mocker
     */
    @SafeVarargs
    public final MockIncoming<P> emit(Message<P>... message) {
        for (Message<P> msg : message) {
            publisher.emit(msg);
        }
        return this;
    }

    /**
     * Send terminal complete signal to the channel.
     *
     * @return this channel mocker
     */
    public MockIncoming<P> complete() {
        publisher.complete();
        onAbortOrComplete.complete(null);
        return this;
    }

    /**
     * Send terminal error signal to the channel.
     *
     * @param t cause of the channel termination
     * @return this channel mocker
     */
    public MockIncoming<P> fail(Throwable t) {
        publisher.fail(t);
        onAbortOrComplete.complete(null);
        return this;
    }

    /**
     * Wait and block till the stream is in terminal state
     * and asserts if the terminal state is caused by cancel signal.
     *
     * @param timeout Timeout for waiting on the stream being cancelled.
     * @return this mock
     */
    public MockIncoming<P> awaitCancelled(Duration timeout) {
        Single.create(onAbortOrComplete, true).await(timeout);
        assertThat(publisher.isCancelled(), is(true));
        return this;
    }

    BufferedEmittingPublisher<Message<?>> emitter() {
        return this.publisher;
    }
}
