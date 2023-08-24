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

package io.helidon.microprofile.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import io.helidon.common.http.Http;
import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.HelidonTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@HelidonTest
@AddBean(AutoFlushTest.AutoFlushResource.class)
class AutoFlushTest {

    private static File file;
    private static byte[] sha256;
    private static final int FILE_SIZE = 40 * 1024 * 1024;
    private static final int BUFFER_SIZE = 8 * 1024;

    @Inject
    private WebTarget target;

    @Path("auto-flush")
    public static class AutoFlushResource {

        @GET
        public Response getDefaultMessage() {
            return Response.ok((StreamingOutput) outputStream -> {
                try (InputStream is = new FileInputStream(file)) {
                    is.transferTo(outputStream);
                }
            }).header(Http.Header.CONTENT_LENGTH, String.valueOf(FILE_SIZE)).build();
        }
    }

    @BeforeAll
    static void createTempFile() throws IOException, NoSuchAlgorithmException {
        file = File.createTempFile("file", ".bin");
        file.deleteOnExit();
        MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
        try (FileOutputStream fos = new FileOutputStream(file)) {
            byte[] bytes = new byte[BUFFER_SIZE];
            for (int i = 0; i < FILE_SIZE / BUFFER_SIZE; i++) {
                Arrays.fill(bytes, (byte) (i % 10 + '0'));
                fos.write(bytes);
                messageDigest.update(bytes);
            }
        }
        sha256 = messageDigest.digest();
    }

    @Test
    void testAutoFlush() throws NoSuchAlgorithmException {
        try (Response resp = target.path("auto-flush")
                .request()
                .get()) {
            assertThat(resp.getStatus(), is(200));
            assertThat(resp.getHeaderString(Http.Header.CONTENT_LENGTH), is(String.valueOf(FILE_SIZE)));

            byte[] file = resp.readEntity(byte[].class);
            assertThat(file.length, is(FILE_SIZE));
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            for (int i = 0; i < FILE_SIZE / BUFFER_SIZE; i++) {
                messageDigest.update(file, i * BUFFER_SIZE, BUFFER_SIZE);
            }
            byte[] newSha256 = messageDigest.digest();
            assertThat(sha256, is(newSha256));
        }
    }
}
