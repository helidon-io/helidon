/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
 *
 */

package io.helidon.microprofile.messaging;

import jakarta.inject.Inject;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Flow;

import io.helidon.common.reactive.Multi;
import io.helidon.messaging.connectors.mock.MockConnector;
import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.AddConfig;
import io.helidon.microprofile.tests.junit5.AddExtension;
import io.helidon.microprofile.tests.junit5.DisableDiscovery;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import org.eclipse.microprofile.reactive.messaging.Channel;
import org.junit.jupiter.api.Test;

import static io.helidon.messaging.connectors.mock.MockConnector.CONNECTOR_NAME;
import static org.eclipse.microprofile.reactive.messaging.spi.ConnectorFactory.INCOMING_PREFIX;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

@HelidonTest
@DisableDiscovery
@AddBean(MockConnector.class)
@AddExtension(MessagingCdiExtension.class)
@AddConfig(key = INCOMING_PREFIX + "test-channel-1.connector", value = CONNECTOR_NAME)
@AddConfig(key = INCOMING_PREFIX + "test-channel-1.mock-data-type", value = "java.lang.Integer")
@AddConfig(key = INCOMING_PREFIX + "test-channel-1.mock-data", value = "1,2,3")
public class FlowSupportTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    @Inject
    @Channel("test-channel-1")
    private Flow.Publisher<Integer> flowChannel1;


    @Test
    void injectedFlowPub() {
        List<Integer> actual = Multi.create(flowChannel1)
                .limit(3)
                .collectList()
                .await(TIMEOUT);

        assertThat(actual, contains(1,2,3));
    }
}
