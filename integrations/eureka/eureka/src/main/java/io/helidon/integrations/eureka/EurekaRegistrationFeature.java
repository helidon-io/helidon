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

import java.lang.System.Logger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.helidon.common.config.Config;
import io.helidon.http.Status;
import io.helidon.service.registry.Services;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientConfig;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRouting;

import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;

import static io.helidon.common.media.type.MediaTypes.APPLICATION_JSON;
import static io.helidon.http.HeaderNames.ACCEPT_ENCODING;
import static io.helidon.http.Status.Family.SUCCESSFUL;
import static jakarta.json.Json.createBuilderFactory;
import static jakarta.json.Json.createValue;
import static jakarta.json.JsonValue.EMPTY_JSON_OBJECT;
import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.WARNING;
import static java.lang.System.getLogger;
import static java.lang.Thread.sleep;

/**
 * An {@link HttpFeature} that unobtrusively attempts to register the current, just-started microservice as a <dfn>Eureka
 * service instance</dfn> with a <a href="https://github.com/Netflix/eureka">Eureka server</a> present elsewhere in the
 * currently running microservice's runtime environment.
 *
 * <p>The Eureka server must be based upon the Eureka codebase at <a
 * href="https://github.com/Netflix/eureka/tree/v2.0.4">version 2.0.4</a> or later or undefined behavior may result.</p>
 *
 * <p>Any failure of registration or deregistration or any other significant operation will be logged and will not
 * prevent the current microservice from otherwise operating normally.</p>
 *
 * <h2>Thread Safety</h2>
 *
 * <p>Instances of this class are safe for concurrent use by multiple threads.</p>
 *
 * <h2>Usage</h2>
 *
 * <p>Ensure this class is on your classpath.</p>
 *
 * <h2>Configuration</h2>
 *
 * <p>Eureka defines its own configuration mechanism and naming scheme for clients that use its native tools. To make
 * migration easier, Helidon's Eureka integration reuses these names, their hierarchy, and their default values where
 * possible.</p>
 *
 * <table class="striped">
 *
 *   <caption style="display:none">Helidon Configuration key names</caption>
 *
 *   <thead>
 *
 *     <tr>
 *
 *       <th scope="col">Name</th>
 *
 *       <th scope="col">Type</th>
 *
 *       <th scope="col">Description</th>
 *
 *       <th scope="col">Default Value</th>
 *
 *       <th scope="col">Notes</th>
 *
 *     </tr>
 *
 *   </thead>
 *
 *   <tbody>
 *
 *     <tr>
 *
 *       <th scope="row">{@code eureka.client.registration}</th>
 *
 *       <td>{@link Config}</td>
 *
 *       <td>A configuration node that <a
 *       href="https://helidon.io/docs/v4/config/io_helidon_webclient_api_HttpClientConfig">describes</a> an {@link
 *       Http1ClientConfig HttpClientConfig}</td>
 *
 *       <td>none</td>
 *
 *       <td>At a minimum, the {@code base-uri} leaf node is required. For testing, a value of {@code
 *       http://localhost:8761/eureka} is often suitable.</td>
 *
 *     </tr>
 *
 *     <tr>
 *
 *       <th scope="row">{@code eureka.instance.instanceId}</th>
 *
 *       <td>{@link String}</td>
 *
 *       <td>The identifier identifying the service instance to be registered.</td>
 *
 *       <td>The value of the {@code eureka.instance.hostName} key, concatenated with a "{@code :}", concatenated with
 *       the value of the port on which the webserver is currently running</td>
 *
 *       <td></td>
 *
 *     </tr>
 *
 *     <tr>
 *
 *       <th scope="row">{@code eureka.instance.name}</th>
 *
 *       <td>{@link String}</td>
 *
 *       <td>The name of the service instance to be registered with Eureka.</td>
 *
 *       <td>{@code unknown}</td>
 *
 *       <td></td>
 *
 *     </tr>
 *
 *     <tr>
 *
 *       <th scope="row">{@code eureka.instance.appGroup}</th>
 *
 *       <td>{@link String}</td>
 *
 *       <td>The name of the application group to which the service belongs.</td>
 *
 *       <td>{@code unknown}</td>
 *
 *       <td></td>
 *
 *     </tr>
 *
 *     <tr>
 *
 *       <th scope="row">{@code eureka.instance.dataCenterInfo.name}</th>
 *
 *       <td>{@link String}</td>
 *
 *       <td>The Eureka-defined name of the <dfn>datacenter type</dfn> of the datacenter within which the service
 *       instance is deployed.</td>
 *
 *       <td>{@code MyOwn}</td>
 *
 *       <td>Eureka permits two values here: either {@code Amazon} or {@code MyOwn}.</td>
 *
 *     </tr>
 *
 *     <tr>
 *
 *       <th scope="row">{@code eureka.instance.ipAddr}</th>
 *
 *       <td>{@link String}</td>
 *
 *       <td>The IP (4) address to be registered for this service instance.</td>
 *
 *       <td>The return value of an invocation of {@link java.net.InetAddress#getHostAddress()} on the return value of
 *       an invocation of {@link java.net.InetAddress#getLocalHost()}</td>
 *
 *       <td></td>
 *
 *     </tr>
 *
 *     <tr>
 *
 *       <th scope="row">{@code eureka.instance.hostName}</th>
 *
 *       <td>{@link String}</td>
 *
 *       <td>The hostname to be registered for this service instance.</td>
 *
 *       <td>The return value of an invocation of {@link java.net.InetAddress#getHostName()} on the return value of an
 *       invocation of {@link java.net.InetAddress#getLocalHost()}</td>
 *
 *       <td></td>
 *
 *     </tr>
 *
 *     <tr>
 *
 *       <th scope="row">{@code eureka.instance.port}</th>
 *
 *       <td>{@code int}</td>
 *
 *       <td>The <dfn>port</dfn> to be registered for this service instance.</td>
 *
 *       <td>If the {@linkplain WebServer#hasTls() webserver has TLS enabled}, the default value is {@code 80}. If the
 *       webserver does not have TLS enabled, the default value is the port on which the webserver is currently running</td>
 *
 *       <td>Eureka makes a distinction between a <dfn>port</dfn> and a <dfn>secure port</dfn>. Both values
 *       are registered, even if one of the two is not applicable.</td>
 *
 *     </tr>
 *
 *     <tr>
 *
 *       <th scope="row">{@code eureka.instance.securePort}</th>
 *
 *       <td>{@code int}</td>
 *
 *       <td>The <dfn>secure port</dfn> to be registered for this service instance.</td>
 *
 *       <td>If the {@linkplain WebServer#hasTls() webserver has TLS enabled}, the default value is the port on which the webserver is currently running. If the
 *       webserver does not have TLS enabled, the default value is {@code 443}.</td>
 *
 *       <td>Eureka makes a distinction between a <dfn>port</dfn> and a <dfn>secure port</dfn>. Both values
 *       are registered, even if one of the two is not applicable.</td>
 *
 *     </tr>
 *
 *     <tr>
 *
 *       <th scope="row">{@code eureka.instance.traffic.enabled}</th>
 *
 *       <td>{@code boolean}</td>
 *
 *       <td>Whether the service instance is able to respond to requests upon registration.</td>
 *
 *       <td>{@code true}</td>
 *
 *       <td></td>
 *
 *     </tr>
 *
 *     <tr>
 *
 *       <th scope="row">{@code eureka.instance.lease.renewalInterval}</th>
 *
 *       <td>{@code int}</td>
 *
 *       <td>The duration, in seconds, between registration (lease) renewal attempts.</td>
 *
 *       <td>{@code 30}</td>
 *
 *       <td></td>
 *
 *     </tr>
 *
 *     <tr>
 *
 *       <th scope="row">{@code eureka.instance.lease.duration}</th>
 *
 *       <td>{@code int}</td>
 *
 *       <td>The duration, in seconds, of a successful registration (lease).</td>
 *
 *       <td>{@code 90}</td>
 *
 *       <td></td>
 *
 *     </tr>
 *
 *     <tr>
 *
 *       <th scope="row">{@code eureka.instance.metadata}</th>
 *
 *       <td>{@link Map Map&lt;String, String&gt;}</td>
 *
 *       <td>{@link String}-typed key-value pairs describing metadata to accompany a service instance registration.</td>
 *
 *       <td>none</td>
 *
 *       <td></td>
 *
 *     </tr>
 *
 *   </tbody>
 *
 * </table>
 *
 * <h2>Logging</h2>
 *
 * <p>The {@link Logger} used by instances of this class is named {@code io.helidon.integtrations.eureka.EurekaRegistrationFeature}.</p>
 *
 * @see #afterStart(WebServer)
 */
public final class EurekaRegistrationFeature implements HttpFeature {


    /*
     * Static fields.
     */


    private static final JsonBuilderFactory JBF = createBuilderFactory(Map.of());

    private static final Logger LOGGER = getLogger(EurekaRegistrationFeature.class.getName());

    private static final JsonString UP = createValue("UP");
    private static final JsonString DOWN = createValue("DOWN");
    private static final JsonString STARTING = createValue("STARTING");
    private static final JsonString OUT_OF_SERVICE = createValue("OUT_OF_SERVICE");
    private static final JsonString UNKNOWN = createValue("UNKNOWN");


    /*
     * Instance fields.
     */


    private volatile JsonObject instanceInfo;

    private volatile boolean stop;

    private volatile Thread renewer;

    private volatile Http1Client client;


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link EurekaRegistrationFeature}.
     *
     * @deprecated For service loader use only.
     */
    @Deprecated // For service loader use only
    public EurekaRegistrationFeature() {
        super();
    }


    /*
     * Public instance methods.
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
            this.afterStart(Services.get(Config.class), webServer.port(), webServer.hasTls());
        }
    }

    // for testing
    void afterStart(Config rootConfig, int actualPort, boolean tls) {
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
        Config eurekaConfig = rootConfig.get("eureka");
        if (!eurekaConfig.isObject()) {
            if (LOGGER.isLoggable(WARNING)) {
                LOGGER.log(WARNING,
                           "No top-level object node named \"eureka\" in global configuration;"
                           + " no attempt at registration will occur");
            }
            return;
        }
        final Http1ClientConfig.Builder builder;
        try {
            builder = Http1ClientConfig.builder()
                .sendExpectContinue(false) // Spring's version of Eureka server has trouble otherwise
                .config(eurekaConfig.get("client.registration"));
        } catch (RuntimeException e) {
            if (LOGGER.isLoggable(ERROR)) {
                LOGGER.log(ERROR,
                           "Error configuring Eureka registration client from top-level object node named "
                           + " \"eureka.client.registration\""
                           + " in global configuration;"
                           + " no attempt at registration will occur",
                           e);
            }
            return;
        }
        if (builder.baseUri().isPresent()) {
            Config eurekaInstanceConfig = eurekaConfig.get("instance");
            if (!eurekaConfig.isObject()) {
                if (LOGGER.isLoggable(WARNING)) {
                    LOGGER.log(WARNING,
                               "No top-level object node named \"eureka.instance\" in global configuration;"
                               + " no attempt at registration will occur");
                }
                return;
            }
            var instanceInfo = json(eurekaInstanceConfig, actualPort, tls);
            var client = builder.build();
            this.instanceInfo = instanceInfo; // volatile write
            this.client = client; // volatile write
            this.createAndStartRenewalLoop(instanceInfo, client);
        } else if (LOGGER.isLoggable(WARNING)) {
            LOGGER.log(WARNING,
                       "No Eureka Server URL found in configuration node named"
                       + " \"eureka.client.registration.base-uri\""
                       + " in global configuration; no attempt at registration will occur");
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
                LOGGER.log(WARNING,
                           "Cancellation operation failed");
            }
            if (e != null && LOGGER.isLoggable(ERROR)) {
                LOGGER.log(ERROR, e);
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
            return false;
        }
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
                        }
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
                // (Can't test for equality with Status.NOT_FOUND_404 equality because the reason is not set by the
                // server.)
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
            return instanceInfo;
        }
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


    /*
     * Package-private static methods.
     */


    /**
     * Returns a {@link JsonObject} representing service instance registration details suitable for sending to a Eureka
     * server.
     *
     * @param config a {@link Config} representing Eureka-related data; often acquired via {@code
     * Services.get(Config.class).get("eureka.instance")}; must not be {@code null}
     *
     * @param actualPort an {@code int} representing the port the currently running microservice is exposed on; not
     * validated in any way
     *
     * @param tls whether TLS is in effect for the currently running microservice; Eureka makes distinctions throughout
     * the registration details concerning "secure" and "non-secure" items based on the value of this parameter
     *
     * @return a {@link JsonObject} representing service instance registration details; never {@code null}
     *
     * @exception NullPointerException if {@code config} is {@code null}
     */
    static JsonObject json(Config config, int actualPort, boolean tls) {
        // JSON will validate successfully against ../../../../../resources/META-INF/instance-info.schema.json.
        // https://github.com/Netflix/eureka/blob/v2.0.4/eureka-client/src/main/java/com/netflix/appinfo/InstanceInfo.java#L55
        // https://github.com/Netflix/eureka/blob/v2.0.4/eureka-client/src/main/java/com/netflix/appinfo/InstanceInfo.java#L164-L189
        // https://github.com/Netflix/eureka/issues/1563#issuecomment-2625648853
        var instance = JBF.createObjectBuilder();

        instance.add("instanceId", instanceId(config, actualPort));

        // https://github.com/Netflix/eureka/blob/v2.0.4/eureka-client/src/main/java/com/netflix/appinfo/InstanceInfo.java#L892
        // https://github.com/Netflix/eureka/blob/v2.0.4/eureka-client/src/main/java/com/netflix/appinfo/PropertiesInstanceConfig.java#L233-L236
        // https://github.com/Netflix/eureka/blob/v2.0.4/eureka-client/src/main/java/com/netflix/appinfo/PropertyBasedInstanceConfigConstants.java#L12
        instance.add("app", config.get("name").asString().orElse("unknown"));

        // https://github.com/Netflix/eureka/blob/v2.0.4/eureka-client/src/main/java/com/netflix/appinfo/PropertyBasedInstanceConfigConstants.java#L13
        // https://github.com/Netflix/eureka/blob/v2.0.4/eureka-client/src/main/java/com/netflix/appinfo/PropertiesInstanceConfig.java#L238-L241
        instance.add("appGroupName", config.get("appGroup").asString().orElse("unknown"));

        var dataCenterInfo = JBF.createObjectBuilder();
        Config dataCenterInfoConfig = config.get("dataCenterInfo");
        String n = dataCenterInfoConfig.isObject() ? dataCenterInfoConfig.get("name").asString().orElse("MyOwn") : "MyOwn";
        switch (n) {
        case "Amazon":
            dataCenterInfo.add("name", "Amazon");
            dataCenterInfo.add("@class", "com.netflix.appinfo.AmazonInfo");
            Config dataCenterInfoMetadataConfig = dataCenterInfoConfig.get("metadata");
            if (dataCenterInfoMetadataConfig.isObject()) {
                dataCenterInfoMetadataConfig.asMap().ifPresent(m -> m.forEach(dataCenterInfo::add));
            }
            break;
        default:
            dataCenterInfo.add("name", "MyOwn");
            dataCenterInfo.add("@class", "com.netflix.appinfo.MyDataCenterInfo");
            break;
        }
        instance.add("dataCenterInfo", dataCenterInfo);

        instance.add("ipAddr", ipAddress(config));

        instance.add("hostName", hostName(config));

        // Add the extremely bizarre port structure.
        instance.add("port", JBF.createObjectBuilder()
                     .add("$", port(config, tls, actualPort))
                     .add("@enabled", portEnabled(config, tls)));

        // Add the extremely bizarre secure port structure.
        instance.add("securePort", JBF.createObjectBuilder()
                     .add("$", securePort(config, tls, actualPort))
                     .add("@enabled", securePortEnabled(config, tls)));

        if (portEnabled(config, tls)) {
            instance.add("vipAddress",
                         config.get("vipAddress").asString()
                         .orElseGet(() -> hostName(config) + ":" + port(config, tls, actualPort)));
        }

        if (securePortEnabled(config, tls)) {
            instance.add("secureVipAddress",
                         config.get("secureVipAddress").asString()
                         .orElseGet(() -> hostName(config) + ":" + securePort(config, tls, actualPort)));
        }

        instance.add("homePageUrl",
                     config.get("homePageUrl").asString()
                     .orElseGet(() -> "http://" + hostName(config) + ":"
                                + port(config, tls, actualPort)
                                + config.get("homePageUrlPath").asString().orElse("/")));

        instance.add("statusPageUrl",
                     config.get("statusPageUrl").asString()
                     .orElseGet(() -> "http://" + hostName(config) + ":"
                                + port(config, tls, actualPort)
                                + config.get("statusPageUrlPath").asString().orElse("/Status")));

        // The acronym ASG means "auto scaling group".
        config.get("asgName").asString().ifPresent(s -> instance.add("asgName", s));

        instance.add("healthCheckUrl", config.get("healthCheckUrl")
                     .asString()
                     .orElseGet(() -> "http://" + hostName(config) + ":"
                                + port(config, tls, actualPort)
                                + config.get("healthCheckUrlPath").asString()
                                .orElseGet(() -> config.root().get("server.features.observe.observers.health.endpoint").asString()
                                           .orElse("/observe/health")))); // Helidon convention; Eureka's is /healthcheck

        instance.add("secureHealthCheckUrl", config.get("healthCheckUrl")
                     .asString()
                     .orElseGet(() -> "https://" + hostName(config) + ":"
                                + port(config, tls, actualPort)
                                + config.get("healthCheckUrlPath").asString()
                                .orElseGet(() -> config.root().get("server.features.observe.observers.health.endpoint").asString()
                                           .orElse("/observe/health")))); // Helidon convention; Eureka's is /healthcheck

        // https://github.com/Netflix/eureka/blob/v2.0.4/eureka-client/src/main/java/com/netflix/appinfo/InstanceInfo.java#L98-L100
        // https://github.com/Netflix/eureka/blob/v2.0.4/eureka-client/src/main/java/com/netflix/appinfo/InstanceInfo.java#L919-L929
        // But also:
        // https://github.com/Netflix/eureka/blob/v2.0.4/eureka-client/src/main/java/com/netflix/appinfo/InstanceInfo.java#L228-L230
        instance.add("sid", config.get("sid").asString().orElse("na"));

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
        instance.add("status", config.get("traffic.enabled").asBoolean().orElse(true) ? "UP" : "STARTING");

        Config metadataConfig = config.get("metadata");
        if (metadataConfig.isObject()) {
            Map<String, String> m = metadataConfig.asMap().get();
            if (m.isEmpty()) {
                instance.add("metadata", EMPTY_JSON_OBJECT);
            } else {
                var metadata = JBF.createObjectBuilder();
                m.forEach(metadata::add);
                instance.add("metadata", metadata);
            }
        } else {
            instance.add("metadata", EMPTY_JSON_OBJECT);
        }

        instance.add("leaseInfo", JBF.createObjectBuilder()
                     .add("renewalIntervalInSecs", config.get("lease.renewalInterval").asInt().orElse(30))
                     .add("durationInSecs", config.get("lease.duration").asInt().orElse(90)));

        return JBF.createObjectBuilder()
            .add("instance", instance) // https://github.com/Netflix/eureka/blob/v2.0.4/eureka-client/src/main/java/com/netflix/appinfo/InstanceInfo.java#L55
            .build();
    }


    /*
     * Private static methods.
     */


    private static String hostName(Config c) {
        // https://github.com/Netflix/eureka/blob/v2.0.4/eureka-client/src/main/java/com/netflix/appinfo/AbstractInstanceConfig.java#L216-L226
        return c.get("hostName").asString()
            .orElseGet(() -> localhost().map(InetAddress::getHostName).orElse(""));
    }

    private static String instanceId(Config c, int actualPort) {
        return c.get("instanceId").asString()
            .orElseGet(() -> {
                    // "Native" Eureka and Spring Cloud Eureka have different defaults.
                    //
                    // See
                    // https://github.com/Netflix/eureka/blob/v2.0.4/eureka-client/src/main/java/com/netflix/appinfo/providers/EurekaConfigBasedInstanceInfoProvider.java#L60-L69;
                    // default is simply hostName
                    //
                    // See
                    // https://cloud.spring.io/spring-cloud-netflix/multi/multi__service_discovery_eureka_clients.html#_changing_the_eureka_instance_id
                    //
                    // Our default will split the difference and use host and port.
                    return c.get("dataCenterInfo.metadata.instance-id").asString()
                        .orElseGet(() -> hostName(c) + ":" + actualPort);
                });
    }

    private static String ipAddress(Config c) {
        // https://github.com/Netflix/eureka/blob/v2.0.4/eureka-client/src/main/java/com/netflix/appinfo/AbstractInstanceConfig.java#L216-L226
        return c.get("ipAddr").asString()
            .orElseGet(() -> localhost().map(InetAddress::getHostAddress).orElse(""));
    }

    private static JsonObject json(JsonObject json, long lastDirtyTimestamp) {
        // https://github.com/Netflix/eureka/blob/v2.0.4/eureka-client/src/main/java/com/netflix/appinfo/InstanceInfo.java#L55
        JsonObject instance = json.getJsonObject("instance");
        if (lastDirtyTimestamp <= instance.getJsonNumber("lastDirtyTimestamp").longValueExact()) {
            return json;
        }
        var b = JBF.createObjectBuilder();
        instance.forEach((k, v) -> {
                b.add(k, k.equals("lastDirtyTimestamp") ? createValue(lastDirtyTimestamp) : v);
            });
        return JBF.createObjectBuilder().add("instance", b).build();
    }

    private static JsonObject json(JsonObject json, JsonString status) {
        // https://github.com/Netflix/eureka/blob/v2.0.4/eureka-client/src/main/java/com/netflix/appinfo/InstanceInfo.java#L55
        JsonObject instance = json.getJsonObject("instance");
        if (instance.get("status").equals(Objects.requireNonNull(status, "status"))) {
            return json;
        }
        var b = JBF.createObjectBuilder();
        var b0 = JBF.createObjectBuilder();
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

    private static Optional<InetAddress> localhost() {
        // https://github.com/Netflix/eureka/blob/v2.0.4/eureka-client/src/main/java/com/netflix/appinfo/AbstractInstanceConfig.java#L216-L226
        try {
            return Optional.of(InetAddress.getLocalHost());
        } catch (UnknownHostException e) {
            if (LOGGER.isLoggable(WARNING)) {
                LOGGER.log(WARNING, e);
            }
            return Optional.empty();
        }
    }

    private static int port(Config config, boolean tls, int actualPort) {
        // https://github.com/Netflix/eureka/blob/v2.0.4/eureka-client/src/main/java/com/netflix/appinfo/PropertiesInstanceConfig.java#L100-L102
        // https://github.com/Netflix/eureka/blob/v2.0.4/eureka-client/src/main/java/com/netflix/appinfo/AbstractInstanceConfig.java#L84-L86
        // https://github.com/Netflix/eureka/blob/v2.0.4/eureka-client/src/main/java/com/netflix/appinfo/AbstractInstanceConfig.java#L49
        // https://github.com/Netflix/eureka/blob/v2.0.4/eureka-client/src/main/java/com/netflix/appinfo/InstanceInfo.java#L85 (!)
        return tls
            ? config.get("port").asInt().orElse(80)
            : actualPort;
    }

    private static boolean portEnabled(Config c, boolean tls) {
        // https://github.com/Netflix/eureka/blob/v2.0.4/eureka-client/src/main/java/com/netflix/appinfo/PropertiesInstanceConfig.java#L120-L122
        // https://github.com/Netflix/eureka/blob/v2.0.4/eureka-client/src/main/java/com/netflix/appinfo/AbstractInstanceConfig.java#L48
        // https://github.com/Netflix/eureka/blob/v2.0.4/eureka-client/src/main/java/com/netflix/appinfo/PropertyBasedInstanceConfigConstants.java#L19
        return tls
            ? c.get("port.enabled").asBoolean().orElse(true)
            : true;
    }

    private static int securePort(Config config, boolean tls, int actualPort) {
        // https://github.com/Netflix/eureka/blob/v2.0.4/eureka-client/src/main/java/com/netflix/appinfo/PropertiesInstanceConfig.java#L110-L112
        // https://github.com/Netflix/eureka/blob/v2.0.4/eureka-client/src/main/java/com/netflix/appinfo/AbstractInstanceConfig.java#L94-L96
        // https://github.com/Netflix/eureka/blob/v2.0.4/eureka-client/src/main/java/com/netflix/appinfo/AbstractInstanceConfig.java#L50
        // https://github.com/Netflix/eureka/blob/v2.0.4/eureka-client/src/main/java/com/netflix/appinfo/InstanceInfo.java#L86 (!)
        return tls
            ? actualPort
            : config.get("securePort").asInt().orElse(443);
    }

    private static boolean securePortEnabled(Config c, boolean tls) {
        // https://github.com/Netflix/eureka/blob/v2.0.4/eureka-client/src/main/java/com/netflix/appinfo/PropertiesInstanceConfig.java#L130-L133
        // https://github.com/Netflix/eureka/blob/v2.0.4/eureka-client/src/main/java/com/netflix/appinfo/AbstractInstanceConfig.java#L47
        // https://github.com/Netflix/eureka/blob/v2.0.4/eureka-client/src/main/java/com/netflix/appinfo/PropertyBasedInstanceConfigConstants.java#L20
        return tls
            ? true
            : c.get("securePort.enabled").asBoolean().orElse(false);
    }

}
