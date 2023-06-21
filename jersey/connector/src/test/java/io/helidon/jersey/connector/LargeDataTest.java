/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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

package io.helidon.jersey.connector;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Extension;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import jakarta.ws.rs.ServerErrorException;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.glassfish.jersey.client.ClientProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * The LargeDataTest reproduces a problem when bytes of large data sent are incorrectly sent.
 * As a result, the request body is different than what was sent by the client.
 * <p>
 * In order to be able to inspect the request body, the generated data is a sequence of numbers
 * delimited with new lines. Such as
 * <pre><code>
 *     1
 *     2
 *     3
 *
 *     ...
 *
 *     57234
 *     57235
 *     57236
 *
 *     ...
 * </code></pre>
 * It is also possible to send the data to netcat: {@code nc -l 8080} and verify the problem is
 * on the client side.
 */
public class LargeDataTest extends AbstractTest {

    private static final int LONG_DATA_SIZE = 100_000;  // for large set around 5GB, try e.g.: 536_870_912;
    private static volatile Throwable exception;
    private static final LongDataReceiver receiver = new LongDataReceiver();

    @BeforeAll
    public static void setup() {
        Extension[] extensions = new Extension[] {
                receiver, new ContentLengthSetter()
        };
        Rules rules = () -> wireMockServer.stubFor(
                        WireMock.any(WireMock.anyUrl()).willReturn(
                                WireMock.ok()));
        setup(rules, extensions);
    }

    @AfterAll
    public static void tearDown() {
        receiver.close();
        AbstractTest.tearDown();
    }

    protected WebTarget target(String uri) {
        WebTarget target = super.target(uri);
        target.property(ClientProperties.READ_TIMEOUT, (int) TimeUnit.MINUTES.toMillis(1L));
        return target;
    }

    @Test
    public void postWithLargeData() throws Throwable {
        long milis = System.currentTimeMillis();
        WebTarget webTarget = target("test");

        try (Response response = webTarget.request().post(Entity.entity(longData(LONG_DATA_SIZE),
                MediaType.TEXT_PLAIN_TYPE))) {
            if (exception != null) {

                // the reason to throw the exception is that IntelliJ gives you an option to compare the expected with the actual
                throw exception;
            }

            assertThat("Unexpected error: " + response.getStatus(),
                    response.getStatusInfo().getFamily(),
                    is(Response.Status.Family.SUCCESSFUL)
            );
        }
        if (LONG_DATA_SIZE > 9_999) {
            System.out.println("Large Data Test took " + (System.currentTimeMillis() - milis) + "milis");
        }
    }

    private static StreamingOutput longData(long sequence) {
        return out -> {
            long offset = 0;
            while (offset < sequence) {
                out.write(Long.toString(offset).getBytes());
                out.write('\n');
                offset++;
            }
        };
    }

    static class LongDataReceiver extends ResponseTransformer implements AutoCloseable {

        final BlockingQueue<Byte> queue = new ArrayBlockingQueue<>(8192);
        final DataVerifier verifier = new DataVerifier();
        final Thread thread;
        LongDataReceiver() {
            thread = new Thread(verifier);
            thread.setDaemon(true);
            thread.start();
        }

        @Override
        public void close() {
            thread.interrupt();
        }

        class DataVerifier implements Runnable {

            @Override
            public void run() {
                try {
                    longData(LONG_DATA_SIZE).write(new OutputStream() {

                        @Override
                        public void write(final int generated) throws IOException {
                            int received = 0;
                            try {
                                received = queue.take().intValue();
                            } catch (InterruptedException e) {
                                throw new IOException(e);
                            }

                            if (received != generated) {
                                long position = 0;
                                throw new IOException("Bytes don't match at position " + position
                                        + ": received=" + received
                                        + ", generated=" + generated);
                            }
                        }
                    });

                } catch (IOException e) {
                    exception = e;
                    throw new ServerErrorException(e.getMessage(), 500, e);
                }
            }
        }

        @Override
        public com.github.tomakehurst.wiremock.http.Response transform(
                Request request,
                com.github.tomakehurst.wiremock.http.Response response,
                FileSource files,
                Parameters parameters) {

            byte [] content = request.getBody();
            for (Byte b : content) {
                do {
                    if (0 < queue.remainingCapacity()) {
                        queue.add(b);
                        break;
                    }
                } while (true);
            }
            return com.github.tomakehurst.wiremock.http.Response.response().build();
        }

        @Override
        public String getName() {
            return "long-data-transformer";
        }
    }
}
