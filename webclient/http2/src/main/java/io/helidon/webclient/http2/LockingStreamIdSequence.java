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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class LockingStreamIdSequence {

    private final AtomicInteger streamIdSeq = new AtomicInteger(0);
    private final Lock lock = new ReentrantLock();

    int lockAndNext() {
        lock.lock();
        return streamIdSeq.updateAndGet(o -> o % 2 == 0 ? o + 1 : o + 2);
    }

    void unlock() {
        lock.unlock();
    }
}
