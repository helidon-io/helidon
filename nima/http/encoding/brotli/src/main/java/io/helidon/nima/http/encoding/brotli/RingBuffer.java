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

package io.helidon.nima.http.encoding.brotli;

class RingBuffer {

    private static int size;
    private static int mask;
    private static int tailSize;
    private static int totalSize;
    private static int currentSize;
    private static int position;
    //Contains last two bytes + data + copy of the beginning of the tail.
    private static int[] data;
    //POZOR : buffer is pointing to data[2] so count a 2 offset when trying to access it.
    private static int[] buffer;

    public static void ringBufferSetUp(State state) {
        int windowBits = computeRbBits(state);
        int tailBits = state.lgBlock;

        setSize(1 << windowBits);
        setMask((1 << windowBits) - 1);
        setTailSize(1 << tailBits);
        setTotalSize(size + tailSize);
    }

    public static void initBufferRingBuffer(int bufferLength) throws BrotliException {
        int kSlackForEightByteHashingEverywhere = 7;
        int length = 2 + bufferLength + kSlackForEightByteHashingEverywhere;
        int[] newData = new int[length];
        if (data != null) {
            Utils.copyBytes(newData, data, 2 + currentSize + kSlackForEightByteHashingEverywhere);
        }
        data = newData;
        setCurrentSize(bufferLength);
        buffer = new int[length + 2];
        Utils.copyBytes(buffer, 2, data, 0, length);
        buffer[0] = 0;
        buffer[1] = 0;
        for (int i = 0; i < kSlackForEightByteHashingEverywhere; i++) {
            buffer[currentSize + i] = 0;
        }
    }

    public static void writeTailRingBuffer(int[] bytes, int offset, int n) throws BrotliException {
        int maskedPosition = position & mask;
        if (!(maskedPosition < tailSize)) {
            int p = size + maskedPosition;
            Utils.writeBuffer(bytes, offset, Math.min(n, tailSize), buffer, p + 2);
        }
    }

    public static void writeRingBuffer(int[] bytes, int offset, int n) throws BrotliException {
        if (position == 0 && n < tailSize) {
            position = n;
            initBufferRingBuffer(position);
            Utils.writeBuffer(bytes, offset, n, buffer, 2);
            return;
        }

        if (currentSize < totalSize) {
            initBufferRingBuffer(totalSize);
            buffer[0] = 0;
            buffer[1] = 0;
            buffer[size] = 241;
        }

        int maskedPosition = position & mask;
        writeTailRingBuffer(bytes, offset, n);

        if (maskedPosition + n <= size) {
            Utils.writeBuffer(bytes, offset, n, buffer, maskedPosition);
        } else {
            Utils.writeBuffer(bytes, offset, Math.min(n, totalSize - maskedPosition), buffer, maskedPosition);
            Utils.writeBuffer(bytes, offset + size - maskedPosition,
                              n - (size - maskedPosition), buffer, 2);
        }

        boolean notFirstLap = (position & (1 << 31)) != 0;
        int rbPositionMask = (1 << 31) - 1;
        buffer[0] = buffer[size - 2];
        buffer[1] = buffer[size - 1];
        position = (position & rbPositionMask) + (n & rbPositionMask);
        if (notFirstLap) {
            position |= (1 << 31);
        }
    }

    public static int getPosition() {
        return position;
    }

    private static void setPosition(int pPosition) {
        position = pPosition;
    }

    public static int getMask() {
        return mask;
    }

    private static void setMask(int pMask) {
        mask = pMask;
    }

    public static int[] getBuffer() {
        return buffer;
    }

    public static int[] getData() {
        return data;
    }

    public static int getCurrentSize() {
        return currentSize;
    }

    private static void setCurrentSize(int size) {
        currentSize = size;
    }

    private static int computeRbBits(State state) {
        return 1 + Math.max(state.window, state.lgBlock);
    }

    private static void setSize(int pSize) {
        size = pSize;
    }

    private static void setTailSize(int pTailSize) {
        tailSize = pTailSize;
    }

    private static void setTotalSize(int pTotalSize) {
        totalSize = pTotalSize;
    }
}
