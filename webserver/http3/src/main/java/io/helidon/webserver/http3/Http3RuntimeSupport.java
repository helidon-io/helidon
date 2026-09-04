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

package io.helidon.webserver.http3;

import java.time.Duration;
import java.util.concurrent.CompletionException;
import java.util.function.LongFunction;

import io.helidon.http.http3.Http3Protocol;

final class Http3RuntimeSupport {
    static final Duration DEFAULT_STREAM_OPEN_TIMEOUT = Duration.ofSeconds(5);

    private Http3RuntimeSupport() {
    }

    static LongFunction<String> applicationErrors() {
        return Http3Protocol::applicationErrorToString;
    }

    static Throwable unwrap(Throwable throwable) {
        return throwable instanceof CompletionException completionException
                && completionException.getCause() != null
                ? completionException.getCause()
                : throwable;
    }

    static String throwableSummary(Throwable throwable) {
        if (throwable == null) {
            return "none";
        }
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return throwable.getClass().getSimpleName() + ": " + message;
    }

}
