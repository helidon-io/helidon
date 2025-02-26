/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.integrations.eureka;

import java.io.UncheckedIOException;
import java.lang.System.Logger;
import java.net.ConnectException;
import java.net.URI;
import java.util.Map;
import java.util.Objects;

import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientConfig;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRouting;

import jakarta.json.JsonObject;
import jakarta.json.JsonString;

import static io.helidon.common.media.type.MediaTypes.APPLICATION_JSON;
import static io.helidon.http.HeaderNames.ACCEPT_ENCODING;
import static io.helidon.http.Status.Family.SUCCESSFUL;
import static jakarta.json.Json.createValue;
import static jakarta.json.JsonValue.EMPTY_JSON_OBJECT;
import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.WARNING;
import static java.lang.System.getLogger;
import static java.lang.Thread.sleep;

final class EurekaRegistrationHttpFeature implements HttpFeature {


    /*
     * Static fields.
     */


    private static final Logger LOGGER = getLogger(EurekaRegistrationHttpFeature.class.getName());

    private static final JsonString UP = createValue("UP");
    private static final JsonString DOWN = createValue("DOWN");
    private static final JsonString STARTING = createValue("STARTING");
    private static final JsonString OUT_OF_SERVICE = createValue("OUT_OF_SERVICE");
    private static final JsonString UNKNOWN = createValue("UNKNOWN");


    /*
     * Instance fields.
     */


    private final EurekaRegistrationConfig prototype;

    private volatile JsonObject instanceInfo;

    private volatile boolean stop;

    private volatile Thread renewer;

    private volatile Http1Client client;


    /*
     * Constructors.
     */


    EurekaRegistrationHttpFeature(EurekaRegistrationConfig prototype) {
        super();
        this.prototype = Objects.requireNonNull(prototype, "prototype");
    }


    /*
     * Instance methods.
     */


    /**
     * Begins the process of registering the current microservice as a <dfn>Eureka service instance</dfn> in an
     * (external) Eureka server.
     *
     * @param webServer the {@link WebServer} that has successfully started; must not be {@code null}
     *
     * @exception NullPointerException if {@code webServer} is {@code null}
     *
     * @deprecated End users should not call this method.
     */
    @Deprecated // End users should not call this method
    @Override // HttpFeature (ServerLifecycle)
    public void afterStart(WebServer webServer) {
        if (webServer.isRunning()) {
            this.afterStart(webServer.port(), webServer.hasTls());
        }
    }

    // for testing
    void afterStart(int actualPort, boolean tls) {
        if (this.stop) { // volatile read
            // Some other thread called this.afterStop() for some reason. This is technically a programming error, but
            // afterStop() is public, so there are many codepaths that might result in it being called for a variety of
            // reasons, some of which may? perhaps? be valid.
            if (LOGGER.isLoggable(WARNING)) {
                LOGGER.log(WARNING,
                           "Unexpected stop explicitly requested;"
                           + " no attempt at registration will occur");
            }
            return;
        }
        Http1ClientConfig.Builder clientBuilder = null;
        try {
            clientBuilder = this.prototype.clientBuilderSupplier().get();
        } catch (RuntimeException e) {
            if (LOGGER.isLoggable(ERROR)) {
                LOGGER.log(ERROR,
                           "Getting a client builder failed"
                           + " so no attempt at registration can or will occur",
                           e);
            }
            return;
        }
        if (clientBuilder == null) {
            // Should never happen but by no means should we crash.
            if (LOGGER.isLoggable(ERROR)) {
                LOGGER.log(ERROR,
                           "No " + Http1ClientConfig.Builder.class.getName() + " was supplied"
                           + " so no attempt at registration can or will occur");
            }
            return;
        }
        if (clientBuilder.baseUri().isPresent()) {
            JsonObject instanceInfo = null;
            try {
                instanceInfo = json(this.prototype.instanceInfo(), actualPort, tls);
            } catch (RuntimeException e) {
                if (LOGGER.isLoggable(ERROR)) {
                    LOGGER.log(ERROR,
                               "Producing the initial JSON failed"
                               + " so no attempt at registration can or will occur",
                               e);
                }
                return;
            }
            if (instanceInfo == null) {
                // Should never happen but by no means should we crash.
                if (LOGGER.isLoggable(ERROR)) {
                    LOGGER.log(ERROR,
                               "Producing the initial JSON resulted in null"
                               + " so no attempt at registration can or will occur");
                }
                return;
            }
            var client = clientBuilder.sendExpectContinue(false) // Spring's version of Eureka server has trouble otherwise
                .build();
            this.instanceInfo = instanceInfo; // volatile write
            this.client = client; // volatile write
            this.createAndStartRenewalLoop(instanceInfo, client);
        } else if (LOGGER.isLoggable(ERROR)) {
            LOGGER.log(ERROR,
                       "No baseUri was set on " + clientBuilder
                       + " so a Eureka Server cannot be contacted,"
                       + " so no attempt at registration will occur");
        }
    }

    /**
     * Unregisters the current microservice as an available <dfn>Eureka service instance</dfn> in an (external) Eureka
     * server.
     *
     * <p>This method deliberately has no effect after the first time it is invoked.</p>
     *
     * @deprecated End users should not call this method.
     */
    @Deprecated // End users should not call this method
    @Override // HttpFeature (ServerLifecycle)
    public void afterStop() {
        // Although users *should* not call this method, they might, from any thread. Proceed with caution.
        if (this.stop) { // volatile read
            return;
        }
        this.stop = true; // volatile write
        var client = this.client; // volatile read
        if (client == null) {
            // Registration never happened
            if (LOGGER.isLoggable(DEBUG)) {
                LOGGER.log(DEBUG,
                           "No cancellation necessary; registration never occurred");
            }
            return;
        }
        Thread renewer = this.renewer; // volatile read
        if (renewer != null) {
            renewer.interrupt();
        }
        boolean canceled = false;
        RuntimeException e = null;
        try {
            canceled = this.cancel(client);
        } catch (RuntimeException e0) {
            e = e0;
        } finally {
            try {
                client.closeResource();
            } catch (RuntimeException e1) {
                if (e == null) {
                    e = e1;
                } else {
                    e.addSuppressed(e1);
                }
            }
            if (!canceled && LOGGER.isLoggable(WARNING)) {
                if (e == null) {
                    LOGGER.log(WARNING,
                               "Cancellation operation failed");
                } else {
                    LOGGER.log(WARNING,
                               "Cancellation operation failed", e);
                }
            }
        }
    }

    /**
     * Implements the {@link HttpFeature#setup(HttpRouting.Builder)} method by deliberately doing nothing.
     *
     * @param routingBuilder an {@link HttpRouting.Builder}; ignored
     *
     * @deprecated End users should not call this method.
     */
    @Deprecated // End users should not call this method
    @Override // HttpFeature
    public void setup(HttpRouting.Builder routingBuilder) {
        // Nothing to do.
    }


    /*
     * Private instance methods.
     */


    // (Called only by the afterStop method.)
    private boolean cancel(Http1Client client) {
        if (!this.stop) { // volatile read
            // Programming error internal to this class. Truly an illegal state.
            throw new IllegalStateException();
        }
        JsonObject instanceInfo = this.instanceInfo; // volatile read; never null here
        // Native Eureka sets the status to DOWN, but then does not publish this status change, and instead simply
        // forcibly unregisters the instance. I'm not sure what the status setting accomplishes, although buried in the
        // sediment seems to be some kind of status change event system that pertains to Eureka's peer-to-peer
        // replication machinery that we may simply not care about here. We'll follow suit in case this sequencing turns
        // out to be important.
        this.up(instanceInfo, false);
        // https://github.com/Netflix/eureka/blob/v2.0.4/eureka-client/src/main/java/com/netflix/appinfo/InstanceInfo.java#L55
        JsonObject instance = instanceInfo.getJsonObject("instance");
        return this.cancel(client, instance.getString("app"), instance.getString("instanceId"));
    }

    // DELETE {baseUri}/v2/apps/{appName}/{id}
    private boolean cancel(Http1Client client, String appName, String id) {
        try (var response = client
             .delete("/v2/apps/" + Objects.requireNonNull(appName, "appName") + "/" + Objects.requireNonNull(id, "id"))
             .request()) {
            if (response.status().family() == SUCCESSFUL) {
                if (LOGGER.isLoggable(DEBUG)) {
                    LOGGER.log(DEBUG,
                               "DELETE /v2/apps/" + appName + "/" + id + ": " + response.status());
                }
                return true;
            } else if (LOGGER.isLoggable(WARNING)) {
                LOGGER.log(WARNING,
                           "DELETE /v2/apps/" + appName + "/" + id + ": " + response.status());
            }
        } catch (UncheckedIOException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ConnectException) {
                if (LOGGER.isLoggable(WARNING)) {
                    LOGGER.log(WARNING,
                               "Eureka Server ("
                               + this.client.prototype().baseUri().orElse(null) // volatile read
                               + ") not reachable",
                               e);
                }
            } else {
                throw e;
            }
        }
        return false;
    }

    private void createAndStartRenewalLoop(JsonObject instanceInfo, Http1Client client) {
        if (LOGGER.isLoggable(DEBUG)) {
            LOGGER.log(DEBUG,
                       "Creating and starting Eureka lease renewal loop");
        }
        long sleepTimeInMilliSeconds = instanceInfo.getJsonObject("instance") // https://github.com/Netflix/eureka/blob/v2.0.4/eureka-client/src/main/java/com/netflix/appinfo/InstanceInfo.java#L55
            .getJsonObject("leaseInfo")
            .getInt("renewalIntervalInSecs") * 1000L;
        this.renewer = Thread.ofVirtual() // volatile write
            .name("Eureka lease renewer")
            .uncaughtExceptionHandler((t, e) -> {
                    if (LOGGER.isLoggable(ERROR)) {
                        LOGGER.log(ERROR, e.getMessage(), e);
                    }
                    this.stop = true; // volatile write
                })
            .start(() -> {
                    // Simplest possible heartbeat loop; nothing more complicated is needed.
                    while (!this.stop) { // volatile read
                        JsonObject newInstanceInfo = this.renew();
                        if (newInstanceInfo != this.instanceInfo) { // volatile read
                            // The server gave us something new for some reason; use it.
                            this.instanceInfo = newInstanceInfo; // volatile write
                        }
                        try {
                            sleep(sleepTimeInMilliSeconds);
                        } catch (InterruptedException e) {
                            if (LOGGER.isLoggable(DEBUG)) {
                                LOGGER.log(DEBUG,
                                           "Eureka lease renewal loop interrupted",
                                           e);
                            }
                        }
                    }
                    if (LOGGER.isLoggable(DEBUG)) {
                        LOGGER.log(DEBUG,
                                   "Eureka lease renewal loop stopped");
                    }
                });
        // Mark our status as up if it wasn't already
        this.up(instanceInfo, true);
    }

    // PUT {baseUri}/v2/apps/{appName}/{id}?status={status}&lastDirtyTimestamp={lastDirtyTimestamp}
    //
    // (...&overriddenstatus={someOverriddenStatus} is recognized by the Eureka server, but never sent by Eureka's
    // registration client.)
    private Http1ClientResponse heartbeat(String appName,
                                          String id,
                                          String status,
                                          Long lastDirtyTimestamp) {
        var request = this.client // volatile read
            .put("/v2/apps/" + Objects.requireNonNull(appName, "appName") + "/" + Objects.requireNonNull(id, "id"))
            .accept(APPLICATION_JSON);
        if (status != null) {
            request.queryParam("status", status);
        }
        if (lastDirtyTimestamp != null) {
            request.queryParam("lastDirtyTimestamp", lastDirtyTimestamp.toString()); // yes, String-typed value
        }
        return request.request();
    }

    private void statusChange() {
        // For later, perhaps; Eureka's DiscoveryClient can be configured to notify the server "on demand"; not sure
        // whether we should as well
    }

    // POST {baseUri}/v2/apps/{payload.getJsonObject("instance").getString("app")}
    private boolean register(JsonObject payload) {
        if (payload == null) {
            return false;
        }
        try (var response = this.client // volatile read
             .post("/v2/apps/" + payload.getJsonObject("instance").getString("app"))
             .accept(APPLICATION_JSON) // needed? native client has it, but throws any entity away
             .contentType(APPLICATION_JSON)
             .header(ACCEPT_ENCODING, "gzip")
             .submit(payload)) {
            switch (response.status().code()) {
            case 200:
                if (LOGGER.isLoggable(DEBUG)) {
                    if (response.entity().hasEntity()) {
                        LOGGER.log(DEBUG,
                                   "Registration succeeded: 200; " + response.entity().as(JsonObject.class));
                    }
                }
                return true;
            case 204:
                return true;
            default:
                if (response.status().family() == SUCCESSFUL) {
                    return true;
                }
                if (LOGGER.isLoggable(WARNING)) {
                    if (response.entity().hasEntity()) {
                        LOGGER.log(WARNING,
                                   "Registration failed: " + response.status()
                                   + "; " + response.entity().as(JsonObject.class).getString("error"));
                    } else {
                        LOGGER.log(WARNING,
                                   "Registration failed: " + response.status());
                    }
                }
                return false;
            }
        }
    }

    /**
     * Calls the {@link #heartbeat(String, String, String, Long)} method and handles its response appropriately.
     *
     * <p>This method is normally invoked in a loop every 30 seconds or so.</p>
     *
     * @return the {@link JsonObject} representing the service registration details, possibly amended to contain a
     * different status and/or other attributes depending on what the server supplied; never {@code null}
     *
     * @see #heartbeat(String, String, String, Long)
     */
    private JsonObject renew() {
        JsonObject instanceInfo = this.instanceInfo;
        // https://github.com/Netflix/eureka/blob/v2.0.4/eureka-client/src/main/java/com/netflix/appinfo/InstanceInfo.java#L55
        JsonObject instance = instanceInfo.getJsonObject("instance");
        try (var response =
             this.heartbeat(instance.getString("app"),
                            instance.getString("instanceId"),
                            instance.getString("status"),
                            instance.getJsonNumber("lastDirtyTimestamp").longValueExact())) {
            switch (response.status()) {
            case Status s when s.family().equals(SUCCESSFUL):
                if (LOGGER.isLoggable(DEBUG)) {
                    LOGGER.log(DEBUG,
                               "Successfully renewed lease");
                }
                // See
                // https://github.com/Netflix/eureka/blob/v2.0.4/eureka-core/src/main/java/com/netflix/eureka/resources/InstanceResource.java;
                // there is often no entity returned, presumably to indicate no changes.
                if (response.entity().hasEntity()) {
                    instanceInfo = response.entity().as(JsonObject.class);
                    assert instanceInfo != null : "Eureka Server contract violation; instanceInfo == null";
                    if (LOGGER.isLoggable(DEBUG)) {
                        LOGGER.log(DEBUG,
                                   "New registration details received: " + instanceInfo);
                    }
                }
                break;
            case Status s when s.code() == 404:
                // (Can't test for equality with Status.NOT_FOUND_404 equality because the reason is not (always?!) set
                // by the server.)
                //
                // Eureka's native machinery re-registers here.
                if (LOGGER.isLoggable(DEBUG)) {
                    LOGGER.log(DEBUG,
                               "Lease not found; reregistering");
                }
                instanceInfo = json(instanceInfo, System.currentTimeMillis());
                boolean registrationResult = this.register(instanceInfo);
                if (!registrationResult && LOGGER.isLoggable(DEBUG)) {
                    LOGGER.log(DEBUG,
                               "Reregistration failed");
                }
                break;
            default:
                if (LOGGER.isLoggable(WARNING)) {
                    LOGGER.log(WARNING,
                               "Heartbeat HTTP status: " + response.status());
                    if (response.entity().hasEntity()) {
                        LOGGER.log(WARNING,
                                   response.entity().as(JsonObject.class).getString("error"));
                    }
                }
                break;
            }
        } catch (UncheckedIOException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ConnectException) {
                if (LOGGER.isLoggable(WARNING)) {
                    LOGGER.log(WARNING,
                               "Eureka Server ("
                               + this.client.prototype().baseUri().orElse(null) // volatile read
                               + ") not reachable",
                               e);
                }
            } else {
                throw e;
            }
        }
        return instanceInfo;
    }

    private boolean up(JsonObject oldInstanceInfo, boolean up) {
        if (oldInstanceInfo == null) {
            return false;
        }
        JsonObject instanceInfo = json(oldInstanceInfo, up ? UP : DOWN);
        if (instanceInfo == oldInstanceInfo) {
            return false;
        }
        this.instanceInfo = instanceInfo; // volatile write
        this.statusChange();
        return true;
    }

    private JsonObject json(JsonObject json, long lastDirtyTimestamp) {
        // https://github.com/Netflix/eureka/blob/v2.0.4/eureka-client/src/main/java/com/netflix/appinfo/InstanceInfo.java#L55
        JsonObject instance = json.getJsonObject("instance");
        if (lastDirtyTimestamp <= instance.getJsonNumber("lastDirtyTimestamp").longValueExact()) {
            return json;
        }
        var jbf = this.prototype.instanceInfo().jsonBuilderFactory();
        var b = jbf.createObjectBuilder();
        instance.forEach((k, v) -> {
                b.add(k, k.equals("lastDirtyTimestamp") ? createValue(lastDirtyTimestamp) : v);
            });
        return jbf.createObjectBuilder().add("instance", b).build();
    }

    private JsonObject json(JsonObject json, JsonString status) {
        // https://github.com/Netflix/eureka/blob/v2.0.4/eureka-client/src/main/java/com/netflix/appinfo/InstanceInfo.java#L55
        JsonObject instance = json.getJsonObject("instance");
        if (instance.get("status").equals(Objects.requireNonNull(status, "status"))) {
            return json;
        }
        var jbf = this.prototype.instanceInfo().jsonBuilderFactory();
        var b = jbf.createObjectBuilder();
        var b0 = jbf.createObjectBuilder();
        instance.forEach((k, v) -> {
                b0.add(k, switch (k) {
                    case "lastDirtyTimestamp" -> createValue(Long.valueOf(System.currentTimeMillis()));
                    case "status" -> switch (status.getString()) {
                        case "UP" -> UP;
                        case "DOWN" -> DOWN;
                        case "STARTING" -> STARTING;
                        case "OUT_OF_SERVICE" -> OUT_OF_SERVICE;
                        default -> UNKNOWN;
                    };
                    default -> v;
                    });
            });
        b.add("instance", b0);
        return b.build();
    }


    /*
     * Static methods.
     */


    static JsonObject json(InstanceInfoConfig prototype, int actualPort, boolean tls) {
        var jbf = prototype.jsonBuilderFactory();
        var instance = jbf.createObjectBuilder();

        instance.add("instanceId", prototype.instanceId(actualPort));

        // https://github.com/Netflix/eureka/blob/v2.0.4/eureka-client/src/main/java/com/netflix/appinfo/InstanceInfo.java#L892
        // https://github.com/Netflix/eureka/blob/v2.0.4/eureka-client/src/main/java/com/netflix/appinfo/PropertiesInstanceConfig.java#L233-L236
        // https://github.com/Netflix/eureka/blob/v2.0.4/eureka-client/src/main/java/com/netflix/appinfo/PropertyBasedInstanceConfigConstants.java#L12
        instance.add("app", prototype.appName());

        // https://github.com/Netflix/eureka/blob/v2.0.4/eureka-client/src/main/java/com/netflix/appinfo/PropertyBasedInstanceConfigConstants.java#L13
        // https://github.com/Netflix/eureka/blob/v2.0.4/eureka-client/src/main/java/com/netflix/appinfo/PropertiesInstanceConfig.java#L238-L241
        instance.add("appGroupName", prototype.appGroupName());

        instance.add("dataCenterInfo", jbf.createObjectBuilder()
                     .add("name", "MyOwn") // ! yes really
                     .add("@class", "com.netflix.appinfo.MyDataCenterInfo")); // ! yes really

        instance.add("ipAddr", prototype.ipAddr());

        instance.add("hostName", prototype.hostName());

        boolean portEnabled = prototype.portEnabled(tls);
        int port = prototype.port(actualPort, tls);
        instance.add("port", jbf.createObjectBuilder()
                     .add("$", port)
                     .add("@enabled", portEnabled));

        boolean securePortEnabled = prototype.securePortEnabled(tls);
        int securePort = prototype.securePort(actualPort, tls);
        instance.add("securePort", jbf.createObjectBuilder()
                     .add("$", securePort)
                     .add("@enabled", securePortEnabled));

        if (portEnabled) {
            instance.add("vipAddress",
                         prototype.vipAddress().orElseGet(() -> prototype.hostName() + ":" + port));
        }

        if (securePortEnabled) {
            instance.add("secureVipAddress",
                         prototype.secureVipAddress().orElseGet(() -> prototype.hostName() + ":" + securePort));
        }

        instance.add("homePageUrl",
                     prototype.homePageUrl()
                     .map(URI::toString)
                     .orElseGet(() -> "http://" + prototype.hostName() + ":"
                                + port + prototype.homePageUrlPath()));

        instance.add("statusPageUrl",
                     prototype.statusPageUrl()
                     .map(URI::toString)
                     .orElseGet(() -> "http://" + prototype.hostName() + ":"
                                + port
                                + prototype.statusPageUrlPath()));

        prototype.asgName().ifPresent(asgName -> instance.add("asgName", asgName));

        instance.add("healthCheckUrl",
                     prototype.healthCheckUrl()
                     .map(URI::toString)
                     .orElseGet(() -> "http://" + prototype.hostName() + ":"
                                + port
                                + prototype.healthCheckUrlPath()));

        instance.add("secureHealthCheckUrl",
                     prototype.secureHealthCheckUrl()
                     .map(URI::toString)
                     .orElseGet(() -> "https://" + prototype.hostName() + ":"
                                + securePort
                                + prototype.healthCheckUrlPath()));

        // https://github.com/Netflix/eureka/blob/v2.0.4/eureka-client/src/main/java/com/netflix/appinfo/InstanceInfo.java#L98-L100
        // https://github.com/Netflix/eureka/blob/v2.0.4/eureka-client/src/main/java/com/netflix/appinfo/InstanceInfo.java#L919-L929
        // But also:
        // https://github.com/Netflix/eureka/blob/v2.0.4/eureka-client/src/main/java/com/netflix/appinfo/InstanceInfo.java#L228-L230
        instance.add("sid", "na");

        // countryId; cannot be anything other than 1; must be shipped in the payload, however, to ensure data integrity
        // on the server side
        instance.add("countryId", 1);

        // (The default value must be shipped with the payload to ensure data integrity on the server. Or so it seems?
        // There are places in the Eureka codebase where the field is dereferenced assuming it is not null; the JSON
        // marshalling can and absolutely will set it to null. Which is correct? it is hard to say.)
        // https://github.com/Netflix/eureka/blob/v2.0.4/eureka-client/src/main/java/com/netflix/appinfo/InstanceInfo.java#L209
        // https://github.com/Netflix/eureka/blob/v2.0.4/eureka-client/src/main/java/com/netflix/appinfo/InstanceInfo.java#L138
        instance.add("overriddenStatus", "UNKNOWN");

        long ts = System.currentTimeMillis();
        instance.add("lastUpdatedTimestamp", ts);
        instance.add("lastDirtyTimestamp", ts);

        // https://github.com/Netflix/eureka/blob/v2.0.4/eureka-client/src/main/java/com/netflix/appinfo/InstanceInfo.java#L137
        // https://github.com/Netflix/eureka/blob/v2.0.4/eureka-client/src/main/java/com/netflix/appinfo/InstanceInfo.java#L316-L321
        // https://github.com/Netflix/eureka/blob/v2.0.4/eureka-client/src/main/java/com/netflix/appinfo/providers/EurekaConfigBasedInstanceInfoProvider.java#L104-L113
        // Eureka uses false as a default value, but I think true is better given that we are running in afterStart()
        instance.add("status", prototype.trafficEnabled() ? "UP" : "STARTING");

        Map<String, String> metadata = prototype.metadata();
        if (metadata.isEmpty()) {
            instance.add("metadata", EMPTY_JSON_OBJECT);
        } else {
            var mb = jbf.createObjectBuilder();
            metadata.forEach(mb::add);
            instance.add("metadata", mb);
        }

        instance.add("leaseInfo", jbf.createObjectBuilder()
                     .add("renewalIntervalInSecs", prototype.leaseInfo().renewalIntervalInSecs())
                     .add("durationInSecs", prototype.leaseInfo().durationInSecs()));

        return jbf.createObjectBuilder()
            .add("instance", instance) // https://github.com/Netflix/eureka/blob/v2.0.4/eureka-client/src/main/java/com/netflix/appinfo/InstanceInfo.java#L55
            .build();
    }

}
