/*
 * Copyright (c)  2020 Oracle and/or its affiliates.
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
 *
 */

package io.helidon.messaging;

import java.util.LinkedList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.eclipse.microprofile.reactive.messaging.Message;

public class TestMessages<T> extends LinkedList<TestMessages.TestMessage<T>> {

    public void assertAllAcked() {
        assertThat(this.stream().allMatch(TestMessage::acked), is(true));
    }

    public void assertNoneAcked() {
        assertThat(this.stream().anyMatch(TestMessage::acked), is(false));
    }

    public Message<T> of(T payload) {
        TestMessage<T> m = new TestMessage<T>(payload);
        this.add(m);
        return m;
    }

    static class TestMessage<T> implements Message<T> {

        boolean acked = false;
        T payload;

        TestMessage(final T payload) {
            this.payload = payload;
        }

        boolean acked() {
            return acked;
        }

        @Override
        public T getPayload() {
            return payload;
        }

        @Override
        public CompletionStage<Void> ack() {
            acked = true;
            return CompletableFuture.completedStage(null);
        }

    }
}
