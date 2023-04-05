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
    private static final int FILE_SIZE = 6000000;

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
    static void createTempFile() throws IOException {
        file = File.createTempFile("zero", ".bin");
        file.deleteOnExit();
        byte[] zero = new byte[10 * 1000];
        try (FileOutputStream fos = new FileOutputStream(file)) {
            for (int i = 0; i < FILE_SIZE / zero.length; i++) {
                fos.write(zero);
            }
        }
    }

    @Test
    void testAutoFlush() {
        try (Response resp = target.path("auto-flush")
                .request()
                .get()) {
            assertThat(resp.getStatus(), is(200));
            assertThat(resp.getHeaderString(Http.Header.CONTENT_LENGTH), is(String.valueOf(FILE_SIZE)));
            assertThat(resp.readEntity(byte[].class).length, is(FILE_SIZE));
        }
    }
}
