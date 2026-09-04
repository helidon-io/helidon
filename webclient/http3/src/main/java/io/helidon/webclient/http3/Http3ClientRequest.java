/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webclient.http3;

import io.helidon.common.Api;
import io.helidon.common.buffers.BufferData;
import io.helidon.webclient.api.ClientRequest;

/**
 * Request of HTTP/3 client.
 *
 * <p>Request bodies written through {@link #outputStream(OutputStreamHandler)} are one-shot once the output stream
 * handler starts. An entity submitted through {@link #submit(Object)} is also one-shot when its writer streams the
 * encoded entity, such as when its length is unknown or exceeds the configured in-memory entity limit. A one-shot body
 * that has started cannot be replayed for an automatic retry, a TCP protocol fallback, or a {@code 307}/{@code 308}
 * redirect; the request fails instead. Materialized request bodies can be replayed for those operations.
 */
@Api.Incubating
public interface Http3ClientRequest extends ClientRequest<Http3ClientRequest> {
    /**
     * Configure prior knowledge of HTTP/3.
     *
     * @param priorKnowledge set to {@code true} to fail instead of falling back to a configured TCP protocol
     * @return updated request
     */
    Http3ClientRequest priorKnowledge(boolean priorKnowledge);

    @Override
    default Http3ClientResponse request() {
        return submit(BufferData.EMPTY_BYTES);
    }

    @Override
    Http3ClientResponse submit(Object entity);

    @Override
    Http3ClientResponse outputStream(OutputStreamHandler outputStreamConsumer);
}
