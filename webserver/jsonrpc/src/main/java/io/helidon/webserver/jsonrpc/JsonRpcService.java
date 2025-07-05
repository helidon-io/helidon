/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.webserver.jsonrpc;

import io.helidon.webserver.ServerLifecycle;

/**
 * An interface that must be implemented by all JSON-RPC services. The single
 * {@link #routing} method is used to update the routes.
 */
@FunctionalInterface
public interface JsonRpcService extends ServerLifecycle {

    /**
     * Update JSON-RPC rules for this service.
     *
     * @param rules the rules to update
     */
    void routing(JsonRpcRules rules);
}
