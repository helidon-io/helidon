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

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.common.LazyValue;

import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;
import org.reactivestreams.Publisher;

abstract class OutgoingEmitter implements Emitter<Object>, OutgoingMember {

    private final String channelName;
    private final String fieldName;
    private final OnOverflow onOverflow;
    private final LazyValue<Long> bufferLimit;

    private final Lock lock = new ReentrantLock();
    private volatile boolean completed = false;

    static OutgoingEmitter create(String channelName, String fieldName, OnOverflow onOverflow) {
        switch (onOverflow.value()) {
            case DROP:
                return new PlainEmitter(channelName, fieldName, onOverflow);
            case THROW_EXCEPTION:
                return new ThrowEmitter(channelName, fieldName, onOverflow);
            case FAIL:
                return new FailEmitter(channelName, fieldName, onOverflow);
            case LATEST:
                return new LatestEmitter(channelName, fieldName, onOverflow);
            case NONE:
                return new NoneEmitter(channelName, fieldName, onOverflow);
            default:
                return new BufferedEmitter(channelName, fieldName, onOverflow);
        }
    }

    OutgoingEmitter(String channelName, String fieldName, OnOverflow onOverflow) {
        this.channelName = channelName;
        this.fieldName = fieldName;
        this.onOverflow = onOverflow;
        long bufferSize = this.onOverflow.bufferSize();
        if (bufferSize == 0) {
            this.bufferLimit = LazyValue.create(() -> ConfigProvider.getConfig()
                    .getOptionalValue("mp.messaging.emitter.default-buffer-size", Long.TYPE)
                    .orElse(128L));
        } else {
            this.bufferLimit = LazyValue.create(bufferSize);
        }
    }

    @Override
    public void complete() {
        try {
            lock().lock();
            this.completed = true;
        } finally {
            lock().unlock();
        }
    }

    @Override
    public void error(Exception e) {
        try {
            lock().lock();
            this.completed = true;
        } finally {
            lock().unlock();
        }
    }

    @Override
    public Publisher<?> getPublisher(String unused) {
        return getPublisher();
    }

    @Override
    public String getDescription() {
        return "emitter " + getFieldName();
    }

    protected Lock lock() {
        return this.lock;
    }

    void validate(Object item) {
        if (item == null) {
            throw new IllegalArgumentException("Null is not allowed in emitter");
        }
        if (completed) {
            throw new IllegalStateException("Emitter is already completed");
        }
    }

    String getChannelName() {
        return this.channelName;
    }

    String getFieldName() {
        return fieldName;
    }

    long getBufferLimit() {
        return this.bufferLimit.get();
    }

    OnOverflow.Strategy getOverflowStrategy() {
        return this.onOverflow.value();
    }

    abstract Publisher<?> getPublisher();
}
