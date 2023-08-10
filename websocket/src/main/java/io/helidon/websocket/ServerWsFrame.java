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

package io.helidon.websocket;

import java.nio.charset.StandardCharsets;

import io.helidon.common.LazyValue;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.socket.SocketContext;

/**
 * Frame from a server (never masked).
 */
public final class ServerWsFrame extends AbstractWsFrame {
    private static final System.Logger LOGGER = System.getLogger(ServerWsFrame.class.getName());

    ServerWsFrame(WsOpCode opCode, BufferData data, boolean fin, boolean isPayload) {
        super(LazyValue.create(data), data.available(), fin, isPayload, opCode);
    }

    /**
     * Create a text data frame. This method does not split the message into smaller frames if the number of bytes
     * is too high, make sure to use with buffer sizes that are guaranteed to be processed by clients.
     *
     * @param text text data content
     * @param last whether the data is last
     * @return a new server frame
     */
    public static ServerWsFrame data(String text, boolean last) {
        BufferData bufferData = BufferData.create(text.getBytes(StandardCharsets.UTF_8));

        return new ServerWsFrame(WsOpCode.TEXT,
                                 bufferData,
                                 last,
                                 true);
    }

    /**
     * Create a binary data frame. This method does not split the message into smaller frames if the number of bytes
     * is too high, make sure to use with buffer sizes that are guaranteed to be processed by clients.
     *
     * @param bufferData binary data content
     * @param last       whether the data is last
     * @return a new server frame
     */
    public static ServerWsFrame data(BufferData bufferData, boolean last) {
        return new ServerWsFrame(WsOpCode.BINARY,
                                 bufferData,
                                 last,
                                 true);
    }

    /**
     * Create a new control frame.
     *
     * @param opCode     operation code of this frame
     * @param bufferData data of the frame, maximally 125 bytes
     * @return a new server frame
     * @throws java.lang.IllegalArgumentException in case the length is invalid
     */
    public static ServerWsFrame control(WsOpCode opCode, BufferData bufferData) {
        if (bufferData.available() > 125) {
            throw new IllegalArgumentException("Control frames cannot have more than 125 bytes");
        }
        return new ServerWsFrame(opCode, bufferData, true, false);
    }

    /**
     * Read server frame from request data.
     *
     * @param ctx            socket context
     * @param dataReader     data reader to get frame bytes from
     * @param maxFrameLength maximal length of a frame, to protect memory from too big frames
     * @return a new server frame
     * @throws WsCloseException in case of invalid frame
     * @throws java.lang.RuntimeException                 depending on implementation of dataReader
     */
    public static ServerWsFrame read(SocketContext ctx, DataReader dataReader, int maxFrameLength) {

        FrameHeader header = readFrameHeader(dataReader, maxFrameLength);

        if (header.masked()) {
            throw new WsCloseException("Masked server frame", WsCloseCodes.PROTOCOL_ERROR);
        }

        // next frameLength bytes - actual payload
        // we can safely cast to int, as we make sure it is smaller or equal to MAX_INT
        BufferData payload = readPayload(dataReader, header);

        ServerWsFrame frame = new ServerWsFrame(header.opCode(),
                                                payload,
                                                header.fin(),
                                                isPayload(header));

        if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
            ctx.log(LOGGER, System.Logger.Level.TRACE, "ws server frame recv %s", frame);
        }

        return frame;
    }

    @Override
    public boolean masked() {
        return false;
    }

    @Override
    public int[] maskingKey() {
        throw new IllegalStateException("Server WebSocket frames must not have masking key");
    }
}
