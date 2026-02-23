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

package io.helidon.webserver.cors;

import java.lang.System.Logger.Level;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import io.helidon.http.HeaderNames;
import io.helidon.http.PathMatcher;
import io.helidon.http.PathMatchers;
import io.helidon.http.Status;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import static io.helidon.webserver.cors.Cors.ALLOW_ALL;

class CorsPathValidator {
    private static final System.Logger LOGGER = System.getLogger(CorsPathValidator.class.getName());

    private final CorsPathConfig config;
    private final PathMatcher matcher;
    private final boolean exclusive;
    private final boolean allowCredentials;
    private final boolean setMaxAge;
    private final String maxAgeSeconds;

    private final boolean allowAllOrigins;
    private final Set<String> allowedOriginsExact;
    private final Set<Pattern> allowedOriginPatterns;
    private final boolean allowAllHeaders;
    private final Set<String> allowedHeaders;
    private final boolean allowAllMethods;
    private final Set<String> allowedMethods;
    private final boolean exposeAllHeaders;
    private final Set<String> exposeHeaders;

    private CorsPathValidator(CorsPathConfig config) {
        this.config = config;
        this.matcher = PathMatchers.create(config.pathPattern());
        this.exclusive = config.exclusive();
        this.allowCredentials = config.allowCredentials();
        Duration maxAge = config.maxAge();
        this.maxAgeSeconds = String.valueOf(maxAge.getSeconds());
        this.setMaxAge = maxAge.getSeconds() > 0;

        this.allowAllOrigins = config.allowOrigins().contains(ALLOW_ALL);
        if (allowAllOrigins) {
            this.allowedOriginsExact = Set.of();
            this.allowedOriginPatterns = Set.of();
        } else {
            var origins = config.allowOrigins();
            Set<String> exactMatchOrigins = new HashSet<>();
            Set<Pattern> originPatterns = new HashSet<>();
            for (String origin : origins) {
                if (origin.indexOf('\\') > -1
                        || origin.indexOf('*') > -1
                        || origin.indexOf('{') > -1) {
                    // this is a pattern
                    originPatterns.add(Pattern.compile(origin, Pattern.CASE_INSENSITIVE));
                } else {
                    exactMatchOrigins.add(origin);
                }
            }

            this.allowedOriginsExact = Set.copyOf(exactMatchOrigins);
            this.allowedOriginPatterns = Set.copyOf(originPatterns);
        }
        this.allowAllHeaders = config.allowHeaders().contains(ALLOW_ALL);
        if (allowAllHeaders) {
            this.allowedHeaders = Set.of();
        } else {
            this.allowedHeaders = new TreeSet<>(String::compareToIgnoreCase);
            this.allowedHeaders.addAll(config.allowHeaders());
        }
        this.allowAllMethods = config.allowMethods().contains(ALLOW_ALL);
        if (allowAllMethods) {
            this.allowedMethods = Set.of();
        } else {
            this.allowedMethods = config.allowMethods();
        }
        this.exposeAllHeaders = config.exposeHeaders().contains(ALLOW_ALL);
        if (exposeAllHeaders) {
            this.exposeHeaders = Set.of();
        } else {
            this.exposeHeaders = new TreeSet<>(String::compareToIgnoreCase);
            this.exposeHeaders.addAll(config.exposeHeaders());
        }
    }

    static CorsPathValidator create(CorsPathConfig corsPathConfig) {
        return new CorsPathValidator(corsPathConfig);
    }

    @Override
    public String toString() {
        return "Path validator for " + config;
    }

    Result preFlight(ServerRequest req, ServerResponse res) {
        if (notMatched(req)) {
            return Result.NOT_MATCHED;
        }

        // we should not get here unless there is a method in the request
        String method = req.headers().get(HeaderNames.ACCESS_CONTROL_REQUEST_METHOD).get();
        if (invalidMethod(req, res, method)) {
            if (exclusive) {
                log(req, Level.DEBUG, "CORS denying request; actual method: %s, allowedMethods: %s", method, allowedMethods);
                forbid(res);
                return Result.FORBIDDEN;
            } else {
                return Result.NOT_MATCHED;
            }
        }

        String origin = req.headers().get(HeaderNames.ORIGIN).get();
        if (invalidOrigin(req, res, origin)) {
            return Result.FORBIDDEN;
        }

        List<String> headers = req.headers()
                .find(HeaderNames.ACCESS_CONTROL_REQUEST_HEADERS)
                .map(it -> it.allValues(true))
                .orElse(List.of());
        if (invalidHeaders(req, res, headers)) {
            return Result.FORBIDDEN;
        }

        /*
        All is valid, we can configure response headers!
         */
        var resHeaders = res.headers();

        if (allowCredentials) {
            resHeaders.set(HeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
            resHeaders.set(HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
            resHeaders.set(HeaderNames.VARY, HeaderNames.ORIGIN.defaultCase());
            resHeaders.set(HeaderNames.ACCESS_CONTROL_ALLOW_METHODS, method);
        } else {
            if (allowAllOrigins) {
                // as per the fetch spec - we either return the provided origin, or *
                resHeaders.set(HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOW_ALL);
            } else {
                resHeaders.set(HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
                resHeaders.set(HeaderNames.VARY, HeaderNames.ORIGIN.defaultCase());
            }
            if (allowAllMethods) {
                // as per the fetch spec - we either return the provided origin, or *
                resHeaders.set(HeaderNames.ACCESS_CONTROL_ALLOW_METHODS, ALLOW_ALL);
            } else {
                resHeaders.set(HeaderNames.ACCESS_CONTROL_ALLOW_METHODS, allowedMethods);
            }
        }

        if (!headers.isEmpty()) {
            resHeaders.set(HeaderNames.ACCESS_CONTROL_ALLOW_HEADERS, headers);
        }
        if (setMaxAge) {
            resHeaders.set(HeaderNames.ACCESS_CONTROL_MAX_AGE, maxAgeSeconds);
        }
        log(req, Level.DEBUG, "CORS allowing request");
        return Result.ALLOWED;
    }

    Result flight(ServerRequest req, ServerResponse res) {
        if (notMatched(req)) {
            return Result.NOT_MATCHED;
        }

        String method = req.prologue().method().text();
        if (invalidMethod(req, res, method)) {
            if (exclusive) {
                log(req, Level.DEBUG, "CORS denying request; actual method: %s, allowedMethods: %s", method, allowedMethods);
                forbid(res);
                return Result.FORBIDDEN;
            } else {
                return Result.NOT_MATCHED;
            }
        }

        String origin = req.headers().get(HeaderNames.ORIGIN).get();
        if (invalidOrigin(req, res, origin)) {
            return Result.FORBIDDEN;
        }

        var resHeaders = res.headers();
        if (allowCredentials) {
            resHeaders.set(HeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
            resHeaders.set(HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
            resHeaders.set(HeaderNames.VARY, HeaderNames.ORIGIN.defaultCase());
        } else {
            if (allowAllOrigins) {
                resHeaders.set(HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOW_ALL);
            } else {
                resHeaders.set(HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
                resHeaders.set(HeaderNames.VARY, HeaderNames.ORIGIN.defaultCase());
            }
        }
        /*
        Expose headers is never sent as part of pre-flight check, only here
         */
        if (exposeAllHeaders) {
            if (!allowCredentials) {
                // we cannot send * when allowCredentials is true - this would be treated as a literal `*`
                resHeaders.set(HeaderNames.ACCESS_CONTROL_EXPOSE_HEADERS, ALLOW_ALL);
            }
        } else {
            if (!exposeHeaders.isEmpty()) {
                resHeaders.set(HeaderNames.ACCESS_CONTROL_EXPOSE_HEADERS, exposeHeaders);
            }
        }
        return Result.ALLOWED;
    }

    private boolean invalidHeaders(ServerRequest req, ServerResponse res, List<String> headers) {
        if (allowAllHeaders || allowedHeaders.containsAll(headers)) {
            log(req, Level.TRACE, "CORS allowing headers; actual headers: %s, allowedHeaders: %s", headers, allowedHeaders);
            return false;
        }
        log(req, Level.DEBUG, "CORS denying request; actual headers: %s, allowedHeaders: %s", headers, allowedHeaders);
        forbid(res);
        return true;
    }

    private boolean invalidOrigin(ServerRequest req, ServerResponse res, String origin) {
        if (origin.isBlank()) {
            log(req, Level.DEBUG, "CORS denying request; missing required header 'Origin'");
            forbid(res);
            return true;
        }
        if (allowAllOrigins) {
            log(req, Level.TRACE, "CORS allowing all origins; actual origin: %s", origin);
            return false;
        }
        if (allowedOriginsExact.contains(origin)) {
            log(req, Level.TRACE, "CORS allowing origin; actual origin: %s, allowedOrigins: %s", origin, allowedOriginsExact);
            return false;
        }
        if (matchOrigin(origin)) {
            log(req,
                Level.TRACE,
                "CORS allowing origin by pattern; actual origin: %s, allowedOriginsPatterns: %s",
                origin,
                allowedOriginPatterns);
            return false;
        }
        log(req,
            Level.DEBUG,
            "CORS denying request; actual origin: %s, allowedOrigins: %s, allowedOriginPatterns: %s",
            origin,
            allowedOriginsExact,
            allowedOriginPatterns);
        forbid(res);
        return true;
    }

    private boolean matchOrigin(String origin) {
        for (Pattern allowedOriginPattern : allowedOriginPatterns) {
            if (allowedOriginPattern.matcher(origin).matches()) {
                return true;
            }
        }
        return false;
    }

    private boolean invalidMethod(ServerRequest req, ServerResponse res, String method) {
        if (allowAllMethods) {
            log(req, Level.TRACE, "CORS allowing method; actual method: %s, allowed all", method);
            return false;
        }
        if (allowedMethods.contains(method)) {
            log(req, Level.TRACE, "CORS allowing method; actual method: %s, allowedMethods: %s", method, allowedMethods);
            return false;
        }
        return true;
    }

    private void forbid(ServerResponse res) {
        res.status(Status.FORBIDDEN_403)
                .send();
    }

    private void log(ServerRequest req, Level level, String format, Object... params) {
        if (LOGGER.isLoggable(level)) {
            LOGGER.log(level,
                       message(req, format, params));
        }
    }

    private String message(ServerRequest req, String format, Object... variables) {
        Object[] newVars = new Object[2 + variables.length];
        newVars[0] = req.serverSocketId();
        newVars[1] = req.socketId();
        System.arraycopy(variables, 0, newVars, 2, variables.length);
        return String.format("[%s %s] " + format, newVars);
    }

    private boolean notMatched(ServerRequest request) {
        return !matcher.match(request.path()).accepted();
    }

    record Result(boolean matched, boolean shouldContinue) {
        private static final Result NOT_MATCHED = new Result(false, true);
        private static final Result ALLOWED = new Result(true, true);
        private static final Result FORBIDDEN = new Result(true, false);
    }
}
