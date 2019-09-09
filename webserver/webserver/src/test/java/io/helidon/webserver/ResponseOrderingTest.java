/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.helidon.common.InputStreamHelper;
import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;
import io.helidon.common.reactive.Multi;

import org.glassfish.jersey.client.JerseyClient;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.glassfish.jersey.client.JerseyWebTarget;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsArrayWithSize.arrayWithSize;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.core.AllOf.allOf;
import static org.hamcrest.core.Is.is;

/**
 * The ResponseOrderingTest tests whether http chunks were sent in a correct order which was reported as MIC-6419.
 * Note that in order to better reproduce the original intermittent failures, {@code REQUESTS_COUNT}
 * environment variable needs to be set to {@code 1000} at least.
 */
public class ResponseOrderingTest {

    private static ConcurrentLinkedQueue<Long> queue = new ConcurrentLinkedQueue<>();
    private static ConcurrentLinkedQueue<Throwable> errorQueue = new ConcurrentLinkedQueue<>();

    private static WebServer webServer;

    /**
     * To test this, run
     * <p>
     * {@code for A in `seq 1000`; do curl http://localhost:8081/ -vvv http://localhost:8081 http://localhost:8081
     * http://localhost:8081 ; done}
     *
     * @param args not used
     */
    public static void main(String[] args) throws InterruptedException, ExecutionException, TimeoutException {

        Routing routing = Routing.builder()
                .any("/multi", (req, res) -> {
                    req.content().as(String.class).whenComplete((o, throwable) -> {
                        queue.add(res.requestId());
                        res.status(201);
                        res.send("" + res.requestId())
                                .exceptionally(throwable1 -> {
                                    errorQueue.add(throwable1);
                                    return null;
                                });
                        System.out.println("Response sent: " + res.requestId() + " .. " + Thread.currentThread().toString());
                    }).exceptionally(throwable -> {
                        throwable.printStackTrace();
                        res.status(500);
                        res.send(throwable.getMessage());
                        return null;
                    });
                })
                .any("/stream", (req, res) -> {
                    res.status(Http.Status.ACCEPTED_202);
                    Multi<DataChunk> multi = Multi.from(req.content()).map(chunk -> {
                        return DataChunk.create(false, chunk.data(), chunk::release);
                    });
                    res.send(multi);
                })
                .error(Throwable.class, (req, res, ex) -> {
                    errorQueue.add(ex);
                    System.out.println("#### EXCEPTION: " + ex);
                    req.next(ex);
                })
                .build();

        webServer = WebServer.create(routing);
        webServer.start()
                .thenRun(() -> System.out.println("UP and RUNNING!"))
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);
        webServer.whenShutdown().thenRun(() -> System.out.println("=============== SERVER IS DOWN ================!"));
    }

    @BeforeAll
    public static void setUp() throws Exception {
        ResponseOrderingTest.main(null);
    }

    @AfterAll
    public static void tearDown() throws Exception {
        webServer.shutdown().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    @Test
    public void testOrdering() throws Exception {
        JerseyClient client = JerseyClientBuilder.createClient();

        JerseyWebTarget target = client.target("http://0.0.0.0:" + webServer.port());
        ArrayList<Long> returnedIds = new ArrayList<>();

        int i1 = Optional.ofNullable(System.getenv("REQUESTS_COUNT")).map(Integer::valueOf).orElse(10);
        for (int i = 0; i < i1; i++) {
            Response response = target.path("multi").request().get();
            assertThat(response.getStatusInfo().getFamily(), is(Response.Status.Family.SUCCESSFUL));

            String entity = response.readEntity(String.class);
            returnedIds.add(Long.valueOf(entity));
        }

        assertThat(returnedIds.toArray(), allOf(arrayWithSize(i1), is(queue.toArray())));
        assertThat("No exceptions expected: " + exceptions(), errorQueue, hasSize(0));
    }

    @Test
    public void testContentOrdering() throws Exception {
        JerseyClient client = JerseyClientBuilder.createClient();

        JerseyWebTarget target = client.target("http://0.0.0.0:" + webServer.port()).path("stream");

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            sb.append(i)
                    .append("\n");
        }
        ByteArrayInputStream inputStream = new ByteArrayInputStream(sb.toString().getBytes());

        Response response = target.request().post(Entity.entity(inputStream, MediaType.APPLICATION_OCTET_STREAM_TYPE));
        InputStream resultStream = response.readEntity(InputStream.class);

        String s = new String(InputStreamHelper.readAllBytes(resultStream));
        assertThat(s, is(sb.toString()));
    }

    private String exceptions() {
        StringWriter writer = new StringWriter();
        for (Throwable throwable : errorQueue) {
            throwable.printStackTrace(new PrintWriter(writer));
            writer.append("\n");
        }
        return writer.toString();
    }
}
