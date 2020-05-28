/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security.integration.jersey;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import io.helidon.common.reactive.IoMulti;
import io.helidon.common.reactive.Multi;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit test for {@code InputStreamPublisher}'s replacement.
 */
public class InputStreamPublisherTest {
    @Test
    public void testSingle() throws InterruptedException {
        String teststring = "My text to publish with publisher";

        Multi<ByteBuffer> p = IoMulti.builder(new ByteArrayInputStream(teststring.getBytes(StandardCharsets.UTF_8)))
                .byteBufferSize(1024)
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CountDownLatch cdl = new CountDownLatch(1);

        p.subscribe(new Flow.Subscriber<ByteBuffer>() {
            public Flow.Subscription subscription;

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                this.subscription = subscription;
                this.subscription.request(1);
            }

            @Override
            public void onNext(ByteBuffer item) {
                byte[] bytes = new byte[item.remaining()];
                item.get(bytes);
                try {
                    baos.write(bytes);
                } catch (IOException ignored) {
                }
            }

            @Override
            public void onError(Throwable throwable) {

            }

            @Override
            public void onComplete() {
                cdl.countDown();
            }
        });

        cdl.await(1000, TimeUnit.MILLISECONDS);
        String result = new String(baos.toByteArray(), StandardCharsets.UTF_8);
        assertThat(result, is(teststring));
    }

    @Test
    public void testMultiple() throws InterruptedException {
        String teststring = "My text to publish with publisher";
        Multi<ByteBuffer> p = IoMulti.builder(new ByteArrayInputStream(teststring.getBytes(StandardCharsets.UTF_8)))
                .byteBufferSize(1)
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CountDownLatch cdl = new CountDownLatch(1);

        p.subscribe(new Flow.Subscriber<ByteBuffer>() {
            public Flow.Subscription subscription;

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                this.subscription = subscription;
                this.subscription.request(1);
            }

            @Override
            public void onNext(ByteBuffer item) {
                byte[] bytes = new byte[item.remaining()];
                item.get(bytes);
                try {
                    baos.write(bytes);
                } catch (IOException ignored) {
                }
                this.subscription.request(1);
            }

            @Override
            public void onError(Throwable throwable) {

            }

            @Override
            public void onComplete() {
                cdl.countDown();
            }
        });

        cdl.await(1000, TimeUnit.MILLISECONDS);
        String result = new String(baos.toByteArray(), StandardCharsets.UTF_8);
        assertThat(result, is(teststring));
    }

    @Test
    public void testVeryLong() throws IOException, InterruptedException {
        String teststring = "someMoreBytesBytes";
        StringBuilder expectedResult = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            expectedResult.append(teststring);
        }
        teststring = expectedResult.toString();

        Multi<ByteBuffer> p = IoMulti.builder(new ByteArrayInputStream(teststring.getBytes(StandardCharsets.UTF_8)))
                .byteBufferSize(2)
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CountDownLatch cdl = new CountDownLatch(1);

        p.subscribe(new Flow.Subscriber<ByteBuffer>() {
            private Flow.Subscription subscription;

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                this.subscription = subscription;
                this.subscription.request(2);
            }

            @Override
            public void onNext(ByteBuffer item) {
                byte[] bytes = new byte[item.remaining()];
                item.get(bytes);
                try {
                    baos.write(bytes);
                } catch (IOException ignored) {
                }
                this.subscription.request(1);
            }

            @Override
            public void onError(Throwable throwable) {

            }

            @Override
            public void onComplete() {
                cdl.countDown();
            }
        });

        cdl.await(1000, TimeUnit.MILLISECONDS);
        String result = new String(baos.toByteArray(), StandardCharsets.UTF_8);
        assertThat(result, is(teststring));
    }
}
