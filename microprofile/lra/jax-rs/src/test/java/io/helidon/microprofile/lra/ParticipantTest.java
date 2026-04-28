/*
 * Copyright (c) 2021, 2026 Oracle and/or its affiliates.
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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.http.Status;
import io.helidon.lra.coordinator.client.CoordinatorClient;
import io.helidon.microprofile.config.ConfigCdiExtension;
import io.helidon.microprofile.lra.resources.DontEnd;
import io.helidon.microprofile.lra.resources.Work;
import io.helidon.microprofile.server.JaxRsCdiExtension;
import io.helidon.microprofile.server.RoutingPath;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.AddConfig;
import io.helidon.microprofile.testing.junit5.AddExtension;
import io.helidon.microprofile.testing.junit5.DisableDiscovery;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import io.helidon.webserver.http.HttpService;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.lra.annotation.AfterLRA;
import org.eclipse.microprofile.lra.annotation.Compensate;
import org.eclipse.microprofile.lra.annotation.Complete;
import org.eclipse.microprofile.lra.annotation.Forget;
import org.eclipse.microprofile.lra.annotation.LRAStatus;
import org.eclipse.microprofile.lra.annotation.ParticipantStatus;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;
import org.glassfish.jersey.ext.cdi1x.internal.CdiComponentProvider;
import org.hamcrest.core.AnyOf;
import org.junit.jupiter.api.Test;

import static jakarta.interceptor.Interceptor.Priority.PLATFORM_AFTER;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;

@HelidonTest
@DisableDiscovery
// Helidon MP
@AddExtension(ConfigCdiExtension.class)
@AddExtension(ServerCdiExtension.class)
@AddExtension(JaxRsCdiExtension.class)
@AddExtension(CdiComponentProvider.class)
// LRA client
@AddExtension(LraCdiExtension.class)
// Test resources
@AddBean(ParticipantTest.TestResource.class)
@AddBean(ParticipantTest.DirectCallbackResource.class)
@AddBean(ParticipantTest.JaxRsParticipantResource.class)
@AddBean(ParticipantTest.InheritedJaxRsParticipantResource.class)
@AddBean(ParticipantTest.TransitiveInterfaceJaxRsParticipantResource.class)
@AddBean(ParticipantTest.GenericInterfaceJaxRsParticipantResource.class)
@AddBean(ParticipantTest.SuperclassJaxRsParticipantResource.class)
@AddBean(ParticipantTest.OverridingJaxRsParticipantResource.class)
@AddBean(ParticipantTest.CustomHttpMethodParticipantResource.class)
@AddBean(ParticipantTest.SuperclassCallbackInterfaceJaxRsParticipantResource.class)
@AddBean(ParticipantTest.PartialOverrideJaxRsParticipantResource.class)
@AddBean(ParticipantTest.ConsumesOverrideJaxRsParticipantResource.class)
@AddBean(ParticipantTest.HeaderParamOverrideJaxRsParticipantResource.class)
@AddBean(ParticipantTest.SuperclassConsumesInterfaceJaxRsParticipantResource.class)
// Override context
@AddConfig(key = NonJaxRsResource.CONFIG_CONTEXT_PATH_KEY, value = ParticipantTest.CUSTOM_CONTEXT)
class ParticipantTest {

    private static final long TIMEOUT_SEC = 10L;
    static final String CUSTOM_CONTEXT = "custom-lra-context";

    private static final AtomicBoolean UNANNOTATED_METHOD_CALLED = new AtomicBoolean();
    private static final AtomicBoolean UNREGISTERED_PARTICIPANT_INITIALIZED = new AtomicBoolean();
    private static final AtomicBoolean COMPLETE_CALLED = new AtomicBoolean();
    private static final AtomicBoolean COMPENSATE_CALLED = new AtomicBoolean();
    private static final AtomicBoolean JAX_RS_COMPENSATE_CALLED = new AtomicBoolean();
    private static final AtomicBoolean INHERITED_JAX_RS_COMPENSATE_CALLED = new AtomicBoolean();
    private static final AtomicBoolean TRANSITIVE_INTERFACE_JAX_RS_COMPENSATE_CALLED = new AtomicBoolean();
    private static final AtomicBoolean GENERIC_INTERFACE_JAX_RS_COMPENSATE_CALLED = new AtomicBoolean();
    private static final AtomicBoolean SUPERCLASS_JAX_RS_COMPENSATE_CALLED = new AtomicBoolean();
    private static final AtomicBoolean OVERRIDING_JAX_RS_COMPENSATE_CALLED = new AtomicBoolean();
    private static final AtomicBoolean CUSTOM_HTTP_METHOD_COMPENSATE_CALLED = new AtomicBoolean();
    private static final AtomicBoolean SUPERCLASS_CALLBACK_INTERFACE_JAX_RS_COMPENSATE_CALLED = new AtomicBoolean();
    private static final AtomicBoolean PARTIAL_OVERRIDE_JAX_RS_COMPENSATE_CALLED = new AtomicBoolean();
    private static final AtomicBoolean CONSUMES_OVERRIDE_JAX_RS_COMPENSATE_CALLED = new AtomicBoolean();
    private static final AtomicBoolean HEADER_PARAM_OVERRIDE_JAX_RS_COMPENSATE_CALLED = new AtomicBoolean();
    private static final AtomicBoolean SUPERCLASS_CONSUMES_INTERFACE_JAX_RS_COMPENSATE_CALLED = new AtomicBoolean();
    private static final AtomicBoolean DIRECT_COMPLETE_CALLED = new AtomicBoolean();
    private static final AtomicBoolean DIRECT_COMPENSATE_CALLED = new AtomicBoolean();
    private static final AtomicBoolean DIRECT_FORGET_CALLED = new AtomicBoolean();
    private static final AtomicBoolean DIRECT_AFTER_LRA_CALLED = new AtomicBoolean();
    private static final AtomicBoolean DIRECT_STATUS_CALLED = new AtomicBoolean();

    private volatile int port = -1;

    private List<String> paths = Collections.synchronizedList(new ArrayList<>());
    private CompletableFuture<Void> completed = new CompletableFuture<>();

    @Inject
    CoordinatorClient coordinatorClient;

    @Inject
    CoordinatorLocatorService coordinatorLocatorService;

    @Produces
    @ApplicationScoped
    @RoutingPath("/lra-coordinator")
    HttpService mockCoordinator() {
        return rules -> rules
                .post("/start", (req, res) -> {
                    String lraId = URI.create("http://localhost:" + port + "/lra-coordinator/xxx-xxx-001").toASCIIString();
                    res.status(Status.CREATED_201)
                            .header(LRA_HTTP_CONTEXT_HEADER, lraId)
                            .send();
                })
                .put("/{lraId}/close", (req, res) -> {
                    res.send();
                })
                .put("/{lraId}", (req, res) -> {
                    Arrays.stream(req.content().as(String.class).split(","))
                            .map(s -> s.split(";")[0])
                            .map(s -> s
                                    .replaceAll("^<", "")
                                    .replaceAll(">$", "")
                            )
                            .map(URI::create)
                            .map(URI::getPath)
                            .forEach(paths::add);
                    res.send();
                    completed.complete(null);
                });
    }

    private void ready(
            @Observes
            @Priority(PLATFORM_AFTER + 101)
            @Initialized(ApplicationScoped.class) Object event,
            BeanManager bm) {
        port = bm.getExtension(ServerCdiExtension.class).port();
        // Provide LRA client with coordinator loadbalancer url
        coordinatorLocatorService.overrideCoordinatorUriSupplier(() ->
                URI.create("http://localhost:" + port + "/lra-coordinator"));
    }

    @Test
    void configurableContext(WebTarget target) throws Exception {
        Response response = target.path("participant-test")
                .path("start")
                .request()
                .header(Work.HEADER_KEY, Work.NOOP)
                .async()
                .put(Entity.text(""))
                .get(TIMEOUT_SEC, TimeUnit.SECONDS);

        assertThat(response.getStatus(), AnyOf.anyOf(is(200), is(204)));

        completed.get(TIMEOUT_SEC, TimeUnit.SECONDS);

        assertThat(paths.size(), is(2));
        paths.forEach(path -> assertThat(path, startsWith("/" + CUSTOM_CONTEXT)));
        assertThat(paths, hasItems("/" + CUSTOM_CONTEXT + "/complete/" + TestResource.class.getName() + "/complete",
                                   "/" + CUSTOM_CONTEXT + "/compensate/" + TestResource.class.getName() + "/compensate"));
    }

    @Test
    void rejectUnregisteredParticipantClass(WebTarget target) throws Exception {
        UNREGISTERED_PARTICIPANT_INITIALIZED.set(false);

        Response response = participantCallback(target, "compensate", UnregisteredParticipant.class, "compensate");

        assertThat(response.getStatus(), is(Response.Status.NOT_FOUND.getStatusCode()));
        assertThat(UNREGISTERED_PARTICIPANT_INITIALIZED.get(), is(false));
    }

    @Test
    void rejectUnannotatedParticipantMethod(WebTarget target) throws Exception {
        UNANNOTATED_METHOD_CALLED.set(false);

        Response response = participantCallback(target, "compensate", TestResource.class, "unannotatedParticipantMethod");

        assertThat(response.getStatus(), is(Response.Status.NOT_FOUND.getStatusCode()));
        assertThat(UNANNOTATED_METHOD_CALLED.get(), is(false));
    }

    @Test
    void rejectJaxRsParticipantMethod(WebTarget target) throws Exception {
        JAX_RS_COMPENSATE_CALLED.set(false);

        Response response = participantCallback(target, "compensate", JaxRsParticipantResource.class, "jaxRsCompensate");

        assertThat(response.getStatus(), is(Response.Status.NOT_FOUND.getStatusCode()));
        assertThat(JAX_RS_COMPENSATE_CALLED.get(), is(false));
    }

    @Test
    void rejectInheritedJaxRsParticipantMethod(WebTarget target) throws Exception {
        INHERITED_JAX_RS_COMPENSATE_CALLED.set(false);

        Response response = participantCallback(target, "compensate", InheritedJaxRsParticipantResource.class,
                                                "inheritedCompensate");

        assertThat(response.getStatus(), is(Response.Status.NOT_FOUND.getStatusCode()));
        assertThat(INHERITED_JAX_RS_COMPENSATE_CALLED.get(), is(false));
    }

    @Test
    void rejectTransitiveInterfaceJaxRsParticipantMethod(WebTarget target) throws Exception {
        TRANSITIVE_INTERFACE_JAX_RS_COMPENSATE_CALLED.set(false);

        Response response = participantCallback(target, "compensate", TransitiveInterfaceJaxRsParticipantResource.class,
                                                "transitiveInterfaceCompensate");

        assertThat(response.getStatus(), is(Response.Status.NOT_FOUND.getStatusCode()));
        assertThat(TRANSITIVE_INTERFACE_JAX_RS_COMPENSATE_CALLED.get(), is(false));
    }

    @Test
    void rejectGenericInterfaceJaxRsParticipantMethod(WebTarget target) throws Exception {
        GENERIC_INTERFACE_JAX_RS_COMPENSATE_CALLED.set(false);

        Response response = participantCallback(target, "compensate", GenericInterfaceJaxRsParticipantResource.class,
                                                "genericInterfaceCompensate");

        assertThat(response.getStatus(), is(Response.Status.NOT_FOUND.getStatusCode()));
        assertThat(GENERIC_INTERFACE_JAX_RS_COMPENSATE_CALLED.get(), is(false));
    }

    @Test
    void rejectSuperclassJaxRsParticipantMethod(WebTarget target) throws Exception {
        SUPERCLASS_JAX_RS_COMPENSATE_CALLED.set(false);

        Response response = participantCallback(target, "compensate", SuperclassJaxRsParticipantResource.class,
                                                "superclassCompensate");

        assertThat(response.getStatus(), is(Response.Status.NOT_FOUND.getStatusCode()));
        assertThat(SUPERCLASS_JAX_RS_COMPENSATE_CALLED.get(), is(false));
    }

    @Test
    void rejectOverridingJaxRsParticipantMethod(WebTarget target) throws Exception {
        OVERRIDING_JAX_RS_COMPENSATE_CALLED.set(false);

        Response response = participantCallback(target, "compensate", OverridingJaxRsParticipantResource.class,
                                                "overridingCompensate");

        assertThat(response.getStatus(), is(Response.Status.NOT_FOUND.getStatusCode()));
        assertThat(OVERRIDING_JAX_RS_COMPENSATE_CALLED.get(), is(false));
    }

    @Test
    void rejectCustomHttpMethodParticipantMethod(WebTarget target) throws Exception {
        CUSTOM_HTTP_METHOD_COMPENSATE_CALLED.set(false);

        Response response = participantCallback(target, "compensate", CustomHttpMethodParticipantResource.class,
                                                "customHttpMethodCompensate");

        assertThat(response.getStatus(), is(Response.Status.NOT_FOUND.getStatusCode()));
        assertThat(CUSTOM_HTTP_METHOD_COMPENSATE_CALLED.get(), is(false));
    }

    @Test
    void invokeSuperclassCallbackWithInterfaceJaxRsAsNonJaxRsParticipantMethod(WebTarget target) throws Exception {
        SUPERCLASS_CALLBACK_INTERFACE_JAX_RS_COMPENSATE_CALLED.set(false);

        Response response = participantCallback(target, "compensate",
                                                SuperclassCallbackInterfaceJaxRsParticipantResource.class,
                                                "interfaceSuperclassCompensate");

        assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
        assertThat(SUPERCLASS_CALLBACK_INTERFACE_JAX_RS_COMPENSATE_CALLED.get(), is(true));
    }

    @Test
    void invokeConsumesOnlyOverrideAsNonJaxRsParticipantMethod(WebTarget target) throws Exception {
        CONSUMES_OVERRIDE_JAX_RS_COMPENSATE_CALLED.set(false);

        Response response = participantCallback(target, "compensate", ConsumesOverrideJaxRsParticipantResource.class,
                                                "consumesOverrideCompensate");

        assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
        assertThat(CONSUMES_OVERRIDE_JAX_RS_COMPENSATE_CALLED.get(), is(true));
    }

    @Test
    void invokeHeaderParamOverrideAsNonJaxRsParticipantMethod(WebTarget target) throws Exception {
        HEADER_PARAM_OVERRIDE_JAX_RS_COMPENSATE_CALLED.set(false);

        Response response = participantCallback(target, "compensate", HeaderParamOverrideJaxRsParticipantResource.class,
                                                "headerParamOverrideCompensate");

        assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
        assertThat(HEADER_PARAM_OVERRIDE_JAX_RS_COMPENSATE_CALLED.get(), is(true));
    }

    @Test
    void invokeSuperclassConsumesWithInterfaceJaxRsAsNonJaxRsParticipantMethod(WebTarget target) throws Exception {
        SUPERCLASS_CONSUMES_INTERFACE_JAX_RS_COMPENSATE_CALLED.set(false);

        Response response = participantCallback(target, "compensate",
                                                SuperclassConsumesInterfaceJaxRsParticipantResource.class,
                                                "superclassConsumesInterfaceCompensate");

        assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
        assertThat(SUPERCLASS_CONSUMES_INTERFACE_JAX_RS_COMPENSATE_CALLED.get(), is(true));
    }

    @Test
    void invokeInheritedJaxRsParticipantMethods(WebTarget target) throws Exception {
        INHERITED_JAX_RS_COMPENSATE_CALLED.set(false);
        TRANSITIVE_INTERFACE_JAX_RS_COMPENSATE_CALLED.set(false);
        GENERIC_INTERFACE_JAX_RS_COMPENSATE_CALLED.set(false);
        SUPERCLASS_JAX_RS_COMPENSATE_CALLED.set(false);
        OVERRIDING_JAX_RS_COMPENSATE_CALLED.set(false);
        PARTIAL_OVERRIDE_JAX_RS_COMPENSATE_CALLED.set(false);

        Response inherited = jaxRsCallback(target, "inherited-jax-rs-participant-test",
                                           "inherited-compensate");
        Response transitiveInterface = jaxRsCallback(target, "transitive-interface-jax-rs-participant-test",
                                                     "transitive-interface-compensate");
        Response genericInterface = jaxRsCallback(target, "generic-interface-jax-rs-participant-test",
                                                  "generic-interface-compensate");
        Response superclass = jaxRsCallback(target, "superclass-jax-rs-participant-test",
                                            "superclass-compensate");
        Response overriding = jaxRsCallback(target, "overriding-jax-rs-participant-test",
                                            "overriding-compensate");
        Response partialOverride = jaxRsCallback(target, "partial-override-jax-rs-participant-test", "");

        assertThat(inherited.getStatus(), is(Response.Status.OK.getStatusCode()));
        assertThat(transitiveInterface.getStatus(), is(Response.Status.OK.getStatusCode()));
        assertThat(genericInterface.getStatus(), is(Response.Status.OK.getStatusCode()));
        assertThat(superclass.getStatus(), is(Response.Status.OK.getStatusCode()));
        assertThat(overriding.getStatus(), is(Response.Status.OK.getStatusCode()));
        assertThat(partialOverride.getStatus(), is(Response.Status.OK.getStatusCode()));
        assertThat(INHERITED_JAX_RS_COMPENSATE_CALLED.get(), is(true));
        assertThat(TRANSITIVE_INTERFACE_JAX_RS_COMPENSATE_CALLED.get(), is(true));
        assertThat(GENERIC_INTERFACE_JAX_RS_COMPENSATE_CALLED.get(), is(true));
        assertThat(SUPERCLASS_JAX_RS_COMPENSATE_CALLED.get(), is(true));
        assertThat(OVERRIDING_JAX_RS_COMPENSATE_CALLED.get(), is(true));
        assertThat(PARTIAL_OVERRIDE_JAX_RS_COMPENSATE_CALLED.get(), is(true));
    }

    @Test
    void invokeValidNonJaxRsParticipantMethods(WebTarget target) throws Exception {
        DIRECT_COMPLETE_CALLED.set(false);
        DIRECT_COMPENSATE_CALLED.set(false);
        DIRECT_FORGET_CALLED.set(false);
        DIRECT_AFTER_LRA_CALLED.set(false);
        DIRECT_STATUS_CALLED.set(false);

        Response complete = participantCallback(target, "complete", DirectCallbackResource.class, "complete");
        Response compensate = participantCallback(target, "compensate", DirectCallbackResource.class, "compensate");
        Response forget = participantCallback(target, "forget", DirectCallbackResource.class, "forget");
        Response after = participantCallback(target, "afterlra", DirectCallbackResource.class,
                                             "afterLra", LRAStatus.Closing.name());
        Response status = participantCallback(target, "status", DirectCallbackResource.class, "status");

        assertThat(complete.getStatus(), is(Response.Status.OK.getStatusCode()));
        assertThat(compensate.getStatus(), is(Response.Status.OK.getStatusCode()));
        assertThat(forget.getStatus(), is(Response.Status.OK.getStatusCode()));
        assertThat(after.getStatus(), is(Response.Status.OK.getStatusCode()));
        assertThat(status.getStatus(), is(Response.Status.OK.getStatusCode()));
        assertThat(DIRECT_COMPLETE_CALLED.get(), is(true));
        assertThat(DIRECT_COMPENSATE_CALLED.get(), is(true));
        assertThat(DIRECT_FORGET_CALLED.get(), is(true));
        assertThat(DIRECT_AFTER_LRA_CALLED.get(), is(true));
        assertThat(DIRECT_STATUS_CALLED.get(), is(true));
    }

    @Test
    void rejectCallbackTypeMismatch(WebTarget target) throws Exception {
        COMPLETE_CALLED.set(false);
        COMPENSATE_CALLED.set(false);

        Response compensateToComplete = participantCallback(target, "compensate", TestResource.class, "complete");
        Response completeToCompensate = participantCallback(target, "complete", TestResource.class, "compensate");

        assertThat(compensateToComplete.getStatus(), is(Response.Status.NOT_FOUND.getStatusCode()));
        assertThat(completeToCompensate.getStatus(), is(Response.Status.NOT_FOUND.getStatusCode()));
        assertThat(COMPLETE_CALLED.get(), is(false));
        assertThat(COMPENSATE_CALLED.get(), is(false));
    }

    @Test
    void methodScan() throws NoSuchMethodException {
        ParticipantImpl p = new ParticipantImpl(
                URI.create("http://localhost:8888"),
                NonJaxRsResource.CONTEXT_PATH_DEFAULT,
                DontEnd.class);
        assertThat(p.isLraMethod(DontEnd.class.getMethod("startDontEndLRA", URI.class)), is(true));
        assertThat(p.isLraMethod(DontEnd.class.getMethod("endLRA", URI.class)), is(true));
    }

    @Test
    void inheritedJaxRsPathUsedForParticipantUri() {
        ParticipantImpl interfaceParticipant = new ParticipantImpl(
                URI.create("http://localhost:8888"),
                NonJaxRsResource.CONTEXT_PATH_DEFAULT,
                InheritedJaxRsParticipantResource.class);
        ParticipantImpl superclassParticipant = new ParticipantImpl(
                URI.create("http://localhost:8888"),
                NonJaxRsResource.CONTEXT_PATH_DEFAULT,
                SuperclassJaxRsParticipantResource.class);
        ParticipantImpl transitiveInterfaceParticipant = new ParticipantImpl(
                URI.create("http://localhost:8888"),
                NonJaxRsResource.CONTEXT_PATH_DEFAULT,
                TransitiveInterfaceJaxRsParticipantResource.class);
        ParticipantImpl genericInterfaceParticipant = new ParticipantImpl(
                URI.create("http://localhost:8888"),
                NonJaxRsResource.CONTEXT_PATH_DEFAULT,
                GenericInterfaceJaxRsParticipantResource.class);
        ParticipantImpl overridingParticipant = new ParticipantImpl(
                URI.create("http://localhost:8888"),
                NonJaxRsResource.CONTEXT_PATH_DEFAULT,
                OverridingJaxRsParticipantResource.class);
        ParticipantImpl superclassCallbackInterfaceParticipant = new ParticipantImpl(
                URI.create("http://localhost:8888"),
                NonJaxRsResource.CONTEXT_PATH_DEFAULT,
                SuperclassCallbackInterfaceJaxRsParticipantResource.class);
        ParticipantImpl partialOverrideParticipant = new ParticipantImpl(
                URI.create("http://localhost:8888"),
                NonJaxRsResource.CONTEXT_PATH_DEFAULT,
                PartialOverrideJaxRsParticipantResource.class);
        ParticipantImpl consumesOverrideParticipant = new ParticipantImpl(
                URI.create("http://localhost:8888"),
                NonJaxRsResource.CONTEXT_PATH_DEFAULT,
                ConsumesOverrideJaxRsParticipantResource.class);
        ParticipantImpl headerParamOverrideParticipant = new ParticipantImpl(
                URI.create("http://localhost:8888"),
                NonJaxRsResource.CONTEXT_PATH_DEFAULT,
                HeaderParamOverrideJaxRsParticipantResource.class);
        ParticipantImpl superclassConsumesInterfaceParticipant = new ParticipantImpl(
                URI.create("http://localhost:8888"),
                NonJaxRsResource.CONTEXT_PATH_DEFAULT,
                SuperclassConsumesInterfaceJaxRsParticipantResource.class);

        assertThat(interfaceParticipant.compensate().orElseThrow().getPath(),
                   is("/inherited-jax-rs-participant-test/inherited-compensate"));
        assertThat(transitiveInterfaceParticipant.compensate().orElseThrow().getPath(),
                   is("/transitive-interface-jax-rs-participant-test/transitive-interface-compensate"));
        assertThat(genericInterfaceParticipant.compensate().orElseThrow().getPath(),
                   is("/generic-interface-jax-rs-participant-test/generic-interface-compensate"));
        assertThat(superclassParticipant.compensate().orElseThrow().getPath(),
                   is("/superclass-jax-rs-participant-test/superclass-compensate"));
        assertThat(overridingParticipant.compensate().orElseThrow().getPath(),
                   is("/overriding-jax-rs-participant-test/overriding-compensate"));
        assertThat(superclassCallbackInterfaceParticipant.compensate().orElseThrow().getPath(),
                   is(NonJaxRsResource.CONTEXT_PATH_DEFAULT
                              + "/compensate/"
                              + SuperclassCallbackInterfaceJaxRsParticipantResource.class.getName()
                              + "/interfaceSuperclassCompensate"));
        assertThat(partialOverrideParticipant.compensate().orElseThrow().getPath(),
                   is("/partial-override-jax-rs-participant-test"));
        assertThat(consumesOverrideParticipant.compensate().orElseThrow().getPath(),
                   is(NonJaxRsResource.CONTEXT_PATH_DEFAULT
                              + "/compensate/"
                              + ConsumesOverrideJaxRsParticipantResource.class.getName()
                              + "/consumesOverrideCompensate"));
        assertThat(headerParamOverrideParticipant.compensate().orElseThrow().getPath(),
                   is(NonJaxRsResource.CONTEXT_PATH_DEFAULT
                              + "/compensate/"
                              + HeaderParamOverrideJaxRsParticipantResource.class.getName()
                              + "/headerParamOverrideCompensate"));
        assertThat(superclassConsumesInterfaceParticipant.compensate().orElseThrow().getPath(),
                   is(NonJaxRsResource.CONTEXT_PATH_DEFAULT
                              + "/compensate/"
                              + SuperclassConsumesInterfaceJaxRsParticipantResource.class.getName()
                              + "/superclassConsumesInterfaceCompensate"));
    }

    private Response participantCallback(WebTarget target,
                                         String type,
                                         Class<?> participantClass,
                                         String methodName) throws Exception {
        return participantCallback(target, type, participantClass, methodName, "");
    }

    private Response participantCallback(WebTarget target,
                                         String type,
                                         Class<?> participantClass,
                                         String methodName,
                                         String entity) throws Exception {
        return target.path(CUSTOM_CONTEXT)
                .path(type)
                .path(participantClass.getName())
                .path(methodName)
                .request()
                .header(LRA_HTTP_CONTEXT_HEADER, URI.create("http://localhost/lra-coordinator/test-lra"))
                .async()
                .put(Entity.text(entity))
                .get(TIMEOUT_SEC, TimeUnit.SECONDS);
    }

    private Response jaxRsCallback(WebTarget target, String resourcePath, String methodPath) throws Exception {
        WebTarget callbackTarget = target.path(resourcePath);
        if (!methodPath.isEmpty()) {
            callbackTarget = callbackTarget.path(methodPath);
        }
        return callbackTarget
                .request()
                .header(LRA_HTTP_CONTEXT_HEADER, URI.create("http://localhost/lra-coordinator/test-lra"))
                .async()
                .put(Entity.text(""))
                .get(TIMEOUT_SEC, TimeUnit.SECONDS);
    }

    @ApplicationScoped
    @Path("/participant-test")
    public static class TestResource {

        @PUT
        @LRA(value = LRA.Type.REQUIRES_NEW)
        @Path("/start")
        public void start(
                @HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId,
                @HeaderParam(Work.HEADER_KEY) Work work) {
            work.doWork(lraId);
        }

        @Complete
        public Response complete(URI lraId) {
            COMPLETE_CALLED.set(true);
            return Response.ok(ParticipantStatus.Completed.name()).build();
        }

        @Compensate
        public Response compensate(URI lraId) {
            COMPENSATE_CALLED.set(true);
            return Response.ok(ParticipantStatus.Compensated.name()).build();
        }

        public Response unannotatedParticipantMethod(URI lraId) {
            UNANNOTATED_METHOD_CALLED.set(true);
            return Response.ok().build();
        }
    }

    @ApplicationScoped
    public static class DirectCallbackResource {

        @Complete
        public Response complete(URI lraId) {
            DIRECT_COMPLETE_CALLED.set(true);
            return Response.ok(ParticipantStatus.Completed.name()).build();
        }

        @Compensate
        public Response compensate(URI lraId) {
            DIRECT_COMPENSATE_CALLED.set(true);
            return Response.ok(ParticipantStatus.Compensated.name()).build();
        }

        @Forget
        public Response forget(URI lraId) {
            DIRECT_FORGET_CALLED.set(true);
            return Response.ok().build();
        }

        @AfterLRA
        public Response afterLra(URI lraId, LRAStatus status) {
            DIRECT_AFTER_LRA_CALLED.set(true);
            return Response.ok(status.name()).build();
        }

        @org.eclipse.microprofile.lra.annotation.Status
        public ParticipantStatus status(URI lraId) {
            DIRECT_STATUS_CALLED.set(true);
            return ParticipantStatus.Active;
        }
    }

    @ApplicationScoped
    @Path("/jax-rs-participant-test")
    public static class JaxRsParticipantResource {

        @PUT
        @Path("/compensate")
        @Compensate
        public Response jaxRsCompensate(URI lraId) {
            JAX_RS_COMPENSATE_CALLED.set(true);
            return Response.ok(ParticipantStatus.Compensated.name()).build();
        }
    }

    @ApplicationScoped
    @Path("/custom-http-method-participant-test")
    public static class CustomHttpMethodParticipantResource {

        @CustomHttpMethod
        @Compensate
        public Response customHttpMethodCompensate(URI lraId) {
            CUSTOM_HTTP_METHOD_COMPENSATE_CALLED.set(true);
            return Response.ok(ParticipantStatus.Compensated.name()).build();
        }
    }

    interface InheritedJaxRsParticipant {

        @PUT
        @Path("/inherited-compensate")
        Response inheritedCompensate();
    }

    interface BaseInterfaceJaxRsParticipant {

        @PUT
        @Path("/transitive-interface-compensate")
        Response transitiveInterfaceCompensate();
    }

    interface TransitiveInterfaceJaxRsParticipant extends BaseInterfaceJaxRsParticipant {
    }

    @ApplicationScoped
    @Path("/inherited-jax-rs-participant-test")
    public static class InheritedJaxRsParticipantResource implements InheritedJaxRsParticipant {

        @Override
        @Compensate
        public Response inheritedCompensate() {
            INHERITED_JAX_RS_COMPENSATE_CALLED.set(true);
            return Response.ok(ParticipantStatus.Compensated.name()).build();
        }
    }

    @ApplicationScoped
    @Path("/transitive-interface-jax-rs-participant-test")
    public static class TransitiveInterfaceJaxRsParticipantResource implements TransitiveInterfaceJaxRsParticipant {

        @Override
        @Compensate
        public Response transitiveInterfaceCompensate() {
            TRANSITIVE_INTERFACE_JAX_RS_COMPENSATE_CALLED.set(true);
            return Response.ok(ParticipantStatus.Compensated.name()).build();
        }
    }

    interface GenericInterfaceJaxRsParticipant<T> {

        @PUT
        @Path("/generic-interface-compensate")
        Response genericInterfaceCompensate(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) T lraId);
    }

    @ApplicationScoped
    @Path("/generic-interface-jax-rs-participant-test")
    public static class GenericInterfaceJaxRsParticipantResource implements GenericInterfaceJaxRsParticipant<URI> {

        @Override
        @Compensate
        public Response genericInterfaceCompensate(URI lraId) {
            GENERIC_INTERFACE_JAX_RS_COMPENSATE_CALLED.set(true);
            return Response.ok(ParticipantStatus.Compensated.name()).build();
        }
    }

    public static class OverriddenParticipant {

        @Compensate
        public Response overridingCompensate() {
            return Response.ok().build();
        }
    }

    @ApplicationScoped
    @Path("/overriding-jax-rs-participant-test")
    public static class OverridingJaxRsParticipantResource extends OverriddenParticipant {

        @Override
        @PUT
        @Path("/overriding-compensate")
        public Response overridingCompensate() {
            OVERRIDING_JAX_RS_COMPENSATE_CALLED.set(true);
            return Response.ok(ParticipantStatus.Compensated.name()).build();
        }
    }

    public static class SuperclassJaxRsParticipant {

        @PUT
        @Path("/superclass-compensate")
        public Response superclassCompensate() {
            return Response.ok().build();
        }
    }

    @ApplicationScoped
    @Path("/superclass-jax-rs-participant-test")
    public static class SuperclassJaxRsParticipantResource extends SuperclassJaxRsParticipant {

        @Override
        @Compensate
        public Response superclassCompensate() {
            SUPERCLASS_JAX_RS_COMPENSATE_CALLED.set(true);
            return Response.ok(ParticipantStatus.Compensated.name()).build();
        }
    }

    interface InterfaceJaxRsParticipant {

        @PUT
        @Path("/interface-superclass-compensate")
        Response interfaceSuperclassCompensate();
    }

    public static class SuperclassCallbackParticipant {

        @Compensate
        public Response interfaceSuperclassCompensate() {
            SUPERCLASS_CALLBACK_INTERFACE_JAX_RS_COMPENSATE_CALLED.set(true);
            return Response.ok(ParticipantStatus.Compensated.name()).build();
        }
    }

    @ApplicationScoped
    @Path("/superclass-callback-interface-jax-rs-participant-test")
    public static class SuperclassCallbackInterfaceJaxRsParticipantResource
            extends SuperclassCallbackParticipant implements InterfaceJaxRsParticipant {
    }

    public static class PartialOverrideJaxRsParticipant {

        @PUT
        @Path("/base-compensate")
        public Response partialOverrideCompensate() {
            return Response.ok().build();
        }
    }

    @ApplicationScoped
    @Path("/partial-override-jax-rs-participant-test")
    public static class PartialOverrideJaxRsParticipantResource extends PartialOverrideJaxRsParticipant {

        @Override
        @PUT
        @Compensate
        public Response partialOverrideCompensate() {
            PARTIAL_OVERRIDE_JAX_RS_COMPENSATE_CALLED.set(true);
            return Response.ok(ParticipantStatus.Compensated.name()).build();
        }
    }

    public static class ConsumesOverrideJaxRsParticipant {

        @PUT
        @Path("/base-consumes")
        public Response consumesOverrideCompensate() {
            return Response.ok().build();
        }
    }

    @ApplicationScoped
    @Path("/consumes-override-jax-rs-participant-test")
    public static class ConsumesOverrideJaxRsParticipantResource extends ConsumesOverrideJaxRsParticipant {

        @Override
        @Consumes("text/plain")
        @Compensate
        public Response consumesOverrideCompensate() {
            CONSUMES_OVERRIDE_JAX_RS_COMPENSATE_CALLED.set(true);
            return Response.ok(ParticipantStatus.Compensated.name()).build();
        }
    }

    public static class HeaderParamOverrideJaxRsParticipant {

        @PUT
        @Path("/base-header-param")
        public Response headerParamOverrideCompensate(URI lraId) {
            return Response.ok().build();
        }
    }

    @ApplicationScoped
    @Path("/header-param-override-jax-rs-participant-test")
    public static class HeaderParamOverrideJaxRsParticipantResource extends HeaderParamOverrideJaxRsParticipant {

        @Override
        @Compensate
        public Response headerParamOverrideCompensate(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId) {
            HEADER_PARAM_OVERRIDE_JAX_RS_COMPENSATE_CALLED.set(true);
            return Response.ok(ParticipantStatus.Compensated.name()).build();
        }
    }

    interface SuperclassConsumesInterfaceJaxRsParticipant {

        @PUT
        @Path("/superclass-consumes-interface-compensate")
        Response superclassConsumesInterfaceCompensate();
    }

    public static class SuperclassConsumesParticipant {

        @Consumes("text/plain")
        public Response superclassConsumesInterfaceCompensate() {
            return Response.ok().build();
        }
    }

    @ApplicationScoped
    @Path("/superclass-consumes-interface-jax-rs-participant-test")
    public static class SuperclassConsumesInterfaceJaxRsParticipantResource
            extends SuperclassConsumesParticipant implements SuperclassConsumesInterfaceJaxRsParticipant {

        @Override
        @Compensate
        public Response superclassConsumesInterfaceCompensate() {
            SUPERCLASS_CONSUMES_INTERFACE_JAX_RS_COMPENSATE_CALLED.set(true);
            return Response.ok(ParticipantStatus.Compensated.name()).build();
        }
    }

    public static class UnregisteredParticipant {

        static {
            UNREGISTERED_PARTICIPANT_INITIALIZED.set(true);
        }

        @Compensate
        public Response compensate(URI lraId) {
            return Response.ok(ParticipantStatus.Compensated.name()).build();
        }
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @HttpMethod("CUSTOM")
    public @interface CustomHttpMethod {
    }
}
