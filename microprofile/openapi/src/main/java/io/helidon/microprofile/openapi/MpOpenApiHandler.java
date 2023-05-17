/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.microprofile.openapi;

import io.helidon.nima.webserver.http.Handler;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;

class MpOpenApiHandler implements Handler {

    private static final String USE_JAXRS_SEMANTICS_CONFIG_KEY = "use-jaxrs-semantics";

    private static final String USE_JAXRS_SEMANTICS_FULL_CONFIG_KEY =
            "mp.openapi.extensions.helidon." + USE_JAXRS_SEMANTICS_CONFIG_KEY;
    private static final boolean USE_JAXRS_SEMANTICS_DEFAULT = true;

    private boolean useJaxRsSemantics = USE_JAXRS_SEMANTICS_DEFAULT;

    @Override
    public void handle(ServerRequest req, ServerResponse res) throws Exception {

    }
}
