/*
 * Copyright (c) 2021, 2026 Oracle and/or its affiliates.
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

package io.helidon.quic.stream;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.helidon.common.Api;
import io.helidon.quic.QuicConnectionImpl;
import io.helidon.quic.SequentialScheduler;

/**
 * An implementation of a bidirectional stream.
 * A bidirectional stream implements both {@link QuicSenderStream}
 * and {@link QuicReceiverStream}.
 */
@Api.Internal
public final class QuicBidiStreamImpl extends AbstractQuicStream implements QuicBidiStream {

    // The sender part of this bidirectional stream
    private final QuicSenderStreamImpl senderPart;

    // The receiver part of this bidirectional stream
    private final QuicReceiverStreamImpl receiverPart;

    QuicBidiStreamImpl(QuicConnectionImpl connection,
                       long streamId,
                       int maxSmallFragments,
                       int streamBufferSize) {
        this(connection,
             streamId,
             new QuicSenderStreamImpl(connection, streamId, streamBufferSize),
             new QuicReceiverStreamImpl(connection, streamId, maxSmallFragments));
    }

    private QuicBidiStreamImpl(QuicConnectionImpl connection, long streamId,
                               QuicSenderStreamImpl sender, QuicReceiverStreamImpl receiver) {
        super(connection, streamId);
        this.senderPart = sender;
        this.receiverPart = receiver;
    }

    @Override
    public ReceivingStreamState receivingState() {
        return receiverPart.receivingState();
    }

    @Override
    public QuicStreamReader connectReader(SequentialScheduler scheduler) {
        return receiverPart.connectReader(scheduler);
    }

    @Override
    public void disconnectReader(QuicStreamReader reader) {
        receiverPart.disconnectReader(reader);
    }

    @Override
    public void requestStopSending(long errorCode) {
        receiverPart.requestStopSending(errorCode);
    }

    @Override
    public boolean isStopSendingRequested() {
        return receiverPart.isStopSendingRequested();
    }

    @Override
    public long dataReceived() {
        return receiverPart.dataReceived();
    }

    @Override
    public long maxStreamData() {
        return receiverPart.maxStreamData();
    }

    @Override
    public SendingStreamState sendingState() {
        return senderPart.sendingState();
    }

    @Override
    public QuicStreamWriter connectWriter(SequentialScheduler scheduler) {
        return senderPart.connectWriter(scheduler);
    }

    @Override
    public void disconnectWriter(QuicStreamWriter writer) {
        senderPart.disconnectWriter(writer);
    }

    @Override
    public void reset(long errorCode) {
        senderPart.reset(errorCode);
    }

    @Override
    public long dataSent() {
        return senderPart.dataSent();
    }

    @Override
    public CompletableFuture<SendingStreamState> futureSendingCompletion() {
        return senderPart.futureSendingCompletion();
    }

    /**
     * Returns the sender part implementation of this bidirectional stream.
     *
     * @return the sender part implementation of this bidirectional stream
     */
    public QuicSenderStreamImpl senderPart() {
        return senderPart;
    }

    /**
     * Returns the receiver part implementation of this bidirectional stream.
     *
     * @return the receiver part implementation of this bidirectional stream
     */
    public QuicReceiverStreamImpl receiverPart() {
        return receiverPart;
    }

    @Override
    public boolean isDone() {
        return receiverPart.isDone() && senderPart.isDone();
    }

    @Override
    public long rcvErrorCode() {
        return receiverPart.rcvErrorCode();
    }

    @Override
    public long sndErrorCode() {
        return senderPart.sndErrorCode();
    }

    @Override
    public boolean stopSendingReceived() {
        return senderPart.stopSendingReceived();
    }

    @Override
    public CompletionStage<Long> whenStopSendingReceived() {
        return senderPart.whenStopSendingReceived();
    }
}
