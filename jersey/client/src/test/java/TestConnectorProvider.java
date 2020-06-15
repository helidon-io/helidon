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

import java.util.NoSuchElementException;
import java.util.ServiceLoader;

import org.junit.jupiter.api.Test;
import org.glassfish.jersey.client.spi.ConnectorProvider;
import io.helidon.jersey.connector.HelidonConnectorProvider;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;

public class TestConnectorProvider {

    /**
     * Test how Jersey will load the Helidon connector.
     *
     * @throws NoSuchElementException If not found.
     */
    @Test
    public void testConnectorProviderLoader() throws NoSuchElementException {
        ServiceLoader<ConnectorProvider> loader = ServiceLoader.load(ConnectorProvider.class);
        ConnectorProvider provider = loader.findFirst().orElseThrow();
        assertThat(provider, instanceOf(HelidonConnectorProvider.class));
    }
}
