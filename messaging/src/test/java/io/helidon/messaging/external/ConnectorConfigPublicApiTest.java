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

package io.helidon.messaging.external;

import java.lang.reflect.Modifier;

import io.helidon.messaging.ConnectorConfig;
import io.helidon.messaging.ConnectorDirection;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

class ConnectorConfigPublicApiTest {
    @Test
    void directionIsPubliclyNameable() throws NoSuchMethodException {
        ConnectorConfig config = ConnectorConfig.builder()
                .direction(ConnectorDirection.INCOMING)
                .channel("orders")
                .connector("test")
                .buildPrototype();

        assertThat(config.direction(), is(ConnectorDirection.INCOMING));
        assertThat(ConnectorConfig.class.getMethod("direction").getReturnType(), sameInstance(ConnectorDirection.class));
        assertThat(Modifier.isPublic(ConnectorDirection.class.getModifiers()), is(true));
    }
}
