/*
 * Copyright (c) 2020, 2024 Oracle and/or its affiliates.
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

package io.helidon.jersey.connector;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Flow;
import java.util.function.Function;

import javax.ws.rs.ProcessingException;

import io.helidon.common.GenericType;
import io.helidon.common.http.DataChunk;
import io.helidon.common.http.MediaType;
import io.helidon.common.reactive.IoMulti;
import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.OutputStreamMulti;
import io.helidon.common.reactive.Single;
import io.helidon.media.common.ContentWriters;
import io.helidon.media.common.MessageBodyWriter;
import io.helidon.media.common.MessageBodyWriterContext;
import io.helidon.webclient.WebClientRequestBuilder;
import io.helidon.webclient.WebClientResponse;

import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.ClientRequest;

/**
 * A utility class that converts outbound client entity to a class understandable by Helidon.
 * Based on the {@link HelidonEntityType} an entity writer is provided to be registered by Helidon client
 * and an Entity is provided to be submitted by the Helidon Client.
 */
class HelidonEntity {

    private HelidonEntity() {
    }

    /**
     * HelidonEntity type chosen by HelidonEntityType.
     */
    enum HelidonEntityType {
        /**
         * Simplest structure. Loads all data to the memory.
         */
        BYTE_ARRAY_OUTPUT_STREAM,
        /**
         * Readable ByteChannel that is capable of sending data in chunks.
         * Capable of caching of bytes before the data are consumed by Helidon.
         */
        READABLE_BYTE_CHANNEL,
        /**
         * Helidon most native entity. Could be slower than {@link #READABLE_BYTE_CHANNEL}.
         */
        // Check LargeDataTest with OUTPUT_STREAM_MULTI
        OUTPUT_STREAM_MULTI
    }

    /**
     * Get optional entity writer to be registered by the Helidon Client. For some default providers,
     * nothing is needed to be registered.
     * @param type the type of the entity class that works best for the Http Client request use case.
     * @return possible writer to be registerd by the Helidon Client.
     */
    static Optional<MessageBodyWriter<?>> helidonWriter(HelidonEntityType type) {
        switch (type) {
            case BYTE_ARRAY_OUTPUT_STREAM:
                return Optional.of(new OutputStreamBodyWriter());
            case OUTPUT_STREAM_MULTI:
            case READABLE_BYTE_CHANNEL:
            default:
                return Optional.empty();
        }
    }

    /**
     * Convert Jersey {@code OutputStream} to an entity based on the client request use case and submits to the provided
     * {@code WebClientRequestBuilder}.
     * @param type the type of the Helidon entity.
     * @param requestContext Jersey {@link ClientRequest} providing the entity {@code OutputStream}.
     * @param requestBuilder Helidon {@code WebClientRequestBuilder} which is used to submit the entity
     * @param executorService {@link ExecutorService} that fills the entity instance for Helidon with data from Jersey
     *                      {@code OutputStream}.
     * @return Helidon Client response completion stage.
     */
    static CompletionStage<WebClientResponse> submit(HelidonEntityType type,
                                                     ClientRequest requestContext,
                                                     WebClientRequestBuilder requestBuilder,
                                                     ExecutorService executorService) {
        CompletionStage<WebClientResponse> stage = null;
        if (type != null) {
            final int bufferSize = requestContext.resolveProperty(
                    ClientProperties.OUTBOUND_CONTENT_LENGTH_BUFFER, 8192);
            CompletableFuture<Void> firstWrite = new CompletableFuture<>();
            switch (type) {
                case BYTE_ARRAY_OUTPUT_STREAM:
                    final ByteArrayOutputStream baos = new ByteArrayOutputStream(bufferSize);
                    requestContext.setStreamProvider(unused -> {
                        requestBuilder.headers(HelidonStructures.createHeaders(requestContext.getRequestHeaders()));
                        firstWrite.complete(null);
                        return baos;
                    });

                    try {
                        requestContext.writeEntity();
                        stage = requestBuilder.submit(baos);
                    } catch (IOException e) {
                        stage = CompletableFuture.failedStage(e);
                    }
                    break;
                case READABLE_BYTE_CHANNEL:
                    final OutputStreamChannel channel = new OutputStreamChannel(bufferSize);
                    requestContext.setStreamProvider(unused -> {
                        requestBuilder.headers(HelidonStructures.createHeaders(requestContext.getRequestHeaders()));
                        firstWrite.complete(null);
                        return channel;
                    });
                    executorService.execute((ProcessingRunnable) requestContext::writeEntity);
                    stage = firstWrite.thenCompose(unused -> requestBuilder.submit(channel));
                    break;
                case OUTPUT_STREAM_MULTI:
                    final OutputStreamMulti publisher = IoMulti.outputStreamMulti();
                    requestContext.setStreamProvider(unused -> {
                        requestBuilder.headers(HelidonStructures.createHeaders(requestContext.getRequestHeaders()));
                        firstWrite.complete(null);
                        return publisher;
                    });
                    executorService.execute((ProcessingRunnable) () -> {
                        requestContext.writeEntity();
                        publisher.close();
                    });
                    Multi<DataChunk> m = Multi.create(publisher).map(DataChunk::create);
                    stage = firstWrite.thenCompose(unused -> requestBuilder.submit(m));
                    break;
                default:
            }
        }
        return stage;
    }

    @FunctionalInterface
    private interface ProcessingRunnable extends Runnable {
        void runOrThrow() throws IOException;

        @Override
        default void run() {
            try {
                runOrThrow();
            } catch (IOException e) {
                throw new ProcessingException("Error writing entity:", e);
            }
        }
    }

    private static class OutputStreamBodyWriter implements MessageBodyWriter<ByteArrayOutputStream> {
        private OutputStreamBodyWriter() {
        }

        @Override
        public Flow.Publisher<DataChunk> write(
                Single<? extends ByteArrayOutputStream> content,
                GenericType<? extends ByteArrayOutputStream> type,
                MessageBodyWriterContext context) {
            context.contentType(MediaType.APPLICATION_OCTET_STREAM);
            return content.flatMap(new ByteArrayOutputStreamToChunks());
        }

        @Override
        public PredicateResult accept(GenericType<?> type, MessageBodyWriterContext messageBodyWriterContext) {
            return PredicateResult.supports(ByteArrayOutputStream.class, type);
        }

        private static class ByteArrayOutputStreamToChunks implements Function<ByteArrayOutputStream, Flow.Publisher<DataChunk>> {
            @Override
            public Flow.Publisher<DataChunk> apply(ByteArrayOutputStream byteArrayOutputStream) {
                return ContentWriters.writeBytes(byteArrayOutputStream.toByteArray(), false);
            }
        }
    }
}
