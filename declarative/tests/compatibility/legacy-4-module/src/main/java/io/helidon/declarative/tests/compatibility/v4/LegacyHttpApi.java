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

package io.helidon.declarative.tests.compatibility.v4;

import java.util.Optional;

import io.helidon.common.Default;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.Http;
import io.helidon.webserver.http.RestServer;

@SuppressWarnings("deprecation")
@Http.Path("/legacy")
public interface LegacyHttpApi {
    @Http.GET
    @Http.Path("/hello/{name}")
    @Http.Produces(MediaTypes.TEXT_PLAIN_VALUE)
    @RestServer.Header(name = "X-Legacy-Static", value = "legacy-static")
    @RestServer.ComputedHeader(name = LegacyHeaderFunction.HEADER_NAME, function = LegacyHeaderFunction.SERVICE_NAME)
    String hello(@Http.PathParam("name") String name,
                 @Http.QueryParam("prefix") @Default.Value("Hello") String prefix,
                 @Http.HeaderParam("X-Legacy") String header);

    @Http.POST
    @Http.Path("/entity")
    @Http.Consumes(MediaTypes.TEXT_PLAIN_VALUE)
    @Http.Produces(MediaTypes.TEXT_PLAIN_VALUE)
    String entity(@Http.Entity String entity);

    @Http.GET
    @Http.Path("/optional")
    @Http.Produces(MediaTypes.TEXT_PLAIN_VALUE)
    Optional<String> optional();

    @Http.GET
    @Http.Path("/ft/fallback")
    String fallback();

    @Http.GET
    @Http.Path("/ft/retry")
    String retry();

    @Http.GET
    @Http.Path("/ft/circuit")
    String circuit();

    @Http.GET
    @Http.Path("/ft/timeout")
    String timeout(@Http.QueryParam("sleepMillis") Optional<Integer> sleepMillis);

    @Http.GET
    @Http.Path("/ft/bulkhead")
    String bulkhead();
}
