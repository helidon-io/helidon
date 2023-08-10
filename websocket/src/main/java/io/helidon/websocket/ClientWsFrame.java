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
import java.security.SecureRandom;
import java.util.Random;

import io.helidon.common.LazyValue;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.socket.SocketContext;

/**
 * Frame from a client (always masked).
 */
public final class ClientWsFrame extends AbstractWsFrame {
    private static final System.Logger LOGGER = System.getLogger(ClientWsFrame.class.getName());
    private static final LazyValue<Random> RANDOM = LazyValue.create(SecureRandom::new);

    private final LazyValue<BufferData> masked;
    private final int[] mask;

    private ClientWsFrame(WsOpCode opCode,
                          long payloadLength,
                          BufferData data,
                          boolean fin,
                          int[] mask,
                          boolean masked,
                          boolean isPayload) {
        super(unmaskedValue(masked, data, mask), payloadLength, fin, isPayload, opCode);

        this.mask = mask;

        if (masked) {
            this.masked = LazyValue.create(data);
        } else {
            this.masked = LazyValue.create(() -> mask(data, mask));
        }
    }

    /**
     * Create a text data frame. This method does not split the message into smaller frames if the number of bytes
     * is too high, make sure to use with buffer sizes that are guaranteed to be processed by clients.
     *
     * @param text text data content
     * @param last whether the data is last
     * @return a new client frame
     */
    public static ClientWsFrame data(String text, boolean last) {
        BufferData bufferData = BufferData.create(text.getBytes(StandardCharsets.UTF_8));
        return new ClientWsFrame(WsOpCode.TEXT,
                                 bufferData.available(),
                                 bufferData,
                                 last,
                                 newMaskingKey(),
                                 false,
                                 true);
    }

    /**
     * Create a binary data frame. This method does not split the message into smaller frames if the number of bytes
     * is too high, make sure to use with buffer sizes that are guaranteed to be processed by clients.
     *
     * @param bufferData binary data content
     * @param last       whether the data is last
     * @return a new client frame
     */
    public static ClientWsFrame data(BufferData bufferData, boolean last) {
        return new ClientWsFrame(WsOpCode.BINARY,
                                 bufferData.available(),
                                 bufferData,
                                 last,
                                 newMaskingKey(),
                                 false,
                                 true);
    }

    /**
     * Create a new control frame.
     *
     * @param opCode     operation code of this frame
     * @param bufferData data of the frame, maximally 125 bytes
     * @return a new client frame
     * @throws java.lang.IllegalArgumentException in case the length is invalid
     */
    public static ClientWsFrame control(WsOpCode opCode, BufferData bufferData) {
        return new ClientWsFrame(opCode,
                                 bufferData.available(),
                                 bufferData,
                                 true,
                                 newMaskingKey(),
                                 false,
                                 false);
    }

    /**
     * Read client frame from request data.
     *
     * @param ctx            socket context
     * @param dataReader     data reader to get frame bytes from
     * @param maxFrameLength maximal length of a frame, to protect memory from too big frames
     * @return a new client frame
     * @throws WsCloseException in case of invalid frame
     * @throws java.lang.RuntimeException                 depending on implementation of dataReader
     */
    public static ClientWsFrame read(SocketContext ctx,
                                     DataReader dataReader,
                                     int maxFrameLength) {

        FrameHeader header = readFrameHeader(dataReader, maxFrameLength);

        if (!header.masked()) {
            throw new WsCloseException("Unmasked client frame", WsCloseCodes.PROTOCOL_ERROR);
        }

        // next 4 bytes - masking key
        int[] maskingKey = new int[4];
        maskingKey[0] = dataReader.read();
        maskingKey[1] = dataReader.read();
        maskingKey[2] = dataReader.read();
        maskingKey[3] = dataReader.read();

        // next frameLength bytes - actual payload
        BufferData payload = readPayload(dataReader, header);

        ClientWsFrame frame = new ClientWsFrame(header.opCode(),
                                                header.length(),
                                                payload,
                                                header.fin(),
                                                maskingKey,
                                                true,
                                                isPayload(header));

        if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
            ctx.log(LOGGER, System.Logger.Level.TRACE, "ws client frame recv %s", frame);
        }

        return frame;
    }

    @Override
    public boolean masked() {
        return true;
    }

    @Override
    public int[] maskingKey() {
        return mask;
    }

    /**
     * Masked data of this frame, to be sent over the network.
     *
     * @return masked data
     */
    public BufferData maskedData() {
        return masked.get();
    }

    private static LazyValue<BufferData> unmaskedValue(boolean masked, BufferData data, int[] mask) {
        if (masked) {
            return LazyValue.create(() -> unmask(data, mask));
        } else {
            return LazyValue.create(data);
        }
    }

    private static int[] newMaskingKey() {
        Random random = RANDOM.get();

        int[] maskingKey = new int[4];
        for (int i = 0; i < maskingKey.length; i++) {
            maskingKey[i] = random.nextInt(256);

        }

        return maskingKey;
    }

    private static BufferData unmask(BufferData data, int[] masks) {
        int length = data.available();
        BufferData unmasked = BufferData.create(length);

        /*
        Octet i of the transformed data ("transformed-octet-i") is the XOR of
        octet i of the original data ("original-octet-i") with octet at index
        i modulo 4 of the masking key ("masking-key-octet-j"):

        j                   = i MOD 4
        transformed-octet-i = original-octet-i XOR masking-key-octet-j
         */
        for (int i = 0; i < length; i++) {
            int maskIndex = i % 4;
            int mask = masks[maskIndex];
            unmasked.write(data.read() ^ mask);
        }

        return unmasked;
    }

    private static BufferData mask(BufferData data, int[] masks) {
        int length = data.available();
        BufferData unmasked = BufferData.create(length);

        /*
        Octet i of the transformed data ("transformed-octet-i") is the XOR of
        octet i of the original data ("original-octet-i") with octet at index
        i modulo 4 of the masking key ("masking-key-octet-j"):

        j                   = i MOD 4
        transformed-octet-i = original-octet-i XOR masking-key-octet-j
         */
        for (int i = 0; i < length; i++) {
            int maskIndex = i % 4;
            int mask = masks[maskIndex];
            unmasked.write(data.read() ^ mask);
        }

        return unmasked;
    }
}
