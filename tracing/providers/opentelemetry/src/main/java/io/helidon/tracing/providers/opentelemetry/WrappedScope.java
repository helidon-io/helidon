/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.tracing.providers.opentelemetry;

import java.util.Objects;

import io.helidon.tracing.Wrapper;

import io.opentelemetry.context.Scope;

class WrappedScope implements io.opentelemetry.context.Scope, Wrapper {

    private final io.helidon.tracing.Scope helidonScope;

    static WrappedScope create(io.helidon.tracing.Scope helidonScope) {
        return new WrappedScope(helidonScope);
    }

    private WrappedScope(io.helidon.tracing.Scope helidonScope) {
        this.helidonScope = helidonScope;
    }

    @Override
    public void close() {
        helidonScope.close();
    }

    @Override
    public <R> R unwrap(Class<? extends R> c) {
        Scope nativeScope = helidonScope.unwrap(Scope.class);
        if (c.isInstance(nativeScope)) {
            return c.cast(nativeScope);
        }
        if (c.isInstance(helidonScope)) {
            return c.cast(helidonScope);
        }
        throw new IllegalArgumentException("Cannot provide an instance of " + c.getName()
                                                   + "; the wrapped telemetry scope has type "
                                                   + nativeScope.getClass().getName()
                                                   + " and the wrapped Helidon scope has type "
                                                   + helidonScope.getClass().getName());
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof WrappedScope that)) {
            return false;
        }
        return Objects.equals(helidonScope, that.helidonScope);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(helidonScope);
    }
}
