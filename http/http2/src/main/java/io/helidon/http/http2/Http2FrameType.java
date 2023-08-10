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

import java.util.HashMap;
import java.util.Map;

/**
 * HTTP/2 frame types.
 */
public enum Http2FrameType {
    /**
     * Data frame.
     */
    DATA(0x0, -1),
    /**
     * Headers frame.
     */
    HEADERS(0x1, -1),
    /**
     * Priority frame.
     */
    PRIORITY(0x2, 5),
    /**
     * RST stream frame.
     */
    RST_STREAM(0x3, 4),
    /**
     * Setting frame.
     */
    SETTINGS(0x4, -1),
    /**
     * Push promise frame (not supported by this server).
     */
    PUSH_PROMISE(0x5, -1),
    /**
     * Ping frame.
     */
    PING(0x6, 8),
    /**
     * Go away frame.
     */
    GO_AWAY(0x7, -1),
    /**
     * Window update frame.
     */
    WINDOW_UPDATE(0x8, 4),
    /**
     * Continuation frame.
     */
    CONTINUATION(0x9, -1),
    /**
     * Unknown frame.
     */
    UNKNOWN(Short.MAX_VALUE, -1);

    private static final Map<Integer, Http2FrameType> BY_ID;

    static {
        Http2FrameType[] values = Http2FrameType.values();
        Map<Integer, Http2FrameType> map = new HashMap<>(values.length);
        for (Http2FrameType value : values) {
            map.put(value.type(), value);
        }
        BY_ID = Map.copyOf(map);
    }

    private final int type;
    private final int exactLength;

    Http2FrameType(int type, int exactLength) {
        this.type = type;
        this.exactLength = exactLength;
    }

    /**
     * Get a frame type by frame id.
     *
     * @param id frame id
     * @return frame type
     */
    public static Http2FrameType byId(int id) {
        return BY_ID.getOrDefault(id, UNKNOWN);
    }

    /**
     * Type id.
     *
     * @return frame type id
     */
    public int type() {
        return type;
    }

    /**
     * Check if the length of the frame is valid for this frame type.
     *
     * @param length length of frame
     */
    public void checkLength(int length) {
        if (exactLength == -1) {
            return;
        }
        if (exactLength != length) {
            throw new Http2Exception(Http2ErrorCode.FRAME_SIZE,
                                     name() + " frame size must be " + exactLength + ", but is " + length);
        }
    }
}
