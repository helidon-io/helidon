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

package io.helidon.nima.webserver;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;

import io.helidon.common.GenericType;
import io.helidon.common.http.WritableHeaders;
import io.helidon.config.Config;
import io.helidon.nima.http.encoding.ContentEncodingContext;
import io.helidon.nima.http.media.EntityWriter;
import io.helidon.nima.http.media.MediaContext;
import io.helidon.nima.http.media.jsonp.JsonpSupport;
import io.helidon.nima.webserver.spi.ServerConnectionSelector;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonStructure;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.Assertions.fail;

public class WebServerConfigTest {

    @Test
    void testConnectionProvidersEnabled() {
        // This will pick up application.yaml from the classpath as default configuration file
        Config config = Config.create();
        WebServer.Builder wsBuilder = WebServer.builder().config(config.get("server"));
        List<ServerConnectionSelector> providers = wsBuilder.connectionProviders();
        // Providers shall be loaded with ServiceLoader.
        assertThat(providers, notNullValue());
        assertThat(providers, is(not(empty())));
    }

    @Test
    void testConnectionProvidersDisabled() {
        // This will pick up application.yaml from the classpath as default configuration file
        Config config = Config.create();
        WebServer.Builder wsBuilder = WebServer.builder().config(config.get("server2"));
        List<ServerConnectionSelector> providers = wsBuilder.connectionProviders();
        // No providers shall be loaded with ServiceLoader disabled for connection providers.
        assertThat(providers, notNullValue());
        assertThat(providers, is(empty()));
    }

    // Check that WebServer ContentEncodingContext is disabled when disable is present in config
    @Test
    void testContentEncodingConfig() {
        // This will pick up application.yaml from the classpath as default configuration file
        Config config = Config.create();
        WebServer.Builder wsBuilder = WebServer.builder().config(config.get("server"));
        ContentEncodingContext contentEncodingContext = wsBuilder.contentEncodingContext();
        assertThat(contentEncodingContext.contentEncodingEnabled(), is(true));
        assertThat(contentEncodingContext.contentDecodingEnabled(), is(true));
        failsWith(() -> contentEncodingContext.decoder("gzip"), NoSuchElementException.class);
        failsWith(() -> contentEncodingContext.decoder("gzip"), NoSuchElementException.class);
        failsWith(() -> contentEncodingContext.encoder("gzip"), NoSuchElementException.class);
        failsWith(() -> contentEncodingContext.decoder("x-gzip"), NoSuchElementException.class);
        failsWith(() -> contentEncodingContext.encoder("x-gzip"), NoSuchElementException.class);
    }

    // Check that WebServer MediaContext builder produces expected provider using MediaContext configuration from Config file:
    //  - java service loader enabled in server node of application.yaml
    //  - JSON should be present
    // Writing JsonObject should work and produce valid JSON data.
    @Test
    void testMediaSupportFileConfigJson() throws IOException {
        Config config = Config.create();
        Config server = config.get("server2");
        WebServer.Builder wsBuilder = WebServer.builder().config(server);
        wsBuilder.build(); // triggers processing of media context
        MediaContext mediaContext = wsBuilder.mediaContext();
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
        WebServer.Builder wsBuilder = WebServer.builder().config(config.get("server"));
        wsBuilder.build(); // trigger processing of media context
        MediaContext mediaContext = wsBuilder.mediaContext();
        assertThat(mediaContext, is(notNullValue()));
        WritableHeaders<?> writableHeaders = WritableHeaders.create();
        EntityWriter<JsonObject> writer = mediaContext.writer(GenericType.create(JsonObject.class), writableHeaders);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(1024);
        failsWith(() -> writer.write(
                        GenericType.create(JsonObject.class),
                        Json.createObjectBuilder()
                                .add("name", "John Smith")
                                .build(),
                        outputStream,
                        writableHeaders),
                IllegalArgumentException.class);
        outputStream.close();
    }

    // Check that WebServer MediaContext builder produces expected provider using manually built MediaContext:
    //  - java service loader disabled
    //  - JSON added manually
    // Writing JsonObject should work and produce valid JSON data.
    @Test
    void testMediaSupportManualConfigJson() throws IOException {
        Config config = Config.create();
        WebServer.Builder wsBuilder = WebServer.builder()
                .config(config.get("server"))
                .mediaContext(MediaContext.builder()
                        .discoverServices(false)
                        .addMediaSupport(JsonpSupport.create(Config.empty()))
                        .build());
        MediaContext mediaContext = wsBuilder.mediaContext();
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
        WebServer.Builder wsBuilder = WebServer.builder()
                .config(config.get("server"))
                .mediaContext(MediaContext.builder()
                        .discoverServices(false)
                        .build());
        MediaContext mediaContext = wsBuilder.mediaContext();
        assertThat(mediaContext, is(notNullValue()));
        WritableHeaders<?> writableHeaders = WritableHeaders.create();
        EntityWriter<JsonObject> writer = mediaContext.writer(GenericType.create(JsonObject.class), writableHeaders);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(1024);
        failsWith(() -> writer.write(
                GenericType.create(JsonObject.class),
                        Json.createObjectBuilder()
                                .add("name", "John Smith")
                                .build(),
                        outputStream,
                        writableHeaders),
                IllegalArgumentException.class);
        outputStream.close();
    }

    // Verify that provided task throws an exception
    private static void failsWith(Runnable task, Class<? extends Exception> exception) {
        try {
            task.run();
            // Fail the test when no Exception was thrown
            fail(String.format("Exception %s was not thrown", exception.getName()));
        } catch (Exception ex) {
            if (!exception.isAssignableFrom(ex.getClass())) {
                throw ex;
            }
        }
    }

}
