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

package io.helidon.webclient.grpc;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.http.http2.Http2ErrorCode;
import io.helidon.http.http2.Http2RstStream;

import io.grpc.Status;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

class GrpcClientStreamTest {

    @Test
    void mapsHttp2ResetCodesToGrpcStatus() {
        assertThat(GrpcClientCall.resetStatus(Http2ErrorCode.PROTOCOL).getCode(), is(Status.Code.INTERNAL));
        assertThat(GrpcClientCall.resetStatus(Http2ErrorCode.REFUSED_STREAM).getCode(), is(Status.Code.UNAVAILABLE));
        assertThat(GrpcClientCall.resetStatus(Http2ErrorCode.CANCEL).getCode(), is(Status.Code.CANCELLED));
        assertThat(GrpcClientCall.resetStatus(Http2ErrorCode.ENHANCE_YOUR_CALM).getCode(),
                   is(Status.Code.RESOURCE_EXHAUSTED));
        assertThat(GrpcClientCall.resetStatus(Http2ErrorCode.INADEQUATE_SECURITY).getCode(),
                   is(Status.Code.PERMISSION_DENIED));
        assertThat(GrpcClientCall.resetStatus(Http2ErrorCode.HTTP_1_1_REQUIRED).getCode(), is(Status.Code.UNKNOWN));
    }

    @Test
    void resetDeliveryIsOneShotAcrossRegistrationRace() throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            var notifier = new GrpcClientStream.ResetNotifier();
            Http2RstStream reset = new Http2RstStream(Http2ErrorCode.CANCEL);
            AtomicInteger deliveries = new AtomicInteger();
            AtomicReference<Http2RstStream> deliveredReset = new AtomicReference<>();
            AtomicReference<InterruptedException> failure = new AtomicReference<>();
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Thread resetThread = Thread.startVirtualThread(() -> {
                ready.countDown();
                try {
                    start.await();
                    notifier.reset(reset);
                } catch (InterruptedException e) {
                    failure.set(e);
                }
            });
            Thread handlerThread = Thread.startVirtualThread(() -> {
                ready.countDown();
                try {
                    start.await();
                    notifier.handler(it -> {
                        deliveredReset.set(it);
                        deliveries.incrementAndGet();
                    });
                } catch (InterruptedException e) {
                    failure.set(e);
                }
            });

            assertThat(ready.await(5, TimeUnit.SECONDS), is(true));
            start.countDown();
            resetThread.join();
            handlerThread.join();
            notifier.handler(ignored -> deliveries.incrementAndGet());
            notifier.reset(new Http2RstStream(Http2ErrorCode.INTERNAL));

            assertThat(failure.get(), nullValue());
            assertThat(deliveries.get(), is(1));
            assertThat(deliveredReset.get(), sameInstance(reset));
        }
    }
}
