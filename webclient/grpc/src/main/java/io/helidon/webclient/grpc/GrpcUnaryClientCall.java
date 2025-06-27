/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

import java.time.Duration;

import io.helidon.common.buffers.BufferData;

import io.grpc.CallOptions;
import io.grpc.MethodDescriptor;
import io.grpc.Status;

import static java.lang.System.Logger.Level.DEBUG;

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
        BufferData headerData = BufferData.create(DATA_PREFIX_LENGTH);
        headerData.writeInt8(0);                                // no compression
        headerData.writeUnsignedInt32(messageData.available());         // length prefixed
        clientStream().writeData(BufferData.create(headerData, messageData), true);
        requestSent = true;

        // Update bytes sent
        if (enableMetrics()) {
            bytesSent().addAndGet(serialized.length);
        }

        // read response headers
        clientStream().readHeaders();

        while (isRemoteOpen()) {
            // trailers or eos received?
            if (clientStream().trailers().isDone() || !clientStream().hasEntity()) {
                socket().log(LOGGER, DEBUG, "[Reading thread] trailers or eos received");
                break;
            }

            // read single gRPC frame
            BufferData bufferData = readGrpcFrame();
            if (bufferData != null) {
                socket().log(LOGGER, DEBUG, "response received");

                // update bytes received excluding prefix
                if (enableMetrics()) {
                    bytesRcvd().addAndGet(bufferData.available() - DATA_PREFIX_LENGTH);
                }

                responseListener().onMessage(toResponse(bufferData));
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

            // update metrics
            if (enableMetrics() && status == Status.OK) {
                MethodMetrics methodMetrics = methodMetrics();
                methodMetrics.callDuration().record(
                        Duration.ofMillis(System.currentTimeMillis() - startMillis()));
                methodMetrics.recvMessageSize().record(bytesRcvd().get());
                methodMetrics.sentMessageSize().record(bytesSent().get());
            }

            unblockUnaryExecutor();
            closeCalled = true;
        }
    }
}
