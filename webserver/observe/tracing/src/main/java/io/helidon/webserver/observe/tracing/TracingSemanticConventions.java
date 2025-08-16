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

package io.helidon.webserver.observe.tracing;

import io.helidon.tracing.Span;

/**
 * Applies a particular set of semantic conventions to spans.
 */
public interface TracingSemanticConventions {

    /**
     * Provides the span name.
     *
     * @return span name
     */
    String spanName();

    /**
     * Updates the span builder, applying the semantic conventions.
     * @param spanBuilder span builder to update
     */
    void update(Span.Builder<?> spanBuilder);

    /**
     * Updates the span once built.
     *
     * @param span span to update
     */
    void update(Span span);

    /**
     * Updates the span once built if the request ended abnormally.
     *
     * @param span span to update
     * @param e exception
     */
    void update(Span span, Exception e);
}
