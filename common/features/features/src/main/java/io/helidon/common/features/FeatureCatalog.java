/*
 * Copyright (c) 2020, 2024 Oracle and/or its affiliates.
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

package io.helidon.common.features;

import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger.Level;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import io.helidon.common.Version;
import io.helidon.common.features.api.HelidonFlavor;

/**
 * Feature catalog discovers features from META-INF/helidon/feature-metadata.properties.
 */
final class FeatureCatalog {
    private static final System.Logger LOGGER = System.getLogger(FeatureCatalog.class.getName());
    private static final HelidonFlavor[] NO_FLAVORS = new HelidonFlavor[0];

    // hide utility class constructor
    private FeatureCatalog() {
    }

    static List<FeatureDescriptor> features(ClassLoader classLoader) {
        List<FeatureDescriptor> features = new LinkedList<>();
        try {
            Enumeration<URL> resources = classLoader.getResources("META-INF/helidon/feature-metadata.properties");
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                Properties props = new Properties();
                try (InputStream in = url.openStream()) {
                    props.load(in);
                }
                String module = props.getProperty("m");
                if (module == null) {
                    LOGGER.log(Level.WARNING, "Got module descriptor with no module name. Available properties: " + props
                            + " at " + url);
                    continue;
                }
                FeatureDescriptor.Builder builder = FeatureDescriptor.builder();
                builder.name(props.getProperty("n", module))
                        .module(module)
                        .description(props.getProperty("d", ""))
                        .path(toArray(props.getProperty("p"), props.getProperty("n")))
                        .flavor(toFlavor(module, props.getProperty("in"), true))
                        .notFlavor(toFlavor(module, props.getProperty("not"), false))
                        .since(props.getProperty("s", "1.0.0"));

                if ("true".equals(props.getProperty("pr"))) {
                    builder.preview(true);
                }
                if ("false".equals(props.getProperty("aot"))) {
                    builder.nativeSupported(false);
                }
                if ("true".equals(props.getProperty("i"))) {
                    builder.incubating(true);
                }
                if ("true".equals(props.getProperty("dep"))) {
                    builder.deprecated(true);
                    builder.deprecatedSince(props.getProperty("deps", Version.VERSION));
                }
                String aotDescription = props.getProperty("aotd");
                if (aotDescription != null) {
                    builder.nativeDescription(aotDescription);
                }
                features.add(builder.build());
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not discover Helidon features", e);
        }
        Collections.sort(features);
        return features;
    }

    private static HelidonFlavor[] toFlavor(String module, String flavorString, boolean useAllIfMissing) {
        if (flavorString == null || flavorString.isBlank()) {
            return useAllIfMissing ? HelidonFlavor.values() : NO_FLAVORS;
        }
        String[] values = toArray(flavorString, flavorString);
        HelidonFlavor[] result = new HelidonFlavor[values.length];
        for (int i = 0; i < values.length; i++) {
            try {
                result[i] = HelidonFlavor.valueOf(values[i]);
            } catch (IllegalArgumentException e) {
                LOGGER.log(Level.ERROR, "Invalid flavor defined: " + values[i] + " in module " + module);
                return NO_FLAVORS;
            }
        }
        return result;
    }

    private static String[] toArray(String property, String defaultValue) {
        String toProcess = property;
        if (property == null) {
            toProcess = defaultValue;
        }
        if (toProcess == null) {
            return new String[0];
        }
        return toProcess.split(",");
    }
}
