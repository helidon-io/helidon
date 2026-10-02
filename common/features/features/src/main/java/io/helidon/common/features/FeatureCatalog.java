/*
 * Copyright (c) 2020, 2026 Oracle and/or its affiliates.
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.helidon.common.features.metadata.FeatureMetadata;
import io.helidon.common.features.metadata.FeatureRegistry;
import io.helidon.metadata.MetadataConstants;
import io.helidon.metadata.MetadataDiscovery;
import io.helidon.metadata.hson.Hson;

/**
 * Feature catalog discovers features from Helidon JSON feature registries.
 */
final class FeatureCatalog {
    private static final System.Logger LOGGER = System.getLogger(FeatureCatalog.class.getName());

    // hide utility class constructor
    private FeatureCatalog() {
    }

    static List<FeatureMetadata> features(ClassLoader classLoader) {
        Map<String, FeatureMetadata> features = new LinkedHashMap<>();
        var thread = Thread.currentThread();
        ClassLoader original = thread.getContextClassLoader();
        MetadataDiscovery discovery;
        try {
            thread.setContextClassLoader(classLoader);
            discovery = MetadataDiscovery.instance();
        } finally {
            thread.setContextClassLoader(original);
        }
        discovery.list(MetadataConstants.FEATURE_REGISTRY_FILE)
                .forEach(metadatum -> {
                    Hson.Array hson;
                    try (InputStream in = metadatum.inputStream()) {
                        hson = Hson.parse(in)
                                .asArray();
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Failed to read features from " + metadatum.absoluteLocation(), e);
                        return;
                    }

                    List<FeatureMetadata> metadatas = FeatureRegistry.metadata("Classpath: "
                                                                                       + metadatum.absoluteLocation(), hson);
                    for (FeatureMetadata metadata : metadatas) {
                        features.putIfAbsent(metadata.name(), metadata);
                    }
                });
        return orderFeatureMetadata(features);
    }

    private static List<FeatureMetadata> orderFeatureMetadata(Map<String, FeatureMetadata> features) {
        List<FeatureMetadata> result = new ArrayList<>(features.values());
        result.sort((first, second) -> {
            List<String> path = first.path();
            List<String> path2 = second.path();

            for (int i = 0; i < path.size() && i < path2.size(); i++) {
                int comparison = path.get(i).compareTo(path2.get(i));
                if (comparison != 0) {
                    return comparison;
                }
            }
            // same base path
            return path.size() - path2.size();
        });
        return result;
    }
}
