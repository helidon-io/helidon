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

package io.helidon.common.features;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.common.features.api.HelidonFlavor;
import io.helidon.common.features.metadata.FeatureRegistry;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;

class HelidonFeaturesLoggingTest {
    @Test
    void detailedTreeUsesLogger() throws Exception {
        // Features are scanned once per class loader. Isolate this catalog from the empty-catalog tests.
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try (var classLoader = new FeatureClassLoader()) {
            Thread.currentThread().setContextClassLoader(classLoader);
            var test = (Runnable) classLoader.loadClass(LogFeatureTree.class.getName())
                    .getConstructor()
                    .newInstance();
            test.run();
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    public static class LogFeatureTree implements Runnable {
        @Override
        public void run() {
            var logger = Logger.getLogger(HelidonFeatures.class.getName());
            Level originalLevel = logger.getLevel();
            boolean originalUseParentHandlers = logger.getUseParentHandlers();
            List<LogRecord> records = new ArrayList<>();
            var handler = new Handler() {
                @Override
                public void publish(LogRecord record) {
                    records.add(record);
                }

                @Override
                public void flush() {
                }

                @Override
                public void close() {
                }
            };
            logger.addHandler(handler);
            logger.setLevel(Level.INFO);
            logger.setUseParentHandlers(false);
            try {
                HelidonFeatures.features(HelidonFlavor.SE, "VERSION", true);

                assertThat(records.stream().map(LogRecord::getMessage).toList(), contains(
                        "Helidon SE VERSION features: [Test Root]",
                        "Detailed feature tree:",
                        "Test Root           Root description",
                        "  Undescribed",
                        "    Test Child      Child description (NOT SUPPORTED in native image)"));
                assertThat(records.stream().map(LogRecord::getLevel).toList(), everyItem(is(Level.INFO)));
            } finally {
                logger.removeHandler(handler);
                logger.setLevel(originalLevel);
                logger.setUseParentHandlers(originalUseParentHandlers);
            }
        }
    }

    private static class FeatureClassLoader extends URLClassLoader {
        private FeatureClassLoader() {
            super(new URL[] {
                    HelidonFeatures.class.getProtectionDomain().getCodeSource().getLocation(),
                    HelidonFeaturesLoggingTest.class.getProtectionDomain().getCodeSource().getLocation()
            }, HelidonFeaturesLoggingTest.class.getClassLoader());
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (name.equals(FeatureRegistry.FEATURE_REGISTRY_LOCATION_V1)) {
                return Collections.enumeration(List.of(getResource("feature-tree/root.properties"),
                                                       getResource("feature-tree/child.properties")));
            }
            return super.getResources(name);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.equals(HelidonFeatures.class.getName())
                    || name.startsWith(HelidonFeatures.class.getName() + "$")
                    || name.equals(FeatureCatalog.class.getName())
                    || name.startsWith(LogFeatureTree.class.getName())) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    loaded = findClass(name);
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
            return super.loadClass(name, resolve);
        }
    }
}
