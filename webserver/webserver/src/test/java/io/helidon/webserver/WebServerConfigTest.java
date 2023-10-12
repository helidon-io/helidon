/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;

import io.helidon.common.GenericType;
import io.helidon.config.Config;
import io.helidon.http.WritableHeaders;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.media.EntityWriter;
import io.helidon.http.media.MediaContext;
import io.helidon.http.media.UnsupportedTypeException;
import io.helidon.http.media.jsonp.JsonpSupport;
import io.helidon.webserver.spi.ProtocolConfig;
import io.helidon.webserver.spi.ServerConnectionSelector;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonStructure;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class WebServerConfigTest {

    @Test
    void testConnectionProvidersEnabled() {
        // This will pick up application.yaml from the classpath as default configuration file
        Config config = Config.create();
        var wsConfig = WebServer.builder().config(config.get("server")).buildPrototype();
        List<ServerConnectionSelector> providers = wsConfig.connectionSelectors();
        // this is a list of selectors explicitly configured
        assertThat(providers, notNullValue());
        assertThat(providers, is(empty()));
        // and this is combined with service loader lookup
        List<ProtocolConfig> protocols = wsConfig.protocols();
        assertThat(protocols, notNullValue());
        assertThat(protocols, not(empty()));
    }

    @Test
    void testConnectionProvidersDisabled() {
        // This will pick up application.yaml from the classpath as default configuration file
        Config config = Config.create();
        var wsConfig = WebServer.builder().config(config.get("server2")).buildPrototype();
        List<ServerConnectionSelector> providers = wsConfig.connectionSelectors();
        // No providers shall be loaded with ServiceLoader disabled for connection providers.
        assertThat(providers, notNullValue());
        assertThat(providers, is(empty()));
    }

    // Check that WebServer ContentEncodingContext is disabled when disable is present in config
    @Test
    void testContentEncodingConfig() {
        // This will pick up application.yaml from the classpath as default configuration file
        Config config = Config.create();
        var wsConfig = WebServer.builder().config(config.get("server")).buildPrototype();
        ContentEncodingContext contentEncodingContext = wsConfig.contentEncoding().orElseThrow();
        assertThat(contentEncodingContext.contentEncodingEnabled(), is(false));
        assertThat(contentEncodingContext.contentDecodingEnabled(), is(false));
        assertThrows(NoSuchElementException.class, () -> contentEncodingContext.decoder("gzip"));
        assertThrows(NoSuchElementException.class, () -> contentEncodingContext.decoder("gzip"));
        assertThrows(NoSuchElementException.class, () -> contentEncodingContext.encoder("gzip"));
        assertThrows(NoSuchElementException.class, () -> contentEncodingContext.decoder("x-gzip"));
        assertThrows(NoSuchElementException.class, () -> contentEncodingContext.encoder("x-gzip"));
    }

    // Check that WebServer MediaContext builder produces expected provider using MediaContext configuration from Config file:
    //  - java service loader enabled in server node of application.yaml
    //  - JSON should be present
    // Writing JsonObject should work and produce valid JSON data.
    @Test
    void testMediaSupportFileConfigJson() throws IOException {
        Config config = Config.create();
        Config server = config.get("server2");
        var wsConfig = WebServer.builder().config(server).buildPrototype();
        MediaContext mediaContext = wsConfig.mediaContext().orElseThrow();
        assertThat(mediaContext, is(notNullValue()));
        WritableHeaders<?> writableHeaders = WritableHeaders.create();
        EntityWriter<JsonObject> writer = mediaContext.writer(GenericType.create(JsonObject.class), writableHeaders);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(1024);
        writer.write(GenericType.create(JsonObject.class),
                     Json.createObjectBuilder()
                             .add("name", "John Smith")
                             .build(),
                     outputStream,
                     writableHeaders);
        outputStream.close();
        // Verify written data
        JsonObject verify = Json.createObjectBuilder()
                .add("name", "John Smith")
                .build();
        JsonStructure js = Json.createReader(new ByteArrayInputStream(outputStream.toByteArray())).read();
        assertThat(js.asJsonObject(), is(verify));
    }

    // Check that WebServer MediaContext builder produces expected provider using MediaContext configuration from Config file:
    //  - java service loader disabled in server node of application.yaml
    //  - JSON should not be present
    // Writing JsonObject should work and produce valid JSON data.
    @Test
    void testMediaSupportFileConfigNoJson() throws IOException {
        Config config = Config.create();
        var wsConfig = WebServer.builder().config(config.get("server")).buildPrototype();
        MediaContext mediaContext = wsConfig.mediaContext().orElseThrow();
        assertThat(mediaContext, is(notNullValue()));
        WritableHeaders<?> writableHeaders = WritableHeaders.create();
        EntityWriter<JsonObject> writer = mediaContext.writer(GenericType.create(JsonObject.class), writableHeaders);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(1024);
        assertThrows(UnsupportedTypeException.class, () -> writer.write(
                          GenericType.create(JsonObject.class),
                          Json.createObjectBuilder()
                                  .add("name", "John Smith")
                                  .build(),
                          outputStream,
                          writableHeaders));
        outputStream.close();
    }

    // Check that WebServer MediaContext builder produces expected provider using manually built MediaContext:
    //  - java service loader disabled
    //  - JSON added manually
    // Writing JsonObject should work and produce valid JSON data.
    @Test
    void testMediaSupportManualConfigJson() throws IOException {
        Config config = Config.create();
        var wsConfig = WebServer.builder()
                .config(config.get("server"))
                .mediaContext(MediaContext.builder()
                                      .mediaSupportsDiscoverServices(false)
                                      .addMediaSupport(JsonpSupport.create(Config.empty()))
                                      .build())
                .buildPrototype();
        MediaContext mediaContext = wsConfig.mediaContext().orElseThrow();
        assertThat(mediaContext, is(notNullValue()));
        WritableHeaders<?> writableHeaders = WritableHeaders.create();
        EntityWriter<JsonObject> writer = mediaContext.writer(GenericType.create(JsonObject.class), writableHeaders);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(1024);
        writer.write(GenericType.create(JsonObject.class),
                     Json.createObjectBuilder()
                             .add("name", "John Smith")
                             .build(),
                     outputStream,
                     writableHeaders);
        outputStream.close();
        // Verify written data
        JsonObject verify = Json.createObjectBuilder()
                .add("name", "John Smith")
                .build();
        JsonStructure js = Json.createReader(new ByteArrayInputStream(outputStream.toByteArray())).read();
        assertThat(js.asJsonObject(), is(verify));
    }

    // Check that WebServer MediaContext builder produces expected provider using manually built MediaContext:
    //  - java service loader disabled
    //  - JSON not added manually
    // Writing JsonObject should fail on missing JSOn media support.
    @Test
    void testMediaSupportManualConfigNoJson() throws IOException {
        Config config = Config.create();
        var wsConfig = WebServer.builder()
                .config(config.get("server"))
                .mediaContext(MediaContext.builder()
                                      .mediaSupportsDiscoverServices(false)
                                      .build())
                .buildPrototype();
        MediaContext mediaContext = wsConfig.mediaContext().orElseThrow();
        assertThat(mediaContext, is(notNullValue()));
        WritableHeaders<?> writableHeaders = WritableHeaders.create();
        EntityWriter<JsonObject> writer = mediaContext.writer(GenericType.create(JsonObject.class), writableHeaders);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(1024);
        assertThrows(UnsupportedTypeException.class,
                     () -> writer.write(
                          GenericType.create(JsonObject.class),
                          Json.createObjectBuilder()
                                  .add("name", "John Smith")
                                  .build(),
                          outputStream,
                          writableHeaders));
        outputStream.close();
    }
}
