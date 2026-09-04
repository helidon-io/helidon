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

import java.util.Optional;

import io.helidon.common.Api;

/**
 * HTTP/3 unidirectional stream types defined by the protocol.
 */
@Api.Internal
public enum Http3StreamType {
    /**
     * Control stream carrying SETTINGS and GOAWAY frames.
     */
    CONTROL(0x00),
    /**
     * Push stream.
     */
    PUSH(0x01),
    /**
     * QPACK encoder instruction stream.
     */
    QPACK_ENCODER(0x02),
    /**
     * QPACK decoder instruction stream.
     */
    QPACK_DECODER(0x03);

    private final long code;

    Http3StreamType(long code) {
        this.code = code;
    }

    /**
     * Return the wire-encoded stream type value.
     *
     * @return stream type code
     */
    public long code() {
        return code;
    }

    /**
     * Resolve a stream type from its wire-encoded value.
     *
     * @param code wire-encoded stream type
     * @return matching stream type, if known
     */
    public static Optional<Http3StreamType> of(long code) {
        for (Http3StreamType value : values()) {
            if (value.code == code) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
