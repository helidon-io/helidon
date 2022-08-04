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
package io.helidon.microprofile.messaging;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.helidon.common.reactive.EmittingPublisher;
import io.helidon.common.reactive.Multi;

import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;
import org.reactivestreams.FlowAdapters;
import org.reactivestreams.Publisher;

/**
 * Emitter used for {@link org.eclipse.microprofile.reactive.messaging.OnOverflow.Strategy#DROP}.
 */
class PlainEmitter extends OutgoingEmitter {

    private final EmittingPublisher<Object> ep = EmittingPublisher.create();

    PlainEmitter(String channelName, String fieldName, OnOverflow onOverflow) {
        super(channelName, fieldName, onOverflow);
    }

    @Override
    @SuppressWarnings("rawtypes")
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
            ep.emit(m);
        } finally {
            lock().unlock();
        }
    }

    @Override
    public void complete() {
        try {
            lock().lock();
            super.complete();
            ep.complete();
        } finally {
            lock().unlock();
        }
    }

    @Override
    public void error(Exception e) {
        try {
            lock().lock();
            super.error(e);
            ep.fail(e);
        } finally {
            lock().unlock();
        }
    }

    @Override
    public boolean isCancelled() {
        try {
            lock().lock();
            return ep.isCancelled() || ep.isCompleted();
        } finally {
            lock().unlock();
        }
    }

    @Override
    public boolean hasRequests() {
        return ep.hasRequests();
    }

    @Override
    public Publisher<?> getPublisher() {
        return FlowAdapters.toPublisher(Multi.create(ep).log());
    }
}
