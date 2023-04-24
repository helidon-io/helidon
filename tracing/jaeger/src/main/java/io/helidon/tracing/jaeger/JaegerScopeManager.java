/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

package io.helidon.tracing.jaeger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;

/**
 * An implementation of {@link ScopeManager} that closes a scope from any thread.
 * This is necessary to support async calls where the close operation may be
 * called from a different thread.
 */
class JaegerScopeManager implements ScopeManager {

    private static final ReentrantLock LOCK = new ReentrantLock();
    static final ConcurrentMap<Long, ThreadScope> SCOPES = new ConcurrentHashMap<>();

    @Override
    public Scope activate(Span span) {
        return new ThreadScope(span);
    }

    @Override
    public Span activeSpan() {
        ThreadScope scope = SCOPES.get(Thread.currentThread().getId());
        return scope == null ? null : scope.span();
    }

    static class ThreadScope implements Scope {
        private final Span span;
        private final ThreadScope parentScope;
        private ThreadScope childScope;
        private final long creatingThreadId;
        private boolean closed;

        ThreadScope(Span span) {
            this.span = span;
            this.creatingThreadId = Thread.currentThread().getId();
            this.parentScope = SCOPES.put(this.creatingThreadId, this);
            if (this.parentScope != null) {
                this.parentScope.childScope = this;
            }
        }

        /**
         * Closes this scope and all of its children scopes by setting the
         * private flag {@code closed}. Updates {@code SCOPES} map accordingly.
         */
        @Override
        public void close() {
            LOCK.lock();
            try {
                if (!closed) {
                    ThreadScope scope = SCOPES.get(creatingThreadId);

                    // handle out-of-order closings
                    while (scope != null && scope != this) {
                        scope = scope.parentScope;
                    }

                    if (scope != null) {
                        scope.closed = true;

                        // close all children scopes
                        ThreadScope child = scope.childScope;
                        while (child != null) {
                            child.closed = true;
                            child = child.childScope;
                        }

                        // update SCOPES setting parent as current
                        if (scope.parentScope == null) {
                            SCOPES.remove(creatingThreadId);
                        } else {
                            SCOPES.put(creatingThreadId, scope.parentScope);
                        }
                    }
                }
            } finally {
                LOCK.unlock();
            }
        }

        Span span() {
            return span;
        }

        boolean isClosed() {
            return closed;
        }
    }
}
