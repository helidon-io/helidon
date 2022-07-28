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

package io.helidon.tests.integration.restclient;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.net.URI;

/**
 * Replaces 8080 port by ephemeral port allocated for the webserver.
 */
public class GreetResourceFilter implements ClientRequestFilter  {

    @Context
    UriInfo uriInfo;

    @Override
    public void filter(ClientRequestContext requestContext) throws IOException {
        URI uri = requestContext.getUri();
        String fixedUri = uri.toString().replace("8080", extractDynamicPort());
        requestContext.setUri(URI.create(fixedUri));
    }

    private String extractDynamicPort() {
        String uriString = uriInfo.getBaseUri().toString();
        int k = uriString.lastIndexOf(":");
        int j = uriString.indexOf("/", k);
        return uriString.substring(k + 1, j);
    }
}
