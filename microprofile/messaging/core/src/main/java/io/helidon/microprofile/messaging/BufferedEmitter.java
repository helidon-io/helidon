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
 *
 */

package io.helidon.microprofile.messaging;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.helidon.common.reactive.BufferedEmittingPublisher;

import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;
import org.reactivestreams.FlowAdapters;
import org.reactivestreams.Publisher;

class BufferedEmitter extends OutgoingEmitter {

    private final BufferedEmittingPublisher<Object> bep = BufferedEmittingPublisher.create();

    BufferedEmitter(String channelName, String fieldName, OnOverflow onOverflow) {
        super(channelName, fieldName, onOverflow);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public synchronized CompletionStage<Void> send(Object p) {
        validate(p);
        CompletableFuture<Void> acked = new CompletableFuture<>();
        this.send(MessageUtils.create(p, acked));
        return acked;
    }

    @Override
    public synchronized <M extends Message<? extends Object>> void send(M m) {
        validate(m);
        bep.emit(m);
    }

    @Override
    public synchronized void complete() {
        super.complete();
        bep.complete();
    }

    @Override
    public synchronized void error(Exception e) {
        super.error(e);
        bep.fail(e);
    }

    @Override
    public synchronized boolean isCancelled() {
        return bep.isCancelled() || bep.isCompleted();
    }

    @Override
    public boolean hasRequests() {
        return bep.hasRequests();
    }

    public Publisher<?> getPublisher() {
        return FlowAdapters.toPublisher(bep);
    }

    public void validate(Object payload) {
        super.validate(payload);
        if (getOverflowStrategy().equals(OnOverflow.Strategy.BUFFER)) {
            int bufferSize = bep.bufferSize();
            if (getBufferLimit() > 0 && bufferSize > getBufferLimit()) {
                RuntimeException ex = new IllegalStateException("Emitter buffer overflow");
                throw ex;
            }
        }
    }
}
