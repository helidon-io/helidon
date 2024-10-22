/*
 * Copyright (c) 2020, 2024 Oracle and/or its affiliates.
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

package io.helidon.cors;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.helidon.cors.CorsSupportHelper.RequestType;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.Method;

class LogHelper {

    static final System.Logger.Level DECISION_LEVEL = System.Logger.Level.TRACE;
    static final System.Logger.Level DETAILED_DECISION_LEVEL = System.Logger.Level.TRACE;

    private LogHelper() {
    }

    /**
     * Collects headers for assignment to a request or response and logging during assignment.
     */
    static class Headers {
        private final List<Map.Entry<HeaderName, Object>> headers = new ArrayList<>();
        private final List<String> notes = CorsSupportHelper.LOGGER.isLoggable(DECISION_LEVEL) ? new ArrayList<>() : null;

        Headers add(HeaderName key, Object value) {
            headers.add(new AbstractMap.SimpleEntry<>(key, value));
            return this;
        }

        Headers add(HeaderName key, Object value, String note) {
            add(key, value);
            if (notes != null) {
                notes.add(note);
            }
            return this;
        }

        void setAndLog(BiConsumer<HeaderName, Object> consumer, String note) {
            headers.forEach(entry -> consumer.accept(entry.getKey(), entry.getValue()));
            CorsSupportHelper.LOGGER.log(DECISION_LEVEL, () -> note + ": " + headers + (notes == null ? "" : notes));
        }
    }


    static <T> void logIsRequestTypeNormalNoOrigin(boolean silent, CorsRequestAdapter<T> requestAdapter) {
        if (silent || !CorsSupportHelper.LOGGER.isLoggable(DECISION_LEVEL)) {
            return;
        }
        CorsSupportHelper.LOGGER.log(DECISION_LEVEL,
                                     String.format("Request %s is not cross-host: %s",
                                                   requestAdapter,
                                                   List.of("header " + HeaderNames.ORIGIN + " is absent")));
    }

    static <T> void logOpaqueOrigin(boolean silent, CorsRequestAdapter<T> requestAdapter) {
        if (silent || !CorsSupportHelper.LOGGER.isLoggable(DECISION_LEVEL)) {
            return;
        }
        CorsSupportHelper.LOGGER.log(DECISION_LEVEL,
                                     String.format("Request %s specifies opaque origin: %s",
                                                   requestAdapter,
                                                   List.of("header " + HeaderNames.ORIGIN)));
    }

    static <T> void logIsRequestTypeNormal(boolean result, boolean silent, CorsRequestAdapter<T> requestAdapter,
            Optional<String> originOpt, String effectiveOrigin, String host) {
        if (silent || !CorsSupportHelper.LOGGER.isLoggable(DECISION_LEVEL)) {
            return;
        }
        // If no origin header or same as host, then just normal

        List<String> reasonsWhyNormal = new ArrayList<>();
        List<String> factorsWhyCrossHost = new ArrayList<>();

        if (originOpt.isEmpty()) {
            reasonsWhyNormal.add("header " + HeaderNames.ORIGIN + " is absent");
        } else {
            factorsWhyCrossHost.add(String.format("header %s is present (%s)", HeaderNames.ORIGIN, effectiveOrigin));
        }

        if (host.isEmpty()) {
            reasonsWhyNormal.add("header " + HeaderNames.HOST + "/requested URI is absent");
        } else {
            factorsWhyCrossHost.add(String.format("header %s/requested URI is present (%s)", HeaderNames.HOST, host));
        }

        if (originOpt.isPresent()) {
            String partOfOriginMatchingHost = "://" + host;
            if (originOpt.get()
                    .contains(partOfOriginMatchingHost)) {
                reasonsWhyNormal.add(String.format("header %s '%s' matches header/requested URI %s '%s'", HeaderNames.ORIGIN,
                                                   effectiveOrigin, HeaderNames.HOST, host));
            } else {
                factorsWhyCrossHost.add(String.format("header %s '%s' does not match header/requested URI %s '%s'",
                                                      HeaderNames.ORIGIN,
                                                      effectiveOrigin,
                                                      HeaderNames.HOST,
                                                      host));
            }
        }

        if (result) {
            if (CorsSupportHelper.LOGGER.isLoggable(DECISION_LEVEL)) {
                CorsSupportHelper.LOGGER.log(DECISION_LEVEL,
                                             String.format("Request %s is not cross-host: %s",
                                                                 requestAdapter,
                                                                 reasonsWhyNormal));
            }
        } else {
            if (CorsSupportHelper.LOGGER.isLoggable(DECISION_LEVEL)) {
                CorsSupportHelper.LOGGER.log(DECISION_LEVEL,
                                             () -> String.format("Request %s is cross-host: %s",
                                                                 requestAdapter,
                                                                 factorsWhyCrossHost));
            }
        }
    }

    static <T> void logInferRequestType(RequestType result,
                                        boolean silent,
                                        CorsRequestAdapter<T> requestAdapter,
                                        String methodName,
                                        boolean requestContainsAccessControlRequestMethodHeader) {
        if (silent || !CorsSupportHelper.LOGGER.isLoggable(DECISION_LEVEL)) {
            return;
        }
        List<String> reasonsWhyCORS = new ArrayList<>(); // any reason is determinative
        List<String> factorsWhyPreflight = new ArrayList<>(); // factors contribute but, individually, do not determine

        if (!methodName.equalsIgnoreCase(Method.OPTIONS.text())) {
            reasonsWhyCORS.add(String.format("method is %s, not %s", methodName, Method.OPTIONS.text()));
        } else {
            factorsWhyPreflight.add(String.format("method is %s", methodName));
        }

        if (!requestContainsAccessControlRequestMethodHeader) {
            reasonsWhyCORS.add(String.format("header %s is absent", HeaderNames.ACCESS_CONTROL_REQUEST_METHOD.defaultCase()));
        } else {
            factorsWhyPreflight.add(String.format("header %s is present(%s)",
                                                  HeaderNames.ACCESS_CONTROL_REQUEST_METHOD.defaultCase(),
                                                  requestAdapter.firstHeader(HeaderNames.ACCESS_CONTROL_REQUEST_METHOD)));
        }

        if (CorsSupportHelper.LOGGER.isLoggable(DECISION_LEVEL)) {
            CorsSupportHelper.LOGGER.log(DECISION_LEVEL,
                                         String.format("Request %s is of type %s; %s", requestAdapter, result.name(),
                                                       result == RequestType.PREFLIGHT ? factorsWhyPreflight : reasonsWhyCORS));
        }
    }

    static class MatcherChecks<T> {
        private final Map<CrossOriginConfig, MatcherCheck> checks;
        private final System.Logger logger;
        private final boolean isLoggable;
        private final Function<T, CrossOriginConfig> getter;

        MatcherChecks(System.Logger logger, Function<T, CrossOriginConfig> getter) {
            this.logger = logger;
            isLoggable = logger.isLoggable(DETAILED_DECISION_LEVEL);
            this.getter = getter;
            checks = isLoggable ? new LinkedHashMap<>() : null;
        }

        void put(T matcher) {
            if (isLoggable) {
                checks.put(getter.apply(matcher), new MatcherCheck());
            }
        }

        void matched(T matcher) {
            if (isLoggable) {
                checks.get(getter.apply(matcher)).matched(true);
            }
        }

        void enabled(CrossOriginConfig crossOriginConfig) {
            if (isLoggable) {
                checks.get(crossOriginConfig).enabled(true);
            }
        }

        void log() {
            if (!isLoggable) {
                return;
            }
            List<String> results = new ArrayList<>();
            checks.forEach((k, v) -> results.add(v.toString(k)));
            logger.log(DETAILED_DECISION_LEVEL, results.stream()
                    .collect(Collectors.joining(System.lineSeparator(), "Matching results: [", "]")));
        }

        private static class MatcherCheck {
            private boolean matched;
            private boolean enabled;

            void matched(boolean value) {
                matched = value;
            }

            void enabled(boolean value) {
                enabled = value;
            }

            public String toString(CrossOriginConfig crossOriginConfig) {
                return new StringJoiner(", ", MatcherCheck.class.getSimpleName() + "{", "}")
                        .add("crossOriginConfig=" + crossOriginConfig)
                        .add("matched=" + matched)
                        .add("enabled=" + enabled)
                        .toString();
            }
        }
    }
}
