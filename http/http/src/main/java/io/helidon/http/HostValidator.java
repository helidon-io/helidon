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

package io.helidon.http;

import io.helidon.common.uri.UriValidator;

/**
 * Validate the host string (maybe from the {@code Host} header).
 * <p>
 * Validation is based on
 * <a href="https://www.rfc-editor.org/rfc/rfc3986#section-3.2.2">RFC-3986</a>.
 *
 * @deprecated use {@link io.helidon.common.uri.UriValidator} instead
 */
@Deprecated(since = "4.1.5", forRemoval = true)
public final class HostValidator {
    private HostValidator() {
    }

    /**
     * Validate a host string.
     *
     * @param host host to validate
     * @throws java.lang.IllegalArgumentException in case the host is not valid, the message is percent encoded
     * @deprecated use {@link io.helidon.common.uri.UriValidator#validateHost(String)} instead
     */
    @Deprecated(forRemoval = true, since = "4.1.5")
    public static void validate(String host) {
        UriValidator.validateHost(host);
    }

    /**
     * An IP literal starts with {@code [} and ends with {@code ]}.
     *
     * @param ipLiteral host literal string, may be an IPv6 address, or IP version future
     * @throws java.lang.IllegalArgumentException in case the host is not valid, the message is percent encoded
     * @deprecated use {@link io.helidon.common.uri.UriValidator#validateIpLiteral(String)} instead
     */
    @Deprecated(forRemoval = true, since = "4.1.5")
    public static void validateIpLiteral(String ipLiteral) {
        UriValidator.validateIpLiteral(ipLiteral);
    }

    /**
     * Validate IPv4 address or a registered name.
     *
     * @param host string with either an IPv4 address, or a registered name
     * @throws java.lang.IllegalArgumentException in case the host is not valid, the message is percent encoded
     * @deprecated use {@link io.helidon.common.uri.UriValidator#validateNonIpLiteral(String)} instead
     */
    @Deprecated(forRemoval = true, since = "4.1.5")
    public static void validateNonIpLiteral(String host) {
        UriValidator.validateNonIpLiteral(host);
    }
}
