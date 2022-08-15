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

class Command {

    private long insertLen;
    private long copyLen;
    private long distExtra;
    private int cmdPrefix;
    private int distPrefix;

    Command() {
        this.insertLen = 0;
        this.copyLen = 0;
        this.distExtra = 0;
        this.cmdPrefix = 0;
        this.distPrefix = 0;
    }

    Command(long insertLen, long copyLen, long distExtra, int cmdPrefix, int distPrefix) {
        this.insertLen = insertLen;
        this.copyLen = copyLen;
        this.distExtra = distExtra;
        this.cmdPrefix = cmdPrefix;
        this.distPrefix = distPrefix;
    }

    public static int getInsertLengthCode(int insertLen) {
        if (insertLen < 6) {
            return insertLen;
        } else if (insertLen < 130) {
            int nbits = Utils.log2FloorNonZero((int) (insertLen - 2)) - 1;
            return ((nbits << 1) + ((insertLen - 2) >> nbits) + 2);
        } else if (insertLen < 2114) {
            return Utils.log2FloorNonZero(insertLen - 66) + 10;
        } else if (insertLen < 6210) {
            return 21;
        } else if (insertLen < 22594) {
            return 22;
        } else {
            return 23;
        }
    }

    public static int getCopyLengthCode(int copyLen) {
        if (copyLen < 10) {
            return (copyLen - 2);
        } else if (copyLen < 134) {
            int nbits = Utils.log2FloorNonZero(copyLen - 6) - 1;
            return ((nbits << 1) + ((copyLen - 6) >> nbits) + 4);
        } else if (copyLen < 2118) {
            return (Utils.log2FloorNonZero(copyLen - 70) + 12);
        } else {
            return 23;
        }
    }

    public static int combineLengthCode(int insCode, int copyCode, boolean useLastDistance) {
        int bits64 = ((copyCode & 0x7) | ((insCode & 0x7) << 3));
        if (useLastDistance && insCode < 8 && copyCode < 16) {
            return (copyCode < 8) ? bits64 : (bits64 | 64);
        } else {
            /* Specification: 5 Encoding of ... (last table) */
            /* offset = 2 * index, where index is in range [0..8] */
            int offset = 2 * ((copyCode >> 3) + 3 * (insCode >> 3));
            /* All values in specification are K * 64,
               where   K = [2, 3, 6, 4, 5, 8, 7, 9, 10],
                   i + 1 = [1, 2, 3, 4, 5, 6, 7, 8,  9],
               K - i - 1 = [1, 1, 3, 0, 0, 2, 0, 1,  2] = D.
               All values in D require only 2 bits to encode.
               Magic constant is shifted 6 bits left, to avoid final multiplication. */
            offset = (offset << 5) + 0x40 + ((0x520D40 >> offset) & 0xC0);
            return (offset | bits64);
        }
    }

    public static void initInsertCommand(State state) {
        state.commands[state.numCommands] = new Command();
        Command command = state.commands[state.numCommands++];
        command.setInsertLen(state.lastInsertLength);
        command.setCopyLen(4 << 25);
        command.setDistExtra(0);
        command.setDistPrefix(Constant.BROTLI_NUM_DISTANCE_SHORT_CODES);
        Compress.getLengthCode(state.lastInsertLength, 4, false, command);
    }

    public static void initCommand(State state, int index, int insertLen, int copyLen,
                                   int copyLenCodeDelta, int distanceCode) {

        state.commands[index].insertLen = insertLen;
        state.commands[index].copyLen = (copyLen | ((long) copyLenCodeDelta << 25));
        prefixEncodeCopyDistanceCommand(state, index, distanceCode);
        Compress.getLengthCode(insertLen, copyLen + copyLenCodeDelta,
                               (state.commands[index].distPrefix & 0x3FF) == 0, state.commands[index]);
    }

    public static long commandCopyLen(Command cmd) {
        return cmd.copyLen & 0x1FFFFFF;
    }

    public static int commandDistanceContext(Command self) {
        int r = self.getCmdPrefix() >> 6;
        int c = self.getCmdPrefix() & 7;
        if ((r == 0 || r == 2 || r == 4 || r == 7) && (c <= 2)) {
            return c;
        }
        return 3;
    }

    public static void storeCommandExtra(State state, Command cmd) throws BrotliException {
        int copylen_code = commandCopyLenCode(cmd);
        int inscode = getInsertLengthCode((int) cmd.getInsertLen());
        int copycode = getCopyLengthCode(copylen_code);
        int insnumextra = getInsertExtra(inscode);
        long insextraval = cmd.getInsertLen() - getInsertBase(inscode);
        long copyextraval = copylen_code - getCopyBase(copycode);
        long bits = (copyextraval << insnumextra) | insextraval;

        state.storageBit = BitWriter.writeBit(
                insnumextra + getCopyExtra(copycode), bits, state.storageBit, state.storage);
    }

    public static int getCopyExtra(int copycode) {
        return Tables.kBrotliCopyExtra[copycode];
    }

    public static int getCopyBase(int copycode) {
        return Tables.kBrotliCopyBase[copycode];
    }

    public static int getInsertBase(int inscode) {
        return Tables.kBrotliInsBase[inscode];
    }

    public static int getInsertExtra(int inscode) {
        return Tables.kBrotliInsExtra[inscode];
    }

    public static int commandCopyLenCode(Command self) {
        int modifier = (int) (self.getCopyLen() >> 25);
        int delta = ((modifier | ((modifier & 0x40) << 1)));
        return (int) ((self.getCopyLen() & 0x1FFFFFF) + delta);
    }

    public int getCmdPrefix() {
        return cmdPrefix;
    }

    public void setCmdPrefix(int cmdPrefix) {
        this.cmdPrefix = cmdPrefix;
    }

    public int getDistPrefix() {
        return distPrefix;
    }

    public void setDistPrefix(int distPrefix) {
        this.distPrefix = distPrefix;
    }

    public long getCopyLen() {
        return copyLen;
    }

    public void setCopyLen(long copyLen) {
        this.copyLen = copyLen;
    }

    public long getDistExtra() {
        return distExtra;
    }

    public void setDistExtra(long distExtra) {
        this.distExtra = distExtra;
    }

    public long getInsertLen() {
        return insertLen;
    }

    public void setInsertLen(long insertLen) {
        this.insertLen = insertLen;
    }

    private static void prefixEncodeCopyDistanceCommand(State state, int index, int distanceCode) {
        if (distanceCode < Constant.BROTLI_NUM_DISTANCE_SHORT_CODES + state.distance.numDirectDistanceCodes) {
            state.commands[index].distPrefix = distanceCode;
            state.commands[index].distExtra = 0;
            return;
        }
        int dist = (1 << (state.distance.distancePostfixBits + 2)) +
                (distanceCode - Constant.BROTLI_NUM_DISTANCE_SHORT_CODES - state.distance.numDirectDistanceCodes);
        int bucket = Utils.log2FloorNonZero(dist) - 1;
        int postfixMask = (1 << state.distance.distancePostfixBits) - 1;
        int postfix = dist & postfixMask;
        int prefix = (dist >> bucket) & 1;
        int offset = (2 + prefix) << bucket;
        int nBits = bucket - state.distance.distancePostfixBits;
        state.commands[index].distPrefix = (nBits << 10) |
                (
                        Constant.BROTLI_NUM_DISTANCE_SHORT_CODES + state.distance.numDirectDistanceCodes +
                                ((2 * (nBits - 1) + prefix) << state.distance.distancePostfixBits) + postfix);
        state.commands[index].distExtra = (dist - offset) >> state.distance.distancePostfixBits;
    }
}
