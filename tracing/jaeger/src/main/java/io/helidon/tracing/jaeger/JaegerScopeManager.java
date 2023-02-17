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
        private final ThreadScope previousScope;
        private final long creatingThreadId;
        private boolean closed;
        private boolean closePreviousScope;

        ThreadScope(Span span) {
            this.span = span;
            this.creatingThreadId = Thread.currentThread().getId();
            this.previousScope = SCOPES.put(this.creatingThreadId, this);
        }

        /**
         * Close this scope. Normally scopes are closed innermost to outermost, in
         * order. However, with async processing, it is possible for the close
         * operations to come out of other. This method needs to handle that, yet
         * still close the scopes in innermost-to-outermost order.
         */
        @Override
        public void close() {
            LOCK.lock();
            try {
                if (!closed) {
                    ThreadScope innermost = SCOPES.get(creatingThreadId);
                    // Are we closing innermost scope?
                    if (innermost == this) {
                        if (previousScope == null) {
                            SCOPES.remove(creatingThreadId);
                        } else {
                            SCOPES.put(creatingThreadId, previousScope);
                            if (closePreviousScope) {
                                previousScope.close();
                            }
                        }
                        closed = true;
                    } else {
                        // Delay closing to after we close all inner scopes
                        innermost.delayClose(this);
                    }
                }
            } finally {
                LOCK.unlock();
            }
        }

        /**
         * Turns on {@code closePreviousScope} flag on immediate child of
         * the scope.
         *
         * @param scope the scope to delay closing on
         */
        private void delayClose(ThreadScope scope) {
            assert scope != this && scope != null;
            if (scope == previousScope) {
                closePreviousScope = true;
            } else if (previousScope != null) {
                previousScope.delayClose(scope);
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
