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

package io.helidon.nima.webserver;

import java.util.List;
import java.util.concurrent.ExecutorService;

import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.http.Http;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.common.socket.SocketContext;
import io.helidon.nima.http.encoding.ContentEncodingContext;
import io.helidon.nima.http.media.MediaContext;
import io.helidon.nima.webserver.http.SimpleHandlers;

/**
 * Server connection context.
 */
public interface ConnectionContext extends SocketContext {
    /**
     * Create a new context.
     *
     * @param mediaContext           media context to read and write typed entities
     * @param contentEncodingContext content encoding to support various entity content encodings
     * @param sharedExecutor         executor service to use to handle asynchronous tasks
     * @param dataWriter             data writer to write response
     * @param dataReader             data reader to read request
     * @param router                 router with available routings
     * @param serverChannelId        server channel id (listener)
     * @param channelId              channel id (connection)
     * @param simpleHandlers         error handling configuration
     * @param socket                 socket to obtain information about peers
     * @param maxPayloadSize         maximal size of a payload entity
     * @param authorityHeaders
     * @return a new context
     */
    static ConnectionContext create(MediaContext mediaContext,
                                    ContentEncodingContext contentEncodingContext,
                                    ExecutorService sharedExecutor,
                                    DataWriter dataWriter,
                                    DataReader dataReader,
                                    Router router,
                                    String serverChannelId,
                                    String channelId,
                                    SimpleHandlers simpleHandlers,
                                    HelidonSocket socket,
                                    long maxPayloadSize,
                                    List<Http.HeaderName> authorityHeaders) {
        return new ConnectionContextImpl(mediaContext,
                                         contentEncodingContext,
                                         sharedExecutor,
                                         dataWriter,
                                         dataReader,
                                         router,
                                         serverChannelId,
                                         channelId,
                                         simpleHandlers,
                                         socket,
                                         maxPayloadSize,
                                         authorityHeaders);
    }

    /**
     * Media context to read and write typed entities.
     *
     * @return media context
     */
    MediaContext mediaContext();

    /**
     * Content encoding support, to handle entity encoding (such as gzip, deflate).
     *
     * @return content encoding support
     */
    ContentEncodingContext contentEncodingContext();

    /**
     * Executor service to submit asynchronous tasks.
     *
     * @return executor service
     */
    ExecutorService sharedExecutor();

    /**
     * Data writer to write response bytes.
     *
     * @return data writer
     */
    DataWriter dataWriter();

    /**
     * Data reader to read request bytes.
     *
     * @return data reader
     */
    DataReader dataReader();

    /**
     * Router that may contain routings of different types (HTTP, WebSocket, grpc).
     *
     * @return rouer
     */
    Router router();

    /**
     * Maximal number of bytes in an entity.
     *
     * @return maximal size
     */
    long maxPayloadSize();

    /**
     * Simple handler to customize processing of HTTP exceptions.
     *
     * @return simple handlers
     */
    SimpleHandlers simpleHandlers();

    /**
     * Headers that can be used to obtain authority. Níma semantically understands the following headers (in order of use):
     * {@link io.helidon.common.http.Http.Header#HOST} (or HTTP/2 authority pseudo-header),
     * {@link io.helidon.common.http.Http.Header#FORWARDED} (using the Host directive),
     * {@link io.helidon.common.http.Http.Header#X_FORWARDED_HOST},
     * or {@link io.helidon.common.http.Http.Header#X_FORWARDED_FOR}.
     * Any other header defined will be used verbatim.
     *
     * @return list of header names to use to obtain authority
     */
    List<Http.HeaderName> authorityHeaders();
}
