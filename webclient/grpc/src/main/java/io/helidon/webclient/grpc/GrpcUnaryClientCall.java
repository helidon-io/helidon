/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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
import io.helidon.http.Header;
import io.helidon.http.Headers;
import io.helidon.http.http2.Http2Headers;
import io.helidon.webclient.http2.StreamTimeoutException;

import io.grpc.CallOptions;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

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

    private volatile boolean requestSent;
    private volatile boolean responseReceived;
    private volatile Http2Headers responseHeaders;

    GrpcUnaryClientCall(GrpcChannel grpcChannel,
                        MethodDescriptor<ReqT, ResT> methodDescriptor,
                        CallOptions callOptions) {
        super(grpcChannel, methodDescriptor, callOptions);
    }

    @Override
    public void request(int numMessages) {
        LOGGER.log(DEBUG, "request called {0}", numMessages);
        if (numMessages < 1) {
            close(Status.INVALID_ARGUMENT);
        }
    }

    @Override
    public void halfClose() {
        if (isClosed()) {
            return;
        }
        LOGGER.log(DEBUG, "halfClose called");
        if (responseReceived) {
            var trailers = clientStream().trailers();
            if (trailers.isDone() && !trailers.isCompletedExceptionally()) {
                Headers headers = trailers.join();
                if (headers.contains(STATUS_NAME)) {
                    closeResponse(Status.fromCodeValue(headers.get(STATUS_NAME).getInt()));
                    return;
                }
            }
            if (responseHeaders != null) {
                Headers headers = responseHeaders.httpHeaders();
                if (headers.contains(STATUS_NAME)) {
                    Header status = headers.get(STATUS_NAME);
                    closeResponse(Status.fromCodeValue(status.getInt()));
                    return;
                }
            }
            close(Status.OK);
        } else {
            close(Status.UNKNOWN);
        }
    }

    @Override
    public void sendMessage(ReqT message) {
        if (isClosed()) {
            return;
        }
        try {
            sendRequest(message);
        } catch (StreamTimeoutException e) {
            close(Status.DEADLINE_EXCEEDED.withCause(e));
        } catch (StatusRuntimeException e) {
            Metadata trailers = e.getTrailers();
            close(e.getStatus(), trailers == null ? EMPTY_METADATA : trailers);
        } catch (Throwable e) {
            close(Status.fromThrowable(e));
        }
    }

    @Override
    protected void startStreamingThreads() {
        // no-op
    }

    @Override
    protected void closeStreamingThreads() {
        // no-op
    }

    private void sendRequest(ReqT message) {
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

        // update bytes sent
        if (enableMetrics()) {
            bytesSent().addAndGet(serialized.length);
        }

        // read response headers, or trailers if an error occurred
        responseHeaders = clientStream().readHeaders();
        responseReceived = true;

        while (!isClosed() && (isRemoteOpen() || hasUnreadData())) {
            // trailers or eos received?
            if (clientStream().trailers().isDone() || !clientStream().hasEntity()) {
                socket().log(LOGGER, DEBUG, "[Reading thread] trailers or eos received");
                responseReceived = true;
                break;
            }

            // read single gRPC frame
            BufferData bufferData;
            try {
                bufferData = readGrpcFrame();
            } catch (StatusRuntimeException e) {
                Metadata trailers = e.getTrailers();
                close(e.getStatus(), trailers == null ? EMPTY_METADATA : trailers);
                return;
            } catch (IllegalStateException e) {
                close(Status.UNKNOWN.withDescription(e.getMessage()).withCause(e));
                return;
            }
            if (bufferData != null) {
                socket().log(LOGGER, DEBUG, "response received");

                // update bytes received excluding prefix
                if (enableMetrics()) {
                    bytesRcvd().addAndGet(bufferData.available() - DATA_PREFIX_LENGTH);
                }

                onMessage(toResponse(bufferData));
                responseReceived = true;
            }
        }
    }

}
