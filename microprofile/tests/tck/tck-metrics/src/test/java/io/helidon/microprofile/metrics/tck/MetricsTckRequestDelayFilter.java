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
package io.helidon.microprofile.metrics.tck;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import io.helidon.webserver.http.Filter;
import io.helidon.webserver.http.FilterChain;
import io.helidon.webserver.http.RoutingRequest;
import io.helidon.webserver.http.RoutingResponse;

/**
 * Adds a small configurable buffer before selected metrics TCK requests so timing-sensitive
 * assertions do not race metric publication.
 */
public class MetricsTckRequestDelayFilter implements Filter {

    static final String DELAY_MS_PROPERTY = "helidon.microprofile.metrics.tck.request-delay-ms";
    static final String DELAY_PATH_PATTERN_PROPERTY = "helidon.microprofile.metrics.tck.request-delay-path-pattern";
    static final String DEFAULT_DELAY_PATH_REGEX = "(^|.*/)(get-async|metrics)$";
    static final long DEFAULT_DELAY_MS = 250;

    private final long delayMs;
    private final Pattern delayPathPattern;

    MetricsTckRequestDelayFilter() {
        this(configuredDelayMs(), configuredDelayPathPattern());
    }

    MetricsTckRequestDelayFilter(long delayMs, Pattern delayPathPattern) {
        this.delayMs = Math.max(0, delayMs);
        this.delayPathPattern = Objects.requireNonNull(delayPathPattern);
    }

    @Override
    public void filter(FilterChain chain, RoutingRequest req, RoutingResponse res) {
        if ("GET".equals(req.prologue().method().text())
                && delayMs > 0
                && matchesPath(req.path().path(), delayPathPattern)) {
            try {
                TimeUnit.MILLISECONDS.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        chain.proceed();
    }

    static boolean matchesPath(String path) {
        return matchesPath(path, Pattern.compile(DEFAULT_DELAY_PATH_REGEX));
    }

    static boolean matchesPath(String path, Pattern delayPathPattern) {
        return path != null
                && delayPathPattern.matcher(path).matches();
    }

    static long configuredDelayMs() {
        String configuredValue = System.getProperty(DELAY_MS_PROPERTY);
        if (configuredValue == null || configuredValue.isBlank()) {
            return DEFAULT_DELAY_MS;
        }
        try {
            return Long.parseLong(configuredValue);
        } catch (NumberFormatException ignored) {
            return DEFAULT_DELAY_MS;
        }
    }

    static Pattern configuredDelayPathPattern() {
        String configuredValue = System.getProperty(DELAY_PATH_PATTERN_PROPERTY, DEFAULT_DELAY_PATH_REGEX);
        try {
            return Pattern.compile(configuredValue);
        } catch (PatternSyntaxException ignored) {
            return Pattern.compile(DEFAULT_DELAY_PATH_REGEX);
        }
    }
}
