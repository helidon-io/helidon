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

package io.helidon.webserver.http2;

import java.util.Set;

enum Http2StreamWriteState {
    END,
    TRAILERS_SENT(END),
    DATA_SENT(TRAILERS_SENT, END),
    HEADERS_SENT(DATA_SENT, TRAILERS_SENT, END),
    CONTINUE_100_SENT(HEADERS_SENT, END),
    EXPECTED_100(CONTINUE_100_SENT, HEADERS_SENT, END),
    INIT(EXPECTED_100, HEADERS_SENT, END);

    private final Set<Http2StreamWriteState> allowedTransitions;

    Http2StreamWriteState(Http2StreamWriteState... allowedTransitions) {
        this.allowedTransitions = Set.of(allowedTransitions);
    }

    Http2StreamWriteState checkAndMove(Http2StreamWriteState newState) {
        if (this == newState || allowedTransitions.contains(newState)) {
            return newState;
        }

        IllegalStateException badTransitionException =
                new IllegalStateException("Transition from " + this + " to " + newState + " is not allowed!");
        if (this == END) {
            throw new IllegalStateException("Stream is already closed.", badTransitionException);
        }
        throw badTransitionException;
    }
}
