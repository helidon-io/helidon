/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.common;

import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * Utility methods to help with loading of java services (mostly SPI related).
 */
public final class SpiHelper {
    private SpiHelper() {
    }

    /**
     * Loads the first service implementation or throw an exception if nothing found.
     *
     * @param service the service class to load
     * @param <T>     service type
     * @return the loaded service
     * @throws IllegalStateException if none implementation found
     * @deprecated Use direct access to {@link ServiceLoader} or have such a helper in your module, as from jigsaw this is not
     * allowed
     */
    @Deprecated
    public static <T> T loadSpi(Class<T> service) {
        ServiceLoader<T> servers = ServiceLoader.load(service);
        Iterator<T> serversIt = servers.iterator();
        if (serversIt.hasNext()) {
            return serversIt.next();
        } else {
            throw new IllegalStateException("No implementation found for SPI: " + service.getName());
        }
    }
}
