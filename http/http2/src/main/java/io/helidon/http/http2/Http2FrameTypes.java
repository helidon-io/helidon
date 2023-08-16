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

import io.helidon.http.http2.Http2Flag.ContinuationFlags;
import io.helidon.http.http2.Http2Flag.DataFlags;
import io.helidon.http.http2.Http2Flag.HeaderFlags;
import io.helidon.http.http2.Http2Flag.NoFlags;
import io.helidon.http.http2.Http2Flag.PingFlags;
import io.helidon.http.http2.Http2Flag.PushPromiseFlags;
import io.helidon.http.http2.Http2Flag.SettingsFlags;
import io.helidon.http.http2.Http2FrameTypeUtil.FrameTypeImpl;

/**
 * Frame types with types flags.
 *
 * @param <T> type of flags
 */
public interface Http2FrameTypes<T extends Http2Flag> {
    /**
     * Data frame types.
     */
    Http2FrameTypes<DataFlags> DATA = new FrameTypeImpl<>(Http2FrameType.DATA, DataFlags::create);
    /**
     * Headers frame types.
     */
    Http2FrameTypes<HeaderFlags> HEADERS = new FrameTypeImpl<>(Http2FrameType.HEADERS, HeaderFlags::create);
    /**
     * Priority frame types.
     */
    Http2FrameTypes<NoFlags> PRIORITY = new FrameTypeImpl<>(Http2FrameType.PRIORITY, NoFlags::create);
    /**
     * RST stream frame types.
     */
    Http2FrameTypes<NoFlags> RST_STREAM = new FrameTypeImpl<>(Http2FrameType.RST_STREAM, NoFlags::create);
    /**
     * Settings frame types.
     */
    Http2FrameTypes<SettingsFlags> SETTINGS = new FrameTypeImpl<>(Http2FrameType.SETTINGS, SettingsFlags::create);
    /**
     * Push promise frame types.
     */
    Http2FrameTypes<PushPromiseFlags> PUSH_PROMISE = new FrameTypeImpl<>(Http2FrameType.PUSH_PROMISE, PushPromiseFlags::create);
    /**
     * Ping frame types.
     */
    Http2FrameTypes<PingFlags> PING = new FrameTypeImpl<>(Http2FrameType.PING, PingFlags::create);
    /**
     * Go away frame types.
     */
    Http2FrameTypes<NoFlags> GO_AWAY = new FrameTypeImpl<>(Http2FrameType.GO_AWAY, NoFlags::create);
    /**
     * Window update frame types.
     */
    Http2FrameTypes<NoFlags> WINDOW_UPDATE = new FrameTypeImpl<>(Http2FrameType.WINDOW_UPDATE,
                                                                 NoFlags::create);
    /**
     * Continuation frame types.
     */
    Http2FrameTypes<ContinuationFlags> CONTINUATION = new FrameTypeImpl<>(Http2FrameType.CONTINUATION, ContinuationFlags::create);
    /**
     * Unknown frame types.
     */
    Http2FrameTypes<NoFlags> UNKNOWN = new FrameTypeImpl<>(Http2FrameType.UNKNOWN, NoFlags::create);

    /**
     * Get frame types based on frame type enum.
     *
     * @param frameType frame type
     * @return frame types
     */
    static Http2FrameTypes<? extends Http2Flag> get(Http2FrameType frameType) {
        return Http2FrameTypeUtil.get(frameType);
    }

    /**
     * Frame type enum.
     *
     * @return frame type
     */
    Http2FrameType type();

    /**
     * Typed flags.
     *
     * @param flags flags number
     * @return typed flags instances
     */
    T flags(int flags);
}
