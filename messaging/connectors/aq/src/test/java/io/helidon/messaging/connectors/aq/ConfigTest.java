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

package io.helidon.messaging.connectors.aq;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.sql.DataSource;

import io.helidon.config.mp.MpConfigSources;
import io.helidon.messaging.Channel;
import io.helidon.messaging.Messaging;
import io.helidon.messaging.connectors.jms.JmsConnector;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsMapContaining.hasEntry;
import static org.junit.jupiter.api.Assertions.fail;

import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

public class ConfigTest {

    private static final HashMap<String, Object> results = new HashMap<>();


    @Test
    void seChannelTest() throws SQLException {
        DataSource mockedDataSource = MockedDataSourceBuilder.create();

        AqConnectorImpl seConn = AqConnector.builder()
                .dataSource("test-ds", mockedDataSource)
                .build();

        await(seConn.getPublisherBuilder(conf(Map.of(
                AqConnector.CHANNEL_NAME_ATTRIBUTE, "test-1",
                AqConnector.CONNECTOR_ATTRIBUTE, JmsConnector.CONNECTOR_NAME,
                AqConnector.DATASOURCE_ATTRIBUTE, "test-ds",
                "destination", "test-queue-1"
        ))).peek(message -> results.put("message", message.getPayload()))
                .findFirst().run());

        assertThat(results, hasEntry("message", MockedDataSourceBuilder.TEST_MESSAGE));
    }

    @Test
    void seBuilderTest() throws InterruptedException, ExecutionException, TimeoutException, SQLException {

        DataSource mockedDataSource = MockedDataSourceBuilder.create();

        AqConnector seConn = AqConnector.builder()
                .dataSource("test-ds", mockedDataSource)
                .build();

        Channel<String> fromMockAq = Channel.<String>builder()
                .name("fromMockAq")
                .publisherConfig(AqConnector.configBuilder()
                        .queue("test-queue-1")
                        .dataSource("test-ds")
                        .build())
                .build();

        CompletableFuture<String> messageFuture = new CompletableFuture<>();

        Messaging.builder()
                .connector(seConn)
                .listener(fromMockAq, messageFuture::complete)
                .build()
                .start();

        assertThat(messageFuture.get(500, TimeUnit.MILLISECONDS), Matchers.equalTo(MockedDataSourceBuilder.TEST_MESSAGE));
    }

    private org.eclipse.microprofile.config.Config conf(Map<String, String> m) {
        return ConfigProviderResolver.instance()
                .getBuilder()
                .withSources(MpConfigSources.create(m))
                .build();
    }

    private <T> T await(CompletionStage<T> stage) {
        try {
            return stage.toCompletableFuture().get(500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            fail(e);
            return null;
        }
    }
}
