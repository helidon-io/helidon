/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.integrations.oci.connect;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Optional;

import io.helidon.integrations.common.rest.ApiJsonRequest;

/**
 * A base for OCI requests that acts as a builder.
 * Adds support for {@link #retryToken}.
 *
 * @param <T> type of the request
 */
public abstract class OciRequestBase<T extends OciRequestBase<T>> extends ApiJsonRequest<T> {
    private static final DateTimeFormatter INSTANT_FORMATTER = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendInstant(3)
            .toFormatter();

    private String retryToken;
    private String hostPrefix;
    private String address;

    protected OciRequestBase() {
    }

    /**
     * Retry token to support idempotent request when updating data.
     *
     * @param retryToken retry token
     * @return updated request
     */
    public T retryToken(String retryToken) {
        this.retryToken = retryToken;
        return me();
    }

    /**
     * Host prefix to use. This is intended for API implementation and allows override of
     * the default host prefix (for example in Vault API, we may use two different prefixes).
     *
     * @param hostPrefix host prefix
     * @return updated request
     */
    public T hostPrefix(String hostPrefix) {
        if (this.hostPrefix == null) {
            this.hostPrefix = hostPrefix;
        }
        return me();
    }

    /**
     * Override the base address to use for OCI.
     *
     * @param address full address to override the default
     * @return updated builder
     */
    public T address(String address) {
        if (this.address == null) {
            this.address = address;
        }
        return me();
    }

    /**
     * Add a timestamp to the JSON. Uses the timestamp as defined in OCI API.
     *
     * @param name name of the property
     * @param instant instant value
     * @return updated request
     */
    protected T add(String name, Instant instant) {
        return super.add(name, INSTANT_FORMATTER.format(instant));
    }

    String hostPrefix() {
        if (hostPrefix == null) {
            throw new OciApiException("Host prefix must be defined to find correct OCI host.");
        }
        return hostPrefix;
    }

    Optional<String> address() {
        return Optional.ofNullable(address);
    }

    Optional<String> retryToken() {
        return Optional.ofNullable(retryToken);
    }
}
