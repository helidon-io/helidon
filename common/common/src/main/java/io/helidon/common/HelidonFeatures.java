/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * A java service loader interface defining a Helidon feature.
 */
public final class HelidonFeatures {
    private static final Logger LOGGER = Logger.getLogger(HelidonFeatures.class.getName());

    private static final Map<HelidonFlavor, Set<String>> FEATURES = new EnumMap<>(HelidonFlavor.class);
    private static final AtomicReference<HelidonFlavor> CURRENT_FLAVOR = new AtomicReference<>();
    private static final AtomicBoolean PRINTED = new AtomicBoolean();

    private HelidonFeatures() {
    }

    /**
     * Register a feature for a flavor.
     * This should be called from a static initializer of a feature
     * class. In SE this would be one of the *Support classes or similar,
     * in MP most likely a CDI extension class.
     *
     * @param flavor flavor to register a feature for
     * @param name name of the feature
     */
    public static void register(HelidonFlavor flavor, String name) {
        FEATURES.computeIfAbsent(flavor, key -> new HashSet<>())
                .add(name);
    }

    /**
     * Print features for the current flavor.
     * If {@link #flavor(HelidonFlavor)} is called, this method
     * would only print the list if it matches the flavor provided.
     * This is to make sure we do not print SE flavors in MP, and at the
     * same time can have this method used from Web Server.
     * This method only prints feature the first time it is called.
     * @param flavor flavor to print features for
     */
    public static void print(HelidonFlavor flavor) {
        CURRENT_FLAVOR.compareAndSet(null, HelidonFlavor.SE);

        HelidonFlavor currentFlavor = CURRENT_FLAVOR.get();

        if (currentFlavor != flavor) {
            return;
        }

        if (PRINTED.compareAndSet(false, true)) {
            Set<String> strings = FEATURES.get(currentFlavor);
            if (null == strings) {
                LOGGER.info("Helidon " + currentFlavor + " " + Version.VERSION + " has no registered features");
            } else {
                LOGGER.info("Helidon " + currentFlavor + " " + Version.VERSION + " features: " + strings);
            }
        }
    }

    /**
     * Set the current Helidon flavor. Features will only be printed for the
     * flavor configured.
     *
     * @param flavor current flavor
     */
    public static void flavor(HelidonFlavor flavor) {
        CURRENT_FLAVOR.compareAndSet(null, flavor);
    }
}
