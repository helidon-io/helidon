/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.messaging;

import java.util.Objects;

import io.helidon.common.Api;

/**
 * One immutable messaging header entry.
 * <p>
 * Header names are exact and case-sensitive. The core model does not impose a transport-specific name grammar;
 * connectors validate names when mapping a message to their transport.
 *
 * @param name exact header name
 * @param value header value
 */
@Api.Preview
public record MessageHeader(String name, HeaderValue value) {
    /**
     * Create a header entry.
     */
    public MessageHeader {
        Objects.requireNonNull(name);
        Objects.requireNonNull(value);
    }

    /**
     * Create a header entry.
     *
     * @param name exact header name
     * @param value header value
     * @return header entry
     */
    public static MessageHeader create(String name, HeaderValue value) {
        return new MessageHeader(name, value);
    }

    /**
     * Create a text header entry.
     *
     * @param name exact header name
     * @param value text value
     * @return header entry
     */
    public static MessageHeader create(String name, String value) {
        return new MessageHeader(name, HeaderValue.text(value));
    }
}
