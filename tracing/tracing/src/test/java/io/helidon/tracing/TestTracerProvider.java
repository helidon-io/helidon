/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.tracing;

import java.util.Optional;

/**
 * Approximates the behavior of a custom tracer provider with a constructor that invokes a logger which invokes Span.current().
 */
public class TestTracerProvider extends NoOpTracerProvider {

    private static Optional<Span> earlyCurrentSpan;

    public TestTracerProvider() {
        Optional<Span> currentSpan;
        try {
            currentSpan = Span.current();
        } catch (NullPointerException e) {
            // silent
            currentSpan = null;
        }
        earlyCurrentSpan = currentSpan;
    }

    static Optional<Span> earlyCurrentSpan() {
        return earlyCurrentSpan;
    }
}
