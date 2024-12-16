/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.webserver.http.spi;

import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.http.ServerResponse;

/**
 * A context for {@link io.helidon.webserver.http.spi.SinkProvider}s supplied
 * at creation time.
 */
public interface SinkProviderContext {

    /**
     * Obtains the server response associated with this context.
     *
     * @return the server response
     */
    ServerResponse serverResponse();

    /**
     * Obtains access to the connection context.
     *
     * @return the connection context
     */
    ConnectionContext connectionContext();

    /**
     * Runnable to execute to close the response.
     *
     * @return the close runnable
     */
    Runnable closeRunnable();
}
