/*
 * Copyright (c) 2020, 2025 Oracle and/or its affiliates.
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
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import io.helidon.common.features.metadata.FeatureMetadata;
import io.helidon.common.features.metadata.FeatureRegistry;
import io.helidon.metadata.hson.Hson;

/**
 * Feature catalog discovers features from META-INF/helidon/feature-metadata.properties.
 */
final class FeatureCatalog {
    private static final System.Logger LOGGER = System.getLogger(FeatureCatalog.class.getName());

    // hide utility class constructor
    private FeatureCatalog() {
    }

    static List<FeatureMetadata> features(ClassLoader classLoader) {
        Map<String, FeatureMetadata> features = new LinkedHashMap<>();

        try {
            Enumeration<URL> hsons = classLoader.getResources(FeatureRegistry.FEATURE_REGISTRY_LOCATION_V2);
            while (hsons.hasMoreElements()) {
                URL url = hsons.nextElement();
                Hson.Array hson;
                try (InputStream in = url.openStream()) {
                    hson = Hson.parse(in)
                            .asArray();
                }

                List<FeatureMetadata> metadatas = FeatureRegistry.metadata("Classpath: " + url, hson);
                for (FeatureMetadata metadata : metadatas) {
                    features.putIfAbsent(metadata.name(), metadata);
                }
            }

            Enumeration<URL> resources = classLoader.getResources(FeatureRegistry.FEATURE_REGISTRY_LOCATION_V1);
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                Properties props = new Properties();
                try (InputStream in = url.openStream()) {
                    props.load(in);
                }
                var metadata = FeatureRegistry.metadata("Classpath: " + url.toString(), props);
                features.putIfAbsent(metadata.name(), metadata);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not discover Helidon features", e);
        }
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
