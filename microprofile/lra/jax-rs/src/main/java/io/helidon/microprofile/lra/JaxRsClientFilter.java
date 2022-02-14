/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates.
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

package io.helidon.microprofile.lra;

import java.io.IOException;
import java.net.URI;
import java.util.stream.Collectors;

import io.helidon.common.context.Contexts;
import io.helidon.lra.coordinator.client.PropagatedHeaders;

import jakarta.ws.rs.ConstrainedTo;
import jakarta.ws.rs.RuntimeType;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.MultivaluedMap;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;

@ConstrainedTo(RuntimeType.CLIENT)
class JaxRsClientFilter implements ClientRequestFilter {
    @Override
    public void filter(final ClientRequestContext reqCtx) throws IOException {
        MultivaluedMap<String, Object> headers = reqCtx.getHeaders();

        // LRA context
        if (!headers.containsKey(LRA_HTTP_CONTEXT_HEADER)) {
            Contexts.context()
                    .flatMap(c -> c.get(LRA_HTTP_CONTEXT_HEADER, URI.class))
                    .ifPresent(lraId ->
                            // no explicit lraId header, add the one saved in Helidon context
                            headers.putSingle(LRA_HTTP_CONTEXT_HEADER, lraId.toASCIIString()));
        }

        // Custom header propagation
        Contexts.context()
                .flatMap(c -> c.get(PropagatedHeaders.class.getName(), PropagatedHeaders.class))
                .map(PropagatedHeaders::toMap)
                .ifPresent(propagatedHeaders ->
                        propagatedHeaders.forEach((key, values) -> {
                            // User defined headers have priority
                            if (!headers.containsKey(key)) {
                                headers.put(key, values.stream().map(Object.class::cast).collect(Collectors.toList()));
                            }
                        }));
    }
}
