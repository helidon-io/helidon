/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.http.http2;

/**
 * Stream state.
 *
 * See
 * <a
 * href="https://datatracker.ietf.org/doc/html/rfc7540#section-5.1">https://datatracker.ietf.org/doc/html/rfc7540#section-5.1</a>
 */
public enum Http2StreamState {
    /**
     * The initial state of a stream.
     * <p>
     * Transitions to this state: none
     * <p>
     * Transitions from this state:
     * <ul>
     *     <li>send {@link Http2FrameType#PUSH_PROMISE} -> {@link #RESERVED_LOCAL}</li>
     *     <li>recv {@link Http2FrameType#PUSH_PROMISE} -> {@link #RESERVED_REMOTE}</li>
     * </ul>
     */
    IDLE,
    /**
     * Push promise sent (not supported by Helidon).
     * <p>
     * Transitions to this state:
     * <ul>
     *     <li>{@link #IDLE} -> send {@link Http2FrameType#PUSH_PROMISE}</li>
     * </ul>
     * <p>
     * Transitions from this state:
     * <ul>
     *     <li>send {@link Http2FrameType#HEADERS} -> {@link #HALF_CLOSED_REMOTE}</li>
     *     <li>send {@link Http2FrameType#RST_STREAM} -> {@link #CLOSED}</li>
     *     <li>recv {@link Http2FrameType#RST_STREAM} -> {@link #CLOSED}</li>
     * </ul>
     */
    RESERVED_LOCAL,
    /**
     * Push promise received (not supported by Helidon).
     * <p>
     * Transitions to this state:
     * <ul>
     *     <li>{@link #IDLE} -> recv {@link Http2FrameType#PUSH_PROMISE}</li>
     * </ul>
     * <p>
     * Transitions from this state:
     * <ul>
     *     <li>recv {@link Http2FrameType#HEADERS} -> {@link #HALF_CLOSED_LOCAL}</li>
     *     <li>recv {@link Http2FrameType#RST_STREAM} -> {@link #CLOSED}</li>
     *     <li>send {@link Http2FrameType#RST_STREAM} -> {@link #CLOSED}</li>
     * </ul>
     */
    RESERVED_REMOTE,
    /**
     * Both local and remote streams are open.
     * <p>
     * Transitions to this state:
     * <ul>
     *     <li>{@link #IDLE} -> send {@link Http2FrameType#HEADERS}</li>
     *     <li>{@link #IDLE} -> recv {@link Http2FrameType#HEADERS}</li>
     * </ul>
     * <p>
     * Transitions from this state
     * (end of stream may be part of {@link Http2FrameType#HEADERS} or
     *  {@link Http2FrameType#DATA}):
     * <ul>
     *     <li>send {@link Http2Flag#END_OF_STREAM} -> {@link #HALF_CLOSED_LOCAL}</li>
     *     <li>recv {@link Http2Flag#END_OF_STREAM} -> {@link #HALF_CLOSED_REMOTE}</li>
     *     <li>send {@link Http2FrameType#RST_STREAM} -> {@link #CLOSED}</li>
     *     <li>recv {@link Http2FrameType#RST_STREAM} -> {@link #CLOSED}</li>
     * </ul>
     */
    OPEN,
    /**
     * Local stream is closed, remote stream is open.
     *
     * <p>
     * Transitions to this state:
     * <ul>
     *     <li>{@link #RESERVED_REMOTE} -> recv {@link Http2FrameType#HEADERS}</li>
     *     <li>{@link #OPEN} -> send {@link Http2Flag#END_OF_STREAM}</li>
     * </ul>
     * <p>
     * Transitions from this state:
     * <ul>
     *     <li>recv {@link Http2Flag#END_OF_STREAM} -> {@link #CLOSED}</li>
     *     <li>send {@link Http2FrameType#RST_STREAM} -> {@link #CLOSED}</li>
     *     <li>recv {@link Http2FrameType#RST_STREAM} -> {@link #CLOSED}</li>
     * </ul>
     */
    HALF_CLOSED_LOCAL,
    /**
     * Remote stream is closed, local stream is open.
     *
     * <p>
     * Transitions to this state:
     * <ul>
     *     <li>{@link #RESERVED_LOCAL} -> send {@link Http2FrameType#HEADERS}</li>
     *     <li>{@link #OPEN} -> recv {@link Http2Flag#END_OF_STREAM}</li>
     * </ul>
     * <p>
     * Transitions from this state:
     * <ul>
     *     <li>send {@link Http2Flag#END_OF_STREAM} -> {@link #CLOSED}</li>
     *     <li>send {@link Http2FrameType#RST_STREAM} -> {@link #CLOSED}</li>
     *     <li>recv {@link Http2FrameType#RST_STREAM} -> {@link #CLOSED}</li>
     * </ul>
     */
    HALF_CLOSED_REMOTE,
    /**
     * Closed stream.
     *
     * Transitions to this state:
     * <ul>
     * <li>{@link #HALF_CLOSED_REMOTE} -> by sending "END_STREAM", sending or receiving
     *     {@link Http2FrameType#RST_STREAM}</li>
     * <li>{@link #HALF_CLOSED_LOCAL} -> by receiving "END_STREAM", sending or receiving
     *     {@link Http2FrameType#RST_STREAM}</li>
     * <li>{@link #OPEN}, {@link #RESERVED_LOCAL}, or {@link #RESERVED_REMOTE} - by sending or receiving
     *     {@link Http2FrameType#RST_STREAM}</li>
     * </ul>
     * <p>
     * Transitions from this state: none
     */
    CLOSED;

    /**
     * Check that the frame is ok for current state.
     *
     * @param current      current stream state
     * @param frameType    frame type
     * @param send         send
     * @param endOfStream  whether end of stream
     * @param endOfHeaders whether end of headers
     * @return stream state
     */
    public static Http2StreamState checkAndGetState(Http2StreamState current,
                                                    Http2FrameType frameType,
                                                    boolean send,
                                                    boolean endOfStream,
                                                    boolean endOfHeaders) {
        switch (frameType) {
        case DATA -> {
            return checkData(current, send, endOfStream);
        }
        case HEADERS -> {
            return checkHeaders(current, send, endOfStream, endOfHeaders, "headers");
        }
        case PRIORITY -> {
            return checkPriority(current);
        }
        case RST_STREAM -> {
            return checkRstStream(current, send);
        }
        case SETTINGS -> {
            return fail(Http2FrameType.SETTINGS, send);
        }
        case PUSH_PROMISE -> {
            return checkPushPromise(current, send);
        }
        case PING -> {
            return fail(Http2FrameType.PING, send);
        }
        case GO_AWAY -> {
            return fail(Http2FrameType.GO_AWAY, send);
        }
        case WINDOW_UPDATE -> {
            return checkWindowUpdate(current, send);
        }
        case CONTINUATION -> {
            return checkHeaders(current, send, endOfHeaders, endOfStream, "continuation");
        }
        default -> throw new Http2Exception(Http2ErrorCode.INTERNAL, "Invalid stream state (unknown)");
        }
    }

    private static Http2StreamState checkWindowUpdate(Http2StreamState current, boolean send) {
        // according to spec it seems we can send this anytime
        // if sent within header (e.g. headers->windowupdate->continuation) it must be handled by connection, which
        // makes sure headers are received as a single unit
        return current;
    }

    private static Http2StreamState fail(Http2FrameType frameType, boolean send) {
        if (send) {
            throw new Http2Exception(Http2ErrorCode.INTERNAL, "Attempting to send " + frameType + " frame on a stream");
        } else {
            throw new Http2Exception(Http2ErrorCode.PROTOCOL, "Received " + frameType + " on a stream");
        }
    }

    private static Http2StreamState checkPriority(Http2StreamState current) {
        // https://datatracker.ietf.org/doc/html/rfc7540#section-6.3
        // can be sent/received in any state
        return current;
    }

    private static Http2StreamState checkPushPromise(Http2StreamState current, boolean send) {
        if (current == IDLE) {
            return send ? RESERVED_LOCAL : RESERVED_REMOTE;
        }
        if (send) {
            throw new Http2Exception(Http2ErrorCode.INTERNAL, "Attempting to send push promise in invalid state: " + current);
        } else {
            throw new Http2Exception(Http2ErrorCode.PROTOCOL, "Received push promise in invalid state: " + current);
        }
    }

    private static Http2StreamState checkRstStream(Http2StreamState current, boolean send) {
        if (current == IDLE) {
            if (send) {
                throw new Http2Exception(Http2ErrorCode.INTERNAL, "Attempting to send RST_STREAM in invalid state: " + current);
            } else {
                throw new Http2Exception(Http2ErrorCode.PROTOCOL, "Received RST_STREAM in invalid state: " + current);
            }
        }
        return CLOSED;
    }

    private static Http2StreamState checkHeaders(Http2StreamState current,
                                                 boolean send,
                                                 boolean endOfStream,
                                                 boolean endOfHeaders,
                                                 String type) {
        if (send) {
            if (current == IDLE) {
                if (endOfHeaders) {
                    return endOfStream ? HALF_CLOSED_LOCAL : OPEN;
                } else {
                    return current; // sending headers in progress
                }
            }
            if (current == RESERVED_LOCAL) {
                if (endOfHeaders) {
                    return HALF_CLOSED_REMOTE;
                } else {
                    return current; // sending headers in progress
                }
            }
            throw new Http2Exception(Http2ErrorCode.INTERNAL, "Attempting to send " + type + " in invalid state: " + current);
        } else {
            if (current == IDLE) {
                if (endOfHeaders) {
                    return endOfStream ? HALF_CLOSED_REMOTE : OPEN;
                } else {
                    return current;
                }
            }
            if (current == RESERVED_REMOTE) {
                if (endOfHeaders) {
                    return HALF_CLOSED_LOCAL;
                } else {
                    return current; //receiving headers in progress
                }
            }
            // 5.1. half-closed (local): An endpoint can receive any type of frame in this state
            if (current == HALF_CLOSED_LOCAL) {
                return HALF_CLOSED_LOCAL;
            }

            if (current == OPEN) {
                return endOfStream ? HALF_CLOSED_REMOTE : OPEN;
            }
            throw new Http2Exception(Http2ErrorCode.PROTOCOL, "Received " + type + " in invalid state: " + current);
        }
    }

    private static Http2StreamState checkData(Http2StreamState current, boolean send, boolean endOfStream) {
        if (send) {
            // we are sending data - only allowed if open and half closed (remote)
            if (current == OPEN) {
                return endOfStream ? HALF_CLOSED_LOCAL : current;
            }
            if (current == HALF_CLOSED_REMOTE) {
                return endOfStream ? CLOSED : current;
            }
            throw new Http2Exception(Http2ErrorCode.INTERNAL, "Attempting to send data frame in invalid state: " + current);
        } else {
            // receive
            if (current == OPEN) {
                return endOfStream ? HALF_CLOSED_REMOTE : current;
            }
            if (current == HALF_CLOSED_LOCAL) {
                return endOfStream ? CLOSED : current;
            }
            throw new Http2Exception(Http2ErrorCode.PROTOCOL, "Received data frame in invalid state: " + current);
        }
    }
}
