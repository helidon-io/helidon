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

    private String hostFormat;
    private String retryToken;
    private String hostPrefix;
    private String endpoint;

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
     * Host format to use.
     * Domain specific APIs must define a host format for each request.
     *
     * @param hostFormat host format
     * @return updated request
     */
    public T hostFormat(String hostFormat) {
        if (this.hostFormat == null) {
            this.hostFormat = hostFormat;
        }
        return me();
    }

    /**
     * Override the endpoint to use for this request.
     *
     * @param endpoint full endpoint to override the default
     * @return updated builder
     */
    public T endpoint(String endpoint) {
        if (this.endpoint == null) {
            this.endpoint = endpoint;
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

    /**
     * Endpoint (if configured).
     *
     * @return configured endpoint or empty
     */
    public Optional<String> endpoint() {
        return Optional.ofNullable(endpoint);
    }

    Optional<String> retryToken() {
        return Optional.ofNullable(retryToken);
    }

    String hostFormat() {
        if (hostFormat == null) {
            throw new OciApiException("Host format must be defined to resolve OCI address");
        }
        return hostFormat;
    }
}
