/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.messaging.connectors.mqtt;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.microprofile.config.ConfigCdiExtension;
import io.helidon.microprofile.messaging.MessagingCdiExtension;
import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.AddBeans;
import io.helidon.microprofile.tests.junit5.AddConfig;
import io.helidon.microprofile.tests.junit5.AddConfigs;
import io.helidon.microprofile.tests.junit5.AddExtension;
import io.helidon.microprofile.tests.junit5.AddExtensions;
import io.helidon.microprofile.tests.junit5.BeforeContainer;
import io.helidon.microprofile.tests.junit5.DisableDiscovery;
import io.helidon.microprofile.tests.junit5.HelidonTest;
import io.helidon.microprofile.tests.junit5.ScanBeansForConfig;

import org.apache.activemq.broker.BrokerService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.enterprise.inject.spi.CDI;

@HelidonTest
@DisableDiscovery
@ScanBeansForConfig
@AddBeans({
        @AddBean(MqttConnector.class),
        @AddBean(InOutTestBean.class),
        @AddBean(ProcessorTestBean.class),
})
@AddExtensions({
        @AddExtension(ConfigCdiExtension.class),
        @AddExtension(MessagingCdiExtension.class),
})
@AddConfigs({
        @AddConfig(key = "mp.messaging.connector.helidon-mqtt.server", value = "tcp://localhost:1883"),
})
public class MqttMpTest {

    private static BrokerService broker;

    @BeforeContainer
    static void startBroker() throws Exception {
        // Start ActiveMQ as MQTT broker
        File tmp = new File(MqttMpTest.class.getResource("").getPath());
        broker = new BrokerService();
        broker.addConnector("mqtt://localhost:1883?wireFormat.maxFrameSize=100000");
        broker.setTmpDataDirectory(tmp);
        broker.setDataDirectoryFile(tmp);
        broker.setSchedulerDirectoryFile(tmp);
        broker.start(true);
        broker.waitUntilStarted(400);
    }

    @AfterAll
    static void afterAll() throws Exception {
        broker.stop();
    }

    private static Stream<? extends Arguments> testBeansLookup() {
        return CDI.current().select(TestBean.class)
                .stream()
                .map(bean -> Arguments.of(bean.getClass().getSuperclass().getSimpleName(), bean));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("testBeansLookup")
    void test(String name, TestBean testBean) throws Throwable {
        testBean.assertValid();
    }
}

