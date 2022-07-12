/*
 * Copyright (c) 2018, 2022 Oracle and/or its affiliates.
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
package io.helidon.tracing.spi;

import java.util.Optional;

import io.helidon.tracing.Span;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.TracerBuilder;

/**
 * Java service to integrate various distributed tracers.
 * The first tracer configured will be used.
 */
public interface TracerProvider {
    /**
     * Create a new builder for this tracer.
     *
     * @return a tracer builder
     */
    TracerBuilder<?> createBuilder();

    /**
     * Global tracer that is registered, or a NoOp tracer if none is registered.
     *
     * @return current global tracer
     */
    Tracer global();

    /**
     * Register a global tracer instance. This method should not fail except for the case that tracer is null
     * - if the tracer cannot be registered, silently ignore it.
     * @param tracer tracer to register as global
     * @throws java.lang.NullPointerException in case the tracer is null
     */
    void global(Tracer tracer);

    /**
     * Provide current span.
     *
     * @return current span, or empty optional if current span cannot be found
     */
    Optional<Span> currentSpan();

    /**
     * Whether there is a tracer available by this provider.
     * This allows co-existence of multiple tracing providers within the same VM.
     *
     * @return whether this tracer provider has a tracer available
     */
    boolean available();
}
