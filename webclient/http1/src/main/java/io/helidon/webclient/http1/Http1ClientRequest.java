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

import io.helidon.common.Api;
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
     * Submit an output-stream request after an enclosing protocol has already followed redirects.
     *
     * @param outputStreamConsumer output stream handler
     * @param followedRedirects absolute number of redirects already followed
     * @return client response
     */
    @Api.Internal
    default Http1ClientResponse outputStream(OutputStreamHandler outputStreamConsumer, int followedRedirects) {
        if (followedRedirects != 0) {
            throw new UnsupportedOperationException("Initial redirect count is not supported by this HTTP/1 request");
        }
        return outputStream(outputStreamConsumer);
    }

    /**
     * Defer storage of returned response cookies to an enclosing protocol request.
     */
    @Api.Internal
    default void deferResponseCookies() {
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
