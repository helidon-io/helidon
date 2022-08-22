/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.reactive.media.multipart;

import java.nio.ByteBuffer;

class Utils {
    static byte[] toByteArray(ByteBuffer byteBuffer) {
        byte[] buff = new byte[byteBuffer.remaining()];
        return toByteArray(byteBuffer, buff, 0);
    }

    static byte[] toByteArray(ByteBuffer byteBuffer, byte[] buff, int destPos) {
        if (byteBuffer.hasArray()) {
            System.arraycopy(byteBuffer.array(), byteBuffer.arrayOffset() + byteBuffer.position(), buff, destPos, buff.length);
        } else {
            byteBuffer.get(buff, destPos, byteBuffer.remaining());
        }
        return buff;
    }
}
