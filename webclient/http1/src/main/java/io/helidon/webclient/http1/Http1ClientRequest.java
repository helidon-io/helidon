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

import io.helidon.common.buffers.BufferData;
import io.helidon.webclient.api.ClientRequest;

/**
 * Client request for HTTP/1.1.
 */
public interface Http1ClientRequest extends ClientRequest<Http1ClientRequest> {
    /**
     * Submit the request with the provided entity.
     * For streamed entities, an {@link io.helidon.http.media.EntityWriter} without instance-writer support can run on a
     * helper virtual thread using the current Helidon context. The writer can prepare request headers before its first
     * output, and this method waits for the writer to terminate, including when an early response cancels the upload.
     *
     * @param entity request entity
     * @return response
     */
    @Override
    Http1ClientResponse submit(Object entity);

    @Override
    default Http1ClientResponse request() {
        return submit(BufferData.EMPTY_BYTES);
    }

    /**
     * Handle an output stream and submit this request.
     * When negotiating {@code Expect: 100-continue}, request headers are sent before invoking the handler.
     * An early final response or a redirect that discards the request entity can therefore bypass the handler entirely.
     * Configure request headers before calling this method, rather than inside the handler.
     * <p>
     * A handler that writes no bytes can still trigger the handshake when the content length is unknown.
     * An explicit content length of zero bypasses the handshake. A method-preserving redirect received after an upload
     * can invoke the handler again to replay the entity.
     *
     * @param outputStreamConsumer handler that writes and closes the request output stream
     * @return response
     */
    @Override
    Http1ClientResponse outputStream(OutputStreamHandler outputStreamConsumer);

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
