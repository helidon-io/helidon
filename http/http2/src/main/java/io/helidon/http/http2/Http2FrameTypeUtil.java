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

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

final class Http2FrameTypeUtil {
    private static final Map<Http2FrameType, Http2FrameTypes<? extends Http2Flag>> TYPES_BY_TYPE;

    static {
        Map<Http2FrameType, Http2FrameTypes<? extends Http2Flag>> typesByType = new EnumMap<>(Http2FrameType.class);

        add(typesByType, Http2FrameTypes.DATA);
        add(typesByType, Http2FrameTypes.HEADERS);
        add(typesByType, Http2FrameTypes.PRIORITY);
        add(typesByType, Http2FrameTypes.RST_STREAM);
        add(typesByType, Http2FrameTypes.SETTINGS);
        add(typesByType, Http2FrameTypes.PUSH_PROMISE);
        add(typesByType, Http2FrameTypes.PING);
        add(typesByType, Http2FrameTypes.GO_AWAY);
        add(typesByType, Http2FrameTypes.WINDOW_UPDATE);
        add(typesByType, Http2FrameTypes.CONTINUATION);
        add(typesByType, Http2FrameTypes.UNKNOWN);

        TYPES_BY_TYPE = typesByType;
    }

    private Http2FrameTypeUtil() {
    }

    static Http2FrameTypes<? extends Http2Flag> get(Http2FrameType frameType) {
        var found = TYPES_BY_TYPE.get(frameType);
        if (found == null) {
            throw new Http2Exception(Http2ErrorCode.INTERNAL, "Invalid frame type: " + frameType);
        }
        return found;
    }

    private static void add(Map<Http2FrameType, Http2FrameTypes<? extends Http2Flag>> byId,
                            Http2FrameTypes<? extends Http2Flag> data) {
        byId.put(data.type(), data);
    }

    static class FrameTypeImpl<T extends Http2Flag> implements Http2FrameTypes<T> {
        private final Http2FrameType type;
        private final Function<Integer, T> flagsFunction;

        FrameTypeImpl(Http2FrameType type, Function<Integer, T> flagsFunction) {
            this.type = type;
            this.flagsFunction = flagsFunction;
        }

        @Override
        public Http2FrameType type() {
            return type;
        }

        @Override
        public T flags(int flags) {
            return flagsFunction.apply(flags);
        }
    }
}
