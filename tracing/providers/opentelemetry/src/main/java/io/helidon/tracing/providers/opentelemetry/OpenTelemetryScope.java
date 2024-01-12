/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.tracing.Scope;

class OpenTelemetryScope implements Scope {
    private final io.opentelemetry.context.Scope delegate;
    private final io.opentelemetry.context.Scope baggageScope;
    private final AtomicBoolean closed = new AtomicBoolean();

    OpenTelemetryScope(io.opentelemetry.context.Scope scope, io.opentelemetry.context.Scope baggageScope) {
        delegate = scope;
        this.baggageScope = baggageScope;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true) && delegate != null) {
            delegate.close();
            if (baggageScope != null) {
                baggageScope.close();
            }
        }
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }
}
