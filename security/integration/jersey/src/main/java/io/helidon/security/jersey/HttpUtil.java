/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security.jersey;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.ws.rs.core.MultivaluedMap;

import io.helidon.common.CollectionsHelper;

import io.opentracing.Span;
import io.opentracing.tag.Tags;

/**
 * Utilities for HTTP security - can be used by both Grizzly and Jersey.
 */
final class HttpUtil {
    private static final Logger LOGGER = Logger.getLogger(HttpUtil.class.getName());
    private static final String AUTHN_HEADER = "authorization";
    private static final String BASIC_PREFIX = "basic ";
    private static final Pattern CREDENTIAL_PATTERN = Pattern.compile("(.*):(.*)");

    /**
     * Prevent instantiation - utility class.
     */
    private HttpUtil() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Extracts username and password from HTTP headers to prepare a callback handler.
     * If the header is not present returns null, if invalid format, throws an exception.
     *
     * @param headers HTTP headers
     * @return CallbackHandler for authentication provider or null
     * @throws IllegalArgumentException in case the authentication header is invalid
     */
    static CallbackHandler basicAuthCallbackHandler(Map<String, List<String>> headers) {
        List<String> authorization = headers.get(AUTHN_HEADER);
        if ((null == authorization) || authorization.isEmpty()) {
            return null;
        }
        String authorizationValue = authorization.get(0);

        if (authorizationValue.toLowerCase().startsWith(BASIC_PREFIX)) {
            String b64 = authorizationValue.substring(BASIC_PREFIX.length());
            String usernameAndPassword = new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);

            Matcher matcher = CREDENTIAL_PATTERN.matcher(usernameAndPassword);
            if (!matcher.matches()) {
                LOGGER.finest("Basic authentication header with invalid content: " + usernameAndPassword);
                throw new IllegalArgumentException("Basic authentication header with invalid content");
            }

            final String username = matcher.group(1);
            final char[] password = matcher.group(2).toCharArray();

            return callbacks -> {
                for (Callback callback : callbacks) {
                    if (callback instanceof NameCallback) {
                        ((NameCallback) callback).setName(username);
                    } else if (callback instanceof PasswordCallback) {
                        ((PasswordCallback) callback).setPassword(password);
                    } else {
                        throw new UnsupportedCallbackException(callback);
                    }
                }
            };
        }
        return callbacks -> {
            for (Callback callback : callbacks) {
                throw new UnsupportedCallbackException(callback);
            }
        };
    }

    static Map<String, List<String>> toSimpleMap(MultivaluedMap<String, String> headers) {
        Map<String, List<String>> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        headers.forEach((key, value) -> result.put(key.toLowerCase(), value));

        return headers;
    }

    static void traceError(Span span, Throwable throwable, String description) {
        // failed
        if (null != throwable) {
            Tags.ERROR.set(span, true);
            span.log(CollectionsHelper.mapOf("event", "error",
                                             "error.object", throwable));
        } else {
            Tags.ERROR.set(span, true);
            span.log(CollectionsHelper.mapOf("event", "error",
                                             "message", description,
                                             "error.kind", "SecurityException"));
        }
        span.finish();
    }
}
