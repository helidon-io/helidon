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

package io.helidon.webserver.spi;

import java.util.concurrent.CompletionStage;

/**
 * Protocol codec capability for isolating a failure to an individual response.
 * Implementations are channel handlers installed in the connection pipeline.
 * The server invokes this capability on the connection's I/O thread; implementations must not block that thread.
 */
@FunctionalInterface
public interface ResponseFailureHandler {

    /**
     * Abort an individual response while preserving the connection.
     * Normal completion means that the response is terminated at the protocol level and other responses can proceed.
     * If a termination frame is needed, its write must complete before this stage completes.
     * Exceptional completion causes the server to retire the connection instead.
     *
     * @param responseId non-null identifier assigned to the response by the protocol codec
     * @param cause non-null failure preventing response completion
     * @return non-null stage completed when the response has been terminated
     */
    CompletionStage<Void> abortResponse(String responseId, Throwable cause);
}
