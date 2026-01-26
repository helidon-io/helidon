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

package io.helidon.microprofile.tests.server;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.buffers.Bytes;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.microprofile.testing.AddBean;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.fail;

@HelidonTest
@AddBean(FlushStreamTest.TestResource.class)
public class FlushStreamTest {
    private static final String INITIAL_RESPONSE = "This is the first input for the result stream.\n";

    private static final AtomicReference<CountDownLatch> LATCH = new AtomicReference<>();

    @BeforeEach
    void beforeEach() {
        LATCH.set(new CountDownLatch(1));
    }

    @Test
    void testFlushedStream(WebTarget target) {
        Response response = target.path("/test/stream/flushed")
                .request()
                .get();

        assertThat(response.getStatus(), is(Status.OK_200_CODE));

        assertThat(response.getHeaderString(HeaderNames.TRANSFER_ENCODING_NAME),
                   is("chunked"));
    }

    @Test
    void testUnFlushedStream(WebTarget target) {
        Response response = target.path("/test/stream/unflushed")
                .request()
                .get();

        assertThat(response.getStatus(), is(Status.OK_200_CODE));

        assertThat(response.getHeaderString(HeaderNames.TRANSFER_ENCODING_NAME),
                   nullValue());
    }

    // tests that the data is sent over the network
    @Test
    void testFlushed(WebTarget target) throws Exception {
        try (Socket socket = new Socket()) {
            socket.setSoTimeout(10000);
            socket.connect(new InetSocketAddress("localhost", target.getUri().getPort()), 10000);

            try (OutputStream outputStream = socket.getOutputStream(); InputStream inputStream = socket.getInputStream()) {
                outputStream.write("""
                                        GET /test/stream HTTP/1.1\r
                                        Host: localhost\r
                                        Connection: close\r
                                        Content-Length: 0\r
                                        \r
                                        """.getBytes(StandardCharsets.UTF_8));
                outputStream.flush();

                ByteArrayOutputStream firstLine = new ByteArrayOutputStream();
                while (true) {
                    // read first line (status)
                    int read = inputStream.read();
                    if (read == Bytes.CR_BYTE) {
                        read = inputStream.read();
                        if (read != Bytes.LF_BYTE) {
                            fail("Unexpected first line, missing CRLF" + firstLine);
                        }
                        break;
                    }
                    firstLine.write(read);
                }
                assertThat(firstLine.toString(), CoreMatchers.is("HTTP/1.1 200 OK"));
                ByteArrayOutputStream headers = new ByteArrayOutputStream();
                while(true) {
                    // read first line (status)
                    int read = inputStream.read();
                    if (read == Bytes.CR_BYTE) {
                        read = inputStream.read();
                        if (read != Bytes.LF_BYTE) {
                            fail("Unexpected first line, missing CRLF" + firstLine);
                        }
                        read = inputStream.read();
                        if (read != Bytes.CR_BYTE) {
                            // next header
                            headers.write("\n".getBytes(StandardCharsets.UTF_8));
                            headers.write(read);
                            continue;
                        } else {
                            read = inputStream.read();
                            if (read != Bytes.LF_BYTE) {
                                fail("Unexpected first line, missing CRLF" + firstLine);
                            }
                            break;
                        }
                    }
                    headers.write(read);
                }
                // read first line (HTTP/1.1 200 OK)
                // read headers (Transfer-Encoding: chunked)

                // exact size, as otherwise it may block
                // this must be chunked encoding (as flush is called)
                byte[] buffer = new byte[INITIAL_RESPONSE.length() + 4];
                // we expect to be able to read the full first line, as it was flushed by the server
                inputStream.readNBytes(buffer, 0, buffer.length);
                // length + CRLF
                assertThat(new String(buffer, 0, 4), CoreMatchers.is("2f\r\n"));
                // the string
                assertThat(new String(buffer, 4, buffer.length - 4), CoreMatchers.is(INITIAL_RESPONSE));
                LATCH.get().countDown();
            }
        }
    }



    @Path("/test/stream")
    public static class TestResource {
        @GET
        @Path(value = "/")
        @Produces(MediaType.TEXT_PLAIN)
        public Response stream() {

            StreamingOutput stream = output -> {
                Writer writer = new BufferedWriter(new OutputStreamWriter(output));

                writer.write(INITIAL_RESPONSE);
                writer.flush();
                try {
                    LATCH.get().await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                writer.write("This is the rest of the input of the result stream." + "\n");
            };
            return Response.ok(stream).build();
        }

        @GET
        @Path(value = "/flushed")
        @Produces(MediaType.TEXT_PLAIN)
        public Response streamFlushed() {

            StreamingOutput stream = output -> {
                Writer writer = new BufferedWriter(new OutputStreamWriter(output));
                for (int i = 0; i < 2; i++) {
                    writer.write("This is the " + i + "th input for the result stream." + "\n");
                    writer.flush();
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }

            };
            return Response.ok(stream).build();
        }

        @GET
        @Path(value = "/unflushed")
        @Produces(MediaType.TEXT_PLAIN)
        public Response streamUnflushed() {

            StreamingOutput stream = output -> {
                Writer writer = new BufferedWriter(new OutputStreamWriter(output));
                for (int i = 0; i < 10; i++) {
                    writer.write("This is the " + i + "th input for the result stream." + "\n");
                }

            };
            return Response.ok(stream).build();
        }

    }
}
