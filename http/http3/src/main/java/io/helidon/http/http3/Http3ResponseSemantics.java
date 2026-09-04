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

package io.helidon.http.http3;

import java.util.Objects;

import io.helidon.common.Api;
import io.helidon.http.Method;
import io.helidon.http.Status;

/**
 * Shared HTTP response semantics used by HTTP/3 readers and senders.
 */
@Api.Internal
public final class Http3ResponseSemantics {
    private static final Http3ResponseSemantics TUNNEL = new Http3ResponseSemantics(true,
                                                                                   true,
                                                                                   false,
                                                                                   false,
                                                                                   false,
                                                                                   true);
    private static final Http3ResponseSemantics INFORMATIONAL = new Http3ResponseSemantics(false,
                                                                                          false,
                                                                                          false,
                                                                                          false,
                                                                                          false,
                                                                                          false);
    private static final Http3ResponseSemantics NO_CONTENT = new Http3ResponseSemantics(false,
                                                                                       false,
                                                                                       false,
                                                                                       false,
                                                                                       false,
                                                                                       true);
    private static final Http3ResponseSemantics NOT_MODIFIED = new Http3ResponseSemantics(false,
                                                                                         false,
                                                                                         false,
                                                                                         true,
                                                                                         false,
                                                                                         true);
    private static final Http3ResponseSemantics RESET_CONTENT = new Http3ResponseSemantics(false,
                                                                                          false,
                                                                                          true,
                                                                                          true,
                                                                                          true,
                                                                                          true);
    private static final Http3ResponseSemantics HEAD = new Http3ResponseSemantics(false,
                                                                                 false,
                                                                                 true,
                                                                                 true,
                                                                                 false,
                                                                                 true);
    private static final Http3ResponseSemantics MESSAGE = new Http3ResponseSemantics(false,
                                                                                    true,
                                                                                    true,
                                                                                    true,
                                                                                    false,
                                                                                    true);

    private final boolean tunnel;
    private final boolean dataAllowed;
    private final boolean trailersAllowed;
    private final boolean contentLengthAllowed;
    private final boolean contentLengthMustBeZero;
    private final boolean finalResponseAllowed;

    private Http3ResponseSemantics(boolean tunnel,
                                   boolean dataAllowed,
                                   boolean trailersAllowed,
                                   boolean contentLengthAllowed,
                                   boolean contentLengthMustBeZero,
                                   boolean finalResponseAllowed) {
        this.tunnel = tunnel;
        this.dataAllowed = dataAllowed;
        this.trailersAllowed = trailersAllowed;
        this.contentLengthAllowed = contentLengthAllowed;
        this.contentLengthMustBeZero = contentLengthMustBeZero;
        this.finalResponseAllowed = finalResponseAllowed;
    }

    /**
     * Resolve response semantics from the request method and response status.
     *
     * @param requestMethod request method
     * @param status response status
     * @return response semantics
     */
    public static Http3ResponseSemantics create(Method requestMethod, Status status) {
        Objects.requireNonNull(requestMethod, "requestMethod");
        Objects.requireNonNull(status, "status");
        if (requestMethod.equals(Method.CONNECT) && status.family() == Status.Family.SUCCESSFUL) {
            return TUNNEL;
        }
        if (status.family() == Status.Family.INFORMATIONAL) {
            return INFORMATIONAL;
        }
        int statusCode = status.code();
        if (statusCode == Status.NO_CONTENT_204.code()) {
            return NO_CONTENT;
        }
        if (statusCode == Status.NOT_MODIFIED_304.code()) {
            return NOT_MODIFIED;
        }
        if (statusCode == Status.RESET_CONTENT_205.code()) {
            return RESET_CONTENT;
        }
        if (requestMethod.equals(Method.HEAD)) {
            return HEAD;
        }
        return MESSAGE;
    }

    /**
     * Whether this response switches a successful CONNECT request to tunnel mode.
     *
     * @return whether this is a tunnel response
     */
    public boolean tunnel() {
        return tunnel;
    }

    /**
     * Whether DATA frames are allowed after the initial field section.
     *
     * @return whether DATA is allowed
     */
    public boolean dataAllowed() {
        return dataAllowed;
    }

    /**
     * Whether a trailing field section is allowed.
     *
     * @return whether trailers are allowed
     */
    public boolean trailersAllowed() {
        return trailersAllowed;
    }

    /**
     * Whether a sender may include Content-Length.
     *
     * @return whether Content-Length is allowed
     */
    boolean contentLengthAllowed() {
        return contentLengthAllowed;
    }

    /**
     * Whether an allowed Content-Length value must be zero.
     *
     * @return whether Content-Length must be zero
     */
    boolean contentLengthMustBeZero() {
        return contentLengthMustBeZero;
    }

    /**
     * Whether a recipient must ignore Content-Length without parsing it.
     *
     * @return whether received Content-Length is ignored
     */
    boolean receivedContentLengthIgnored() {
        return tunnel;
    }

    /**
     * Whether this status may terminate a final response.
     *
     * @return whether this is a valid final response
     */
    boolean finalResponseAllowed() {
        return finalResponseAllowed;
    }
}
