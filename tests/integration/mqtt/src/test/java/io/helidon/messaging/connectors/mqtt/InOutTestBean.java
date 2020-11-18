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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.enterprise.context.ApplicationScoped;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import io.helidon.microprofile.tests.junit5.AddConfig;
import io.helidon.microprofile.tests.junit5.AddConfigs;

@ApplicationScoped
@AddConfigs({
        @AddConfig(key = "mp.messaging.outgoing.toMqtt.connector", value = "helidon-mqtt"),
        @AddConfig(key = "mp.messaging.outgoing.toMqtt.topic", value = "test-1"),

        @AddConfig(key = "mp.messaging.incoming.fromMqtt.connector", value = "helidon-mqtt"),
        @AddConfig(key = "mp.messaging.incoming.fromMqtt.topic", value = "test-1"),
})
public class InOutTestBean implements TestBean {

    private static final List<String> DATA = List.of("Hello1", "Hello2", "Hello3", "Hello4");
    private final CountDownLatch countDownLatch = new CountDownLatch(DATA.size());
    private final ArrayList<String> result = new ArrayList<>();

    public void assertValid() throws InterruptedException {
        assertThat("Not finished in time", countDownLatch.await(200, TimeUnit.MILLISECONDS));
        assertThat(result, containsInAnyOrder(DATA.toArray()));
    }

    @Incoming("fromMqtt")
    public void receive(MqttMessage mqttMessage) {
        result.add(new String(mqttMessage.getPayload()));
        countDownLatch.countDown();
    }

    @Outgoing("toMqtt")
    public PublisherBuilder<MqttMessage> publish() {
        return ReactiveStreams.fromIterable(DATA)
                .map(String::getBytes)
                .map(MqttMessage::new);
    }
}
