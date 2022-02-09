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
package io.helidon.metrics.serviceapi;

import java.util.function.BiConsumer;

import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;

/**
 * Encapsulates metrics-related post-request processing that other components use and factory methods for creating instances of
 * the related context.
 */
public interface PostRequestMetricsSupport {

    /**
     * Creates a new instance.
     *
     * @return new instance
     */
    static PostRequestMetricsSupport create() {
        return PostRequestMetricsSupportImpl.create();
    }

    /**
     * Records a post-processing task to be performed once the response has been sent to the client.
     *
     * @param request {@code ServerRequest} with which to associate the post-processing task
     * @param task the work to perform
     */
    static void recordPostProcessingWork(ServerRequest request, BiConsumer<ServerResponse, Throwable> task) {
        PostRequestMetricsSupport prms = request.context()
                .get(PostRequestMetricsSupport.class)
                .orElseThrow();

        prms.registerPostRequestWork(task);
    }

    /**
     * Records post-request processing to be performed once the server sends the response to the client.
     *
     * @param task the work to perform
     */
    void registerPostRequestWork(BiConsumer<ServerResponse, Throwable> task);

    /**
     * Run the post-processing tasks.
     *
     * @param request the {@code ServerRequest} from the client
     * @param response the {@code ServerResponse} already sent to the client
     * @param throwable the {@code Throwable} for any problem encountered in preparing the response; null if successful
     */
    void runTasks(ServerRequest request, ServerResponse response, Throwable throwable);
}
