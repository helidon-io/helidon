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

package io.helidon.http;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.common.Api;
import io.helidon.common.LazyValue;
import io.helidon.common.parameters.Parameters;

/**
 * HTTP support methods used from generated code.
 */
@Api.Internal
public final class HttpSupport {
    private HttpSupport() {
    }

    /**
     * Read form parameters.
     *
     * @param formParams form parameters supplier
     * @return form parameters, or empty form parameters if none were supplied
     * @throws HttpException if reading form parameters fails with a specific HTTP status
     * @throws BadRequestException if reading form parameters fails
     */
    public static Parameters formParams(Supplier<Optional<Parameters>> formParams) {
        Objects.requireNonNull(formParams);

        try {
            return formParams.get()
                    .orElseGet(() -> Parameters.empty("form-params"));
        } catch (HttpException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new BadRequestException("Failed to read form parameters.", e);
        }
    }

    /**
     * Lazily read form parameters.
     *
     * @param formParams form parameters supplier
     * @return lazy form parameters
     */
    public static LazyValue<Parameters> lazyFormParams(Supplier<Optional<Parameters>> formParams) {
        Objects.requireNonNull(formParams);

        return LazyValue.create(() -> formParams(formParams));
    }

    /**
     * Create a single cookie pair for the {@code Cookie} header.
     *
     * @param name cookie name
     * @param value cookie value
     * @return cookie pair
     * @throws IllegalArgumentException if the name or value cannot be sent as a cookie pair
     */
    public static String cookie(String name, Object value) {
        validateCookieName(name);
        if (value == null) {
            throw new IllegalArgumentException("Cookie parameter " + name + " has invalid value.");
        }

        String cookieValue = String.valueOf(value);
        validateCookieValue(name, cookieValue);
        return name + "=" + cookieValue;
    }

    /**
     * Create a single {@code Cookie} header from cookie pairs.
     *
     * @param cookies cookie pairs
     * @return cookie header value
     */
    public static String cookieHeader(List<String> cookies) {
        Objects.requireNonNull(cookies);

        return String.join("; ", cookies);
    }

    /**
     * Get a mandatory list parameter.
     *
     * @param parameters parameter source
     * @param name parameter name
     * @param source source description for error reporting
     * @return parameter values
     * @throws BadRequestException if the named parameter is missing
     */
    public static List<String> paramList(Parameters parameters, String name, String source) {
        Objects.requireNonNull(parameters);
        Objects.requireNonNull(name);
        Objects.requireNonNull(source);

        if (parameters.contains(name)) {
            return parameters.all(name);
        }
        throw new BadRequestException(source + " " + name + " is not present in the request.");
    }

    /**
     * Get an optional list parameter.
     *
     * @param parameters parameter source
     * @param name parameter name
     * @return parameter values if the named parameter is present, empty otherwise
     */
    public static Optional<List<String>> paramOptionalList(Parameters parameters, String name) {
        Objects.requireNonNull(parameters);
        Objects.requireNonNull(name);

        if (parameters.contains(name)) {
            return Optional.of(parameters.all(name));
        }
        return Optional.empty();
    }

    /**
     * Get a mandatory scalar parameter.
     *
     * @param parameters parameter source
     * @param name parameter name
     * @param source source description for error reporting
     * @return parameter value, or empty string if the named parameter is present without values
     * @throws BadRequestException if the named parameter is missing
     */
    public static String paramValue(Parameters parameters, String name, String source) {
        Objects.requireNonNull(parameters);
        Objects.requireNonNull(name);
        Objects.requireNonNull(source);

        if (parameters.contains(name)) {
            return parameters.first(name).orElse("");
        }
        throw new BadRequestException(source + " " + name + " is not present in the request.");
    }

    /**
     * Get an optional scalar parameter.
     *
     * @param parameters parameter source
     * @param name parameter name
     * @return parameter value if the named parameter is present, empty otherwise
     */
    public static Optional<String> paramOptionalValue(Parameters parameters, String name) {
        Objects.requireNonNull(parameters);
        Objects.requireNonNull(name);

        if (parameters.contains(name)) {
            return Optional.of(parameters.first(name).orElse(""));
        }
        return Optional.empty();
    }

    private static void validateCookieName(String name) {
        Objects.requireNonNull(name);
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Cookie parameter name has invalid value.");
        }

        try {
            HttpToken.validate(name);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Cookie parameter name has invalid value.", e);
        }
    }

    private static void validateCookieValue(String name, String cookieValue) {
        for (int i = 0; i < cookieValue.length(); i++) {
            char c = cookieValue.charAt(i);
            if (c <= 0x20 || c >= 0x7f || c == '"' || c == ',' || c == ';' || c == '\\') {
                throw new IllegalArgumentException("Cookie parameter " + name + " has invalid value.");
            }
        }
    }
}
