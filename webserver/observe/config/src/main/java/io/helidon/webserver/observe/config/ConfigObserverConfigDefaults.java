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

package io.helidon.webserver.observe.config;

import java.util.Set;

final class ConfigObserverConfigDefaults {
    static final String SECRET_PASSWORD = ".*password";
    static final String SECRET_PASSPHRASE = ".*passphrase";
    static final String SECRET_SECRET = ".*secret";
    static final Set<String> SECRETS = Set.of(SECRET_PASSWORD,
                                              SECRET_PASSPHRASE,
                                              SECRET_SECRET);

    static final String SAFE_KEY_SERVER_HOST = "server[.]host";
    static final String SAFE_KEY_SERVER_PORT = "server[.]port";
    static final String SAFE_KEY_SERVER_SOCKET_HOST = "server[.]sockets[.][^.]+[.]host";
    static final String SAFE_KEY_SERVER_SOCKET_PORT = "server[.]sockets[.][^.]+[.]port";
    static final String SAFE_KEY_OBSERVE_ENABLED = "server[.]features[.]observe[.]enabled";
    static final String SAFE_KEY_OBSERVE_ENDPOINT = "server[.]features[.]observe[.]endpoint";
    static final String SAFE_KEY_OBSERVE_SOCKETS = "server[.]features[.]observe[.]sockets";
    static final String SAFE_KEY_OBSERVE_WEIGHT = "server[.]features[.]observe[.]weight";
    static final String SAFE_KEY_OBSERVER_ENABLED = "server[.]features[.]observe[.]observers[.][^.]+[.]enabled";
    static final String SAFE_KEY_OBSERVER_ENDPOINT = "server[.]features[.]observe[.]observers[.][^.]+[.]endpoint";
    static final String SAFE_KEY_OBSERVER_NAME = "server[.]features[.]observe[.]observers[.][^.]+[.]name";
    static final Set<String> SAFE_KEYS = Set.of(SAFE_KEY_SERVER_HOST,
                                                SAFE_KEY_SERVER_PORT,
                                                SAFE_KEY_SERVER_SOCKET_HOST,
                                                SAFE_KEY_SERVER_SOCKET_PORT,
                                                SAFE_KEY_OBSERVE_ENABLED,
                                                SAFE_KEY_OBSERVE_ENDPOINT,
                                                SAFE_KEY_OBSERVE_SOCKETS,
                                                SAFE_KEY_OBSERVE_WEIGHT,
                                                SAFE_KEY_OBSERVER_ENABLED,
                                                SAFE_KEY_OBSERVER_ENDPOINT,
                                                SAFE_KEY_OBSERVER_NAME);

    private ConfigObserverConfigDefaults() {
    }
}
