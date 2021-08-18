/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
 *
 */

package io.helidon.microprofile.lra;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;

import javax.ws.rs.ConstrainedTo;
import javax.ws.rs.RuntimeType;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;

import io.helidon.common.context.Contexts;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;

@ConstrainedTo(RuntimeType.CLIENT)
class JaxRsClientFilter implements ClientRequestFilter {
    @Override
    public void filter(final ClientRequestContext requestContext) throws IOException {
        Optional<URI> lraId = Contexts.context().flatMap(c -> c.get(LRA_HTTP_CONTEXT_HEADER, URI.class));
        if ((!requestContext.getHeaders().containsKey(LRA_HTTP_CONTEXT_HEADER)) && lraId.isPresent()) {
            // no explicit lraId header, add the one saved in thread local
            requestContext.getHeaders().putSingle(LRA_HTTP_CONTEXT_HEADER, lraId.get().toASCIIString());
        }
    }
}
