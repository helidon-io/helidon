/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.codegen;

import java.util.Optional;

/**
 * An exception for any code processing and generation tools.
 * This exception can hold {@link #originatingElement()} that may be used to provide more information to the user.
 */
public class CodegenException extends RuntimeException {
    private final Object originatingElement;

    /**
     * Constructor with a message.
     *
     * @param message descriptive message
     */
    public CodegenException(String message) {
        super(message);
        this.originatingElement = null;
    }

    /**
     * Constructor with a message and a cause.
     *
     * @param message descriptive message
     * @param cause   throwable triggering this exception
     */
    public CodegenException(String message, Throwable cause) {
        super(message, cause);
        this.originatingElement = null;
    }

    /**
     * Constructor with a message and an originating element.
     *
     * @param message            descriptive message
     * @param originatingElement element that caused this exception
     */
    public CodegenException(String message, Object originatingElement) {
        super(message);
        this.originatingElement = originatingElement;
    }

    /**
     * Constructor with a message, cause, and an originating element.
     *
     * @param message            descriptive message
     * @param cause              throwable triggering this exception
     * @param originatingElement element that caused this exception
     */
    public CodegenException(String message, Throwable cause, Object originatingElement) {
        super(message, cause);
        this.originatingElement = originatingElement;
    }

    /**
     * Originating element.
     * This may be an annotation processing element, a classpath scanning {@code ClassInfo}, or a
     * {@link io.helidon.common.types.TypeName}.
     * Not type will cause an exception, each environment may check the instance and use it or not.
     *
     * @return originating element of this exception
     */
    public Optional<Object> originatingElement() {
        return Optional.ofNullable(originatingElement);
    }

    /**
     * Create a codegen event to log with {@link io.helidon.codegen.CodegenLogger#log(CodegenEvent)}.
     *
     * @param level log level to use
     * @param message additional message describing the location
     * @return a new codegen event that can be directly logged
     */
    public CodegenEvent toEvent(System.Logger.Level level, String message) {
        return CodegenEvent.builder()
                .level(level)
                .message(message)
                .throwable(this)
                .update(it -> originatingElement().ifPresent(it::addObject))
                .build();
    }
}
