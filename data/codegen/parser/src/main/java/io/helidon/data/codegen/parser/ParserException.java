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
package io.helidon.data.codegen.parser;

import java.util.Objects;

/**
 * Parser {@link RuntimeException}.
 */
public class ParserException extends RuntimeException {

    private final String string;

    /**
     * Create new exception for a message.
     *
     * @param message descriptive message, shall not be {@code null}
     * @param string  {@link String} being parsed
     */
    public ParserException(String message, String string) {
        super(message);
        Objects.requireNonNull(string, "Value of String being parsed is null");
        this.string = string;
    }

    /**
     * Create new exception for a message and a cause.
     *
     * @param message descriptive message
     * @param cause   original throwable causing this exception
     * @param string  {@link String} being parsed
     */
    public ParserException(String message, Throwable cause, String string) {
        super(message, cause);
        Objects.requireNonNull(string, "Value of String being parsed is null");
        this.string = string;
    }

    /**
     * {@link String} being parsed.
     *
     * @return the method name
     */
    public String string() {
        return string;
    }

}
