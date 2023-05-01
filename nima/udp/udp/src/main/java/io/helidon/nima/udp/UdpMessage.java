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

package io.helidon.nima.udp;

import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * A UDP message whose content can be requested in a few different formats.
 */
public interface UdpMessage {

    /**
     * Client from which we received this message.
     *
     * @return the client
     */
    UdpClient udpClient();

    /**
     * Request conversion of message content to a class instance.
     *
     * @param clazz the class
     * @return the converted value
     * @param <T> the value's type
     */
    <T> T as(Class<T> clazz);

    /**
     * Request conversion to {@link InputStream}.
     *
     * @return the input stream
     */
    default InputStream asInputStream() {
        return as(InputStream.class);
    }

    /**
     * Request conversion to a byte array.
     *
     * @return the byte array
     */
    default byte[] asByteArray() {
        return as(byte[].class);
    }

    /**
     * Request conversion to {@link ByteBuffer}.
     *
     * @return the byte buffer
     */
    default ByteBuffer asByteBuffer() {
        return as(ByteBuffer.class);
    }
}
