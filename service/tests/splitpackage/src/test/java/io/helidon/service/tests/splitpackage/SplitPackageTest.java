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

package io.helidon.service.tests.splitpackage;

import java.util.List;

import io.helidon.service.registry.ServiceRegistry;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;

@Testing.Test
public class SplitPackageTest {
    private final ServiceRegistry registry;

    public SplitPackageTest(ServiceRegistry registry) {
        this.registry = registry;
    }

    @Test
    public void testSplitPackage() {
        List<MyContract> services = registry.all(MyContract.class);

        assertThat(services, hasSize(2));
        assertThat("Test service has higher weight, should be first", services.get(0).name(), is("2"));
        assertThat(services.get(1).name(), is("1"));
    }
}
