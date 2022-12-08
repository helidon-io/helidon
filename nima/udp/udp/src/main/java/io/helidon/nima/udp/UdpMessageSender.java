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

package io.helidon.nima.udp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Methods for UDP senders.
 */
public interface UdpMessageSender {

    /**
     * Send an arbitrary instance after conversion to a buffer.
     *
     * @param msg the instance
     * @throws IOException if an I/O error occurs
     */
    void sendMessage(Object msg) throws IOException;

    /**
     * Send a byte array.
     *
     * @param bytes the byte array
     * @throws IOException if an I/O error occurs
     */
    void sendMessage(byte[] bytes) throws IOException;

    /**
     * Send a message by reading an {@link InputStream}.
     *
     * @param is the input stream
     * @throws IOException if an I/O error occurs
     */
    void sendMessage(InputStream is) throws IOException;

    /**
     * Send a {@link ByteBuffer} with remaining bytes to read.
     *
     * @param buffer the byte buffer
     * @throws IOException if an I/O error occurs
     */
    void sendMessage(ByteBuffer buffer) throws IOException;
}
