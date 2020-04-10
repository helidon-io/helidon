/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.webserver.cors.internal;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.logging.Level;

import io.helidon.common.http.Http;
import io.helidon.webserver.cors.internal.CrossOriginHelper.RequestAdapter;
import io.helidon.webserver.cors.internal.CrossOriginHelper.RequestType;

import static io.helidon.common.http.Http.Header.HOST;
import static io.helidon.common.http.Http.Header.ORIGIN;
import static io.helidon.webserver.cors.CrossOriginConfig.ACCESS_CONTROL_REQUEST_METHOD;
import static io.helidon.webserver.cors.internal.CrossOriginHelper.LOGGER;

class LogHelper {

    static final Level DECISION_LEVEL = Level.FINE;

    private LogHelper() {
    }

    /**
     * Collects headers for assignment to a request or response and logging during assignment.
     */
    static class Headers {
        private final List<Map.Entry<String, Object>> headers = new ArrayList<>();
        private final List<String> notes = LOGGER.isLoggable(DECISION_LEVEL) ? new ArrayList<>() : null;

        Headers add(String key, Object value) {
            headers.add(new AbstractMap.SimpleEntry<>(key, value));
            return this;
        }

        Headers add(String key, Object value, String note) {
            add(key, value);
            if (notes != null) {
                notes.add(note);
            }
            return this;
        }

        void set(BiConsumer<String, Object> consumer, String note) {
            headers.forEach(entry -> consumer.accept(entry.getKey(), entry.getValue()));
            LOGGER.log(DECISION_LEVEL, () -> note + ": " + headers + (notes == null ? "" : notes));
        }
    }

    static <T> boolean isRequestTypeNormal(boolean result, RequestAdapter<T> requestAdapter, Optional<String> originOpt,
            Optional<String> hostOpt) {
        // If no origin header or same as host, then just normal

        List<String> reasonsWhyNormal = new ArrayList<>();
        List<String> factorsWhyCrossHost = new ArrayList<>();

        if (originOpt.isEmpty()) {
            reasonsWhyNormal.add("header " + ORIGIN + " is absent");
        } else {
            factorsWhyCrossHost.add(String.format("header %s is present (%s)", ORIGIN, originOpt.get()));
        }

        if (hostOpt.isEmpty()) {
            reasonsWhyNormal.add("header " + HOST + " is absent");
        } else {
            factorsWhyCrossHost.add(String.format("header %s is present (%s)", HOST, hostOpt.get()));
        }

        if (hostOpt.isPresent() && originOpt.isPresent()) {
            String partOfOriginMatchingHost = "://" + hostOpt.get();
            if (originOpt.get()
                    .contains(partOfOriginMatchingHost)) {
                reasonsWhyNormal.add(String.format("header %s '%s' matches header %s '%s'; not cross-host", ORIGIN,
                        originOpt.get(), HOST, hostOpt.get()));
            } else {
                factorsWhyCrossHost.add(String.format("header %s (%s) does not match header %s %s; cross-host", ORIGIN,
                        originOpt.get(), HOST, hostOpt.get()));
            }
        }

        if (result) {
            LOGGER.log(LogHelper.DECISION_LEVEL,
                    () -> String.format("Request %s is not cross-host: %s", requestAdapter, reasonsWhyNormal));
        } else {
            LOGGER.log(LogHelper.DECISION_LEVEL,
                    () -> String.format("Request %s is cross-host: %s", requestAdapter, factorsWhyCrossHost));
        }
        return result;
    }

    static <T> RequestType inferCORSRequestType(RequestType result, RequestAdapter<T> requestAdapter, String methodName,
            boolean requestContainsAccessControlRequestMethodHeader) {
        List<String> reasonsWhyCORS = new ArrayList<>(); // any reason is determinative
        List<String> factorsWhyPreflight = new ArrayList<>(); // factors contribute but, individually, do not determine

        if (!methodName.equalsIgnoreCase(Http.Method.OPTIONS.name())) {
            reasonsWhyCORS.add(String.format("method is %s, not %s", methodName, Http.Method.OPTIONS.name()));
        } else {
            factorsWhyPreflight.add(String.format("method is %s", methodName));
        }

        if (!requestContainsAccessControlRequestMethodHeader) {
            reasonsWhyCORS.add(String.format("header %s is absent", ACCESS_CONTROL_REQUEST_METHOD));
        } else {
            factorsWhyPreflight.add(String.format("header %s is present(%s)", ACCESS_CONTROL_REQUEST_METHOD,
                    requestAdapter.firstHeader(ACCESS_CONTROL_REQUEST_METHOD)));
        }

        LOGGER.log(DECISION_LEVEL, String.format("Request %s is of type %s; %s", requestAdapter, result.name(),
                result == RequestType.PREFLIGHT ? factorsWhyPreflight : reasonsWhyCORS));

        return result;
    }
}
