/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.metadata;

import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;

public class MetadataTest {

    @Test
    public void testDefaultDiscovery() {
        MetadataDiscovery metadata = MetadataDiscovery.create(MetadataDiscovery.Mode.RESOURCES);

        var serviceLoaders = metadata.list("service.loader")
                .stream()
                .map(MetadataFile::location)
                .collect(Collectors.toSet());

        assertThat(serviceLoaders,
                   hasItems("META-INF/helidon/service.loader",
                            "META-INF/helidon/io.helidon.metadata/service.loader"));

        var serviceRegistries = metadata.list("service-registry.json")
                .stream()
                .map(MetadataFile::location)
                .collect(Collectors.toSet());

        assertThat(serviceRegistries,
                   hasItems("META-INF/helidon/service-registry.json",
                            "META-INF/helidon/io.helidon.metadata/service-registry.json"));
    }

    @Test
    public void testClasspathScanning() {
        MetadataDiscovery metadata = MetadataDiscoveryImpl.
                createFromClasspathScanning(MetadataDiscoveryContext.create(MetadataTest.class.getClassLoader()));

        var serviceLoaders = metadata.list("service.loader")
                .stream()
                .map(MetadataFile::location)
                .collect(Collectors.toSet());

        assertThat(serviceLoaders,
                   hasItems("META-INF/helidon/service.loader",
                            "META-INF/helidon/io.helidon.metadata/service.loader"));

        var serviceRegistries = metadata.list("service-registry.json")
                .stream()
                .map(MetadataFile::location)
                .collect(Collectors.toSet());

        assertThat(serviceRegistries,
                   hasItems("META-INF/helidon/service-registry.json",
                            "META-INF/helidon/io.helidon.metadata/service-registry.json"));
    }
}
