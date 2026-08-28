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

package io.helidon.messaging;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class ConnectorConfigGeneratedArtifactsTest {
    @Test
    void generatedArtifactsUsePublicConnectorApi() throws IOException {
        Path generatedSource = Path.of("target/generated-sources/annotations/io/helidon/messaging/ConnectorConfig.java");
        Path metadataFile = Path.of("target/classes/META-INF/helidon/config-metadata.json");
        assertThat(Files.isRegularFile(generatedSource), is(true));
        assertThat(Files.isRegularFile(metadataFile), is(true));
        assertThat(ConnectorConfigBlueprint.class.getDeclaredFields().length, is(0));

        String source = Files.readString(generatedSource);
        assertThat(source,
                   containsString("String CHANNEL_NAME_ATTRIBUTE = MessagingConfigSupport.CHANNEL_NAME_ATTRIBUTE"));
        assertThat(source,
                   containsString("String CONNECTOR_ATTRIBUTE = MessagingConfigSupport.CONNECTOR_ATTRIBUTE"));
        assertThat(source, containsString("type = ConnectorDirection.class"));
        assertThat(source, containsString("ConnectorDirection direction()"));
        assertThat(source, containsString("direction(ConnectorDirection direction)"));
        assertThat(source, containsString("String channelName()"));
        assertThat(source, containsString("channelName(String channelName)"));
        assertThat(source.contains("String channel()"), is(false));
        assertThat(source.contains("ConnectorConfigBlueprint.Direction"), is(false));

        String metadata = Files.readString(metadataFile);
        assertThat(metadata, containsString("\"key\":\"channel-name\""));
        assertThat(metadata,
                   containsString("\"key\":\"direction\","
                                          + "\"type\":\"io.helidon.messaging.ConnectorDirection\""));
        assertThat(metadata.contains("ConnectorConfigBlueprint.Direction"), is(false));
    }
}
