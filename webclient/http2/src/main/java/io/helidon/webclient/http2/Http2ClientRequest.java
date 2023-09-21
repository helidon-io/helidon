/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.webclient.http2;

import java.time.Duration;

import io.helidon.common.buffers.BufferData;
import io.helidon.webclient.api.ClientRequest;

/**
 * Request of HTTP/2 client.
 */
public interface Http2ClientRequest extends ClientRequest<Http2ClientRequest> {
    /**
     * Priority defines a weight between 1 and 256 (inclusive) to prioritize this stream by the server.
     * Priorities are a suggestion.
     *
     * @param priority priority to configure for this stream (request/response)
     * @return updated request
     */
    Http2ClientRequest priority(int priority);

    /**
     * Configure prior knowledge of HTTP/2 (e.g. we know the server supports it and we do not need to handle
     * upgrade).
     *
     * @param priorKnowledge set to {@code true} to skip HTTP/1.1 upgrade process and use prior knowledge
     * @return updated request
     */
    Http2ClientRequest priorKnowledge(boolean priorKnowledge);

    /**
     * Configure request prefetch, increases both connection and stream inbound flow control window
     * and may send window updates to inform the server, depending on flow control strategy.
     *
     * @param requestPrefetch inbound flow control window increment in bytes
     * @return updated request
     */
    Http2ClientRequest requestPrefetch(int requestPrefetch);

    /**
     * Outbound flow control blocking timeout.
     *
     * @param timeout duration
     * @return updated request
     */
    Http2ClientRequest flowControlTimeout(Duration timeout);

    @Override
    default Http2ClientResponse request() {
        return submit(BufferData.EMPTY_BYTES);
    }

    @Override
    Http2ClientResponse submit(Object entity);

    @Override
    Http2ClientResponse outputStream(OutputStreamHandler outputStreamConsumer);
}
