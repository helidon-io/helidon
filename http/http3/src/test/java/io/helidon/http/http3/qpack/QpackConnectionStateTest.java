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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.HuffmanCodec;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.http3.Http3ErrorCode;
import io.helidon.http.http3.Http3ProtocolException;
import io.helidon.http.http3.Http3QpackContext;
import io.helidon.http.http3.Http3ReadTimeoutException;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QpackConnectionStateTest {
    private static final long MAX_TABLE_CAPACITY = 128;
    private static final long MAX_TABLE_ENTRIES = MAX_TABLE_CAPACITY / 32;

    @Test
    void rejectsNullInstructionSendersBeforeBinding() {
        QpackConnectionState state = qpackState(MAX_TABLE_CAPACITY, 1);
        state.peerSettings(MAX_TABLE_CAPACITY, 1);

        assertThrows(NullPointerException.class, () -> state.encoderInstructionsSender(null));
        List<byte[]> encoderInstructions = new ArrayList<>();
        state.encoderInstructionsSender(encoderInstructions::add);

        assertThat(encoderInstructions, hasSize(1));
        assertThrows(NullPointerException.class, () -> state.decoderInstructionsSender(null));
        state.decoderInstructionsSender(_ -> {
        });
    }

    @Test
    void resumesBlockedSectionOnlyAfterCompleteInsertion() throws Exception {
        QpackConnectionState state = qpackState(MAX_TABLE_CAPACITY, 1);
        List<byte[]> decoderInstructions = new ArrayList<>();
        state.decoderInstructionsSender(decoderInstructions::add);
        QpackConnectionState.DecoderStream stream = state.openDecoderStream(0);
        DecodeAttempt section = DecodeAttempt.start(stream, indexedFieldSection(1, 1, 0), -1);
        section.awaitBlocked();

        assertThat(section.isDone(), is(false));

        state.onEncoderStreamData(capacityUpdate(MAX_TABLE_CAPACITY));
        assertThat(section.isDone(), is(false));

        String value = "r\u0080\u00e9\u00ff";
        byte[] insertion = literalInsertion("x-blocked", value);
        for (int index = 0; index < insertion.length - 1; index++) {
            state.onEncoderStreamData(new byte[] {insertion[index]});
            assertThat("partial insertion ending at byte " + index, section.isDone(), is(false));
        }

        state.onEncoderStreamData(new byte[] {insertion[insertion.length - 1]});

        List<Header> headers = section.join();
        assertThat(headers, hasSize(1));
        assertThat(headers.getFirst().headerName(), equalTo(HeaderNames.create("x-blocked")));
        assertThat(headers.getFirst().get(), equalTo(value));
        assertThat(decoderInstructions, hasSize(2));
        assertThat(decoderInstructions.get(0), equalTo(insertCountIncrement(1)));
        assertThat(decoderInstructions.get(1), equalTo(sectionAcknowledgment(0)));
    }

    @Test
    void irrelevantEncoderInputDoesNotExtendBlockedSectionTimeout() throws Exception {
        Duration readTimeout = Duration.ofSeconds(8);
        QpackConnectionState capacityState = qpackState(MAX_TABLE_CAPACITY, 1);
        capacityState.decoderInstructionsSender(_ -> {
        });
        capacityState.onEncoderStreamData(capacityUpdate(MAX_TABLE_CAPACITY));
        DecodeAttempt capacitySection = DecodeAttempt.start(capacityState.openDecoderStream(0),
                                                            indexedFieldSection(1, 1, 0),
                                                            -1,
                                                            readTimeout);
        capacitySection.awaitTimedWait();

        QpackConnectionState incompleteInsertionState = qpackState(MAX_TABLE_CAPACITY, 1);
        incompleteInsertionState.decoderInstructionsSender(_ -> {
        });
        incompleteInsertionState.onEncoderStreamData(capacityUpdate(MAX_TABLE_CAPACITY));
        DecodeAttempt incompleteInsertionSection = DecodeAttempt.start(incompleteInsertionState.openDecoderStream(0),
                                                                       indexedFieldSection(1, 1, 0),
                                                                       -1,
                                                                       readTimeout);
        incompleteInsertionSection.awaitTimedWait();

        Thread.sleep(4_000);
        assertThat("capacity update section should still be waiting", capacitySection.isDone(), is(false));
        assertThat("incomplete insertion section should still be waiting",
                   incompleteInsertionSection.isDone(),
                   is(false));

        capacityState.onEncoderStreamData(capacityUpdate(MAX_TABLE_CAPACITY));
        byte[] insertion = literalInsertion("x-incomplete", "value");
        incompleteInsertionState.onEncoderStreamData(Arrays.copyOf(insertion, insertion.length - 1));

        // The original deadline has four seconds left, while an incorrect restart would leave eight seconds.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(6);
        ExecutionException capacityFailure = assertThrows(ExecutionException.class,
                                                          () -> capacitySection.getBefore(deadline));
        ExecutionException incompleteInsertionFailure = assertThrows(ExecutionException.class,
                                                                     () -> incompleteInsertionSection.getBefore(deadline));
        assertReadTimeout("capacity update", capacityFailure);
        assertReadTimeout("incomplete insertion", incompleteInsertionFailure);
    }

    @Test
    void completeInsertionProgressRestartsBlockedSectionTimeout() throws Exception {
        QpackConnectionState state = qpackState(MAX_TABLE_CAPACITY, 1);
        state.decoderInstructionsSender(_ -> {
        });
        state.onEncoderStreamData(capacityUpdate(MAX_TABLE_CAPACITY));
        DecodeAttempt section = DecodeAttempt.start(state.openDecoderStream(0),
                                                    indexedFieldSection(4, 4, 3),
                                                    -1,
                                                    Duration.ofSeconds(3));
        section.awaitTimedWait();

        Thread.sleep(800);
        state.onEncoderStreamData(literalInsertion("x-one", "value"));
        Thread.sleep(800);
        state.onEncoderStreamData(literalInsertion("x-two", "value"));
        Thread.sleep(800);
        state.onEncoderStreamData(literalInsertion("x-three", "value"));
        Thread.sleep(800);
        state.onEncoderStreamData(literalInsertion("x-four", "ready"));

        List<Header> headers = section.join();
        assertThat(headers, hasSize(1));
        assertThat(headers.getFirst().headerName(), equalTo(HeaderNames.create("x-four")));
        assertThat(headers.getFirst().get(), equalTo("ready"));
    }

    @Test
    void cancelsBlockedSectionExactlyOnceWithoutLaterAcknowledgment() throws Exception {
        QpackConnectionState state = qpackState(MAX_TABLE_CAPACITY, 1);
        List<byte[]> decoderInstructions = new ArrayList<>();
        state.decoderInstructionsSender(decoderInstructions::add);
        QpackConnectionState.DecoderStream stream = state.openDecoderStream(0);
        DecodeAttempt section = DecodeAttempt.start(stream, indexedFieldSection(1, 1, 0), -1);
        section.awaitBlocked();

        stream.cancel();
        stream.cancel();

        assertThrows(CancellationException.class, section::join);
        assertThat(decoderInstructions, hasSize(1));
        assertThat(decoderInstructions.getFirst(), equalTo(streamCancellation(0)));

        state.onEncoderStreamData(capacityUpdate(MAX_TABLE_CAPACITY));
        state.onEncoderStreamData(literalInsertion("x-cancelled", "late"));

        assertThat(decoderInstructions, hasSize(2));
        assertThat(decoderInstructions.get(1), equalTo(insertCountIncrement(1)));
        assertThat(decoderInstructions.stream()
                           .filter(bytes -> Arrays.equals(bytes, sectionAcknowledgment(0)))
                           .count(),
                   equalTo(0L));
    }

    @Test
    void closesBlockedSectionWithoutDecoderOutput() throws Exception {
        QpackConnectionState state = qpackState(MAX_TABLE_CAPACITY, 1);
        List<byte[]> decoderInstructions = new ArrayList<>();
        state.decoderInstructionsSender(decoderInstructions::add);
        QpackConnectionState.DecoderStream stream = state.openDecoderStream(0);
        DecodeAttempt section = DecodeAttempt.start(stream, indexedFieldSection(1, 1, 0), -1);
        section.awaitBlocked();
        IllegalStateException closeCause = new IllegalStateException("connection closed");

        state.close(closeCause);
        state.close(new IllegalStateException("duplicate close"));
        stream.cancel();

        CompletionException failure = assertThrows(CompletionException.class, section::join);
        assertThat(failure.getCause(), sameInstance(closeCause));
        assertThat(decoderInstructions, empty());
        assertThrows(IllegalStateException.class,
                     () -> state.onEncoderStreamData(capacityUpdate(MAX_TABLE_CAPACITY)));
        assertThat(decoderInstructions, empty());
    }

    @Test
    void interruptedBlockedDecodeRestoresInterruptAndFailsUnchecked() throws Exception {
        QpackConnectionState state = qpackState(MAX_TABLE_CAPACITY, 1);
        List<byte[]> decoderInstructions = new ArrayList<>();
        state.decoderInstructionsSender(decoderInstructions::add);
        DecodeAttempt section = DecodeAttempt.start(state.openDecoderStream(0),
                                                    indexedFieldSection(1, 1, 0),
                                                    -1);
        section.awaitBlocked();

        section.thread.interrupt();
        section.thread.join(5_000);

        CompletionException failure = assertThrows(CompletionException.class, section::join);
        assertThat(failure.getCause().getClass(), equalTo(IllegalStateException.class));
        assertThat(section.thread.isInterrupted(), is(true));
        assertThat(decoderInstructions, hasSize(1));
        assertThat(decoderInstructions.getFirst(), equalTo(streamCancellation(0)));
    }

    @Test
    void wrapsResidualIoFailureFromConnectionShutdown() {
        QpackConnectionState state = qpackState(MAX_TABLE_CAPACITY, 1);
        DecodeAttempt section = DecodeAttempt.start(state.openDecoderStream(0),
                                                    indexedFieldSection(1, 1, 0),
                                                    -1);
        section.awaitBlocked();
        IOException cause = new IOException("external I/O failure");

        state.close(cause);

        CompletionException failure = assertThrows(CompletionException.class, section::join);
        assertThat(failure.getCause().getClass(), equalTo(UncheckedIOException.class));
        assertThat(failure.getCause().getCause(), sameInstance(cause));
    }

    @Test
    void rejectsConcurrentBlockedSectionsOnSameStream() {
        QpackConnectionState state = qpackState(MAX_TABLE_CAPACITY, 2);
        QpackConnectionState.DecoderStream firstStream = state.openDecoderStream(0);
        DecodeAttempt firstSection = DecodeAttempt.start(firstStream, indexedFieldSection(1, 1, 0), -1);
        firstSection.awaitBlocked();
        DecodeAttempt excessSection = DecodeAttempt.start(firstStream, indexedFieldSection(1, 1, 0), -1);

        CompletionException excessFailure = assertThrows(CompletionException.class, excessSection::join);
        Http3ProtocolException limitFailure = Http3ProtocolException.find(excessFailure).orElseThrow();
        assertThat(limitFailure.errorCode(), equalTo(Http3ErrorCode.QPACK_DECOMPRESSION_FAILED));
        assertThat(assertThrows(CompletionException.class, firstSection::join).getCause(),
                   sameInstance(limitFailure));
    }

    @Test
    void limitsUniqueBlockedStreams() {
        QpackConnectionState state = qpackState(MAX_TABLE_CAPACITY, 1);
        DecodeAttempt firstSection = DecodeAttempt.start(state.openDecoderStream(0),
                                                         indexedFieldSection(1, 1, 0),
                                                         -1);
        firstSection.awaitBlocked();
        DecodeAttempt excessSection = DecodeAttempt.start(state.openDecoderStream(4),
                                                          indexedFieldSection(1, 1, 0),
                                                          -1);

        CompletionException excessFailure = assertThrows(CompletionException.class, excessSection::join);
        Http3ProtocolException limitFailure = Http3ProtocolException.find(excessFailure).orElseThrow();
        assertThat(limitFailure.errorCode(), equalTo(Http3ErrorCode.QPACK_DECOMPRESSION_FAILED));
        assertThat(assertThrows(CompletionException.class, firstSection::join).getCause(),
                   sameInstance(limitFailure));
    }

    @Test
    void decoderCancellationDoesNotEraseEncoderOutstandingState() throws Exception {
        QpackConnectionState state = qpackState(MAX_TABLE_CAPACITY, 1);
        state.peerSettings(MAX_TABLE_CAPACITY, 1);
        List<Header> headers = List.of(HeaderValues.create("x-dynamic", "value"));

        state.encodeHeaders(0, headers);
        state.onDecoderStreamData(insertCountIncrement(1));
        state.encodeHeaders(0, headers);

        QpackConnectionState.DecoderStream decoderStream = state.openDecoderStream(0);
        decoderStream.cancel();

        state.onDecoderStreamData(sectionAcknowledgment(0));
        Http3ProtocolException duplicateAcknowledgment = assertThrows(
                Http3ProtocolException.class,
                () -> state.onDecoderStreamData(sectionAcknowledgment(0)));
        assertThat(duplicateAcknowledgment.errorCode(), equalTo(Http3ErrorCode.QPACK_DECODER_STREAM_ERROR));
    }

    @Test
    void rejectsDynamicReferenceOutsideRequiredInsertCount() throws Exception {
        QpackConnectionState state = qpackState(MAX_TABLE_CAPACITY, 1);
        List<byte[]> decoderInstructions = new ArrayList<>();
        state.decoderInstructionsSender(decoderInstructions::add);
        state.onEncoderStreamData(capacityUpdate(MAX_TABLE_CAPACITY));
        state.onEncoderStreamData(literalInsertion("x-first", "one"));
        state.onEncoderStreamData(literalInsertion("x-second", "two"));
        QpackConnectionState.DecoderStream stream = state.openDecoderStream(0);

        DecodeAttempt section = DecodeAttempt.start(stream, indexedFieldSection(1, 2, 1), -1);

        CompletionException failure = assertThrows(CompletionException.class, section::join);
        Http3ProtocolException protocolFailure = Http3ProtocolException.find(failure).orElseThrow();
        assertThat(protocolFailure.errorCode(), equalTo(Http3ErrorCode.QPACK_DECOMPRESSION_FAILED));
        assertThat(decoderInstructions.stream()
                           .filter(bytes -> Arrays.equals(bytes, sectionAcknowledgment(0)))
                           .count(),
                   equalTo(0L));
    }

    @Test
    void rejectsInstructionIntegerOverflow() {
        QpackConnectionState encoderState = qpackState(MAX_TABLE_CAPACITY, 1);

        Http3ProtocolException encoderFailure = assertThrows(
                Http3ProtocolException.class,
                () -> encoderState.onEncoderStreamData(overflowingInteger((byte) 0x3f)));

        assertThat(encoderFailure.errorCode(), equalTo(Http3ErrorCode.QPACK_ENCODER_STREAM_ERROR));

        QpackConnectionState decoderState = qpackState(MAX_TABLE_CAPACITY, 1);
        DecodeAttempt blockedSection = DecodeAttempt.start(decoderState.openDecoderStream(0),
                                                           indexedFieldSection(1, 1, 0),
                                                           -1);
        blockedSection.awaitBlocked();

        Http3ProtocolException decoderFailure = assertThrows(
                Http3ProtocolException.class,
                () -> decoderState.onDecoderStreamData(overflowingInteger((byte) 0xff)));

        assertThat(decoderFailure.errorCode(), equalTo(Http3ErrorCode.QPACK_DECODER_STREAM_ERROR));
        CompletionException blockedFailure = assertThrows(CompletionException.class,
                                                          blockedSection::join);
        assertThat(blockedFailure.getCause(), sameInstance(decoderFailure));
    }

    @Test
    void acceptsZeroCapacityButRejectsInsertionWhenDynamicTableIsDisabled() {
        QpackConnectionState state = qpackState(0, 0);

        state.onEncoderStreamData(capacityUpdate(0));
        Http3ProtocolException failure = assertThrows(Http3ProtocolException.class,
                                                      () -> state.onEncoderStreamData(literalInsertion("x", "value")));

        assertThat(failure.errorCode(), equalTo(Http3ErrorCode.QPACK_ENCODER_STREAM_ERROR));
    }

    @Test
    void clampsPeerEncoderTableCapacity() throws Exception {
        QpackConnectionState state = qpackState(MAX_TABLE_CAPACITY, 1);
        List<byte[]> encoderInstructions = new ArrayList<>();
        state.encoderInstructionsSender(encoderInstructions::add);

        state.peerSettings(64 * 1024, 1);

        assertThat(encoderInstructions, hasSize(1));
        assertThat(encoderInstructions.getFirst(), equalTo(capacityUpdate(4 * 1024)));
    }

    @Test
    void rejectsOversizedDeclaredEncoderStringWithoutWaitingForPayload() {
        QpackConnectionState state = qpackState(MAX_TABLE_CAPACITY, 1);
        BufferData instruction = BufferData.growing(16);
        QpackCodec.writePrefixedInteger(instruction, 5, 0b0100_0000, 577);

        Http3ProtocolException failure = assertThrows(
                Http3ProtocolException.class,
                () -> state.onEncoderStreamData(instruction.readBytes()));

        assertThat(failure.errorCode(), equalTo(Http3ErrorCode.QPACK_ENCODER_STREAM_ERROR));
        assertThat(failure.scope(), is(Http3ProtocolException.Scope.CONNECTION));
    }

    @Test
    void acceptsEncoderInsertionThatFitsAdvertisedTableAboveHeaderLimit() {
        QpackConnectionState state = QpackConnectionState.create(256, 1, 32, _ -> {
        });

        state.onEncoderStreamData(capacityUpdate(256));

        state.onEncoderStreamData(literalInsertion("x", "v".repeat(200)));
    }

    @Test
    void skipsExactStaticInsertionButInsertsDifferentValueWithSameName() throws Exception {
        QpackConnectionState state = qpackState(MAX_TABLE_CAPACITY, 1);
        List<byte[]> encoderInstructions = new ArrayList<>();
        state.encoderInstructionsSender(encoderInstructions::add);
        state.peerSettings(MAX_TABLE_CAPACITY, 1);

        state.encodeHeaders(0, List.of(HeaderValues.create("x-frame-options", "sameorigin")));

        assertThat(encoderInstructions, hasSize(1));
        assertThat(encoderInstructions.getFirst(), equalTo(capacityUpdate(MAX_TABLE_CAPACITY)));

        state.encodeHeaders(4, List.of(HeaderValues.create("x-frame-options", "custom")));

        assertThat(encoderInstructions, hasSize(2));
        assertThat(encoderInstructions.get(1), equalTo(literalInsertion("x-frame-options", "custom")));
    }

    @Test
    void skipsDynamicInsertionWhenHeaderCannotFitEncoderTable() throws Exception {
        List<Header> headers = List.of(HeaderValues.create("x-large", "x".repeat(4_096)));
        QpackConnectionState queuedState = qpackState(MAX_TABLE_CAPACITY, 1);
        queuedState.peerSettings(MAX_TABLE_CAPACITY, 1);
        queuedState.encodeHeaders(0, headers);
        List<byte[]> queuedInstructions = new ArrayList<>();
        queuedState.encoderInstructionsSender(queuedInstructions::add);

        QpackConnectionState boundState = qpackState(MAX_TABLE_CAPACITY, 1);
        List<byte[]> boundInstructions = new ArrayList<>();
        boundState.encoderInstructionsSender(boundInstructions::add);
        boundState.peerSettings(MAX_TABLE_CAPACITY, 1);
        boundState.encodeHeaders(0, headers);

        assertThat(queuedInstructions, hasSize(1));
        assertThat(queuedInstructions.getFirst(), equalTo(capacityUpdate(MAX_TABLE_CAPACITY)));
        assertThat(boundInstructions, hasSize(1));
        assertThat(boundInstructions.getFirst(), equalTo(capacityUpdate(MAX_TABLE_CAPACITY)));
    }

    @Test
    void rejectsEncodedFieldSectionAboveCommonHeaderLimit() {
        QpackConnectionState state = QpackConnectionState.create(MAX_TABLE_CAPACITY, 1, 8, _ -> {
        });
        QpackConnectionState.DecoderStream stream = state.openDecoderStream(0);

        Http3ProtocolException failure = assertThrows(
                Http3ProtocolException.class,
                () -> stream.decodeHeaderLines(
                        BufferData.create(new byte[Http3QpackContext.encodedFieldSectionLimit(8) + 1]),
                        -1));

        assertThat(failure.errorCode(), equalTo(Http3ErrorCode.MESSAGE_ERROR));
        assertThat(failure.scope(), is(Http3ProtocolException.Scope.STREAM));
    }

    @Test
    void acceptsHuffmanExpansionWithinDecodedHeaderLimit() {
        String value = "\u00ff".repeat(20);
        byte[] encodedValue = new byte[HuffmanCodec.encodedLength(value)];
        assertThat(HuffmanCodec.encode(value, encodedValue), is(encodedValue.length));
        BufferData fieldSection = BufferData.growing(encodedValue.length + 8);
        QpackCodec.writeFieldSectionPrefix(fieldSection, 0, 0, 0);
        QpackCodec.writeString(fieldSection, 3, 0b0010_0000, "x");
        QpackCodec.writePrefixedInteger(fieldSection, 7, 0b1000_0000, encodedValue.length);
        fieldSection.write(encodedValue);
        assertThat(fieldSection.available() > 53, is(true));
        QpackConnectionState state = QpackConnectionState.create(0, 0, 53, _ -> {
        });

        List<Header> decoded = state.openDecoderStream(0).decodeHeaderLines(fieldSection, -1);

        assertThat(decoded, equalTo(List.of(HeaderValues.create("x", value))));
    }

    @Test
    void fallsBackToLiteralEncodingAtUnacknowledgedSectionLimit() throws Exception {
        QpackConnectionState state = qpackState(MAX_TABLE_CAPACITY, 1);
        state.encoderInstructionsSender(_ -> {
        });
        state.peerSettings(MAX_TABLE_CAPACITY, 1);
        List<Header> headers = List.of(HeaderValues.create("x-dynamic", "value"));
        state.encodeHeaders(0, headers);
        state.onDecoderStreamData(insertCountIncrement(1));

        for (int i = 0; i < 1024; i++) {
            assertThat(requiredInsertCount(state.encodeHeaders(0, headers)), equalTo(1L));
        }
        assertThat(requiredInsertCount(state.encodeHeaders(0, headers)), equalTo(0L));

        state.onDecoderStreamData(sectionAcknowledgment(0));

        assertThat(requiredInsertCount(state.encodeHeaders(0, headers)), equalTo(1L));
    }

    @Test
    void cancelsDynamicSectionRejectedByStreamPolicy() throws Exception {
        QpackConnectionState state = qpackState(MAX_TABLE_CAPACITY, 1);
        List<byte[]> decoderInstructions = new ArrayList<>();
        state.decoderInstructionsSender(decoderInstructions::add);
        state.onEncoderStreamData(capacityUpdate(MAX_TABLE_CAPACITY));
        state.onEncoderStreamData(literalInsertion("x-dynamic", "value"));
        decoderInstructions.clear();
        QpackConnectionState.DecoderStream stream = state.openDecoderStream(0);

        DecodeAttempt section = DecodeAttempt.start(stream, indexedFieldSection(1, 1, 0), 1);

        CompletionException failure = assertThrows(CompletionException.class, section::join);
        Http3ProtocolException protocolFailure = Http3ProtocolException.find(failure).orElseThrow();
        assertThat(protocolFailure.errorCode(), equalTo(Http3ErrorCode.MESSAGE_ERROR));

        stream.complete();
        stream.complete();

        assertThat(decoderInstructions, hasSize(1));
        assertThat(decoderInstructions.getFirst(), equalTo(new byte[] {0x40}));
    }

    @Test
    void failsBlockedSectionsWhenInsertCountInstructionFails() throws Exception {
        QpackConnectionState state = qpackState(MAX_TABLE_CAPACITY, 1);
        state.decoderInstructionsSender(_ -> {
            throw new IllegalStateException("decoder stream failed");
        });
        state.onEncoderStreamData(capacityUpdate(MAX_TABLE_CAPACITY));
        DecodeAttempt section = DecodeAttempt.start(state.openDecoderStream(0),
                                                    indexedFieldSection(1, 1, 0),
                                                    -1);
        section.awaitBlocked();

        Http3ProtocolException failure = assertThrows(
                Http3ProtocolException.class,
                () -> state.onEncoderStreamData(literalInsertion("x-blocked", "failed")));

        assertThat(failure.errorCode(), equalTo(Http3ErrorCode.CLOSED_CRITICAL_STREAM));
        CompletionException sectionFailure = assertThrows(CompletionException.class, section::join);
        assertThat(sectionFailure.getCause(), sameInstance(failure));
    }

    @Test
    void reportsCancellationWriteFailureToConnectionOwner() throws Exception {
        List<Throwable> failures = new ArrayList<>();
        QpackConnectionState state = QpackConnectionState.create(MAX_TABLE_CAPACITY, 1, 16_384, failures::add);
        state.decoderInstructionsSender(_ -> {
            throw new IllegalStateException("decoder stream failed");
        });
        QpackConnectionState.DecoderStream stream = state.openDecoderStream(0);

        stream.cancel();
        stream.cancel();

        assertThat(failures, hasSize(1));
        Http3ProtocolException failure = Http3ProtocolException.find(failures.getFirst()).orElseThrow();
        assertThat(failure.errorCode(), equalTo(Http3ErrorCode.CLOSED_CRITICAL_STREAM));
        assertThrows(IllegalStateException.class, () -> state.openDecoderStream(4));
    }

    @Test
    void reportsPendingEncoderInstructionFailureToConnectionOwner() throws Exception {
        List<Throwable> failures = new ArrayList<>();
        QpackConnectionState state = QpackConnectionState.create(0, 0, 16_384, failures::add);
        state.peerSettings(MAX_TABLE_CAPACITY, 1);

        Http3ProtocolException failure = assertThrows(Http3ProtocolException.class,
                                                      () -> state.encoderInstructionsSender(_ -> {
                                                          throw new IllegalStateException("encoder stream failed");
                                                      }));

        assertThat(failure.errorCode(), equalTo(Http3ErrorCode.CLOSED_CRITICAL_STREAM));
        assertThat(failures, equalTo(List.of(failure)));
        assertThrows(IllegalStateException.class, () -> state.encodeHeaders(0, List.of()));
    }

    @Test
    void failsConnectionWhenSectionAcknowledgmentFails() throws Exception {
        QpackConnectionState state = qpackState(MAX_TABLE_CAPACITY, 2);
        List<byte[]> decoderInstructions = new ArrayList<>();
        state.decoderInstructionsSender(bytes -> {
            if (!decoderInstructions.isEmpty()) {
                throw new IllegalStateException("section acknowledgment failed");
            }
            decoderInstructions.add(bytes);
        });
        state.onEncoderStreamData(capacityUpdate(MAX_TABLE_CAPACITY));
        DecodeAttempt firstSection = DecodeAttempt.start(state.openDecoderStream(0),
                                                         indexedFieldSection(1, 1, 0),
                                                         -1);
        DecodeAttempt secondSection = DecodeAttempt.start(state.openDecoderStream(4),
                                                          indexedFieldSection(1, 1, 0),
                                                          -1);
        firstSection.awaitBlocked();
        secondSection.awaitBlocked();

        Http3ProtocolException failure = assertThrows(
                Http3ProtocolException.class,
                () -> state.onEncoderStreamData(literalInsertion("x-blocked", "failed")));

        assertThat(failure.errorCode(), equalTo(Http3ErrorCode.CLOSED_CRITICAL_STREAM));
        assertThat(decoderInstructions, hasSize(1));
        assertThat(decoderInstructions.getFirst(), equalTo(insertCountIncrement(1)));
        CompletionException firstFailure = assertThrows(CompletionException.class,
                                                        firstSection::join);
        CompletionException secondFailure = assertThrows(CompletionException.class,
                                                         secondSection::join);
        assertThat(firstFailure.getCause(), sameInstance(failure));
        assertThat(secondFailure.getCause(), sameInstance(failure));
    }

    @Test
    void doesNotSendCancellationAfterTerminationIsSelected() throws Exception {
        QpackConnectionState state = qpackState(MAX_TABLE_CAPACITY, 1);
        List<byte[]> decoderInstructions = new ArrayList<>();
        state.decoderInstructionsSender(decoderInstructions::add);
        QpackConnectionState.DecoderStream stream = state.openDecoderStream(0);
        DecodeAttempt section = DecodeAttempt.start(stream, indexedFieldSection(1, 1, 0), -1);
        section.awaitBlocked();
        IllegalStateException closeCause = new IllegalStateException("connection closed");
        AtomicReference<Thread> closeThread = new AtomicReference<>();
        AtomicBoolean terminationObserved = new AtomicBoolean();
        state.encoderInstructionsSender(_ -> {
            closeThread.set(Thread.ofPlatform().start(() -> state.close(closeCause)));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!terminationObserved.get() && System.nanoTime() < deadline) {
                try {
                    state.onEncoderStreamData(new byte[0]);
                    Thread.onSpinWait();
                } catch (IllegalStateException e) {
                    terminationObserved.set(true);
                }
            }
            stream.cancel();
        });

        state.peerSettings(MAX_TABLE_CAPACITY, 1);
        closeThread.get().join();

        assertThat(terminationObserved.get(), is(true));
        assertThat(decoderInstructions, empty());
        CompletionException sectionFailure = assertThrows(CompletionException.class, section::join);
        assertThat(sectionFailure.getCause(), sameInstance(closeCause));
        assertThrows(IllegalStateException.class, () -> state.openDecoderStream(4));
    }

    @Test
    void bindsInstructionSendersOnceAndRejectsLateBinding() throws Exception {
        QpackConnectionState state = qpackState(MAX_TABLE_CAPACITY, 1);
        state.encoderInstructionsSender(_ -> {
        });

        assertThrows(IllegalStateException.class, () -> state.encoderInstructionsSender(_ -> {
        }));

        state.close(new IllegalStateException("connection closed"));

        assertThrows(IllegalStateException.class, () -> state.decoderInstructionsSender(_ -> {
        }));
    }

    private static QpackConnectionState qpackState(long maxTableCapacity, long blockedStreams) {
        return QpackConnectionState.create(maxTableCapacity, blockedStreams, 16_384, _ -> {
        });
    }

    private static BufferData indexedFieldSection(long requiredInsertCount, long base, long absoluteIndex) {
        BufferData output = BufferData.growing(16);
        QpackCodec.writeFieldSectionPrefix(output, requiredInsertCount, base, MAX_TABLE_ENTRIES);
        QpackCodec.writeIndexedFieldLine(output, absoluteIndex, false, base);
        return BufferData.create(output.readBytes());
    }

    private static byte[] capacityUpdate(long capacity) {
        BufferData output = BufferData.growing(16);
        QpackCodec.writePrefixedInteger(output, 5, 0b0010_0000, capacity);
        return output.readBytes();
    }

    private static byte[] literalInsertion(String name, String value) {
        BufferData output = BufferData.growing(name.length() + value.length() + 8);
        QpackCodec.writeString(output, 5, 0b0100_0000, name);
        QpackCodec.writeString(output, 7, 0, value);
        return output.readBytes();
    }

    private static byte[] sectionAcknowledgment(long streamId) {
        BufferData output = BufferData.growing(16);
        QpackCodec.writePrefixedInteger(output, 7, 0b1000_0000, streamId);
        return output.readBytes();
    }

    private static byte[] streamCancellation(long streamId) {
        BufferData output = BufferData.growing(16);
        QpackCodec.writePrefixedInteger(output, 6, 0b0100_0000, streamId);
        return output.readBytes();
    }

    private static byte[] insertCountIncrement(long increment) {
        BufferData output = BufferData.growing(16);
        QpackCodec.writePrefixedInteger(output, 6, 0, increment);
        return output.readBytes();
    }

    private static long requiredInsertCount(byte[] fieldSection) {
        BufferData buffer = BufferData.create(fieldSection);
        return QpackCodec.readFieldSectionPrefix(buffer, 1, MAX_TABLE_ENTRIES).requiredInsertCount();
    }

    private static void assertReadTimeout(String inputType, ExecutionException failure) {
        assertThat(inputType + " failure", failure.getCause().getClass(), equalTo(UncheckedIOException.class));
        assertThat(inputType + " cause",
                   failure.getCause().getCause().getClass(),
                   equalTo(Http3ReadTimeoutException.class));
    }

    private static byte[] overflowingInteger(byte first) {
        byte[] bytes = new byte[12];
        Arrays.fill(bytes, (byte) 0xff);
        bytes[0] = first;
        bytes[bytes.length - 1] = 0x7f;
        return bytes;
    }

    private static final class DecodeAttempt {
        private final CompletableFuture<List<Header>> completion = new CompletableFuture<>();
        private final Thread thread;

        private DecodeAttempt(QpackConnectionState.DecoderStream stream,
                              BufferData fieldSection,
                              long maxFieldSectionSize,
                              Duration readTimeout) {
            thread = Thread.ofPlatform().start(() -> {
                try {
                    if (readTimeout == null) {
                        completion.complete(stream.decodeHeaderLines(fieldSection, maxFieldSectionSize));
                    } else {
                        completion.complete(stream.decodeHeaderLines(fieldSection,
                                                                     maxFieldSectionSize,
                                                                     readTimeout,
                                                                     CompletableFuture.completedFuture(null)));
                    }
                } catch (Throwable t) {
                    completion.completeExceptionally(t);
                }
            });
        }

        private static DecodeAttempt start(QpackConnectionState.DecoderStream stream,
                                           BufferData fieldSection,
                                           long maxFieldSectionSize) {
            return new DecodeAttempt(stream, fieldSection, maxFieldSectionSize, null);
        }

        private static DecodeAttempt start(QpackConnectionState.DecoderStream stream,
                                           BufferData fieldSection,
                                           long maxFieldSectionSize,
                                           Duration readTimeout) {
            return new DecodeAttempt(stream, fieldSection, maxFieldSectionSize, readTimeout);
        }

        private void awaitBlocked() {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (thread.isAlive() && thread.getState() != Thread.State.WAITING && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            assertThat("QPACK decode should wait for encoder-stream progress",
                       thread.getState(),
                       is(Thread.State.WAITING));
        }

        private void awaitTimedWait() {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (thread.isAlive() && thread.getState() != Thread.State.TIMED_WAITING && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            assertThat("QPACK decode should wait for encoder-stream progress with a timeout",
                       thread.getState(),
                       is(Thread.State.TIMED_WAITING));
        }

        private boolean isDone() {
            return completion.isDone();
        }

        private List<Header> getBefore(long deadlineNanos)
                throws InterruptedException, ExecutionException, TimeoutException {
            long remainingNanos = Math.max(1, deadlineNanos - System.nanoTime());
            return completion.get(remainingNanos, TimeUnit.NANOSECONDS);
        }

        private List<Header> join() {
            return completion.join();
        }
    }

}
