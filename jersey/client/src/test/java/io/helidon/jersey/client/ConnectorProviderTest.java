/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.jersey.client;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.ServiceLoader;

import org.glassfish.jersey.client.spi.ConnectorProvider;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class ConnectorProviderTest {

    /**
     * There should be no {@link org.glassfish.jersey.client.spi.ConnectorProvider}'s in
     * path when running this test. This test will fail if the {@code io.helidon.jersey.connector}
     * modulesis available.
     *
     * @throws NoSuchElementException If not found.
     */
    @Test
    public void testConnectorProvider() throws NoSuchElementException {
        ServiceLoader<ConnectorProvider> loader = ServiceLoader.load(ConnectorProvider.class);
        assertThat(loader.findFirst(), is(Optional.empty()));
    }
}
