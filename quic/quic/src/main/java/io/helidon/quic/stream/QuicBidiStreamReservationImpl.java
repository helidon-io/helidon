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

package io.helidon.quic.stream;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

final class QuicBidiStreamReservationImpl implements QuicBidiStreamReservation {
    private static final int RESERVED = 0;
    private static final int OPENING = 1;
    private static final int OPENED = 2;
    private static final int CLOSED = 3;
    private static final AtomicIntegerFieldUpdater<QuicBidiStreamReservationImpl> STATE =
            AtomicIntegerFieldUpdater.newUpdater(QuicBidiStreamReservationImpl.class, "state");

    private final QuicConnectionStreams streams;
    private volatile int state = RESERVED;

    QuicBidiStreamReservationImpl(QuicConnectionStreams streams) {
        this.streams = streams;
    }

    @Override
    public QuicBidiStream open() {
        if (!STATE.compareAndSet(this, RESERVED, OPENING)) {
            throw new IllegalStateException("Bidirectional stream reservation already opened or closed");
        }
        try {
            QuicBidiStream stream = streams.openReservedLocalBidiStream();
            state = OPENED;
            return stream;
        } catch (RuntimeException | Error failure) {
            state = CLOSED;
            streams.releaseLocalBidiStreamReservation();
            throw failure;
        }
    }

    @Override
    public void close() {
        if (STATE.compareAndSet(this, RESERVED, CLOSED)) {
            streams.releaseLocalBidiStreamReservation();
        }
    }
}
