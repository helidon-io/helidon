/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

import java.util.List;
import java.util.Optional;

/**
 * An exception for any code processing and generation tools.
 * This exception can hold {@link #originatingElement()} that may be used to provide more information to the user.
 */
public class CodegenException extends RuntimeException {
    /**
     * Originating element, depends on which codegen implementation is used.
     * For annotation processor, this could be the Element that caused this exception.
     */
    private final List<Object> originatingElement;

    /**
     * Constructor with a message.
     *
     * @param message descriptive message
     */
    public CodegenException(String message) {
        super(message);
        this.originatingElement = List.of();
    }

    /**
     * Constructor with a message and a cause.
     *
     * @param message descriptive message
     * @param cause   throwable triggering this exception
     */
    public CodegenException(String message, Throwable cause) {
        super(message, cause);
        this.originatingElement = List.of();
    }

    /**
     * Constructor with a message and an originating element.
     *
     * @param message             descriptive message
     * @param originatingElements elements that caused this exception
     */
    public CodegenException(String message, Object... originatingElements) {
        this(message, null, originatingElements);
    }

    /**
     * Constructor with a message, cause, and an originating element.
     *
     * @param message             descriptive message
     * @param cause               throwable triggering this exception
     * @param originatingElements element that caused this exception
     */
    public CodegenException(String message, Throwable cause, Object... originatingElements) {
        super(message, cause);
        this.originatingElement = List.of(originatingElements);
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
        return originatingElement.stream().findFirst();
    }

    /**
     * Originating elements.
     *
     * @return originating elements of this exception
     */
    public List<Object> originatingElements() {
        return originatingElement;
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

    /**
     * Create a codegen event to log with {@link io.helidon.codegen.CodegenLogger#log(CodegenEvent)}.
     *
     * @param level log level to use
     * @return a new codegen event that can be directly logged
     */
    public CodegenEvent toEvent(System.Logger.Level level) {
        return CodegenEvent.builder()
                .level(level)
                .message(getMessage())
                .throwable(this)
                .update(it -> it.addObjects(originatingElements()))
                .build();
    }
}
