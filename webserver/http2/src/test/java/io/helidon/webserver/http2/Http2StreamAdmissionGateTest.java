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

package io.helidon.webserver.http2;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameTypes;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class Http2StreamAdmissionGateTest {
    @Test
    void replacementAdmissionWaitsForTerminalDataPublication() throws InterruptedException {
        assertAdmissionWaitsFor(Http2FrameHeader.create(1,
                                                        Http2FrameTypes.DATA,
                                                        Http2Flag.DataFlags.create(Http2Flag.END_OF_STREAM),
                                                        1));
    }

    @Test
    void nonTerminalFrameDoesNotDelayAdmissionRetry() throws InterruptedException {
        Http2StreamAdmissionGate gate = new Http2StreamAdmissionGate();
        AtomicReference<Http2StreamAdmissionGate.AwaitResult> result = new AtomicReference<>();
        AtomicReference<Throwable> admissionFailure = new AtomicReference<>();

        gate.frameHeader(null,
                         1,
                         Http2FrameHeader.create(1,
                                                 Http2FrameTypes.DATA,
                                                 Http2Flag.DataFlags.create(0),
                                                 1));
        Thread admission = Thread.ofVirtual().start(() -> {
            try {
                result.set(gate.awaitPublication());
            } catch (Throwable t) {
                admissionFailure.set(t);
            }
        });

        assertThat("non-terminal frame must not delay admission retry",
                   admission.join(Duration.ofSeconds(1)),
                   is(true));
        assertThat(admissionFailure.get(), is(nullValue()));
        assertThat(result.get(), is(Http2StreamAdmissionGate.AwaitResult.NO_PENDING));
    }

    @Test
    void replacementAdmissionWaitsForResetPublication() throws InterruptedException {
        assertAdmissionWaitsFor(Http2FrameHeader.create(4,
                                                        Http2FrameTypes.RST_STREAM,
                                                        Http2Flag.NoFlags.create(),
                                                        1));
    }

    @Test
    void admissionRechecksAfterFirstPublication() throws InterruptedException {
        Http2StreamAdmissionGate gate = new Http2StreamAdmissionGate();
        AtomicReference<Http2StreamAdmissionGate.AwaitResult> result = new AtomicReference<>();
        Http2FrameHeader terminalFrame = Http2FrameHeader.create(1,
                                                                 Http2FrameTypes.DATA,
                                                                 Http2Flag.DataFlags.create(Http2Flag.END_OF_STREAM),
                                                                 1);

        gate.frameHeader(null, 1, terminalFrame);
        gate.frameHeader(null, 3, terminalFrame);
        Thread admission = Thread.ofVirtual().start(() -> result.set(gate.awaitPublication()));

        assertThat("replacement admission must wait for a publication",
                   admission.join(Duration.ofMillis(100)),
                   is(false));
        gate.completePublication();
        admission.join(TimeUnit.SECONDS.toMillis(2));

        assertThat("replacement admission must recheck without waiting for later publications",
                   admission.isAlive(),
                   is(false));
        assertThat(result.get(), is(Http2StreamAdmissionGate.AwaitResult.PUBLISHED));
        gate.completePublication();
    }

    @Test
    void failureUnblocksAdmission() throws InterruptedException {
        Http2StreamAdmissionGate gate = new Http2StreamAdmissionGate();
        AtomicReference<Http2StreamAdmissionGate.AwaitResult> result = new AtomicReference<>();

        gate.frameHeader(null,
                         1,
                         Http2FrameHeader.create(1,
                                                 Http2FrameTypes.DATA,
                                                 Http2Flag.DataFlags.create(Http2Flag.END_OF_STREAM),
                                                 1));
        Thread admission = Thread.ofVirtual().start(() -> result.set(gate.awaitPublication()));

        assertThat("replacement admission must wait for a publication",
                   admission.join(Duration.ofMillis(100)),
                   is(false));
        gate.fail();
        admission.join(TimeUnit.SECONDS.toMillis(2));

        assertThat("failed publication must unblock admission", admission.isAlive(), is(false));
        assertThat(result.get(), is(Http2StreamAdmissionGate.AwaitResult.FAILED));
        assertThat(gate.failed(), is(true));
    }

    @Test
    void admissionRestoresInterruptStatus() throws InterruptedException {
        Http2StreamAdmissionGate gate = new Http2StreamAdmissionGate();
        AtomicBoolean interrupted = new AtomicBoolean();

        gate.frameHeader(null,
                         1,
                         Http2FrameHeader.create(1,
                                                 Http2FrameTypes.DATA,
                                                 Http2Flag.DataFlags.create(Http2Flag.END_OF_STREAM),
                                                 1));
        Thread admission = Thread.ofVirtual().start(() -> {
            Thread.currentThread().interrupt();
            gate.awaitPublication();
            interrupted.set(Thread.currentThread().isInterrupted());
        });

        assertThat("replacement admission must wait despite interruption",
                   admission.join(Duration.ofMillis(100)),
                   is(false));
        gate.completePublication();
        admission.join(TimeUnit.SECONDS.toMillis(2));

        assertThat("replacement admission must terminate", admission.isAlive(), is(false));
        assertThat("interrupt status must be restored", interrupted.get(), is(true));
    }

    private static void assertAdmissionWaitsFor(Http2FrameHeader terminalFrame) throws InterruptedException {
        Http2StreamAdmissionGate gate = new Http2StreamAdmissionGate();
        CountDownLatch admissionStarted = new CountDownLatch(1);
        AtomicBoolean published = new AtomicBoolean();
        AtomicBoolean admissionObservedPublication = new AtomicBoolean();
        AtomicReference<Http2StreamAdmissionGate.AwaitResult> result = new AtomicReference<>();
        AtomicReference<Throwable> admissionFailure = new AtomicReference<>();

        gate.frameHeader(null, 1, terminalFrame);
        Thread admission = Thread.ofVirtual().start(() -> {
            admissionStarted.countDown();
            try {
                result.set(gate.awaitPublication());
                admissionObservedPublication.set(published.get());
            } catch (Throwable t) {
                admissionFailure.set(t);
            }
        });

        assertThat(admissionStarted.await(1, TimeUnit.SECONDS), is(true));
        assertThat("replacement admission must wait for terminal publication",
                   admission.join(Duration.ofMillis(100)),
                   is(false));
        published.set(true);
        gate.completePublication();
        admission.join(TimeUnit.SECONDS.toMillis(2));

        assertThat("replacement admission must terminate", admission.isAlive(), is(false));
        assertThat(admissionFailure.get(), is(nullValue()));
        assertThat(result.get(), is(Http2StreamAdmissionGate.AwaitResult.PUBLISHED));
        assertThat(admissionObservedPublication.get(), is(true));
    }
}
