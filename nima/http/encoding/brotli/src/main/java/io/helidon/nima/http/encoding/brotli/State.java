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

import java.io.OutputStream;

/**
 * State contains the encoder status.
 * Should contain different buffer/List and index as well as context data.
 */
class State {

    BrotliEncoderStreamState streamState;
    BrotliEncoderCompressState compressState;
    Distance distance;
    Hasher hasher;

    int numLastDistancesToCheck;

    int quality;
    int window;
    int lgBlock;
    int sizeHint;

    int[] inputBuffer;
    int availableIn;
    int inputOffset;
    int loadIx;
    int inputLength;

    int[] storage;
    int availableStorage;
    int storageBit;

    OutputStream outputStream;
    byte[] output;
    int outputOffset;
    int outputBitUsed;
    int availableOut;

    int[] cmdDepth;
    int[] cmdBits;
    int[] cmdCode;
    int cmdCodeNumbits;
    int[] table;
    int tableSize;
    int[] smallTable = new int[1 << 10];
    int smallTableSize = 1 << 10;
    int[] largeTable;
    int largeTableSize;

    int totalOut;
    int remainingMetadataBytes;
    int lastInsertLength;
    int lastFlushPosition;
    int lastProcessedPosition;
    int lastBytes;
    int lastBytesBits;
    int flint;
    int prevByte;
    int prevByte2;
    int remainingBlockSize;

    Command[] commands;
    int numCommands;
    int cmdAllocSize;

    int numLiterals;

    int[] distCache = new int[Constant.BROTLI_NUM_DISTANCE_SHORT_CODES];
    int distCacheIndex = 0;
    int[] savedDistCache = new int[4];

    int hasherType = 10;

    int streamOffset;

    boolean isLastBlockEmitted;
    boolean isInitialized;
    boolean eof;
    boolean isFirstBlockEmitted;
    boolean disableLiteralContextModeling;

    public static void initState(State state, OutputStream outputStream) {
        state.outputStream = outputStream;
        state.hasher = new Hasher();
        state.distance = new Distance();
        state.commands = new Command[1];
        state.commands[0] = new Command();
        state.numCommands = 0;
        state.cmdAllocSize = 1;

        state.output = new byte[10_000];
        state.totalOut = 0;
        state.availableOut = 10_000;
        state.outputBitUsed = 0;

        state.quality = 0;
        state.window = -1;

        state.storageBit = 0;
        state.availableStorage = -1;

        state.inputBuffer = new int[Constant.INPUTBUFFER_SIZE];
        state.availableIn = 0;
        state.inputOffset = 0;
        state.loadIx = 0;

        state.smallTableSize = 1 << 10;
        state.lastInsertLength = 0;
        state.lastFlushPosition = 0;
        state.lastProcessedPosition = 0;
        state.flint = 0;
        state.prevByte = 0;
        state.prevByte2 = 0;
        state.remainingMetadataBytes = 0;

        state.cmdDepth = new int[128];
        state.cmdBits = new int[128];
        state.cmdCode = new int[512];

        state.isLastBlockEmitted = false;
        state.isFirstBlockEmitted = false;
        state.isInitialized = false;
        state.disableLiteralContextModeling = false;
        state.eof = false;
    }

    enum BrotliEncoderStreamState {
        BROTLI_STREAM_PROCESSING,
        BROTLI_STREAM_FLUSH_REQUESTED,
        BROTLI_STREAM_FINISHED,
        BROTLI_STREAM_METADATA_HEAD,
        BROTLI_STREAM_METADATA_BODY;
    }

    enum BrotliEncoderCompressState {
        FOR,
        TRAWL,
        NEXT_BLOCK,
        EMIT_REMAINDER,
        EMIT_COMMANDS;
    }
}
