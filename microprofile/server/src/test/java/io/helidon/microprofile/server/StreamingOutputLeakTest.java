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

import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

import javax.validation.constraints.NotNull;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import io.helidon.microprofile.tests.junit5.AddBean;
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
public class StreamingOutputLeakTest {
    private static final byte[] DATA_10MB = new byte[10 * 1024 * 1024];

    @Test
    void name(WebTarget target) throws IOException {
        InputStream is = target.path("/download")
                .request()
                .get(InputStream.class);

        long size = 0;
        while (is.read() != -1) {
            size++;
        }
        is.close();

        // Make sure all data has been read
        assertThat(size, is(50L * DATA_10MB.length));       // 500 MB
    }

    @Path("/download")
    public static class DownloadResource {

        static {
            Random r = new Random();
            r.nextBytes(DATA_10MB);
        }

        @GET
        @Produces(MediaType.MULTIPART_FORM_DATA)
        public Response getPayload(
                @NotNull @QueryParam("fileName") String fileName) {
            StreamingOutput fileStream = output -> {
                for (int i = 0; i < 50; i++) {         // 500 MB
                    try {
                        output.write(DATA_10MB);
                        output.flush();
                    } catch (OutOfMemoryError e) {
                        break;      // incomplete data
                    }
                }
            };
            return Response.ok(fileStream, MediaType.MULTIPART_FORM_DATA).build();
        }
    }
}
