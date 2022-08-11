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
 */

package io.helidon.microprofile.server;

import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;

import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.AddConfig;
import io.helidon.microprofile.tests.junit5.AddExtension;
import io.helidon.microprofile.tests.junit5.DisableDiscovery;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import org.glassfish.jersey.ext.cdi1x.internal.CdiComponentProvider;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;

@HelidonTest
@DisableDiscovery
@AddBean(StreamingOutputLeakTest.DownloadResource.class)
@AddExtension(ServerCdiExtension.class)
@AddExtension(JaxRsCdiExtension.class)
@AddExtension(CdiComponentProvider.class)
@AddConfig(key = "server.backpressure-buffer-size", value = "20971520")//20Mb
class StreamingOutputLeakTest {

    private static final int SIZE10MB = 10 * 1024 * 1024;
    private static final int SIZE = SIZE10MB;
    private static final long NUMBER_OF_BUFS = 20;
    private static final byte[] DATA_10MB = new byte[SIZE];

    static {
        Random r = new Random();
        r.nextBytes(DATA_10MB);
    }

    /**
     * Reproducer for issue #4643
     */
    @Test
    void streamingOutput(WebTarget target) throws IOException {

        InputStream is = target.path("/download")
                .request()
                .get(InputStream.class);
        long size = 0;
        while (is.read() != -1) {
            size++;
        }
        is.close();

        // Make sure all data has been read
        assertThat(size, is(NUMBER_OF_BUFS * SIZE));
    }

    @Path("/download")
    public static class DownloadResource {

        @GET
        @Produces(MediaType.MULTIPART_FORM_DATA)
        public Response getPayload(
                @NotNull @QueryParam("fileName") String fileName) {
            StreamingOutput fileStream = output -> {

                // 2gb
                for (int i = 0; i < NUMBER_OF_BUFS; i++) {
                    output.write(DATA_10MB);
                    output.flush();
                }

            };
            return Response
                    .ok(fileStream, MediaType.MULTIPART_FORM_DATA)
                    .build();
        }
    }
}