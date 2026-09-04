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

package io.helidon.http.http3.qpack;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import io.helidon.common.Api;
import io.helidon.common.buffers.BufferData;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.http3.Http3ErrorCode;
import io.helidon.http.http3.Http3ProtocolException;
import io.helidon.http.http3.Http3QpackContext;
import io.helidon.http.http3.Http3ReadTimeoutException;

import static io.helidon.common.buffers.BufferData.EMPTY_BYTES;

/**
 * Per-connection QPACK state shared between encoder and decoder streams.
 */
@Api.Internal
public final class QpackConnectionState {
    private static final int MAX_LOCAL_ENCODER_TABLE_CAPACITY = 4 * 1024;
    private static final int MAX_PENDING_INSTRUCTION_BYTES = 16 * 1024;
    private static final int MAX_QPACK_INPUT_CHUNK_SIZE = 8 * 1024;
    private static final int MAX_UNACKNOWLEDGED_SECTIONS = 1024;
    private static final int MAX_DECODER_INSTRUCTION_SIZE = 11;
    private static final int ENCODER_INSTRUCTION_ENVELOPE = 64;
    private static final FieldEncoding[] STATIC_INDEXED_ENCODINGS = staticIndexedEncodings();

    private final ReentrantLock encoderLock = new ReentrantLock();
    private final ReentrantLock decoderLock = new ReentrantLock();
    private final QpackDynamicTable encoderTable = new QpackDynamicTable();
    private final QpackDynamicTable decoderTable = new QpackDynamicTable();
    private final Map<Long, Deque<Long>> unacknowledgedSections = new HashMap<>();
    private final Map<Long, DecoderStream> decoderStreams = new HashMap<>();
    private final Map<Long, Deque<FieldSection>> blockedSections = new LinkedHashMap<>();
    private final Deque<byte[]> pendingEncoderInstructions = new ArrayDeque<>();
    private CompletableFuture<Void> decoderProgress = new CompletableFuture<>();
    private final Deque<byte[]> pendingDecoderInstructions = new ArrayDeque<>();
    private final long localMaxTableCapacity;
    private final long localBlockedStreams;
    private final int maxHeadersSize;
    private final int encodedFieldSectionLimit;
    private final int maxEncoderInstructionSize;
    private final Consumer<Throwable> connectionFailureHandler;
    private final AtomicReference<Throwable> terminationSelection = new AtomicReference<>();

    private Http3QpackContext.InstructionSender encoderInstructionsSender;
    private Http3QpackContext.InstructionSender decoderInstructionsSender;
    private long knownReceivedCount;
    private long decoderAcknowledgedInsertCount;
    private int unacknowledgedSectionCount;
    private int pendingEncoderInstructionBytes;
    private int pendingDecoderInstructionBytes;
    private byte[] pendingEncoderInput = EMPTY_BYTES;
    private byte[] pendingDecoderInput = EMPTY_BYTES;

    /**
     * Create per-connection QPACK state.
     *
     * @param localMaxTableCapacity local decoder dynamic-table capacity
     * @param localBlockedStreams   local limit for blocked streams
     */
    private QpackConnectionState(long localMaxTableCapacity,
                                 long localBlockedStreams,
                                 int maxHeadersSize,
                                 Consumer<Throwable> connectionFailureHandler) {
        decoderTable.maxCapacity(localMaxTableCapacity);
        decoderTable.capacity(0);
        encoderTable.maxCapacity(0);
        encoderTable.capacity(0);
        this.localMaxTableCapacity = localMaxTableCapacity;
        this.localBlockedStreams = localBlockedStreams;
        this.maxHeadersSize = maxHeadersSize;
        this.encodedFieldSectionLimit = Http3QpackContext.encodedFieldSectionLimit(maxHeadersSize);
        this.maxEncoderInstructionSize = localMaxTableCapacity > (Integer.MAX_VALUE - ENCODER_INSTRUCTION_ENVELOPE) / 4L
                ? Integer.MAX_VALUE
                : (int) (localMaxTableCapacity * 4 + ENCODER_INSTRUCTION_ENVELOPE);
        this.connectionFailureHandler = connectionFailureHandler;
    }

    /**
     * Create per-connection QPACK state with a connection failure handler and a hard local header limit.
     *
     * @param localMaxTableCapacity local decoder dynamic-table capacity
     * @param localBlockedStreams local limit for blocked streams
     * @param maxHeadersSize hard local decoded-header limit
     * @param connectionFailureHandler connection-owner handler for QPACK connection failure
     * @return new per-connection QPACK state
     */
    public static QpackConnectionState create(long localMaxTableCapacity,
                                              long localBlockedStreams,
                                              int maxHeadersSize,
                                              Consumer<Throwable> connectionFailureHandler) {
        if (maxHeadersSize < 0) {
            throw new IllegalArgumentException("maxHeadersSize must not be negative: " + maxHeadersSize);
        }
        return new QpackConnectionState(localMaxTableCapacity,
                                        localBlockedStreams,
                                        maxHeadersSize,
                                        Objects.requireNonNull(connectionFailureHandler, "connectionFailureHandler"));
    }

    /**
     * Apply peer-advertised QPACK settings.
     *
     * @param qpackMaxTableCapacity peer dynamic-table capacity
     * @param qpackBlockedStreams   peer blocked-stream limit
     */
    public void peerSettings(long qpackMaxTableCapacity, long qpackBlockedStreams) {
        Http3ProtocolException failure = null;
        encoderLock.lock();
        try {
            ensureOpen();
            long effectiveCapacity = Math.min(qpackMaxTableCapacity, MAX_LOCAL_ENCODER_TABLE_CAPACITY);
            encoderTable.maxCapacity(effectiveCapacity);
            if (encoderTable.capacity() != effectiveCapacity) {
                encoderTable.capacity(effectiveCapacity);
                if (effectiveCapacity > 0) {
                    sendEncoderInstruction(encodeTableCapacityUpdate(effectiveCapacity));
                }
            }
        } catch (Http3ProtocolException e) {
            failure = e;
            throw e;
        } finally {
            encoderLock.unlock();
            if (failure != null) {
                instructionStreamFailed(failure);
            }
        }
    }

    /**
     * Register the sender used for local encoder-stream instructions.
     *
     * @param sender encoder instruction sender
     */
    public void encoderInstructionsSender(Http3QpackContext.InstructionSender sender) {
        Objects.requireNonNull(sender, "sender");
        Http3ProtocolException failure = null;
        encoderLock.lock();
        try {
            ensureOpen();
            if (encoderInstructionsSender != null) {
                throw new IllegalStateException("QPACK encoder instruction sender is already registered");
            }
            encoderInstructionsSender = sender;
            while (!pendingEncoderInstructions.isEmpty()) {
                byte[] instruction = pendingEncoderInstructions.getFirst();
                sendEncoderInstruction(instruction);
                pendingEncoderInstructions.removeFirst();
                pendingEncoderInstructionBytes -= instruction.length;
            }
        } catch (Http3ProtocolException e) {
            failure = e;
            throw e;
        } finally {
            encoderLock.unlock();
            if (failure != null) {
                instructionStreamFailed(failure);
            }
        }
    }

    /**
     * Register the sender used for local decoder-stream instructions.
     *
     * @param sender decoder instruction sender
     */
    public void decoderInstructionsSender(Http3QpackContext.InstructionSender sender) {
        Objects.requireNonNull(sender, "sender");
        Http3ProtocolException failure = null;
        decoderLock.lock();
        try {
            ensureOpen();
            if (decoderInstructionsSender != null) {
                throw new IllegalStateException("QPACK decoder instruction sender is already registered");
            }
            decoderInstructionsSender = sender;
            while (!pendingDecoderInstructions.isEmpty()) {
                byte[] instruction = pendingDecoderInstructions.getFirst();
                sendDecoderInstruction(instruction);
                pendingDecoderInstructions.removeFirst();
                pendingDecoderInstructionBytes -= instruction.length;
            }
        } catch (Http3ProtocolException e) {
            failure = e;
            throw e;
        } finally {
            decoderLock.unlock();
            if (failure != null) {
                instructionStreamFailed(failure);
            }
        }
    }

    /**
     * Encode a header set using the current QPACK connection state.
     *
     * @param streamId request or response stream id
     * @param headers  headers to encode
     * @return encoded field section
     */
    public byte[] encodeHeaders(long streamId, Iterable<Header> headers) {
        Http3ProtocolException failure = null;
        encoderLock.lock();
        try {
            ensureOpen();
            long base = encoderTable.insertCount();
            long referenceLimit = base == 0
                    || knownReceivedCount == 0
                    || unacknowledgedSectionCount >= MAX_UNACKNOWLEDGED_SECTIONS
                    ? -1
                    : Math.min(base - 1, knownReceivedCount - 1);

            List<FieldEncoding> encodings = new ArrayList<>();
            List<HeaderField> insertionCandidates = new ArrayList<>();
            long minReferenced = Long.MAX_VALUE;
            long maxReferenced = -1;

            for (Header header : headers) {
                String name = header.headerName().lowerCase();
                for (String value : header.allValues()) {
                    FieldEncoding encoding = chooseFieldEncoding(name, value, referenceLimit);
                    encodings.add(encoding);
                    if (encoding.kind() != Kind.INDEXED) {
                        insertionCandidates.add(new HeaderField(name, value));
                    }
                    if (!encoding.fromStaticTable() && encoding.index() >= 0) {
                        minReferenced = Math.min(minReferenced, encoding.index());
                        maxReferenced = Math.max(maxReferenced, encoding.index());
                    }
                }
            }

            if (unacknowledgedSections.isEmpty()) {
                long protectedMinAbsoluteIndex = maxReferenced < 0 ? Long.MAX_VALUE : minReferenced;
                for (HeaderField header : insertionCandidates) {
                    maybeInsert(header.name(), header.value(), protectedMinAbsoluteIndex);
                }
            }

            long requiredInsertCount = maxReferenced + 1;
            BufferData output = BufferData.growing(128);
            QpackCodec.writeFieldSectionPrefix(output, requiredInsertCount, base, encoderTable.maxEntries());
            for (FieldEncoding encoding : encodings) {
                switch (encoding.kind()) {
                case INDEXED -> QpackCodec.writeIndexedFieldLine(output,
                                                                 encoding.index(),
                                                                 encoding.fromStaticTable(),
                                                                 base);
                case NAME_REFERENCE -> QpackCodec.writeLiteralWithNameReference(output,
                                                                                encoding.index(),
                                                                                encoding.fromStaticTable(),
                                                                                base,
                                                                                encoding.value());
                case LITERAL -> QpackCodec.writeLiteralFieldLine(output, encoding.name(), encoding.value());
                default -> throw new IllegalStateException("Unexpected field encoding kind: " + encoding.kind().text());
                }
            }

            if (requiredInsertCount > 0) {
                unacknowledgedSections.computeIfAbsent(streamId, _ -> new ArrayDeque<>())
                        .add(requiredInsertCount);
                unacknowledgedSectionCount++;
            }
            return output.readBytes();
        } catch (Http3ProtocolException e) {
            failure = e;
            throw e;
        } finally {
            encoderLock.unlock();
            if (failure != null) {
                instructionStreamFailed(failure);
            }
        }
    }

    /**
     * Open the decoder-side state owned by one HTTP/3 request or push stream.
     *
     * @param streamId HTTP/3 stream id
     * @return decoder stream state
     */
    public DecoderStream openDecoderStream(long streamId) {
        decoderLock.lock();
        try {
            ensureOpen();
            boolean registered = localMaxTableCapacity > 0;
            if (registered && decoderStreams.containsKey(streamId)) {
                throw new IllegalStateException("QPACK decoder stream state already exists for HTTP/3 stream " + streamId);
            }
            DecoderStream stream = new DecoderStream(this, streamId, registered);
            if (registered) {
                decoderStreams.put(streamId, stream);
            }
            return stream;
        } finally {
            decoderLock.unlock();
        }
    }

    private FieldSection beginFieldSection(DecoderStream stream,
                                           BufferData buffer,
                                           long maxFieldSectionSize) {
        FieldSection section = null;
        Http3ProtocolException connectionFailure = null;
        decoderLock.lock();
        try {
            ensureOpen();
            stream.ensureOpen();
            if (stream.registered && decoderStreams.get(stream.streamId) != stream) {
                throw new IllegalStateException("QPACK decoder stream state is no longer registered for HTTP/3 stream "
                                                        + stream.streamId);
            }
            try {
                if (buffer.available() > encodedFieldSectionLimit) {
                    throw Http3ProtocolException.streamError(
                            Http3ErrorCode.MESSAGE_ERROR,
                            "Encoded QPACK field section exceeds the local encoded limit: "
                                    + buffer.available() + " > " + encodedFieldSectionLimit);
                }
                QpackCodec.FieldSectionPrefix prefix = QpackCodec.readFieldSectionPrefix(buffer,
                                                                                         decoderTable.insertCount(),
                                                                                         decoderTable.maxEntries());
                long decodedLimit = maxFieldSectionSize < 0
                        ? maxHeadersSize
                        : Math.min(maxFieldSectionSize, maxHeadersSize);
                section = new FieldSection(stream,
                                           prefix,
                                           buffer,
                                           QpackCodec.fieldSectionSizeTracker(decodedLimit));
                if (prefix.requiredInsertCount() > decoderTable.insertCount()) {
                    if (!stream.blockedSections.isEmpty()) {
                        throw Http3ProtocolException.connectionError(
                                Http3ErrorCode.QPACK_DECOMPRESSION_FAILED,
                                "Concurrent blocked QPACK field sections are not allowed on stream " + stream.streamId);
                    }
                    Deque<FieldSection> streamSections = blockedSections.get(stream.streamId);
                    if (streamSections == null) {
                        if (blockedSections.size() >= localBlockedStreams) {
                            throw Http3ProtocolException.connectionError(
                                    Http3ErrorCode.QPACK_DECOMPRESSION_FAILED,
                                    "Peer exceeded the advertised QPACK blocked-stream limit: " + localBlockedStreams);
                        }
                        streamSections = new ArrayDeque<>();
                        blockedSections.put(stream.streamId, streamSections);
                    }
                    streamSections.addLast(section);
                    stream.blockedSections.addLast(section);
                    section.completion = new CompletableFuture<>();
                } else {
                    connectionFailure = resolveFieldSection(section);
                }
            } catch (Http3ProtocolException e) {
                if (e.scope() != Http3ProtocolException.Scope.CONNECTION) {
                    throw e;
                }
                if (section == null) {
                    section = new FieldSection(stream, null, null, null);
                }
                connectionFailure = e;
                section.fail(e);
            } catch (IllegalArgumentException e) {
                section = new FieldSection(stream, null, null, null);
                connectionFailure = Http3ProtocolException.connectionError(
                        Http3ErrorCode.QPACK_DECOMPRESSION_FAILED,
                        "Malformed QPACK field section",
                        e);
                section.fail(connectionFailure);
            }
        } finally {
            decoderLock.unlock();
        }
        section = Objects.requireNonNull(section, "fieldSection");
        section.publishCompletion();
        if (connectionFailure != null) {
            failConnection(connectionFailure);
        }
        return section;
    }

    /**
     * Process bytes received on the remote encoder stream.
     *
     * @param bytes encoder-stream bytes
     */
    public void onEncoderStreamData(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        List<FieldSection> completedSections = null;
        CompletableFuture<Void> progress = null;
        Throwable failure = null;
        decoderLock.lock();
        try {
            ensureOpen();
            long initialInsertCount = decoderTable.insertCount();
            try {
                BufferData incoming = BufferData.create(bytes);
                while (incoming.available() > 0) {
                    byte[] chunk = new byte[Math.min(incoming.available(), MAX_QPACK_INPUT_CHUNK_SIZE)];
                    incoming.read(chunk);
                    pendingEncoderInput = append(pendingEncoderInput, chunk);
                    int consumed = 0;
                    while (consumed < pendingEncoderInput.length) {
                        BufferData buffer = BufferData.createReadOnly(pendingEncoderInput,
                                                                      consumed,
                                                                      pendingEncoderInput.length - consumed);
                        EncoderInstruction instruction = tryReadEncoderInstruction(buffer,
                                                                                    maxEncoderInstructionSize);
                        if (instruction == null) {
                            break;
                        }
                        consumed = pendingEncoderInput.length - buffer.available();
                        applyEncoderInstruction(instruction);
                    }
                    pendingEncoderInput = remaining(pendingEncoderInput, consumed);
                    if (pendingEncoderInput.length > maxEncoderInstructionSize) {
                        throw new IllegalArgumentException("Incomplete QPACK encoder instruction exceeds the local limit");
                    }
                }
                if (decoderTable.insertCount() > initialInsertCount && !blockedSections.isEmpty()) {
                    var streams = blockedSections.entrySet().iterator();
                    while (streams.hasNext()) {
                        var entry = streams.next();
                        Deque<FieldSection> sections = entry.getValue();
                        while (!sections.isEmpty()
                                && sections.getFirst().prefix.requiredInsertCount() <= decoderTable.insertCount()) {
                            FieldSection section = sections.removeFirst();
                            section.stream.blockedSections.removeFirstOccurrence(section);
                            Http3ProtocolException fieldSectionFailure = resolveFieldSection(section);
                            if (completedSections == null) {
                                completedSections = new ArrayList<>();
                            }
                            completedSections.add(section);
                            if (fieldSectionFailure != null) {
                                throw fieldSectionFailure;
                            }
                        }
                        if (sections.isEmpty()) {
                            streams.remove();
                        }
                    }
                }
            } catch (IllegalArgumentException e) {
                failure = Http3ProtocolException.connectionError(Http3ErrorCode.QPACK_ENCODER_STREAM_ERROR,
                                                                 "Malformed QPACK encoder stream instruction",
                                                                 e);
            } catch (Throwable t) {
                failure = t;
            }
            if (failure == null
                    && decoderTable.insertCount() > initialInsertCount
                    && !blockedSections.isEmpty()) {
                progress = decoderProgress;
                decoderProgress = new CompletableFuture<>();
            }
        } finally {
            decoderLock.unlock();
        }
        if (progress != null) {
            progress.complete(null);
        }
        if (completedSections != null) {
            completedSections.forEach(FieldSection::publishCompletion);
        }
        if (failure != null) {
            instructionStreamFailed(failure);
            throw uncheckedFailure("QPACK encoder stream failed", failure);
        }
    }

    /**
     * Process bytes received on the remote decoder stream.
     *
     * @param bytes decoder-stream bytes
     */
    public void onDecoderStreamData(byte[] bytes) {
        Throwable failure = null;
        encoderLock.lock();
        try {
            ensureOpen();
            try {
                BufferData incoming = BufferData.create(bytes);
                while (incoming.available() > 0) {
                    byte[] chunk = new byte[Math.min(incoming.available(), MAX_QPACK_INPUT_CHUNK_SIZE)];
                    incoming.read(chunk);
                    pendingDecoderInput = append(pendingDecoderInput, chunk);
                    int consumed = 0;
                    while (consumed < pendingDecoderInput.length) {
                        BufferData buffer = BufferData.createReadOnly(pendingDecoderInput,
                                                                      consumed,
                                                                      pendingDecoderInput.length - consumed);
                        DecoderInstruction instruction = tryReadDecoderInstruction(buffer);
                        if (instruction == null) {
                            break;
                        }
                        consumed = pendingDecoderInput.length - buffer.available();
                        applyDecoderInstruction(instruction);
                    }
                    pendingDecoderInput = remaining(pendingDecoderInput, consumed);
                    if (pendingDecoderInput.length > MAX_DECODER_INSTRUCTION_SIZE) {
                        throw new IllegalArgumentException("Incomplete QPACK decoder instruction exceeds the local limit");
                    }
                }
            } catch (IllegalArgumentException e) {
                failure = Http3ProtocolException.connectionError(Http3ErrorCode.QPACK_DECODER_STREAM_ERROR,
                                                                 "Malformed QPACK decoder stream instruction",
                                                                 e);
            } catch (Throwable t) {
                failure = t;
            }
        } finally {
            encoderLock.unlock();
        }
        if (failure != null) {
            instructionStreamFailed(failure);
            throw uncheckedFailure("QPACK decoder stream failed", failure);
        }
    }

    /**
     * Fail all connection-owned QPACK work because an instruction stream failed.
     *
     * @param cause instruction-stream failure
     */
    public void instructionStreamFailed(Throwable cause) {
        failConnection(Objects.requireNonNull(cause, "cause"));
    }

    /**
     * Close the connection-owned QPACK state.
     *
     * @param cause connection close cause
     */
    public void close(Throwable cause) {
        terminate(Objects.requireNonNull(cause, "cause"));
    }

    private void failConnection(Throwable failure) {
        if (terminate(failure)) {
            connectionFailureHandler.accept(failure);
        }
    }

    private boolean terminate(Throwable cause) {
        if (!terminationSelection.compareAndSet(null, cause)) {
            return false;
        }
        List<FieldSection> failedSections = new ArrayList<>();
        encoderLock.lock();
        decoderLock.lock();
        try {
            encoderTable.capacity(0);
            decoderTable.capacity(0);
            encoderInstructionsSender = null;
            decoderInstructionsSender = null;
            pendingEncoderInstructions.clear();
            pendingDecoderInstructions.clear();
            pendingEncoderInput = EMPTY_BYTES;
            pendingDecoderInput = EMPTY_BYTES;
            unacknowledgedSections.clear();
            unacknowledgedSectionCount = 0;
            pendingEncoderInstructionBytes = 0;
            pendingDecoderInstructionBytes = 0;
            blockedSections.values().forEach(sections -> sections.forEach(section -> {
                section.fail(cause);
                failedSections.add(section);
            }));
            blockedSections.clear();
            decoderStreams.values().forEach(stream -> {
                stream.blockedSections.clear();
                if (stream.state == DecoderStreamState.OPEN) {
                    stream.state = DecoderStreamState.FAILED;
                }
            });
            decoderStreams.clear();
        } finally {
            decoderLock.unlock();
            encoderLock.unlock();
        }
        failedSections.forEach(FieldSection::publishCompletion);
        return true;
    }

    private void ensureOpen() {
        Throwable cause = terminationSelection.get();
        if (cause != null) {
            throw new IllegalStateException("QPACK connection state is closed", cause);
        }
    }

    private Http3ProtocolException resolveFieldSection(FieldSection section) {
        try {
            List<Header> headers = new ArrayList<>();
            while (section.buffer.available() > 0) {
                int first = section.buffer.get(0) & 0xff;
                if ((first & 0x80) != 0) {
                    headers.add(decodeIndexedFieldLine(section));
                } else if ((first & 0x40) != 0) {
                    headers.add(decodeLiteralWithNameReference(section));
                } else if ((first & 0x20) != 0) {
                    headers.add(decodeLiteralWithLiteralName(section.buffer, section.sizeTracker));
                } else if ((first & 0x10) != 0) {
                    headers.add(decodeIndexedPostBaseFieldLine(section));
                } else {
                    headers.add(decodeLiteralWithPostBaseNameReference(section));
                }
            }

            if (section.prefix.requiredInsertCount() > 0) {
                sendDecoderInstruction(encodeSectionAcknowledgment(section.stream.streamId));
                decoderAcknowledgedInsertCount = Math.max(decoderAcknowledgedInsertCount,
                                                          section.prefix.requiredInsertCount());
            }
            section.decoded(headers);
            return null;
        } catch (IllegalArgumentException e) {
            Http3ProtocolException failure = Http3ProtocolException.connectionError(
                    Http3ErrorCode.QPACK_DECOMPRESSION_FAILED,
                    "Malformed QPACK field section",
                    e);
            section.fail(failure);
            return failure;
        } catch (Throwable t) {
            section.fail(t);
            var protocolFailure = Http3ProtocolException.find(t);
            if (protocolFailure.isPresent()
                    && protocolFailure.orElseThrow().scope() == Http3ProtocolException.Scope.CONNECTION) {
                return protocolFailure.orElseThrow();
            }
            if (section.prefix.requiredInsertCount() > 0) {
                section.stream.cancellationRequired = true;
            }
            return null;
        }
    }

    private void cancelDecoderStream(DecoderStream stream) {
        List<FieldSection> cancelledSections = new ArrayList<>();
        Throwable failure = null;
        decoderLock.lock();
        try {
            if (stream.state != DecoderStreamState.OPEN) {
                return;
            }
            if (terminationSelection.get() != null) {
                return;
            }
            stream.state = DecoderStreamState.CANCELLED;
            if (stream.registered) {
                decoderStreams.remove(stream.streamId, stream);
            }
            Deque<FieldSection> sections = blockedSections.remove(stream.streamId);
            if (sections != null) {
                sections.forEach(section -> {
                    section.cancel();
                    cancelledSections.add(section);
                });
            }
            stream.blockedSections.clear();
            if (localMaxTableCapacity > 0) {
                try {
                    sendDecoderInstruction(encodeStreamCancellation(stream.streamId));
                } catch (Throwable t) {
                    failure = t;
                    stream.state = DecoderStreamState.FAILED;
                    cancelledSections.forEach(section -> section.fail(t));
                }
            }
        } finally {
            decoderLock.unlock();
        }
        cancelledSections.forEach(FieldSection::publishCompletion);
        if (failure != null) {
            instructionStreamFailed(failure);
        }
    }

    private static byte[] append(byte[] current, byte[] additional) {
        if (current.length == 0) {
            return additional.clone();
        }
        byte[] bytes = new byte[current.length + additional.length];
        System.arraycopy(current, 0, bytes, 0, current.length);
        System.arraycopy(additional, 0, bytes, current.length, additional.length);
        return bytes;
    }

    private static byte[] remaining(byte[] source, int consumed) {
        if (consumed == 0) {
            return source;
        }
        if (consumed >= source.length) {
            return EMPTY_BYTES;
        }
        byte[] remaining = new byte[source.length - consumed];
        System.arraycopy(source, consumed, remaining, 0, remaining.length);
        return remaining;
    }

    private static EncoderInstruction tryReadEncoderInstruction(BufferData buffer, int maxInstructionSize) {
        if (buffer.available() == 0) {
            return null;
        }
        int first = buffer.get(0) & 0xff;
        EncoderInstruction instruction;
        if ((first & 0x80) != 0) {
            boolean fromStatic = (first & 0x40) != 0;
            Long nameIndex = tryReadPrefixedInteger(buffer, 6);
            if (nameIndex == null) {
                return null;
            }
            String value = tryReadString(buffer, 7, maxInstructionSize);
            if (value == null) {
                return null;
            }
            instruction = EncoderInstruction.nameReference(nameIndex, fromStatic, value);
        } else if ((first & 0x40) != 0) {
            String name = tryReadString(buffer, 5, maxInstructionSize);
            if (name == null) {
                return null;
            }
            String value = tryReadString(buffer, 7, maxInstructionSize);
            if (value == null) {
                return null;
            }
            instruction = EncoderInstruction.literal(name, value);
        } else if ((first & 0x20) != 0) {
            Long capacity = tryReadPrefixedInteger(buffer, 5);
            if (capacity == null) {
                return null;
            }
            instruction = EncoderInstruction.capacityUpdate(capacity);
        } else {
            Long index = tryReadPrefixedInteger(buffer, 5);
            if (index == null) {
                return null;
            }
            instruction = EncoderInstruction.duplicate(index);
        }
        return instruction;
    }

    private static DecoderInstruction tryReadDecoderInstruction(BufferData buffer) {
        if (buffer.available() == 0) {
            return null;
        }
        int first = buffer.get(0) & 0xff;
        DecoderInstruction instruction;
        if ((first & 0x80) != 0) {
            Long streamId = tryReadPrefixedInteger(buffer, 7);
            if (streamId == null) {
                return null;
            }
            instruction = DecoderInstruction.sectionAcknowledgment(streamId);
        } else if ((first & 0x40) != 0) {
            Long streamId = tryReadPrefixedInteger(buffer, 6);
            if (streamId == null) {
                return null;
            }
            instruction = DecoderInstruction.streamCancellation(streamId);
        } else {
            Long increment = tryReadPrefixedInteger(buffer, 6);
            if (increment == null) {
                return null;
            }
            instruction = DecoderInstruction.insertCountIncrement(increment);
        }
        return instruction;
    }

    private static Long tryReadPrefixedInteger(BufferData buffer, int prefixBits) {
        if (buffer.available() == 0) {
            return null;
        }
        int mask = (1 << prefixBits) - 1;
        int first = buffer.read() & 0xff;
        long value = first & mask;
        if (value < mask) {
            return value;
        }
        int shift = 0;
        int continuationBytes = 0;
        while (true) {
            if (buffer.available() == 0) {
                return null;
            }
            int next = buffer.read() & 0xff;
            continuationBytes++;
            long increment = next & 0x7f;
            if (shift >= Long.SIZE - 1 || increment > (Long.MAX_VALUE - value) >> shift) {
                throw new IllegalArgumentException("QPACK prefixed integer exceeds the supported range");
            }
            value += increment << shift;
            if ((next & 0x80) == 0) {
                return value;
            }
            if (continuationBytes >= 10) {
                throw new IllegalArgumentException("QPACK prefixed integer exceeds the supported encoded length");
            }
            shift += 7;
        }
    }

    private static String tryReadString(BufferData buffer, int prefixBits, int maxEncodedLength) {
        if (buffer.available() == 0) {
            return null;
        }
        int first = buffer.get(0) & 0xff;
        boolean huffman = (first & (1 << prefixBits)) != 0;
        Long length = tryReadPrefixedInteger(buffer, prefixBits);
        if (length == null) {
            return null;
        }
        if (length > maxEncodedLength) {
            throw new IllegalArgumentException("QPACK string exceeds the local encoded-length limit: "
                                                       + length + " > " + maxEncodedLength);
        }
        if (length > buffer.available()) {
            return null;
        }

        byte[] bytes = new byte[Math.toIntExact(length)];
        buffer.read(bytes);
        return QpackCodec.decodeStringBytes(bytes, huffman);
    }

    private static byte[] encodeTableCapacityUpdate(long capacity) {
        BufferData output = BufferData.growing(16);
        QpackCodec.writePrefixedInteger(output, 5, 0b0010_0000, capacity);
        return output.readBytes();
    }

    private static byte[] encodeLiteralInsertion(String name, String value) {
        BufferData output = BufferData.growing(name.length() + value.length() + 8);
        QpackCodec.writeString(output, 5, 0b0100_0000, name);
        QpackCodec.writeString(output, 7, 0, value);
        return output.readBytes();
    }

    private static byte[] encodeSectionAcknowledgment(long streamId) {
        BufferData output = BufferData.growing(16);
        QpackCodec.writePrefixedInteger(output, 7, 0b1000_0000, streamId);
        return output.readBytes();
    }

    private static byte[] encodeStreamCancellation(long streamId) {
        BufferData output = BufferData.growing(16);
        QpackCodec.writePrefixedInteger(output, 6, 0b0100_0000, streamId);
        return output.readBytes();
    }

    private static byte[] encodeInsertCountIncrement(long increment) {
        BufferData output = BufferData.growing(16);
        QpackCodec.writePrefixedInteger(output, 6, 0, increment);
        return output.readBytes();
    }

    private static Http3ProtocolException qpackEncoderStreamError(String message) {
        return Http3ProtocolException.connectionError(Http3ErrorCode.QPACK_ENCODER_STREAM_ERROR, message);
    }

    private static Http3ProtocolException qpackEncoderStreamError(String message, Throwable cause) {
        return Http3ProtocolException.connectionError(Http3ErrorCode.QPACK_ENCODER_STREAM_ERROR, message, cause);
    }

    private static Http3ProtocolException qpackDecoderStreamError(String message) {
        return Http3ProtocolException.connectionError(Http3ErrorCode.QPACK_DECODER_STREAM_ERROR, message);
    }

    private Header decodeIndexedFieldLine(FieldSection section) {
        int first = section.buffer.get(0) & 0xff;
        boolean fromStatic = (first & 0x40) != 0;
        long index = QpackCodec.readPrefixedInteger(section.buffer, 6);
        HeaderField field;
        if (fromStatic) {
            field = QpackStaticTable.get(index);
        } else {
            long absoluteIndex = section.prefix.base() - 1 - index;
            section.dynamicReference(absoluteIndex);
            field = decoderTable.get(absoluteIndex);
        }
        section.sizeTracker.beginFieldLine();
        section.sizeTracker.consume(field.name().length());
        section.sizeTracker.consume(field.value().length());
        return HeaderValues.create(HeaderNames.createFromLowercase(field.name()), field.value());
    }

    private Header decodeIndexedPostBaseFieldLine(FieldSection section) {
        long index = QpackCodec.readPrefixedInteger(section.buffer, 4);
        long absoluteIndex = section.prefix.base() + index;
        section.dynamicReference(absoluteIndex);
        HeaderField field = decoderTable.get(absoluteIndex);
        section.sizeTracker.beginFieldLine();
        section.sizeTracker.consume(field.name().length());
        section.sizeTracker.consume(field.value().length());
        return HeaderValues.create(HeaderNames.createFromLowercase(field.name()), field.value());
    }

    private Header decodeLiteralWithNameReference(FieldSection section) {
        int first = section.buffer.get(0) & 0xff;
        boolean fromStatic = (first & 0x10) != 0;
        long index = QpackCodec.readPrefixedInteger(section.buffer, 4);
        String name;
        if (fromStatic) {
            name = QpackStaticTable.get(index).name();
        } else {
            long absoluteIndex = section.prefix.base() - 1 - index;
            section.dynamicReference(absoluteIndex);
            name = decoderTable.get(absoluteIndex).name();
        }
        section.sizeTracker.beginFieldLine();
        section.sizeTracker.consume(name.length());
        String value = QpackCodec.readString(section.buffer, 7, section.sizeTracker);
        return HeaderValues.create(HeaderNames.createFromLowercase(name), value);
    }

    private Header decodeLiteralWithPostBaseNameReference(FieldSection section) {
        long index = QpackCodec.readPrefixedInteger(section.buffer, 3);
        long absoluteIndex = section.prefix.base() + index;
        section.dynamicReference(absoluteIndex);
        String name = decoderTable.get(absoluteIndex).name();
        section.sizeTracker.beginFieldLine();
        section.sizeTracker.consume(name.length());
        String value = QpackCodec.readString(section.buffer, 7, section.sizeTracker);
        return HeaderValues.create(HeaderNames.createFromLowercase(name), value);
    }

    private Header decodeLiteralWithLiteralName(BufferData buffer,
                                                QpackCodec.FieldSectionSizeTracker sizeTracker) {
        sizeTracker.beginFieldLine();
        String name = QpackCodec.readString(buffer, 3, sizeTracker);
        String value = QpackCodec.readString(buffer, 7, sizeTracker);
        return HeaderValues.create(HeaderNames.createFromLowercase(name), value);
    }

    private void maybeInsert(String name,
                             String value,
                             long protectedMinAbsoluteIndex) {
        if (encoderTable.capacity() == 0) {
            return;
        }
        if (encoderTable.findExact(name, value, Long.MAX_VALUE) >= 0) {
            return;
        }
        if (QpackDynamicTable.headerSize(name, value) > encoderTable.capacity()) {
            return;
        }

        byte[] instruction = encodeLiteralInsertion(name, value);
        if (encoderInstructionsSender == null
                && pendingEncoderInstructionBytes > MAX_PENDING_INSTRUCTION_BYTES - instruction.length) {
            return;
        }
        long inserted = encoderTable.insert(name, value, protectedMinAbsoluteIndex);
        if (inserted >= 0) {
            sendEncoderInstruction(instruction);
        }
    }

    private FieldEncoding chooseFieldEncoding(String name,
                                              String value,
                                              long referenceLimit) {
        QpackStaticTable.HeaderIndices staticIndices = QpackStaticTable.indices(name);
        if (staticIndices != null) {
            int staticExact = staticIndices.exactIndex(value);
            if (staticExact >= 0) {
                return STATIC_INDEXED_ENCODINGS[staticExact];
            }
        }

        if (referenceLimit >= 0) {
            long dynamicExact = encoderTable.findExact(name, value, referenceLimit);
            if (dynamicExact >= 0) {
                return FieldEncoding.indexed(dynamicExact, false);
            }
        }

        if (staticIndices != null) {
            return FieldEncoding.nameReference(staticIndices.nameIndex(), true, value);
        }

        if (referenceLimit >= 0) {
            long dynamicName = encoderTable.findName(name, referenceLimit);
            if (dynamicName >= 0) {
                return FieldEncoding.nameReference(dynamicName, false, value);
            }
        }

        return FieldEncoding.literal(name, value);
    }

    private static FieldEncoding[] staticIndexedEncodings() {
        FieldEncoding[] encodings = new FieldEncoding[QpackStaticTable.size()];
        for (int i = 0; i < encodings.length; i++) {
            encodings[i] = FieldEncoding.indexed(i, true);
        }
        return encodings;
    }

    private void applyEncoderInstruction(EncoderInstruction instruction) {
        switch (instruction.kind()) {
        case CAPACITY_UPDATE -> {
            try {
                decoderTable.capacity(instruction.index());
            } catch (IllegalArgumentException e) {
                throw qpackEncoderStreamError("QPACK capacity update exceeds the advertised limit", e);
            }
        }
        case INSERT_LITERAL -> {
            ensureDynamicTableEnabled();
            long inserted = decoderTable.insert(instruction.name(), instruction.value());
            if (inserted < 0) {
                throw qpackEncoderStreamError("QPACK decoder table cannot insert header: " + instruction.name());
            }
            acknowledgeTableInsertions();
        }
        case INSERT_NAME_REFERENCE -> {
            ensureDynamicTableEnabled();
            long inserted;
            try {
                inserted = decoderTable.insert(instruction.index(),
                                               instruction.fromStaticTable(),
                                               instruction.value());
            } catch (IllegalArgumentException e) {
                throw qpackEncoderStreamError("Invalid QPACK indexed insertion instruction", e);
            }
            if (inserted < 0) {
                throw qpackEncoderStreamError("QPACK decoder table cannot insert indexed header: "
                                                      + instruction.index());
            }
            acknowledgeTableInsertions();
        }
        case DUPLICATE -> {
            ensureDynamicTableEnabled();
            long inserted;
            try {
                inserted = decoderTable.duplicate(instruction.index());
            } catch (IllegalArgumentException e) {
                throw qpackEncoderStreamError("Invalid QPACK duplicate instruction", e);
            }
            if (inserted < 0) {
                throw qpackEncoderStreamError("QPACK decoder table cannot duplicate header: " + instruction.index());
            }
            acknowledgeTableInsertions();
        }
        default -> throw new IllegalStateException("Unexpected encoder instruction kind: " + instruction.kind().text());
        }
    }

    private void applyDecoderInstruction(DecoderInstruction instruction) {
        switch (instruction.kind()) {
        case SECTION_ACKNOWLEDGMENT -> {
            Deque<Long> sections = unacknowledgedSections.get(instruction.value());
            Long requiredInsertCount = sections == null ? null : sections.pollFirst();
            if (requiredInsertCount == null) {
                throw qpackDecoderStreamError("Unexpected QPACK section acknowledgment for stream "
                                                      + instruction.value());
            }
            if (sections.isEmpty()) {
                unacknowledgedSections.remove(instruction.value());
            }
            unacknowledgedSectionCount--;
            knownReceivedCount = Math.max(knownReceivedCount, requiredInsertCount);
        }
        case STREAM_CANCELLATION -> {
            Deque<Long> cancelled = unacknowledgedSections.remove(instruction.value());
            if (cancelled != null) {
                unacknowledgedSectionCount -= cancelled.size();
            }
        }
        case INSERT_COUNT_INCREMENT -> {
            long increment = instruction.value();
            if (increment <= 0 || knownReceivedCount > encoderTable.insertCount() - increment) {
                throw qpackDecoderStreamError("Invalid QPACK insert count increment: " + increment);
            }
            knownReceivedCount += increment;
        }
        default -> throw new IllegalStateException("Unexpected decoder instruction kind: " + instruction.kind().text());
        }
    }

    private void ensureDynamicTableEnabled() {
        if (decoderTable.maxCapacity() == 0) {
            throw qpackEncoderStreamError("Unexpected QPACK encoder instruction while dynamic table is disabled");
        }
    }

    private void acknowledgeTableInsertions() {
        long insertCount = decoderTable.insertCount();
        long increment = insertCount - decoderAcknowledgedInsertCount;
        if (increment > 0) {
            sendDecoderInstruction(encodeInsertCountIncrement(increment));
            decoderAcknowledgedInsertCount = insertCount;
        }
    }

    private void sendEncoderInstruction(byte[] bytes) {
        if (encoderInstructionsSender == null) {
            if (pendingEncoderInstructionBytes > MAX_PENDING_INSTRUCTION_BYTES - bytes.length) {
                throw Http3ProtocolException.connectionError(Http3ErrorCode.EXCESSIVE_LOAD,
                                                             "Pending QPACK encoder instructions exceed the local limit");
            }
            pendingEncoderInstructions.add(bytes);
            pendingEncoderInstructionBytes += bytes.length;
            return;
        }
        try {
            encoderInstructionsSender.send(bytes);
        } catch (RuntimeException e) {
            throw Http3ProtocolException.connectionError(Http3ErrorCode.CLOSED_CRITICAL_STREAM,
                                                         "Local QPACK encoder stream failed",
                                                         e);
        }
    }

    private void sendDecoderInstruction(byte[] bytes) {
        if (decoderInstructionsSender == null) {
            if (pendingDecoderInstructionBytes > MAX_PENDING_INSTRUCTION_BYTES - bytes.length) {
                throw Http3ProtocolException.connectionError(Http3ErrorCode.EXCESSIVE_LOAD,
                                                             "Pending QPACK decoder instructions exceed the local limit");
            }
            pendingDecoderInstructions.add(bytes);
            pendingDecoderInstructionBytes += bytes.length;
            return;
        }
        try {
            decoderInstructionsSender.send(bytes);
        } catch (RuntimeException e) {
            throw Http3ProtocolException.connectionError(Http3ErrorCode.CLOSED_CRITICAL_STREAM,
                                                         "Local QPACK decoder stream failed",
                                                         e);
        }
    }

    private static RuntimeException uncheckedFailure(String message, Throwable failure) {
        Objects.requireNonNull(failure, "failure");
        if (failure instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure instanceof IOException ioException) {
            return new UncheckedIOException(message, ioException);
        }
        return new IllegalStateException(message, failure);
    }

    /**
     * Decoder-side state owned by one HTTP/3 request or push stream.
     */
    @Api.Internal
    public static final class DecoderStream {
        private final QpackConnectionState owner;
        private final long streamId;
        private final boolean registered;
        private final Deque<FieldSection> blockedSections = new ArrayDeque<>();
        private DecoderStreamState state = DecoderStreamState.OPEN;
        private boolean cancellationRequired;

        private DecoderStream(QpackConnectionState owner, long streamId, boolean registered) {
            this.owner = owner;
            this.streamId = streamId;
            this.registered = registered;
        }

        /**
         * Decode one field section, waiting for encoder-stream progress when necessary.
         *
         * @param buffer encoded field section
         * @param maxFieldSectionSize caller-specific decoded size limit, or a negative value to use the connection's
         *                            common header limit; non-negative values are clamped to the common limit
         * @return decoded header lines in wire order
         */
        public List<Header> decodeHeaderLines(BufferData buffer, long maxFieldSectionSize) {
            return owner.beginFieldSection(this,
                                           Objects.requireNonNull(buffer, "buffer"),
                                           maxFieldSectionSize)
                    .await();
        }

        /**
         * Decode one field section, waiting up to the configured read timeout for encoder-stream progress.
         *
         * @param buffer encoded field section
         * @param maxFieldSectionSize caller-specific decoded size limit, or a negative value to use the connection's
         *                            common header limit; non-negative values are clamped to the common limit
         * @param readTimeout maximum time to wait for encoder-stream progress
         * @param readTimeoutActivation completion that activates the message read timeout
         * @return decoded header lines in wire order
         */
        public List<Header> decodeHeaderLines(BufferData buffer,
                                              long maxFieldSectionSize,
                                              Duration readTimeout,
                                              CompletableFuture<Void> readTimeoutActivation) {
            return owner.beginFieldSection(this,
                                           Objects.requireNonNull(buffer, "buffer"),
                                           maxFieldSectionSize)
                    .await(Objects.requireNonNull(readTimeout, "readTimeout"),
                           Objects.requireNonNull(readTimeoutActivation, "readTimeoutActivation"));
        }

        /**
         * Mark peer field-section processing complete for this HTTP/3 stream.
         */
        public void complete() {
            boolean cancel;
            owner.decoderLock.lock();
            try {
                if (state != DecoderStreamState.OPEN) {
                    return;
                }
                cancel = cancellationRequired || !blockedSections.isEmpty();
                if (!cancel) {
                    state = DecoderStreamState.COMPLETED;
                    if (registered) {
                        owner.decoderStreams.remove(streamId, this);
                    }
                }
            } finally {
                owner.decoderLock.unlock();
            }
            if (cancel) {
                owner.cancelDecoderStream(this);
            }
        }

        /**
         * Abandon peer field-section processing for this HTTP/3 stream.
         */
        public void cancel() {
            owner.cancelDecoderStream(this);
        }

        private void ensureOpen() {
            if (state != DecoderStreamState.OPEN) {
                throw new IllegalStateException("QPACK decoder state is not open for HTTP/3 stream " + streamId
                                                        + ": " + state);
            }
        }
    }

    private static final class FieldSection {
        private final DecoderStream stream;
        private final QpackCodec.FieldSectionPrefix prefix;
        private final BufferData buffer;
        private final QpackCodec.FieldSectionSizeTracker sizeTracker;
        private CompletableFuture<List<Header>> completion;

        private volatile FieldSectionState state = FieldSectionState.BLOCKED;
        private List<Header> headers;
        private Throwable failure;

        private FieldSection(DecoderStream stream,
                             QpackCodec.FieldSectionPrefix prefix,
                             BufferData buffer,
                             QpackCodec.FieldSectionSizeTracker sizeTracker) {
            this.stream = stream;
            this.prefix = prefix;
            this.buffer = buffer;
            this.sizeTracker = sizeTracker;
        }

        private List<Header> await() {
            return await(Optional.empty(), CompletableFuture.completedFuture(null));
        }

        private List<Header> await(Duration readTimeout, CompletableFuture<Void> readTimeoutActivation) {
            return await(Optional.of(readTimeout), readTimeoutActivation);
        }

        private List<Header> await(Optional<Duration> readTimeout,
                                   CompletableFuture<Void> readTimeoutActivation) {
            if (state == FieldSectionState.DECODED) {
                return headers;
            }
            if (state == FieldSectionState.CANCELLED || state == FieldSectionState.FAILED) {
                throw uncheckedFailure("QPACK field-section decoding failed", failure);
            }
            CompletableFuture<List<Header>> blockedCompletion = Objects.requireNonNull(
                    completion,
                    "blocked field-section completion");
            try {
                if (readTimeout.isPresent()) {
                    if (!readTimeoutActivation.isDone()) {
                        CompletableFuture.anyOf(blockedCompletion, readTimeoutActivation).get();
                        if (blockedCompletion.isDone()) {
                            return blockedCompletion.get();
                        }
                    }
                    long timeoutNanos = readTimeout.orElseThrow().toNanos();
                    for (;;) {
                        CompletableFuture<Void> progress;
                        stream.owner.decoderLock.lock();
                        try {
                            progress = stream.owner.decoderProgress;
                        } finally {
                            stream.owner.decoderLock.unlock();
                        }
                        CompletableFuture.anyOf(blockedCompletion, progress).get(timeoutNanos, TimeUnit.NANOSECONDS);
                        if (blockedCompletion.isDone()) {
                            return blockedCompletion.get();
                        }
                    }
                }
                return blockedCompletion.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                stream.cancel();
                throw new IllegalStateException("Interrupted while waiting for QPACK dynamic entries", e);
            } catch (ExecutionException e) {
                throw uncheckedFailure("QPACK field-section decoding failed", e.getCause());
            } catch (TimeoutException e) {
                Duration timeoutDuration = readTimeout.orElseThrow();
                Http3ReadTimeoutException timeout = new Http3ReadTimeoutException(
                        "No QPACK progress received for HTTP/3 stream " + stream.streamId
                                + " within the timeout " + timeoutDuration,
                        e);
                try {
                    stream.cancel();
                } catch (RuntimeException cleanupFailure) {
                    timeout.addSuppressed(cleanupFailure);
                }
                throw new UncheckedIOException(timeout);
            }
        }

        private void decoded(List<Header> headers) {
            if (state == FieldSectionState.BLOCKED) {
                this.headers = headers;
                state = FieldSectionState.DECODED;
            }
        }

        private void dynamicReference(long absoluteIndex) {
            if (absoluteIndex < 0 || absoluteIndex >= prefix.requiredInsertCount()) {
                throw new IllegalArgumentException("QPACK dynamic reference " + absoluteIndex
                                                           + " is outside Required Insert Count "
                                                           + prefix.requiredInsertCount());
            }
        }

        private void cancel() {
            if (state == FieldSectionState.BLOCKED) {
                failure = new CancellationException("QPACK field-section decoding was cancelled for HTTP/3 stream "
                                                            + stream.streamId);
                state = FieldSectionState.CANCELLED;
            }
        }

        private void fail(Throwable cause) {
            if (state == FieldSectionState.BLOCKED || state == FieldSectionState.CANCELLED) {
                failure = cause;
                state = FieldSectionState.FAILED;
            }
        }

        private void publishCompletion() {
            CompletableFuture<List<Header>> blockedCompletion = completion;
            if (blockedCompletion == null) {
                return;
            }
            switch (state) {
            case BLOCKED -> {
            }
            case DECODED -> blockedCompletion.complete(headers);
            case CANCELLED, FAILED -> blockedCompletion.completeExceptionally(failure);
            default -> throw new IllegalStateException("Unknown QPACK field-section state: " + state);
            }
        }
    }

    private enum DecoderStreamState {
        OPEN,
        COMPLETED,
        CANCELLED,
        FAILED
    }

    private enum FieldSectionState {
        BLOCKED,
        DECODED,
        CANCELLED,
        FAILED
    }

    private enum Kind {
        INDEXED,
        NAME_REFERENCE,
        LITERAL;

        private String text() {
            return name();
        }
    }

    private enum EncoderKind {
        CAPACITY_UPDATE,
        INSERT_LITERAL,
        INSERT_NAME_REFERENCE,
        DUPLICATE;

        private String text() {
            return name();
        }
    }

    private enum DecoderKind {
        SECTION_ACKNOWLEDGMENT,
        STREAM_CANCELLATION,
        INSERT_COUNT_INCREMENT;

        private String text() {
            return name();
        }
    }

    private record FieldEncoding(Kind kind,
                                 long index,
                                 boolean fromStaticTable,
                                 String name,
                                 String value) {
        static FieldEncoding indexed(long index, boolean fromStaticTable) {
            return new FieldEncoding(Kind.INDEXED, index, fromStaticTable, null, null);
        }

        static FieldEncoding nameReference(long index, boolean fromStaticTable, String value) {
            return new FieldEncoding(Kind.NAME_REFERENCE, index, fromStaticTable, null, value);
        }

        static FieldEncoding literal(String name, String value) {
            return new FieldEncoding(Kind.LITERAL, -1, false, name, value);
        }
    }

    private record EncoderInstruction(EncoderKind kind,
                                      long index,
                                      boolean fromStaticTable,
                                      String name,
                                      String value) {
        static EncoderInstruction capacityUpdate(long capacity) {
            return new EncoderInstruction(EncoderKind.CAPACITY_UPDATE, capacity, false, null, null);
        }

        static EncoderInstruction literal(String name, String value) {
            return new EncoderInstruction(EncoderKind.INSERT_LITERAL, -1, false, name, value);
        }

        static EncoderInstruction nameReference(long index, boolean fromStaticTable, String value) {
            return new EncoderInstruction(EncoderKind.INSERT_NAME_REFERENCE, index, fromStaticTable, null, value);
        }

        static EncoderInstruction duplicate(long index) {
            return new EncoderInstruction(EncoderKind.DUPLICATE, index, false, null, null);
        }
    }

    private record DecoderInstruction(DecoderKind kind, long value) {
        static DecoderInstruction sectionAcknowledgment(long streamId) {
            return new DecoderInstruction(DecoderKind.SECTION_ACKNOWLEDGMENT, streamId);
        }

        static DecoderInstruction streamCancellation(long streamId) {
            return new DecoderInstruction(DecoderKind.STREAM_CANCELLATION, streamId);
        }

        static DecoderInstruction insertCountIncrement(long increment) {
            return new DecoderInstruction(DecoderKind.INSERT_COUNT_INCREMENT, increment);
        }
    }
}
