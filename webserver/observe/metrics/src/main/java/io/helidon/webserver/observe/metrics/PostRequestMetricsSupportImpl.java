/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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
package io.helidon.webserver.observe.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

class PostRequestMetricsSupportImpl implements PostRequestMetricsSupport {

    private static final System.Logger LOGGER = System.getLogger(PostRequestMetricsSupportImpl.class.getName());

    private static boolean loggedMessageAboutMissingKpiDataStructure = false;

    static PostRequestMetricsSupportImpl create() {
        return new PostRequestMetricsSupportImpl();
    }

    // @Deprecated - We should be able to remote this and its call once we update how KPI metrics are handled on multiple sockets.
    @Deprecated(forRemoval = true, since = "4.2.1")
    static void logMessageAboutMissingKpiDataStructure() {
        if (!loggedMessageAboutMissingKpiDataStructure) {
            loggedMessageAboutMissingKpiDataStructure = true;
            LOGGER.log(System.Logger.Level.WARNING,
                       """
                               An expected metrics data structure is missing from the request context;
                               an MP application might have set metrics.rest-request.enabled=true instead of assigning the proper
                               key mp.metrics.rest-request.enabled=true. This message will not be repeated.""");
        }
    }

    private final List<BiConsumer<ServerResponse, Throwable>> tasks = new ArrayList<>();

    private PostRequestMetricsSupportImpl() {
    }

    @Override
    public void registerPostRequestWork(BiConsumer<ServerResponse, Throwable> task) {
        tasks.add(task);
    }

    @Override
    public void runTasks(ServerRequest request, ServerResponse response, Throwable throwable) {
        Exception e = request.context().get("unmappedException", Exception.class).orElse(null);
        tasks.forEach(t -> t.accept(response, e != null ? e : throwable));
    }
}
