/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.examples.microprofile.grpc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.helidon.grpc.api.Grpc;
import io.helidon.microprofile.grpc.client.GrpcClientCdiExtension;
import io.helidon.microprofile.grpc.server.GrpcMpCdiExtension;
import io.helidon.microprofile.testing.junit5.AddExtension;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import io.grpc.stub.StreamObserver;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;

@HelidonTest
@AddExtension(GrpcMpCdiExtension.class)
@AddExtension(GrpcClientCdiExtension.class)
class StringServiceTest {

    @Inject
    @Grpc.GrpcProxy
    private StringServiceClient client;

    @Test
    void testUnaryUpper() {
        Strings.StringMessage res = client.upper(newMessage("hello"));
        assertThat(res.getText(), is("HELLO"));
    }

    @Test
    void testUnaryLower() {
        Strings.StringMessage res = client.lower(newMessage("HELLO"));
        assertThat(res.getText(), is("hello"));
    }

    @Test
    void testServerStreamingSplit() throws InterruptedException {
        ListObserver<Strings.StringMessage> response = new ListObserver<>();
        client.split(newMessage("hello world"), response);
        List<Strings.StringMessage> value = response.value();
        assertThat(value, hasSize(2));
        assertThat(value, contains(newMessage("hello"), newMessage("world")));
    }

    @Test
    void testClientStreamingJoin() throws InterruptedException {
        ListObserver<Strings.StringMessage> response = new ListObserver<>();
        StreamObserver<Strings.StringMessage> request = client.join(response);
        request.onNext(newMessage("hello"));
        request.onNext(newMessage("world"));
        request.onCompleted();
        List<Strings.StringMessage> value = response.value();
        assertThat(value.getFirst(), is(newMessage("hello world")));
    }

    /**
     * Helper method to create a string message from a string.
     *
     * @param data the string
     * @return the string message
     */
    Strings.StringMessage newMessage(String data) {
        return Strings.StringMessage.newBuilder().setText(data).build();
    }

    /**
     * Helper class to collect a list of observed values.
     *
     * @param <T> the type of values
     */
    static class ListObserver<T> implements StreamObserver<T> {
        private static final long TIMEOUT_SECONDS = 10;

        private List<T> value = new ArrayList<>();
        private final CountDownLatch latch = new CountDownLatch(1);

        public List<T> value() throws InterruptedException {
            boolean b = latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assert b;
            return value;
        }

        @Override
        public void onNext(T value) {
            this.value.add(value);
        }

        @Override
        public void onError(Throwable t) {
            value = null;
        }

        @Override
        public void onCompleted() {
            latch.countDown();
        }
    }
}

