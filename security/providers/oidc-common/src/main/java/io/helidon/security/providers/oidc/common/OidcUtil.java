/*
 * Copyright (c) 2021, 2025 Oracle and/or its affiliates.
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

package io.helidon.security.providers.oidc.common;

import java.lang.System.Logger.Level;

import io.helidon.common.Errors;

final class OidcUtil {
    private static final System.Logger LOGGER = System.getLogger(OidcUtil.class.getName());

    private OidcUtil() {
    }

    static String fixServerType(String serverType) {
        if (serverType != null) {
            // explicit server type
            if (!"idcs".equals(serverType) && !OidcConfig.Builder.DEFAULT_SERVER_TYPE.equals(serverType)) {
                LOGGER.log(Level.WARNING, "OIDC server-type is configured to " + serverType + ", currently only \"idcs\", and"
                                       + " \"" + OidcConfig.Builder.DEFAULT_SERVER_TYPE + "\" are supported");
                return OidcConfig.Builder.DEFAULT_SERVER_TYPE;
            }
        } else {
            return OidcConfig.Builder.DEFAULT_SERVER_TYPE;
        }
        return serverType;
    }

    static void validateExists(Errors.Collector collector, Object value, String name, String configKey) {
        // validate
        if (value == null) {
            collector.fatal(name + " must be configured (\"" + configKey + "\" key in config)");
        }
    }
}
