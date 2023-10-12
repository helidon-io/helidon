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
 * HTTP header name.
 */
public sealed interface HeaderName permits HeaderNameImpl, HeaderNameEnum {
    /**
     * Lowercase value of this header, used by HTTP/2, may be used for lookup by HTTP/1.
     * There is no validation of this value, so if this contains an upper-case letter, behavior
     * is undefined.
     *
     * @return name of the header, lowercase
     */
    String lowerCase();

    /**
     * Header name as used in HTTP/1, or "human-readable" value of this header.
     *
     * @return name of the header, may use uppercase and/or lowercase
     */
    String defaultCase();

    /**
     * Index of this header (if one of the known indexed headers), or {@code -1} if this is a custom header name.
     *
     * @return index of this header
     */
    default int index() {
        return -1;
    }

    /**
     * Http2 defines pseudoheaders as headers starting with a {@code :} character. These are used instead
     * of the prologue line from HTTP/1 (to define path, authority etc.) and instead of status line in response.
     *
     * @return whether this header is a pseudo-header
     */
    default boolean isPseudoHeader() {
        return lowerCase().charAt(0) == ':';
    }
}
