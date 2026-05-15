/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver;

/**
 * Basic server lifecycle operations.
 */
public interface ServerLifecycle {
    /**
     * Before server start.
     * If this method throws an exception, server startup fails and the server attempts startup cleanup.
     */
    default void beforeStart() {
    }

    /**
     * After server start.
     * If this method throws an exception, server startup fails and the server attempts startup cleanup.
     *
     * @param webServer the {@link WebServer} that was started
     */
    default void afterStart(WebServer webServer) {
    }

    /**
     * After server stop.
     * Exceptions thrown by this method fail {@link WebServer#stop()} after listener cleanup has been attempted.
     * If this method is invoked during startup cleanup, exceptions may fail {@link WebServer#start()}.
     * If this method is invoked during suspend or resume failure cleanup, exceptions may be suppressed on the original
     * suspend or resume failure.
     */
    default void afterStop() {
    }
}
