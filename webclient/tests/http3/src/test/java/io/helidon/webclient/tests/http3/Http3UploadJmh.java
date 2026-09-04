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

package io.helidon.webclient.tests.http3;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.webclient.api.ClientAltSvcConfig;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http3.Http3Client;
import io.helidon.webclient.http3.Http3ClientProtocolConfig;
import io.helidon.webclient.http3.Http3ClientResponse;
import io.helidon.webserver.http.AltSvc;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.http1.Http1Config;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.infra.ThreadParams;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@State(Scope.Benchmark)
@BenchmarkMode({Mode.Throughput, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Threads(1)
public class Http3UploadJmh {
    private static final int FLOW_CONTROL_BODY_SIZE = 8 * 1024 * 1024;
    private static final int LARGE_BODY_SIZE = 1024 * 1024;
    private static final byte[] SMALL_BODY = new byte[1024];
    private static final byte[] LARGE_BODY = new byte[LARGE_BODY_SIZE];
    private static final byte[] FLOW_CONTROL_BODY = new byte[FLOW_CONTROL_BODY_SIZE];
    private static final Header DOWNLOAD_TRAILER = HeaderValues.create("benchmark-trailer", "present");

    private TestEnvironment environment;
    private TestEnvironment noAdvertisementEnvironment;
    private TestEnvironment unsupportedAdvertisementEnvironment;
    private TestEnvironment sharedRouteAEnvironment;
    private TestEnvironment sharedRouteBEnvironment;
    private Http3Client client;
    private WebClient genericHttp1Client;
    private WebClient genericHttp3Client;
    private WebClient genericHttp1NoAdvertisementClient;
    private WebClient genericHttp1UnsupportedAltSvcClient;
    private WebClient sharedRouteAClient;
    private WebClient sharedRouteBClient;

    public static void main(String[] args) throws Exception {
        String include = args.length == 0 ? Http3UploadJmh.class.getSimpleName() : args[0];
        Options options = new OptionsBuilder()
                .include(include)
                .shouldFailOnError(true)
                .addProfiler("gc")
                .build();
        new Runner(options).run();
    }

    @Setup
    public void setup() throws Exception {
        Consumer<HttpRouting.Builder> uploadRouting = routing -> routing
                .post("/upload", (request, response) -> {
                    try (var inputStream = request.content().inputStream()) {
                        inputStream.transferTo(OutputStream.nullOutputStream());
                        response.send();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                .get("/download/large", (_, response) -> streamDownload(response, LARGE_BODY, false))
                .get("/download/flow-controlled", (_, response) -> streamDownload(response, FLOW_CONTROL_BODY, false))
                .get("/download/application-trailer", (_, response) -> streamDownload(response, LARGE_BODY, true));
        environment = TestEnvironment.createSharedListener(Http1Config.builder()
                                                                   .altSvc(AltSvc.builder().build())
                                                                   .build(),
                                                               uploadRouting);
        noAdvertisementEnvironment = TestEnvironment.createSharedListener(uploadRouting);
        unsupportedAdvertisementEnvironment = TestEnvironment.createSharedListener(
                routing -> routing.post("/upload", (request, response) -> {
                    try (var inputStream = request.content().inputStream()) {
                        inputStream.transferTo(OutputStream.nullOutputStream());
                        response.header(HeaderNames.ALT_SVC, "h2=\":443\"");
                        response.send();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }));
        sharedRouteAEnvironment = TestEnvironment.createSharedListener(Http1Config.builder()
                                                                               .altSvc(AltSvc.builder().build())
                                                                               .build(),
                                                                           uploadRouting);
        sharedRouteBEnvironment = TestEnvironment.createSharedListener(Http1Config.builder()
                                                                               .altSvc(AltSvc.builder().build())
                                                                               .build(),
                                                                           uploadRouting);
        client = Http3Client.builder()
                .baseUri(environment.baseUri())
                .shareConnectionCache(false)
                .proxy(Proxy.noProxy())
                .tls(environment.clientTlsHttp3())
                .build();
        genericHttp1Client = genericClient(environment,
                                           Http3ClientProtocolConfig.create(),
                                           false,
                                           false);
        genericHttp3Client = genericClient(environment, Http3ClientProtocolConfig.create(), false, true);
        genericHttp1NoAdvertisementClient = genericClient(noAdvertisementEnvironment,
                                                          Http3ClientProtocolConfig.create(),
                                                          false,
                                                          true);
        genericHttp1UnsupportedAltSvcClient = genericClient(unsupportedAdvertisementEnvironment,
                                                            Http3ClientProtocolConfig.create(),
                                                            false,
                                                            true);
        sharedRouteAClient = genericClient(sharedRouteAEnvironment, Http3ClientProtocolConfig.create(), true, true);
        sharedRouteBClient = genericClient(sharedRouteBEnvironment, Http3ClientProtocolConfig.create(), true, true);
        prime(genericHttp1Client, Http1Client.PROTOCOL_ID);
        prime(genericHttp3Client, Http3Client.PROTOCOL_ID);
        prime(genericHttp1NoAdvertisementClient, Http1Client.PROTOCOL_ID);
        prime(genericHttp1UnsupportedAltSvcClient, Http1Client.PROTOCOL_ID);
        prime(sharedRouteAClient, Http3Client.PROTOCOL_ID);
        prime(sharedRouteBClient, Http3Client.PROTOCOL_ID);
    }

    @TearDown
    public void tearDown() {
        client.closeResource();
        genericHttp1Client.closeResource();
        genericHttp3Client.closeResource();
        genericHttp1NoAdvertisementClient.closeResource();
        genericHttp1UnsupportedAltSvcClient.closeResource();
        sharedRouteAClient.closeResource();
        sharedRouteBClient.closeResource();
        environment.close();
        noAdvertisementEnvironment.close();
        unsupportedAdvertisementEnvironment.close();
        sharedRouteAEnvironment.close();
        sharedRouteBEnvironment.close();
    }

    @Benchmark
    public void smallMaterializedUpload(Blackhole blackhole) {
        try (Http3ClientResponse response = client.post("/upload").submit(SMALL_BODY)) {
            blackhole.consume(response.status());
        }
    }

    @Benchmark
    public void cachedGenericHttp1Upload(Blackhole blackhole) {
        try (HttpClientResponse response = genericHttp1Client.post("/upload").submit(SMALL_BODY)) {
            blackhole.consume(response.status());
        }
    }

    @Benchmark
    public void cachedGenericHttp1NoAdvertisementUpload(Blackhole blackhole) {
        try (HttpClientResponse response = genericHttp1NoAdvertisementClient.post("/upload").submit(SMALL_BODY)) {
            blackhole.consume(response.status());
        }
    }

    @Benchmark
    public void cachedGenericHttp1UnsupportedAltSvcUpload(Blackhole blackhole) {
        try (HttpClientResponse response = genericHttp1UnsupportedAltSvcClient.post("/upload").submit(SMALL_BODY)) {
            blackhole.consume(response.status());
        }
    }

    @Benchmark
    public void cachedGenericHttp3Upload(Blackhole blackhole) {
        try (HttpClientResponse response = genericHttp3Client.post("/upload").submit(SMALL_BODY)) {
            blackhole.consume(response.status());
        }
    }

    @Benchmark
    @Threads(8)
    public void cachedGenericHttp3ConcurrentUpload(Blackhole blackhole) {
        try (HttpClientResponse response = genericHttp3Client.post("/upload").submit(SMALL_BODY)) {
            blackhole.consume(response.status());
        }
    }

    @Benchmark
    @Threads(8)
    public void cachedSharedHttp3MultiRouteConcurrentUpload(Blackhole blackhole, ThreadParams threadParams) {
        WebClient routeClient = (threadParams.getThreadIndex() & 1) == 0 ? sharedRouteAClient : sharedRouteBClient;
        try (HttpClientResponse response = routeClient.post("/upload").submit(SMALL_BODY)) {
            blackhole.consume(response.status());
        }
    }

    @Benchmark
    public void largeStreamingUpload(Blackhole blackhole) {
        try (Http3ClientResponse response = client.post("/upload").outputStream(outputStream -> {
            try (outputStream) {
                outputStream.write(LARGE_BODY);
            }
        })) {
            blackhole.consume(response.status());
        }
    }

    @Benchmark
    public void flowControlledStreamingUpload(Blackhole blackhole) {
        try (Http3ClientResponse response = client.post("/upload").outputStream(outputStream -> {
            try (outputStream) {
                outputStream.write(FLOW_CONTROL_BODY);
            }
        })) {
            blackhole.consume(response.status());
        }
    }

    @Benchmark
    public void largeStreamingDownload(Blackhole blackhole) {
        download("/download/large", false, blackhole);
    }

    @Benchmark
    public void flowControlledStreamingDownload(Blackhole blackhole) {
        download("/download/flow-controlled", false, blackhole);
    }

    @Benchmark
    public void largeStreamingDownloadWithApplicationTrailer(Blackhole blackhole) {
        download("/download/application-trailer", true, blackhole);
    }

    private WebClient genericClient(TestEnvironment clientEnvironment,
                                    Http3ClientProtocolConfig protocolConfig,
                                    boolean shareConnectionCache,
                                    boolean altSvcEnabled) {
        var builder = WebClient.builder()
                .baseUri(clientEnvironment.baseUri())
                .shareConnectionCache(shareConnectionCache)
                .keepAlive(true)
                .proxy(Proxy.noProxy())
                .tls(clientEnvironment.clientTlsHttp3())
                .addProtocolPreference(Http3Client.PROTOCOL_ID)
                .addProtocolPreference(Http1Client.PROTOCOL_ID)
                .addProtocolConfig(protocolConfig);
        if (altSvcEnabled) {
            builder.altSvc(ClientAltSvcConfig.create());
        }
        return builder.build();
    }

    private void download(String path, boolean trailers, Blackhole blackhole) {
        try (Http3ClientResponse response = client.get(path).request();
             var inputStream = response.inputStream()) {
            blackhole.consume(response.status());
            blackhole.consume(inputStream.transferTo(OutputStream.nullOutputStream()));
            if (trailers) {
                blackhole.consume(response.trailers().contains(DOWNLOAD_TRAILER));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void streamDownload(ServerResponse response, byte[] body, boolean trailers) {
        // Streaming responses always include the internal stream-result trailer.
        if (trailers) {
            response.header(HeaderNames.TRAILER, DOWNLOAD_TRAILER.name());
        }
        try (var outputStream = response.outputStream()) {
            outputStream.write(body);
            if (trailers) {
                response.trailers().add(DOWNLOAD_TRAILER);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void prime(WebClient client, String expectedProtocol) {
        try (HttpClientResponse first = client.post("/upload").submit(SMALL_BODY);
             HttpClientResponse second = client.post("/upload").submit(SMALL_BODY)) {
            if (!expectedProtocol.equals(second.protocolId())) {
                throw new IllegalStateException("Expected primed protocol " + expectedProtocol
                                                        + ", but got " + second.protocolId());
            }
        }
    }
}
