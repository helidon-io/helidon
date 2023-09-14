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

package io.helidon.http;

/**
 * Mutable header value.
 */
public interface HeaderWriteable extends Header {
    /**
     * Create a new mutable header from an existing header.
     *
     * @param header header to copy
     * @return a new mutable header
     */
    static HeaderWriteable create(Header header) {
        return new HeaderValueCopy(header);
    }

    /**
     * Add a value to this header.
     *
     * @param value value to add
     * @return this instance
     */
    HeaderWriteable addValue(String value);
}
