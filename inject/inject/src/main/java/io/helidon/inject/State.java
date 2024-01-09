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

package io.helidon.inject;

import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;

class State implements Cloneable {
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private Phase currentPhase;
    private boolean isFinished;
    private Throwable lastError;

    private State() {
    }

    static State create(Phase phase) {
        return new State().currentPhase(phase);
    }

    @Override
    public State clone() {
        ReentrantReadWriteLock.ReadLock rlock = lock.readLock();
        rlock.lock();
        try {
            return create(currentPhase()).finished(finished()).lastError(lastError());
        } finally {
            rlock.unlock();
        }
    }

    @Override
    public String toString() {
        ReentrantReadWriteLock.WriteLock rlock = lock.writeLock();
        rlock.lock();
        try {
            return "currentPhase=" + currentPhase + ", isFinished=" + isFinished + ", lastError=" + lastError;
        } finally {
            rlock.unlock();
        }
    }

    void reset() {
        ReentrantReadWriteLock.WriteLock wlock = lock.writeLock();
        wlock.lock();
        try {
            currentPhase(Phase.INIT).finished(false).lastError(null);
        } finally {
            wlock.unlock();
        }
    }

    State currentPhase(Phase phase) {
        ReentrantReadWriteLock.WriteLock wlock = lock.writeLock();
        wlock.lock();
        try {
            Phase lastPhase = this.currentPhase;
            this.currentPhase = Objects.requireNonNull(phase);
            if (lastPhase != this.currentPhase) {
                this.isFinished = false;
                this.lastError = null;
            }
            return this;
        } finally {
            wlock.unlock();
        }
    }

    Phase currentPhase() {
        ReentrantReadWriteLock.ReadLock rlock = lock.readLock();
        rlock.lock();
        try {
            return currentPhase;
        } finally {
            rlock.unlock();
        }
    }

    State finished(boolean finished) {
        ReentrantReadWriteLock.WriteLock wlock = lock.writeLock();
        wlock.lock();
        try {
            this.isFinished = finished;
            return this;
        } finally {
            wlock.unlock();
        }
    }

    boolean finished() {
        ReentrantReadWriteLock.ReadLock rlock = lock.readLock();
        rlock.lock();
        try {
            return isFinished;
        } finally {
            rlock.unlock();
        }
    }

    State lastError(Throwable t) {
        ReentrantReadWriteLock.WriteLock wlock = lock.writeLock();
        wlock.lock();
        try {
            this.lastError = t;
            return this;
        } finally {
            wlock.unlock();
        }
    }

    Throwable lastError() {
        ReentrantReadWriteLock.ReadLock rlock = lock.readLock();
        rlock.lock();
        try {
            return lastError;
        } finally {
            rlock.unlock();
        }
    }

}
