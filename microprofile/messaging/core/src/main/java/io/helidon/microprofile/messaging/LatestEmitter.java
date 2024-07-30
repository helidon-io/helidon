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
package io.helidon.microprofile.messaging;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedDeque;

import io.helidon.common.reactive.BufferedEmittingPublisher;

import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;
import org.reactivestreams.FlowAdapters;
import org.reactivestreams.Publisher;

/**
 * Emitter used for {@link org.eclipse.microprofile.reactive.messaging.OnOverflow.Strategy#LATEST}.
 */
class LatestEmitter extends OutgoingEmitter {

    private static final System.Logger LOGGER = System.getLogger(LatestEmitter.class.getName());

    private final ConcurrentLinkedDeque<Object> buffer = new ConcurrentLinkedDeque<>();
    private final BufferedEmittingPublisher<Object> bep = BufferedEmittingPublisher.builder()
            .buffer(buffer)
            .build();

    LatestEmitter(String channelName, String fieldName, OnOverflow onOverflow) {
        super(channelName, fieldName, onOverflow);
    }

    @Override
    public Publisher<?> getPublisher() {
        return FlowAdapters.toPublisher(bep);
    }

    @Override
    public CompletionStage<Void> send(Object p) {
        try {
            lock().lock();
            validate(p);
            CompletableFuture<Void> acked = new CompletableFuture<>();
            this.send(MessageUtils.create(p, acked));
            return acked;
        } finally {
            lock().unlock();
        }
    }

    @Override
    public <M extends Message<? extends Object>> void send(M m) {
        try {
            lock().lock();

            validate(m);
            bep.emit(m);
        } finally {
            lock().unlock();
        }
    }

    @Override
    public void complete() {
        try {
            lock().lock();

            super.complete();
            bep.complete();
        } finally {
            lock().unlock();
        }
    }

    @Override
    public void error(Exception e) {
        try {
            lock().lock();

            super.error(e);
            bep.fail(e);
        } finally {
            lock().unlock();
        }
    }

    @Override
    public boolean isCancelled() {
        try {
            lock().lock();

            return bep.isCancelled() || bep.isCompleted();
        } finally {
            lock().unlock();
        }
    }

    @Override
    public boolean hasRequests() {
        return bep.hasRequests();
    }

    @Override
    public void validate(Object payload) {
        super.validate(payload);
        int bufferSize = bep.bufferSize();
        long bufferLimit = getBufferLimit();
        if (bufferLimit > 0 && bufferSize >= bufferLimit) {
            Object dropped = dropFirst();
            if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                LOGGER.log(System.Logger.Level.TRACE, "Dropped first item: " + MessageUtils.unwrap(dropped));
            }
        }
    }

    @Override
    long getBufferLimit() {
        return 3;
    }

    private Object dropFirst() {
        try {
            lock().lock();
            return this.buffer.pollFirst();
        } finally {
            lock().unlock();
        }
    }
}
