/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.webclient.grpc;

import io.helidon.common.buffers.BufferData;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.webclient.http2.StreamTimeoutException;

import io.grpc.CallOptions;
import io.grpc.MethodDescriptor;
import io.grpc.Status;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.ERROR;

/**
 * An implementation of a unary gRPC call. Expects:
 * <p>
 * start request sendMessage (halfClose | cancel)
 *
 * @param <ReqT> request type
 * @param <ResT> response type
 */
class GrpcUnaryClientCall<ReqT, ResT> extends GrpcBaseClientCall<ReqT, ResT> {
    private static final System.Logger LOGGER = System.getLogger(GrpcUnaryClientCall.class.getName());

    private volatile boolean closeCalled;
    private volatile boolean requestSent;
    private volatile boolean responseSent;

    GrpcUnaryClientCall(GrpcChannel grpcChannel,
                        MethodDescriptor<ReqT, ResT> methodDescriptor,
                        CallOptions callOptions) {
        super(grpcChannel, methodDescriptor, callOptions);
    }

    @Override
    public void request(int numMessages) {
        socket().log(LOGGER, DEBUG, "request called %d", numMessages);
        if (numMessages < 1) {
            close(Status.INVALID_ARGUMENT);
        }
    }

    @Override
    public void cancel(String message, Throwable cause) {
        socket().log(LOGGER, DEBUG, "cancel called %s", message);
        close(Status.CANCELLED);
    }

    @Override
    public void halfClose() {
        socket().log(LOGGER, DEBUG, "halfClose called");
        close(responseSent ? Status.OK : Status.UNKNOWN);
    }

    @Override
    public void sendMessage(ReqT message) {
        // should only be called once
        if (requestSent) {
            close(Status.FAILED_PRECONDITION);
            return;
        }

        // serialize and write message
        byte[] serialized = serializeMessage(message);
        BufferData messageData = BufferData.createReadOnly(serialized, 0, serialized.length);
        BufferData headerData = BufferData.create(5);
        headerData.writeInt8(0);                                // no compression
        headerData.writeUnsignedInt32(messageData.available());         // length prefixed
        clientStream().writeData(BufferData.create(headerData, messageData), true);
        requestSent = true;

        // read response headers
        clientStream().readHeaders();

        while (isRemoteOpen()) {
            // trailers or eos received?
            if (clientStream().trailers().isDone() || !clientStream().hasEntity()) {
                socket().log(LOGGER, DEBUG, "[Reading thread] trailers or eos received");
                break;
            }

            // attempt to read and queue
            Http2FrameData frameData;
            try {
                frameData = clientStream().readOne(pollWaitTime());
            } catch (StreamTimeoutException e) {
                // abort or retry based on config settings
                if (abortPollTimeExpired()) {
                    socket().log(LOGGER, ERROR, "[Reading thread] HTTP/2 stream timeout, aborting");
                    responseListener().onClose(Status.DEADLINE_EXCEEDED, EMPTY_METADATA);
                    break;
                }
                socket().log(LOGGER, ERROR, "[Reading thread] HTTP/2 stream timeout, retrying");
                continue;
            }
            if (frameData != null) {
                socket().log(LOGGER, DEBUG, "response received");
                responseListener().onMessage(toResponse(frameData.data()));
                responseSent = true;
            }
        }
    }

    @Override
    protected void startStreamingThreads() {
        // no-op
    }

    private void close(Status status) {
        if (!closeCalled) {
            socket().log(LOGGER, DEBUG, "closing client call");
            responseListener().onClose(status, EMPTY_METADATA);
            clientStream().cancel();
            connection().close();
            unblockUnaryExecutor();
            closeCalled = true;
        }
    }
}
