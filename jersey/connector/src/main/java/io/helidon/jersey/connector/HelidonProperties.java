/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.jersey.connector;

import io.helidon.config.Config;
import io.helidon.webclient.WebClient;

/**
 * Configuration options specific to the Client API that utilizes {@link HelidonConnector}.
 */
public final class HelidonProperties {

    private HelidonProperties() {
    }

    /**
     * A Helidon {@link Config} instance that is passed to {@link WebClient.Builder#config(Config)} if available.
     * This property is settable on {@link javax.ws.rs.core.Configurable#property(String, Object)} objects.
     */
    public static final String CONFIG = "jersey.connector.helidon.config";
}
