/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.inject.api;

/**
 * Some components may require start when Helidon is bootstrapped, such as WebServer (to open server sockets).
 * This interface is a Helidon Injection contract, that allows us to discover all startable services and start them
 * on boot when desired.
 * <p>
 * This contract should be used for cases where the construction of the object (using constructor, and maybe
 * {@link jakarta.annotation.PostConstruct} - where we create a fully configured instance) is different from its start
 * transition (such as opening sockets, connecting to remote messaging queues, streams, topics, etc.).
 *
 * @see Helidon#start()
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
@Contract
public interface Startable {
    /**
     * Start this service.
     */
    void startService();
}
