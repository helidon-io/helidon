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

import java.util.concurrent.locks.ReentrantLock;

/**
 * Connection-wide budget for queued DATA. Bytes are the primary memory bound and frames independently guard
 * against excessive fragmentation.
 */
final class Http2InboundDataBudget {
    private final ReentrantLock lock = new ReentrantLock();
    private final int maxFrames;
    private final long maxBytes;
    private int retainedFrames;
    private long retainedBytes;

    Http2InboundDataBudget(int maxFrames, long maxBytes) {
        if (maxFrames < 1 || maxBytes < 1) {
            throw new IllegalArgumentException("Inbound DATA budget limits must be positive.");
        }
        this.maxFrames = maxFrames;
        this.maxBytes = maxBytes;
    }

    boolean tryAcquire(int bytes) {
        lock.lock();
        try {
            if (retainedFrames >= maxFrames || maxBytes - retainedBytes < bytes) {
                return false;
            }
            retainedFrames++;
            retainedBytes += bytes;
            return true;
        } finally {
            lock.unlock();
        }
    }

    void release(int frames, long bytes) {
        lock.lock();
        try {
            retainedFrames -= frames;
            retainedBytes -= bytes;
            if (retainedFrames < 0 || retainedBytes < 0) {
                throw new IllegalStateException("Released more queued HTTP/2 DATA than retained.");
            }
        } finally {
            lock.unlock();
        }
    }

    // Package-private budget accessors are test seams for deterministic accounting assertions.
    int availableFrames() {
        lock.lock();
        try {
            return maxFrames - retainedFrames;
        } finally {
            lock.unlock();
        }
    }

    long availableBytes() {
        lock.lock();
        try {
            return maxBytes - retainedBytes;
        } finally {
            lock.unlock();
        }
    }
}
