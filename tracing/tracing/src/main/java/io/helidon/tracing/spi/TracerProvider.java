/*
 * Copyright (c) 2018, 2026 Oracle and/or its affiliates.
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

import java.util.Objects;
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
     * Legacy global tracer access.
     * This method will be removed in a future major version, remove an {@link java.lang.Override} annotation from it to
     * be "future proof".
     *
     * @return never returns
     * @deprecated use {@link io.helidon.service.registry.Services#get(Class)} to obtain the application-wide
     * {@link Tracer} from the service registry
     * @throws java.lang.UnsupportedOperationException always
     */
    @Deprecated(forRemoval = true, since = "27.0.0")
    default Tracer global() {
        throw new UnsupportedOperationException("This method should not be called. Use Services.get(Tracer.class) instead.");
    }

    /**
     * Legacy global tracer assignment.
     * <p>
     * Helidon 27 uses the service registry for application-wide tracer instances. This method is retained only for
     * compatibility with provider implementations compiled against earlier versions. It is expected to be a no-op and
     * will be removed in a future version.
     *
     * @param tracer ignored
     * @throws NullPointerException if tracer is null
     * @deprecated use {@link io.helidon.service.registry.Services#set(Class, Object[])} to register a {@link Tracer} in
     * the service registry before tracing is requested
     */
    @Deprecated(forRemoval = true, since = "27.0.0")
    default void global(Tracer tracer) {
        Objects.requireNonNull(tracer);
        // no-op for compatibility
    }

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
