/*
 * Copyright (c) 2021 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.examples.microprofile.multipart;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.json.JsonObject;
import javax.json.JsonString;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.helidon.microprofile.server.JaxRsCdiExtension;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.AddExtension;
import io.helidon.microprofile.tests.junit5.DisableDiscovery;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.ext.cdi1x.internal.CdiComponentProvider;
import org.glassfish.jersey.media.multipart.MultiPart;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.media.multipart.file.FileDataBodyPart;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Tests {@link FileService}.
 */
@HelidonTest
@DisableDiscovery
@AddExtension(ServerCdiExtension.class)
@AddExtension(JaxRsCdiExtension.class)
@AddExtension(CdiComponentProvider.class)
@AddBean(FileService.class)
@AddBean(FileStorage.class)
@AddBean(MultiPartFeatureProvider.class)
@TestMethodOrder(OrderAnnotation.class)
public class FileServiceTest {

    @Test
    @Order(1)
    public void testUpload(WebTarget target) throws IOException {
        Path tempDirectory = Files.createTempDirectory(null);
        File file = Files.write(tempDirectory.resolve("foo.txt"), "bar\n".getBytes(StandardCharsets.UTF_8)).toFile();
        MultiPart multipart = new MultiPart()
                .bodyPart(new FileDataBodyPart("file[]", file, MediaType.APPLICATION_OCTET_STREAM_TYPE));
        Response response = target
                .property(ClientProperties.FOLLOW_REDIRECTS, Boolean.FALSE)
                .register(MultiPartFeature.class)
                .path("/api")
                .request()
                .post(Entity.entity(multipart, MediaType.MULTIPART_FORM_DATA_TYPE));
        assertThat(response.getStatus(), is(303));
    }

    @Test
    @Order(2)
    public void testList(WebTarget target) {
        Response response = target
                .path("/api")
                .request(MediaType.APPLICATION_JSON_TYPE)
                .get();
        assertThat(response.getStatus(), is(200));
        JsonObject json = response.readEntity(JsonObject.class);
        assertThat(json, is(notNullValue()));
        List<String> files = json.getJsonArray("files").getValuesAs(v -> ((JsonString) v).getString());
        assertThat(files, hasItem("foo.txt"));
    }

    @Test
    @Order(3)
    public void testDownload(WebTarget target) throws IOException {
        Response response = target
                .register(MultiPartFeature.class)
                .path("/api/foo.txt")
                .request(MediaType.APPLICATION_OCTET_STREAM_TYPE)
                .get();
        assertThat(response.getStatus(), is(200));
        assertThat(response.getHeaderString("Content-Disposition"), containsString("filename=\"foo.txt\""));
        InputStream inputStream = response.readEntity(InputStream.class);
        assertThat(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8), is("bar\n"));
    }
}