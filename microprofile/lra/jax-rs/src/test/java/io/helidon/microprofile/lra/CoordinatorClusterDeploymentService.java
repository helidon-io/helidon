/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

package io.helidon.microprofile.lra;

import java.lang.System.Logger.Level;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.reactive.Single;
import io.helidon.common.uri.UriQuery;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.lra.coordinator.CoordinatorService;
import io.helidon.microprofile.server.RoutingName;
import io.helidon.microprofile.server.RoutingPath;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientRequest;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.http.HttpService;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;

import static jakarta.interceptor.Interceptor.Priority.PLATFORM_AFTER;

@ApplicationScoped
public class CoordinatorClusterDeploymentService {
    private static final System.Logger LOGGER = System.getLogger(CoordinatorClusterDeploymentService.class.getName());

    static final String LOAD_BALANCER_NAME = "coordinator-loadbalancer";
    static final String COORDINATOR_A_NAME = "coordinator-a";
    static final String COORDINATOR_B_NAME = "coordinator-b";

    static final AtomicReference<String> forbiddenLoadBalancerCall = new AtomicReference<>();

    private URI[] coordinators;
    private AtomicInteger roundRobinIndex;
    private final CompletableFuture<Integer> loadBalancerPort = new CompletableFuture<>();
    private final CompletableFuture<Integer> coordinatorAPort = new CompletableFuture<>();
    private final CompletableFuture<Integer> coordinatorBPort = new CompletableFuture<>();

    @Inject
    Config config;

    @Inject
    ServerCdiExtension serverCdiExtension;

    @Inject
    CoordinatorLocatorService coordinatorLocatorService;

    private void ready(
            @Observes
            @Priority(PLATFORM_AFTER + 101)
            @Initialized(ApplicationScoped.class) Object event,
            BeanManager beanManager) {

        // Retrieve all cluster random ports
        loadBalancerPort.complete(serverCdiExtension.port(LOAD_BALANCER_NAME));
        coordinatorAPort.complete(serverCdiExtension.port(COORDINATOR_A_NAME));
        coordinatorBPort.complete(serverCdiExtension.port(COORDINATOR_B_NAME));

        // Setup loadbalancer for coordinators
        coordinators = new URI[] {
                URI.create("http://localhost:" + getCoordinatorAPort().await() + "/lra-coordinator/start"),
                URI.create("http://localhost:" + getCoordinatorBPort().await() + "/lra-coordinator/start")
        };

        roundRobinIndex = new AtomicInteger(coordinators.length - 1);

        // Provide LRA client with coordinator loadbalancer url
        coordinatorLocatorService.overrideCoordinatorUriSupplier(() ->
                URI.create("http://localhost:" + getLoadBalancerPort().await() + "/lra-coordinator"));
    }

    Single<Integer> getLoadBalancerPort() {
        return Single.create(loadBalancerPort);
    }

    Single<Integer> getCoordinatorAPort() {
        return Single.create(coordinatorAPort);
    }

    Single<Integer> getCoordinatorBPort() {
        return Single.create(coordinatorBPort);
    }

    @Produces
    @ApplicationScoped
    @RoutingName(value = LOAD_BALANCER_NAME, required = true)
    @RoutingPath("/lra-coordinator")
    public HttpService coordinatorLoadBalancerService() {
        return rules ->
                rules.post("/start", (req, res) -> {
                            Http1ClientRequest clientReq = Http1Client.builder()
                                    .baseUri(coordinators[roundRobinIndex.getAndUpdate(o -> o > 0 ? o - 1 :
                                            coordinators.length - 1)])
                                    .build()
                                    .method(req.prologue().method())
                                    .headers(req.headers());

                            // add all query params
                            UriQuery query = req.query();
                            for (String name : query.names()) {
                                clientReq.queryParam(name, query.all(name).toArray(new String[0]));
                            }

                            try (Http1ClientResponse response = clientReq.request()) {
                                response.headers().forEach(res.headers()::set);
                                res.status(response.status())
                                        .send(response.as(String.class));
                            }
                        })
                        .any((req, res) -> {
                            String path = req.path().absolute().path();
                            if (!path.contains("/start")) {
                                LOGGER.log(Level.ERROR, "Loadbalancer should be called only for starting LRA. " + path);
                                forbiddenLoadBalancerCall.set(req.prologue().method().text() + " " + path);
                            }
                            res.next();
                        });
    }

    @Produces
    @ApplicationScoped
    @RoutingName(value = COORDINATOR_A_NAME, required = true)
    @RoutingPath("/lra-coordinator")
    public HttpService coordinatorServiceA() {
        return CoordinatorService.builder()
                .url(() -> URI.create("http://localhost:" + getCoordinatorAPort().await() + "/lra-coordinator"))
                .config(configForCoordinator(COORDINATOR_A_NAME))
                .build();
    }

    @Produces
    @ApplicationScoped
    @RoutingName(value = COORDINATOR_B_NAME, required = true)
    @RoutingPath("/lra-coordinator")
    public HttpService coordinatorServiceB() {
        return CoordinatorService.builder()
                .url(() -> URI.create("http://localhost:" + getCoordinatorBPort().await() + "/lra-coordinator"))
                .config(configForCoordinator(COORDINATOR_B_NAME))
                .build();
    }

    private Config configForCoordinator(String coordinatorName) {
        return Config.create(
                ConfigSources.create(Config.builder()
                        // Coordinator config file
                        .addSource(ConfigSources.classpath("application.yaml"))
                        .addFilter((key, old) ->
                                // Replace jdbc url to avoid collision between coordinators
                                // use in-memory db with different name
                                old.contains("jdbc:h2:file")
                                        ? "jdbc:h2:mem:lra-" + coordinatorName + ";DB_CLOSE_DELAY=-1"
                                        : old
                        )
                        .build()
                        .get(CoordinatorService.CONFIG_PREFIX).detach())
        );
    }

}
