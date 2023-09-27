/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.webclient.http2;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.http.http2.Http2FrameData;

class StreamBuffer {

    private final Lock streamLock = new ReentrantLock();
    private final Semaphore dequeSemaphore = new Semaphore(1);
    private final Queue<Http2FrameData> buffer = new ArrayDeque<>();
    private final Http2ClientStream stream;
    private final int streamId;

    StreamBuffer(Http2ClientStream stream, int streamId) {
        this.stream = stream;
        this.streamId = streamId;
    }

    Http2FrameData poll(Duration timeout) {
        try {
            // Block deque thread when queue is empty
            // avoid CPU burning
            if (!dequeSemaphore.tryAcquire(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new StreamTimeoutException(stream, streamId, timeout);
            }
        } catch (InterruptedException e) {
            throw new IllegalStateException("Interrupted while waiting for data", e);
        }
        try {
            streamLock.lock();
            return buffer.poll();
        } finally {
            streamLock.unlock();
        }
    }

    void push(Http2FrameData frameData) {
        try {
            streamLock.lock();
            buffer.add(frameData);
        } finally {
            streamLock.unlock();
            // Release deque threads
            dequeSemaphore.release();
        }
    }
}
