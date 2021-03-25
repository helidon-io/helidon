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
 */

package io.helidon.integrations.oci.telemetry;

import io.helidon.common.http.Http;
import io.helidon.common.reactive.Single;
import io.helidon.integrations.oci.connect.OciRestApi;

class OciMetricsImpl implements OciMetrics {
    private final String apiVersion;
    private final OciRestApi restAccess;

    OciMetricsImpl(Builder builder) {
        this.restAccess = builder.restAccess();
        this.apiVersion = builder.apiVersion();
    }

    @Override
    public Single<PostMetricData.Response> postMetricData(PostMetricData.Request request) {
        String apiPath = "/" + apiVersion + "/metrics";

        return restAccess.invokeWithResponse(Http.Method.POST, apiPath,
                                             request.hostPrefix(OciMetrics.API_HOST_PREFIX),
                                             PostMetricData.Response.builder());
    }
}
