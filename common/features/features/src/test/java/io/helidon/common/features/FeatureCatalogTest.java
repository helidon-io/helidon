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

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;

import io.helidon.common.features.metadata.FeatureMetadata;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FeatureCatalogTest {
    @Test
    void discoversJsonAndIgnoresLegacyPropertiesUsingSuppliedClassLoader() throws Exception {
        var thread = Thread.currentThread();
        ClassLoader original = thread.getContextClassLoader();
        URL fixtures = getClass().getClassLoader().getResource("feature-tree/");
        try (var supplied = new URLClassLoader(new URL[] {fixtures}, null);
             var context = new URLClassLoader(new URL[0], null)) {
            thread.setContextClassLoader(context);
            assertThat(supplied.getResource("META-INF/helidon/feature-metadata.properties"), notNullValue());

            var features = FeatureCatalog.features(supplied);

            assertThat(features.stream().map(FeatureMetadata::module).toList(), contains("test.root", "test.child"));
            assertThat(thread.getContextClassLoader(), sameInstance(context));
        } finally {
            thread.setContextClassLoader(original);
        }
    }

    @Test
    void restoresContextClassLoaderWhenDiscoveryFails() {
        var thread = Thread.currentThread();
        ClassLoader original = thread.getContextClassLoader();
        var failure = new IllegalStateException("Cannot discover metadata resources");
        var supplied = new ClassLoader(null) {
            @Override
            public Enumeration<URL> getResources(String name) {
                throw failure;
            }
        };

        try {
            assertThat(assertThrows(IllegalStateException.class, () -> FeatureCatalog.features(supplied)), sameInstance(failure));
            assertThat(thread.getContextClassLoader(), sameInstance(original));
        } finally {
            thread.setContextClassLoader(original);
        }
    }
}
