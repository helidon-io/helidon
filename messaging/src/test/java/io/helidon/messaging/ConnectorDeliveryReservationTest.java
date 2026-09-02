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

package io.helidon.messaging;

import java.util.Optional;

import io.helidon.messaging.spi.ConnectorDelivery;
import io.helidon.messaging.spi.ConnectorDeliveryReservation;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConnectorDeliveryReservationTest {
    private static final MessageBatch<String> BATCH = MessageBatch.create(Message.create("value"));

    @Test
    void defaultStartFailedClosesOnNullBatch() {
        IllegalStateException closeFailure = new IllegalStateException("close failed");
        TestReservation reservation = new TestReservation(closeFailure);

        NullPointerException result = assertThrows(
                NullPointerException.class,
                () -> reservation.startFailed(null, new IllegalStateException("mapping failed")));

        assertThat(result.getSuppressed().length, is(1));
        assertThat(result.getSuppressed()[0], sameInstance(closeFailure));
        assertThat(reservation.closeCalls(), is(1));
    }

    @Test
    void defaultStartFailedClosesOnNullFailure() {
        TestReservation reservation = new TestReservation(null);

        assertThrows(NullPointerException.class, () -> reservation.startFailed(BATCH, null));

        assertThat(reservation.closeCalls(), is(1));
    }

    @Test
    void defaultStartFailedPreservesFailureAndSuppressesCloseError() {
        IllegalStateException failure = new IllegalStateException("mapping failed");
        AssertionError closeFailure = new AssertionError("close failed");
        TestReservation reservation = new TestReservation(closeFailure);

        IllegalStateException result = assertThrows(IllegalStateException.class,
                                                    () -> reservation.startFailed(BATCH, failure));

        assertThat(result, sameInstance(failure));
        assertThat(result.getSuppressed().length, is(1));
        assertThat(result.getSuppressed()[0], sameInstance(closeFailure));
        assertThat(reservation.closeCalls(), is(1));
    }

    @Test
    void defaultStartFailedGuardsSelfSuppression() {
        IllegalStateException failure = new IllegalStateException("shared failure");
        TestReservation reservation = new TestReservation(failure);

        IllegalStateException result = assertThrows(IllegalStateException.class,
                                                    () -> reservation.startFailed(BATCH, failure));

        assertThat(result, sameInstance(failure));
        assertThat(result.getSuppressed().length, is(0));
        assertThat(reservation.closeCalls(), is(1));
    }

    private static final class TestReservation implements ConnectorDeliveryReservation {
        private final Throwable closeFailure;
        private int closeCalls;

        private TestReservation(Throwable closeFailure) {
            this.closeFailure = closeFailure;
        }

        @Override
        public ConnectorDelivery start(MessageBatch<?> batch) {
            throw new AssertionError("Unexpected delivery start");
        }

        @Override
        public Optional<ConnectorDelivery> tryStart(MessageBatch<?> batch) {
            throw new AssertionError("Unexpected delivery start");
        }

        @Override
        public void close() {
            closeCalls++;
            if (closeFailure instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (closeFailure instanceof Error error) {
                throw error;
            }
        }

        private int closeCalls() {
            return closeCalls;
        }
    }
}
