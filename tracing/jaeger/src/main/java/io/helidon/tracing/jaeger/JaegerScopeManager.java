/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;

/**
 * An implementation of {@link ScopeManager} that closes a scope from any thread.
 * This is necessary to support async calls where the close operation may be
 * called from a different thread.
 */
class JaegerScopeManager implements ScopeManager {

    private static final ConcurrentMap<Long, ThreadScope> SCOPES = new ConcurrentHashMap<>();

    @Override
    public Scope activate(Span span) {
        return new ThreadScope(span);
    }

    @Override
    public Span activeSpan() {
        ThreadScope scope = SCOPES.get(Thread.currentThread().getId());
        return scope == null ? null : scope.span();
    }

    void clearScopes() {
        SCOPES.clear();
    }

    static class ThreadScope implements Scope {
        private final Span span;
        private final ThreadScope previousScope;
        private final long creatingThreadId;
        private boolean closed;

        ThreadScope(Span span) {
            this.span = span;
            this.creatingThreadId = Thread.currentThread().getId();
            this.previousScope = SCOPES.put(this.creatingThreadId, this);
        }

        @Override
        public void close() {
            if (!closed) {
                if (previousScope == null) {
                    SCOPES.remove(this.creatingThreadId, this);
                } else {
                    SCOPES.put(this.creatingThreadId, previousScope);
                }
                closed = true;
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
