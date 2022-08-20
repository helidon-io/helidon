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
package io.helidon.webserver;

import java.util.logging.Logger;

/**
 * Initializes Netty's maxOrder property. Reduces default value of 11 to avoid
 * allocating too much native buffer memory.
 */
class NettyInitializer {
    private static final Logger LOGGER = Logger.getLogger(NettyInitializer.class.getName());

    private static final String HELIDON_MAXORDER_DEFAULT = "6";
    private static final String NETTY_MAXORDER_PROPERTY = "io.netty.allocator.maxOrder";
    static {
        if (System.getProperty(NETTY_MAXORDER_PROPERTY) == null) {
            LOGGER.fine("Setting " + NETTY_MAXORDER_PROPERTY + " to " + HELIDON_MAXORDER_DEFAULT + " by default");
            System.setProperty(NETTY_MAXORDER_PROPERTY, HELIDON_MAXORDER_DEFAULT);
        }
    }

    private NettyInitializer() {
    }

    static String getMaxOrderProperty() {
        return NETTY_MAXORDER_PROPERTY;
    }

    static String getMaxOrderValue() {
        return System.getProperty(NETTY_MAXORDER_PROPERTY);
    }
}
