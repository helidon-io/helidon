/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

package io.helidon.webclient.http1;

import java.io.IOException;

import io.helidon.common.buffers.BufferData;
import io.helidon.webclient.api.ClientRequest;

/**
 * Client request for HTTP/1.1.
 */
public interface Http1ClientRequest extends ClientRequest<Http1ClientRequest> {
    @Override
    Http1ClientResponse submit(Object entity);

    @Override
    default Http1ClientResponse request() {
        return submit(BufferData.EMPTY_BYTES);
    }

    @Override
    Http1ClientResponse outputStream(OutputStreamHandler outputStreamConsumer);

    /**
     * Upload an entity while consuming its response concurrently.
     * The upload handler runs on a virtual thread with the request context; the response handler runs on the calling
     * thread. Both handlers must finish before this method returns. Consume the response entity inside its handler;
     * neither the response nor its entity stream may be used after the handler returns.
     * The upload handler must close its output stream and honor interruption when waiting outside transport I/O.
     * A handler failure closes the connection and interrupts the upload handler.
     * <p>
     * Redirect responses are delivered to the response handler without replaying the upload, irrespective of
     * {@link #followRedirects(boolean)}. Successful response headers do not stop an ongoing upload.
     * After the response handler returns for a redirect or error response, any ongoing upload is interrupted and
     * the connection is closed. The handler can consume the response entity before that cancellation.
     * The request read timeout bounds intervals without transport progress while I/O is pending in either direction;
     * time spent in application code with no pending transport I/O is excluded.
     *
     * @param uploadHandler handler producing the request entity
     * @param responseHandler handler consuming the response
     */
    void exchange(OutputStreamHandler uploadHandler, ResponseHandler responseHandler);

    /**
     * Consumes a response during a concurrent upload.
     */
    @FunctionalInterface
    interface ResponseHandler {
        /**
         * Consume the response before returning.
         *
         * @param response response to consume
         * @throws IOException if consuming the response fails
         */
        void handle(Http1ClientResponse response) throws IOException;
    }

    /**
     * Upgrade the current request to a different protocol.
     * As an upgrade is executing the usual HTTP method call, in case of failure to upgrade, the response will be a
     * usual full HTTP response that you would get without an upgrade attempt.
     * <p>
     * Note that the response returned will trigger different behavior depending on whether the upgraded succeeded or failed.
     * For success, it will not close the connection (or return it to the pool), and the upgrade caller must correctly
     * handle the connection close. In case of failure, this is just a regular client response that closes the connection
     *  (or returns it to the pool)
     *
     * @param protocol protocol ID for upgrade
     * @return upgrade response
     */
    UpgradeResponse upgrade(String protocol);
}
