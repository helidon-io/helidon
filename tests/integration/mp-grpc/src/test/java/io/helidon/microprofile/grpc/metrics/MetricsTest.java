/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.grpc.metrics;

import java.io.StringReader;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import javax.enterprise.inject.Instance;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;
import javax.json.Json;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;

import io.helidon.grpc.examples.common.StringServiceGrpc;
import io.helidon.grpc.examples.common.Strings.StringMessage;
import io.helidon.grpc.server.GrpcServer;
import io.helidon.microprofile.grpc.server.GrpcServerCdiExtension;
import io.helidon.microprofile.server.Server;

import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.logging.LoggingFeature;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import services.StringService;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Functional test of gRPC MP metrics.
 * <p>
 * The {@link services.EchoService} and {@link services.StringService }beans should be discovered and deployed
 * automatically as they are annotated with both the {@link io.helidon.microprofile.grpc.core.GrpcService} annotation
 * and the {@link javax.enterprise.context.ApplicationScoped} annotation. Their methods are annotated with various
 * microprofile metrics annotations so they should automatically generate the correct metrics.
 */
@Disabled
public class MetricsTest {
    private static final Logger LOGGER = Logger.getLogger(MetricsTest.class.getName());

    private static Server server;

    private static BeanManager beanManager;

    private static Client client;

    private static Channel channel;

    private static StringServiceGrpc.StringServiceBlockingStub stringStub;

    private static Instance<Object> instance;

    @BeforeAll
    public static void startServer() throws Exception {
        LogManager.getLogManager().readConfiguration(MetricsTest.class.getResourceAsStream("/logging.properties"));

        server = Server.create().start();
        beanManager = CDI.current().getBeanManager();

        client = ClientBuilder.newBuilder()
                .register(new LoggingFeature(LOGGER, Level.WARNING, LoggingFeature.Verbosity.PAYLOAD_ANY, 500))
                .property(ClientProperties.FOLLOW_REDIRECTS, true)
                .build();

        instance = beanManager.createInstance();

        GrpcServer grpcServer = instance.select(GrpcServerCdiExtension.ServerProducer.class).get().server();

        channel = ManagedChannelBuilder.forAddress("localhost", grpcServer.port())
                                       .usePlaintext()
                                       .build();

        stringStub = StringServiceGrpc.newBlockingStub(channel);
    }

    @AfterAll
    public static void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    /**
     * Verify that the StringService upper method has the counter metric.
     */
    @Test
    public void shouldHaveCounterMetric() {
        JsonObject json = getMetrics();
        int count = json.getInt(StringService.class.getName() + ".upper");

        assertThat(count, is(0));
        stringStub.upper(StringMessage.newBuilder().setText("foo").build());

        json = getMetrics();
        count = json.getInt(StringService.class.getName() + ".upper");
        assertThat(count, is(1));
    }

    /**
     * Verify that the StringService lower method has the meter metric.
     */
    @Test
    public void shouldHaveMeterMetric() {
        JsonObject json = getMetrics();
        JsonObject meter = json.getJsonObject(StringService.class.getName() + ".lower");

        assertThat(meter, is(notNullValue()));

        int count = meter.getInt("count");
        JsonNumber meanRate = meter.getJsonNumber("meanRate");

        assertThat(count, is(0));
        assertThat(meanRate, is(notNullValue()));
        assertThat(meanRate.doubleValue(), is(0.0));

        stringStub.lower(StringMessage.newBuilder().setText("FOO").build());

        json = getMetrics();
        meter = json.getJsonObject(StringService.class.getName() + ".lower");

        assertThat(meter, is(notNullValue()));

        count = meter.getInt("count");
        meanRate = meter.getJsonNumber("meanRate");

        assertThat(count, is(1));
        assertThat(meanRate, is(notNullValue()));
        assertThat(meanRate.doubleValue(), is(not(0.0)));
    }

    /**
     * Verify that the StringService split method has the timer metric.
     */
    @Test
    public void shouldHaveTimerMetric() {
        JsonObject json = getMetrics();
        JsonObject timer = json.getJsonObject(StringService.class.getName() + ".split");

        assertThat(timer, is(notNullValue()));

        int count = timer.getInt("count");
        int min = timer.getInt("min");

        assertThat(count, is(0));
        assertThat(min, is(0));

        Iterator<StringMessage> iterator = stringStub.split(StringMessage.newBuilder().setText("A B C").build());
        while (iterator.hasNext()) {
            iterator.next();
        }

        json = getMetrics();
        timer = json.getJsonObject(StringService.class.getName() + ".split");

        assertThat(timer, is(notNullValue()));

        count = timer.getInt("count");
        min = timer.getInt("min");

        assertThat(count, is(1));
        assertThat(min, is(not(0)));
    }

    private JsonObject getMetrics() {
        // request the application metrics in json format from the web server
        String metrics = client.target("http://localhost:" + server.port())
                .path("metrics/application")
                .request()
                .accept(MediaType.APPLICATION_JSON)
                .get(String.class);

        JsonObject json = (JsonObject) Json.createReader(new StringReader(metrics)).read();
        assertThat(json, is(notNullValue()));
        return json;
    }
}
