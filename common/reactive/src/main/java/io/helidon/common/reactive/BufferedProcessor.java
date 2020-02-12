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

package io.helidon.common.reactive;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Flow;

/**
 * Processor with back-pressure buffer.
 *
 * @param <T> subscribed type (input)
 * @param <U> published type (output)
 */
public abstract class BufferedProcessor<T, U> extends BaseProcessor<T, U> {

    private static final int BACK_PRESSURE_BUFFER_SIZE = 1024;

    private BlockingQueue<U> buffer = new ArrayBlockingQueue<U>(BACK_PRESSURE_BUFFER_SIZE);

    @Override
    protected void tryRequest(Flow.Subscription subscription) {
        if (!getSubscriber().isClosed() && !buffer.isEmpty()) {
            try {
                submit(buffer.take());
            } catch (InterruptedException e) {
                failAndCancel(e);
            }
        } else {
            super.tryRequest(subscription);
        }
    }

    @Override
    protected void notEnoughRequest(U item) {
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
