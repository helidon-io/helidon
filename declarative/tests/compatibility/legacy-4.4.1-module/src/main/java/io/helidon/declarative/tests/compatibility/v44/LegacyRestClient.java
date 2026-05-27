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

package io.helidon.declarative.tests.compatibility.v44;

import java.util.Optional;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.faulttolerance.Ft;
import io.helidon.http.Http;
import io.helidon.webclient.api.RestClient;

@SuppressWarnings("deprecation")
@RestClient.Endpoint("/")
@Http.Path("/legacy")
public interface LegacyRestClient {
    @Http.GET
    @Http.Path("/hello/{name}")
    @Http.Produces(MediaTypes.TEXT_PLAIN_VALUE)
    String hello(@Http.PathParam("name") String name,
                 @Http.HeaderParam("X-Legacy") String header);

    @Http.POST
    @Http.Path("/entity")
    @Http.Consumes(MediaTypes.TEXT_PLAIN_VALUE)
    @Http.Produces(MediaTypes.TEXT_PLAIN_VALUE)
    String entity(@Http.Entity String entity);

    @Http.GET
    @Http.Path("/ft/fallback")
    String fallback();

    @Http.GET
    @Http.Path("/ft/retry")
    @Ft.Retry(calls = 2, delay = "PT0.01S", overallTimeout = "PT1S")
    String retry();

    @Http.GET
    @Http.Path("/ft/circuit")
    @Ft.CircuitBreaker(name = "legacy-circuit", volume = 2, errorRatio = 50)
    String circuit();

    @Http.GET
    @Http.Path("/ft/timeout")
    @Ft.Timeout(time = "PT1S")
    String timeout(@Http.QueryParam("sleepMillis") Optional<Integer> sleepMillis);

    @Http.GET
    @Http.Path("/ft/bulkhead")
    @Ft.Bulkhead(limit = 1, queueLength = 1)
    String bulkhead();

    @Http.GET
    @Http.Path("/client-header")
    @Http.Produces(MediaTypes.TEXT_PLAIN_VALUE)
    @RestClient.Header(name = "X-Client-Static", value = "legacy-client")
    String clientHeader();
}
