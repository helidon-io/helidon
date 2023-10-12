/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.webclient.http2;

import java.time.Duration;

/**
 * Thrown when no data are received over the stream within configured request timeout.
 */
public class StreamTimeoutException extends RuntimeException {
    private final Http2ClientStream stream;

    StreamTimeoutException(Http2ClientStream stream, int streamId, Duration timeout) {
        super("No data received on stream " + streamId + " within the timeout " + timeout);
        this.stream = stream;
    }

    Http2ClientStream stream() {
        return stream;
    }
}
