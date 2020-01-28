/*
 * Copyright (c)  2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.reactive;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Flow;

import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.MultiTappedProcessor;

/**
 * Processor executing provided functions on passing signals onNext, onError, onComplete, onCancel.
 *
 * @param <R> Type of the processed items.
 */
class BufferedProcessor<R> extends MultiTappedProcessor<R> implements Multi<R> {

    private static final int BACK_PRESSURE_BUFFER_SIZE = 1024;

    private BlockingQueue<R> buffer = new ArrayBlockingQueue<>(BACK_PRESSURE_BUFFER_SIZE);

    private BufferedProcessor() {
    }

    public static <R> BufferedProcessor<R> create() {
        return new BufferedProcessor<>();
    }

    @Override
    protected void tryRequest(Flow.Subscription subscription) {
        if (!getSubscriber().isClosed() && !buffer.isEmpty()) {
            tryDrainBuffer();
        } else {
            super.tryRequest(subscription);
        }
    }

    private void tryDrainBuffer() {
        while (!buffer.isEmpty() && getRequestedCounter().tryDecrement()) {
            try {
                getSubscriber().get().onNext(buffer.take());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                failAndCancel(ex);
            } catch (Throwable ex) {
                failAndCancel(ex);
            }
        }
    }

    @Override
    protected void notEnoughRequest(R item) {
        if (!buffer.offer(item)) {
            fail(new BackPressureOverflowException(BACK_PRESSURE_BUFFER_SIZE));
        }
    }

    @Override
    public void onComplete() {
        if (buffer.isEmpty()) {
            super.onComplete();
        }
    }
}
