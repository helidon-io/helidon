/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates.
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
package io.helidon.nima.webserver.cors;

import java.lang.System.Logger.Level;
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

import io.helidon.common.http.Http;
import io.helidon.common.http.Http.HeaderName;

import static io.helidon.common.http.Http.Header.ACCESS_CONTROL_REQUEST_METHOD;
import static io.helidon.common.http.Http.Header.ORIGIN;

class LogHelper {

    static final Level DECISION_LEVEL = Level.TRACE;
    static final Level DETAILED_DECISION_LEVEL = Level.TRACE;

    private LogHelper() {
    }

    static <T> void logIsRequestTypeNormal(boolean result, boolean silent, CorsSupportBase.RequestAdapter<T> requestAdapter,
                                           Optional<String> originOpt, String authority) {
        if (silent || !CorsSupportHelper.LOGGER.isLoggable(DECISION_LEVEL)) {
            return;
        }
        // If no origin header or same as host, then just normal

        List<String> reasonsWhyNormal = new ArrayList<>();
        List<String> factorsWhyCrossHost = new ArrayList<>();

        if (originOpt.isEmpty()) {
            reasonsWhyNormal.add("header " + ORIGIN + " is absent");
        } else {
            factorsWhyCrossHost.add(String.format("header %s is present (%s)", ORIGIN, originOpt.get()));
        }

        factorsWhyCrossHost.add(String.format("authority is (%s)", authority));

        if (originOpt.isPresent()) {
            String partOfOriginMatchingHost = "://" + authority;
            if (originOpt.get()
                    .contains(partOfOriginMatchingHost)) {
                reasonsWhyNormal.add(String.format("header %s '%s' matches authority '%s'", ORIGIN,
                                                   originOpt.get(), authority));
            } else {
                factorsWhyCrossHost.add(String.format("header %s '%s' does not match authority '%s'", ORIGIN,
                                                      originOpt.get(), authority));
            }
        }

        if (result) {
            CorsSupportHelper.LOGGER.log(LogHelper.DECISION_LEVEL,
                                         () -> String.format("Request %s is not cross-host: %s",
                                                             requestAdapter,
                                                             reasonsWhyNormal));
        } else {
            CorsSupportHelper.LOGGER.log(LogHelper.DECISION_LEVEL,
                                         () -> String.format("Request %s is cross-host: %s",
                                                             requestAdapter,
                                                             factorsWhyCrossHost));
        }
    }

    static <T> void logInferRequestType(CorsSupportHelper.RequestType result,
                                        boolean silent,
                                        CorsSupportBase.RequestAdapter<T> requestAdapter,
                                        String methodName,
                                        boolean requestContainsAccessControlRequestMethodHeader) {
        if (silent || !CorsSupportHelper.LOGGER.isLoggable(DECISION_LEVEL)) {
            return;
        }
        List<String> reasonsWhyCORS = new ArrayList<>(); // any reason is determinative
        List<String> factorsWhyPreflight = new ArrayList<>(); // factors contribute but, individually, do not determine

        if (!methodName.equalsIgnoreCase(Http.Method.OPTIONS.text())) {
            reasonsWhyCORS.add(String.format("method is %s, not %s", methodName, Http.Method.OPTIONS.text()));
        } else {
            factorsWhyPreflight.add(String.format("method is %s", methodName));
        }

        if (!requestContainsAccessControlRequestMethodHeader) {
            reasonsWhyCORS.add(String.format("header %s is absent", ACCESS_CONTROL_REQUEST_METHOD.defaultCase()));
        } else {
            factorsWhyPreflight.add(String.format("header %s is present(%s)", ACCESS_CONTROL_REQUEST_METHOD.defaultCase(),
                                                  requestAdapter.firstHeader(ACCESS_CONTROL_REQUEST_METHOD)));
        }

        CorsSupportHelper.LOGGER.log(DECISION_LEVEL, String.format("Request %s is of type %s; %s", requestAdapter, result.name(),
                                                                   result == CorsSupportHelper.RequestType.PREFLIGHT
                                                                           ? factorsWhyPreflight
                                                                           : reasonsWhyCORS));
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

            public String toString(CrossOriginConfig crossOriginConfig) {
                return new StringJoiner(", ", MatcherCheck.class.getSimpleName() + "{", "}")
                        .add("crossOriginConfig=" + crossOriginConfig)
                        .add("matched=" + matched)
                        .add("enabled=" + enabled)
                        .toString();
            }

            void matched(boolean value) {
                matched = value;
            }

            void enabled(boolean value) {
                enabled = value;
            }
        }
    }
}
