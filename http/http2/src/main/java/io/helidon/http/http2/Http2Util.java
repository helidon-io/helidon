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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import io.helidon.common.buffers.BufferData;

/**
 * HTTP/2 utility.
 */
public final class Http2Util {
    private static final byte[] PRIOR_KNOWLEDGE_PREFACE =
            "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.UTF_8);
    /**
     * Length of prior knowledge preface.
     */
    public static final int PREFACE_LENGTH = PRIOR_KNOWLEDGE_PREFACE.length;

    private Http2Util() {
    }

    /**
     * Check if the bytes provided start with the prior knowledge preface.
     *
     * @param bytes bytes to check
     * @return {@code true} if the bytes are preface bytes
     */
    public static boolean isPreface(byte[] bytes) {
        return Arrays.compare(PRIOR_KNOWLEDGE_PREFACE, 0, PREFACE_LENGTH,
                              bytes, 0, PREFACE_LENGTH) == 0;
    }

    /**
     * HTTP/2 preface data as a buffer data.
     *
     * @return a buffer that contains the preface
     */
    public static BufferData prefaceData() {
        return BufferData.create(PRIOR_KNOWLEDGE_PREFACE);
    }
}
