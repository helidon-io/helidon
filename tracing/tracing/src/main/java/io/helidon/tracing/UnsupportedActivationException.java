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

/**
 * Indicates a failed attempt to activate a {@link io.helidon.tracing.Span} due to an error
 * in a {@link io.helidon.tracing.SpanLifeCycleListener} callback.
 */
public class UnsupportedActivationException extends UnsupportedOperationException {

    private Scope abandonedScope;

    /**
     * Creates a new exception instance using the specified message, scope, and cause.
     *
     * @param message exception message
     * @param abandonedScope the {@link Scope} created during the activation
     * @param cause the underlying cause of the activation exception
     */
    public UnsupportedActivationException(String message,
                                          Scope abandonedScope,
                                          Throwable cause) {
        super(message, cause);
        this.abandonedScope = abandonedScope;
    }

    /**
     * Creates a new activation exception using the provided message and {@link io.helidon.tracing.Scope}.
     *
     * @param message exceptino message
     * @param abandonedScope the {@code Scope} created during the activation
     */
    public UnsupportedActivationException(String message,
                                          Scope abandonedScope) {
        super(message);
        this.abandonedScope = abandonedScope;
    }

    /**
     * Returns the {@link io.helidon.tracing.Scope} created during the failed activation.
     *
     * @return the abandoned {@code Scope}
     */
    public Scope scope() {
        return abandonedScope;
    }
}
