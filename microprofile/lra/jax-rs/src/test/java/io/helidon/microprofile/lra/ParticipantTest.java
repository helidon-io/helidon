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
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.reactive.Multi;
import io.helidon.lra.coordinator.client.CoordinatorClient;
import io.helidon.microprofile.config.ConfigCdiExtension;
import io.helidon.microprofile.lra.resources.DontEnd;
import io.helidon.microprofile.lra.resources.Work;
import io.helidon.microprofile.server.JaxRsCdiExtension;
import io.helidon.microprofile.server.RoutingPath;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.AddConfig;
import io.helidon.microprofile.tests.junit5.AddExtension;
import io.helidon.microprofile.tests.junit5.DisableDiscovery;
import io.helidon.microprofile.tests.junit5.HelidonTest;
import io.helidon.webserver.Service;

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
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.lra.annotation.Compensate;
import org.eclipse.microprofile.lra.annotation.Complete;
import org.eclipse.microprofile.lra.annotation.LRAStatus;
import org.eclipse.microprofile.lra.annotation.ParticipantStatus;
import org.eclipse.microprofile.lra.annotation.Status;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;
import org.glassfish.jersey.ext.cdi1x.internal.CdiComponentProvider;
import org.hamcrest.core.AnyOf;
import org.junit.jupiter.api.Test;

import static jakarta.interceptor.Interceptor.Priority.PLATFORM_AFTER;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_PARENT_CONTEXT_HEADER;
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
@AddBean(ParticipantTest.JaxRsParticipantResource.class)
@AddBean(ParticipantTest.InheritedJaxRsParticipantResource.class)
@AddBean(ParticipantTest.TransitiveInterfaceJaxRsParticipantResource.class)
@AddBean(ParticipantTest.GenericInterfaceJaxRsParticipantResource.class)
@AddBean(ParticipantTest.SuperclassJaxRsParticipantResource.class)
@AddBean(ParticipantTest.CustomHttpMethodParticipantResource.class)
@AddBean(ParticipantTest.ConsumesOverrideJaxRsParticipantResource.class)
@AddBean(ParticipantTest.HeaderParamOverrideJaxRsParticipantResource.class)
@AddBean(ParticipantTest.InheritedNonJaxRsParticipantResource.class)
@AddBean(ParticipantTest.GenericNonJaxRsParticipantResource.class)
@AddBean(ParticipantTest.DefaultNonJaxRsParticipantResource.class)
@AddBean(ParticipantTest.OverrideDefaultNonJaxRsParticipantResource.class)
@AddBean(ParticipantTest.EmptyCompletionParticipantResource.class)
@AddBean(ParticipantDispatchResource.class)
@AddBean(ParticipantTest.NestedStatusParticipantResource.class)
// Override context
@AddConfig(key = NonJaxRsResource.CONFIG_CONTEXT_PATH_KEY, value = ParticipantTest.CUSTOM_CONTEXT)
class ParticipantTest {

    private static final long TIMEOUT_SEC = 10L;
    static final String CUSTOM_CONTEXT = "custom-lra-context";
    private static final AtomicBoolean JAX_RS_COMPENSATE_CALLED = new AtomicBoolean();
    private static final AtomicBoolean INHERITED_JAX_RS_COMPENSATE_CALLED = new AtomicBoolean();
    private static final AtomicBoolean TRANSITIVE_INTERFACE_JAX_RS_COMPENSATE_CALLED = new AtomicBoolean();
    private static final AtomicBoolean GENERIC_INTERFACE_JAX_RS_COMPENSATE_CALLED = new AtomicBoolean();
    private static final AtomicBoolean SUPERCLASS_JAX_RS_COMPENSATE_CALLED = new AtomicBoolean();
    private static final AtomicBoolean CUSTOM_HTTP_METHOD_COMPENSATE_CALLED = new AtomicBoolean();
    private static final AtomicBoolean CONSUMES_OVERRIDE_JAX_RS_COMPENSATE_CALLED = new AtomicBoolean();
    private static final AtomicBoolean HEADER_PARAM_OVERRIDE_JAX_RS_COMPENSATE_CALLED = new AtomicBoolean();
    private static final AtomicBoolean INHERITED_NON_JAX_RS_COMPENSATE_CALLED = new AtomicBoolean();
    private static final AtomicBoolean GENERIC_NON_JAX_RS_COMPENSATE_CALLED = new AtomicBoolean();
    private static final AtomicBoolean DEFAULT_NON_JAX_RS_COMPENSATE_CALLED = new AtomicBoolean();
    private static final AtomicBoolean OVERRIDE_DEFAULT_NON_JAX_RS_COMPENSATE_CALLED = new AtomicBoolean();
    private static final AtomicBoolean EMPTY_FORGET_CALLED = new AtomicBoolean();
    private static final AtomicBoolean EMPTY_AFTER_LRA_CALLED = new AtomicBoolean();
    private static final AtomicBoolean UNREGISTERED_PARTICIPANT_INITIALIZED = new AtomicBoolean();

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
    Service mockCoordinator() {
        return rules -> rules
                .post("/start", (req, res) -> {
                    String lraId = URI.create("http://localhost:" + port + "/lra-coordinator/xxx-xxx-001").toASCIIString();
                    res.status(201)
                            .addHeader(LRA_HTTP_CONTEXT_HEADER, lraId)
                            .send();
                })
                .put("/{lraId}/close", (req, res) -> {
                    res.send();
                })
                .put("/{lraId}", (req, res) -> {
                    req.content()
                            .as(String.class)
                            .flatMap(s -> Multi.create(Arrays.stream(s.split(","))))
                            .map(s -> s.split(";")[0])
                            .map(s -> s
                                    .replaceAll("^<", "")
                                    .replaceAll(">$", "")
                            )
                            .map(URI::create)
                            .map(URI::getPath)
                            .onComplete(res::send)
                            .onComplete(() -> completed.complete(null))
                            .forEach(paths::add)
                            .exceptionally(res::send);
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
        paths.clear();
        completed = new CompletableFuture<>();

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
    void inheritedJaxRsParticipantLinkRegisteredWithCoordinator(WebTarget target) throws Exception {
        paths.clear();
        completed = new CompletableFuture<>();

        Response response = target.path("inherited-jax-rs-participant-test")
                .path("start")
                .request()
                .header(Work.HEADER_KEY, Work.NOOP)
                .async()
                .put(Entity.text(""))
                .get(TIMEOUT_SEC, TimeUnit.SECONDS);

        assertThat(response.getStatus(), AnyOf.anyOf(is(200), is(204)));

        completed.get(TIMEOUT_SEC, TimeUnit.SECONDS);

        assertThat(paths, hasItems("/inherited-jax-rs-participant-test/inherited-compensate"));
        assertThat(paths.stream().noneMatch(path -> path.startsWith("/" + CUSTOM_CONTEXT)), is(true));
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
        ParticipantDispatchResource.UNANNOTATED_METHOD_CALLED.set(false);

        Response response = participantCallback(target, "compensate",
                                                ParticipantDispatchResource.class,
                                                "unannotatedParticipantMethod");

        assertThat(response.getStatus(), is(Response.Status.NOT_FOUND.getStatusCode()));
        assertThat(ParticipantDispatchResource.UNANNOTATED_METHOD_CALLED.get(), is(false));
    }

    @Test
    void rejectCallbackTypeMismatch(WebTarget target) throws Exception {
        ParticipantDispatchResource.COMPLETE_CALLED.set(false);
        ParticipantDispatchResource.COMPENSATE_CALLED.set(false);
        ParticipantDispatchResource.FORGET_CALLED.set(false);
        ParticipantDispatchResource.AFTER_LRA_CALLED.set(false);
        ParticipantDispatchResource.STATUS_CALLED.set(false);

        Response compensateToComplete = participantCallback(target, "compensate",
                                                            ParticipantDispatchResource.class,
                                                            "complete");
        Response completeToCompensate = participantCallback(target, "complete",
                                                            ParticipantDispatchResource.class,
                                                            "compensate");
        Response afterLraToStatus = participantCallback(target, "afterlra",
                                                        ParticipantDispatchResource.class,
                                                        "status",
                                                        LRAStatus.Closing.name());
        Response statusToForget = participantCallback(target, "status",
                                                      ParticipantDispatchResource.class,
                                                      "forget");
        Response forgetToAfterLra = participantCallback(target, "forget",
                                                       ParticipantDispatchResource.class,
                                                       "afterLra");

        assertThat(compensateToComplete.getStatus(), is(Response.Status.NOT_FOUND.getStatusCode()));
        assertThat(completeToCompensate.getStatus(), is(Response.Status.NOT_FOUND.getStatusCode()));
        assertThat(afterLraToStatus.getStatus(), is(Response.Status.NOT_FOUND.getStatusCode()));
        assertThat(statusToForget.getStatus(), is(Response.Status.NOT_FOUND.getStatusCode()));
        assertThat(forgetToAfterLra.getStatus(), is(Response.Status.NOT_FOUND.getStatusCode()));
        assertThat(ParticipantDispatchResource.COMPLETE_CALLED.get(), is(false));
        assertThat(ParticipantDispatchResource.COMPENSATE_CALLED.get(), is(false));
        assertThat(ParticipantDispatchResource.FORGET_CALLED.get(), is(false));
        assertThat(ParticipantDispatchResource.AFTER_LRA_CALLED.get(), is(false));
        assertThat(ParticipantDispatchResource.STATUS_CALLED.get(), is(false));
    }

    @Test
    void rejectJaxRsParticipantMethodsFromAuxiliaryRoute(WebTarget target) throws Exception {
        JAX_RS_COMPENSATE_CALLED.set(false);
        INHERITED_JAX_RS_COMPENSATE_CALLED.set(false);
        TRANSITIVE_INTERFACE_JAX_RS_COMPENSATE_CALLED.set(false);
        GENERIC_INTERFACE_JAX_RS_COMPENSATE_CALLED.set(false);
        SUPERCLASS_JAX_RS_COMPENSATE_CALLED.set(false);
        CUSTOM_HTTP_METHOD_COMPENSATE_CALLED.set(false);

        Response direct = participantCallback(target, "compensate",
                                              JaxRsParticipantResource.class,
                                              "jaxRsCompensate");
        Response inherited = participantCallback(target, "compensate",
                                                 InheritedJaxRsParticipantResource.class,
                                                 "inheritedCompensate");
        Response transitiveInterface = participantCallback(target, "compensate",
                                                           TransitiveInterfaceJaxRsParticipantResource.class,
                                                           "transitiveInterfaceCompensate");
        Response genericInterface = participantCallback(target, "compensate",
                                                        GenericInterfaceJaxRsParticipantResource.class,
                                                        "genericInterfaceCompensate");
        Response superclass = participantCallback(target, "compensate",
                                                  SuperclassJaxRsParticipantResource.class,
                                                  "superclassCompensate");
        Response customHttpMethod = participantCallback(target, "compensate",
                                                        CustomHttpMethodParticipantResource.class,
                                                        "customHttpMethodCompensate");

        assertThat(direct.getStatus(), is(Response.Status.NOT_FOUND.getStatusCode()));
        assertThat(inherited.getStatus(), is(Response.Status.NOT_FOUND.getStatusCode()));
        assertThat(transitiveInterface.getStatus(), is(Response.Status.NOT_FOUND.getStatusCode()));
        assertThat(genericInterface.getStatus(), is(Response.Status.NOT_FOUND.getStatusCode()));
        assertThat(superclass.getStatus(), is(Response.Status.NOT_FOUND.getStatusCode()));
        assertThat(customHttpMethod.getStatus(), is(Response.Status.NOT_FOUND.getStatusCode()));
        assertThat(JAX_RS_COMPENSATE_CALLED.get(), is(false));
        assertThat(INHERITED_JAX_RS_COMPENSATE_CALLED.get(), is(false));
        assertThat(TRANSITIVE_INTERFACE_JAX_RS_COMPENSATE_CALLED.get(), is(false));
        assertThat(GENERIC_INTERFACE_JAX_RS_COMPENSATE_CALLED.get(), is(false));
        assertThat(SUPERCLASS_JAX_RS_COMPENSATE_CALLED.get(), is(false));
        assertThat(CUSTOM_HTTP_METHOD_COMPENSATE_CALLED.get(), is(false));
    }

    @Test
    void invokeJaxRsParticipantMethods(WebTarget target) throws Exception {
        JAX_RS_COMPENSATE_CALLED.set(false);
        INHERITED_JAX_RS_COMPENSATE_CALLED.set(false);
        TRANSITIVE_INTERFACE_JAX_RS_COMPENSATE_CALLED.set(false);
        GENERIC_INTERFACE_JAX_RS_COMPENSATE_CALLED.set(false);
        SUPERCLASS_JAX_RS_COMPENSATE_CALLED.set(false);
        CUSTOM_HTTP_METHOD_COMPENSATE_CALLED.set(false);

        Response direct = jaxRsCallback(target, "jax-rs-participant-test", "compensate");
        Response inherited = jaxRsCallback(target, "inherited-jax-rs-participant-test",
                                           "inherited-compensate");
        Response transitiveInterface = jaxRsCallback(target, "transitive-interface-jax-rs-participant-test",
                                                     "transitive-interface-compensate");
        Response genericInterface = jaxRsCallback(target, "generic-interface-jax-rs-participant-test",
                                                  "generic-interface-compensate");
        Response superclass = jaxRsCallback(target, "superclass-jax-rs-participant-test",
                                            "superclass-compensate");
        Response customHttpMethod = target.path("custom-http-method-participant-test")
                .request()
                .header(LRA_HTTP_CONTEXT_HEADER, URI.create("http://localhost/lra-coordinator/test-lra"))
                .async()
                .put(Entity.text(""))
                .get(TIMEOUT_SEC, TimeUnit.SECONDS);

        assertThat(direct.getStatus(), is(Response.Status.OK.getStatusCode()));
        assertThat(inherited.getStatus(), is(Response.Status.OK.getStatusCode()));
        assertThat(transitiveInterface.getStatus(), is(Response.Status.OK.getStatusCode()));
        assertThat(genericInterface.getStatus(), is(Response.Status.OK.getStatusCode()));
        assertThat(superclass.getStatus(), is(Response.Status.OK.getStatusCode()));
        assertThat(customHttpMethod.getStatus(), is(Response.Status.OK.getStatusCode()));
        assertThat(JAX_RS_COMPENSATE_CALLED.get(), is(true));
        assertThat(INHERITED_JAX_RS_COMPENSATE_CALLED.get(), is(true));
        assertThat(TRANSITIVE_INTERFACE_JAX_RS_COMPENSATE_CALLED.get(), is(true));
        assertThat(GENERIC_INTERFACE_JAX_RS_COMPENSATE_CALLED.get(), is(true));
        assertThat(SUPERCLASS_JAX_RS_COMPENSATE_CALLED.get(), is(true));
        assertThat(CUSTOM_HTTP_METHOD_COMPENSATE_CALLED.get(), is(true));
    }

    @Test
    void invokeNonJaxRsParticipantMethodsWithNonCallbackJaxRsAnnotations(WebTarget target) throws Exception {
        CONSUMES_OVERRIDE_JAX_RS_COMPENSATE_CALLED.set(false);
        HEADER_PARAM_OVERRIDE_JAX_RS_COMPENSATE_CALLED.set(false);

        Response consumes = participantCallback(target, "compensate",
                                                ConsumesOverrideJaxRsParticipantResource.class,
                                                "consumesOverrideCompensate");
        Response headerParam = participantCallback(target, "compensate",
                                                   HeaderParamOverrideJaxRsParticipantResource.class,
                                                   "headerParamOverrideCompensate");

        assertThat(consumes.getStatus(), is(Response.Status.OK.getStatusCode()));
        assertThat(headerParam.getStatus(), is(Response.Status.OK.getStatusCode()));
        assertThat(CONSUMES_OVERRIDE_JAX_RS_COMPENSATE_CALLED.get(), is(true));
        assertThat(HEADER_PARAM_OVERRIDE_JAX_RS_COMPENSATE_CALLED.get(), is(true));
    }

    @Test
    void invokeInheritedNonJaxRsParticipantMethod(WebTarget target) throws Exception {
        INHERITED_NON_JAX_RS_COMPENSATE_CALLED.set(false);
        GENERIC_NON_JAX_RS_COMPENSATE_CALLED.set(false);
        DEFAULT_NON_JAX_RS_COMPENSATE_CALLED.set(false);
        OVERRIDE_DEFAULT_NON_JAX_RS_COMPENSATE_CALLED.set(false);

        Response inherited = participantCallback(target, "compensate",
                                                 InheritedNonJaxRsParticipantResource.class,
                                                 "inheritedNonJaxRsCompensate");
        Response generic = participantCallback(target, "compensate",
                                               GenericNonJaxRsParticipantResource.class,
                                               "genericNonJaxRsCompensate");
        Response defaultMethod = participantCallback(target, "compensate",
                                                     DefaultNonJaxRsParticipantResource.class,
                                                     "defaultNonJaxRsCompensate");
        Response overrideDefaultMethod = participantCallback(target, "compensate",
                                                             OverrideDefaultNonJaxRsParticipantResource.class,
                                                             "overrideDefaultNonJaxRs");

        assertThat(inherited.getStatus(), is(Response.Status.OK.getStatusCode()));
        assertThat(generic.getStatus(), is(Response.Status.OK.getStatusCode()));
        assertThat(defaultMethod.getStatus(), is(Response.Status.OK.getStatusCode()));
        assertThat(overrideDefaultMethod.getStatus(), is(Response.Status.OK.getStatusCode()));
        assertThat(INHERITED_NON_JAX_RS_COMPENSATE_CALLED.get(), is(true));
        assertThat(GENERIC_NON_JAX_RS_COMPENSATE_CALLED.get(), is(true));
        assertThat(DEFAULT_NON_JAX_RS_COMPENSATE_CALLED.get(), is(true));
        assertThat(OVERRIDE_DEFAULT_NON_JAX_RS_COMPENSATE_CALLED.get(), is(true));
    }

    @Test
    void invokeEmptyCompletionNonJaxRsParticipantMethods(WebTarget target) throws Exception {
        EMPTY_FORGET_CALLED.set(false);
        EMPTY_AFTER_LRA_CALLED.set(false);

        Response forget = participantCallback(target, "forget",
                                              EmptyCompletionParticipantResource.class,
                                              "forget");
        Response after = participantCallback(target, "afterlra",
                                             EmptyCompletionParticipantResource.class,
                                             "afterLra",
                                             LRAStatus.Closing.name());

        assertThat(forget.getStatus(), AnyOf.anyOf(is(200), is(204)));
        assertThat(after.getStatus(), AnyOf.anyOf(is(200), is(204)));
        assertThat(EMPTY_FORGET_CALLED.get(), is(true));
        assertThat(EMPTY_AFTER_LRA_CALLED.get(), is(true));
    }

    @Test
    void invokeValidNonJaxRsParticipantMethods(WebTarget target) throws Exception {
        ParticipantDispatchResource.COMPLETE_CALLED.set(false);
        ParticipantDispatchResource.COMPENSATE_CALLED.set(false);
        ParticipantDispatchResource.FORGET_CALLED.set(false);
        ParticipantDispatchResource.AFTER_LRA_CALLED.set(false);
        ParticipantDispatchResource.STATUS_CALLED.set(false);

        Response complete = participantCallback(target, "complete", ParticipantDispatchResource.class, "complete");
        Response compensate = participantCallback(target, "compensate", ParticipantDispatchResource.class, "compensate");
        Response forget = participantCallback(target, "forget", ParticipantDispatchResource.class, "forget");
        Response after = participantCallback(target, "afterlra", ParticipantDispatchResource.class,
                                             "afterLra", LRAStatus.Closing.name());
        Response status = participantCallback(target, "status", ParticipantDispatchResource.class, "status");

        assertThat(complete.getStatus(), is(Response.Status.OK.getStatusCode()));
        assertThat(compensate.getStatus(), is(Response.Status.OK.getStatusCode()));
        assertThat(forget.getStatus(), is(Response.Status.OK.getStatusCode()));
        assertThat(after.getStatus(), is(Response.Status.OK.getStatusCode()));
        assertThat(status.getStatus(), is(Response.Status.OK.getStatusCode()));
        assertThat(ParticipantDispatchResource.COMPLETE_CALLED.get(), is(true));
        assertThat(ParticipantDispatchResource.COMPENSATE_CALLED.get(), is(true));
        assertThat(ParticipantDispatchResource.FORGET_CALLED.get(), is(true));
        assertThat(ParticipantDispatchResource.AFTER_LRA_CALLED.get(), is(true));
        assertThat(ParticipantDispatchResource.STATUS_CALLED.get(), is(true));
    }

    @Test
    void invokeNestedStatusNonJaxRsParticipantMethod(WebTarget target) throws Exception {
        URI parentId = URI.create("http://localhost/lra-coordinator/test-parent-lra");

        NestedStatusParticipantResource.STATUS_CALLED.set(false);
        NestedStatusParticipantResource.STATUS_PARENT_ID.set(null);

        Response status = participantCallback(target,
                                              "status",
                                              NestedStatusParticipantResource.class,
                                              "status",
                                              "",
                                              parentId);

        assertThat(status.getStatus(), is(Response.Status.OK.getStatusCode()));
        assertThat(NestedStatusParticipantResource.STATUS_CALLED.get(), is(true));
        assertThat(NestedStatusParticipantResource.STATUS_PARENT_ID.get(), is(parentId));
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
        ParticipantImpl directParticipant = new ParticipantImpl(
                URI.create("http://localhost:8888"),
                NonJaxRsResource.CONTEXT_PATH_DEFAULT,
                JaxRsParticipantResource.class);
        ParticipantImpl participant = new ParticipantImpl(
                URI.create("http://localhost:8888"),
                NonJaxRsResource.CONTEXT_PATH_DEFAULT,
                InheritedJaxRsParticipantResource.class);
        ParticipantImpl transitiveInterfaceParticipant = new ParticipantImpl(
                URI.create("http://localhost:8888"),
                NonJaxRsResource.CONTEXT_PATH_DEFAULT,
                TransitiveInterfaceJaxRsParticipantResource.class);
        ParticipantImpl genericInterfaceParticipant = new ParticipantImpl(
                URI.create("http://localhost:8888"),
                NonJaxRsResource.CONTEXT_PATH_DEFAULT,
                GenericInterfaceJaxRsParticipantResource.class);
        ParticipantImpl superclassParticipant = new ParticipantImpl(
                URI.create("http://localhost:8888"),
                NonJaxRsResource.CONTEXT_PATH_DEFAULT,
                SuperclassJaxRsParticipantResource.class);
        ParticipantImpl customHttpMethodParticipant = new ParticipantImpl(
                URI.create("http://localhost:8888"),
                NonJaxRsResource.CONTEXT_PATH_DEFAULT,
                CustomHttpMethodParticipantResource.class);
        ParticipantImpl consumesOverrideParticipant = new ParticipantImpl(
                URI.create("http://localhost:8888"),
                NonJaxRsResource.CONTEXT_PATH_DEFAULT,
                ConsumesOverrideJaxRsParticipantResource.class);
        ParticipantImpl headerParamOverrideParticipant = new ParticipantImpl(
                URI.create("http://localhost:8888"),
                NonJaxRsResource.CONTEXT_PATH_DEFAULT,
                HeaderParamOverrideJaxRsParticipantResource.class);
        ParticipantImpl inheritedNonJaxRsParticipant = new ParticipantImpl(
                URI.create("http://localhost:8888"),
                NonJaxRsResource.CONTEXT_PATH_DEFAULT,
                InheritedNonJaxRsParticipantResource.class);
        ParticipantImpl genericNonJaxRsParticipant = new ParticipantImpl(
                URI.create("http://localhost:8888"),
                NonJaxRsResource.CONTEXT_PATH_DEFAULT,
                GenericNonJaxRsParticipantResource.class);
        ParticipantImpl defaultNonJaxRsParticipant = new ParticipantImpl(
                URI.create("http://localhost:8888"),
                NonJaxRsResource.CONTEXT_PATH_DEFAULT,
                DefaultNonJaxRsParticipantResource.class);
        ParticipantImpl overrideDefaultNonJaxRsParticipant = new ParticipantImpl(
                URI.create("http://localhost:8888"),
                NonJaxRsResource.CONTEXT_PATH_DEFAULT,
                OverrideDefaultNonJaxRsParticipantResource.class);

        assertThat(directParticipant.compensate().orElseThrow().getPath(),
                   is("/jax-rs-participant-test/compensate"));
        assertThat(participant.compensate().orElseThrow().getPath(),
                   is("/inherited-jax-rs-participant-test/inherited-compensate"));
        assertThat(transitiveInterfaceParticipant.compensate().orElseThrow().getPath(),
                   is("/transitive-interface-jax-rs-participant-test/transitive-interface-compensate"));
        assertThat(genericInterfaceParticipant.compensate().orElseThrow().getPath(),
                   is("/generic-interface-jax-rs-participant-test/generic-interface-compensate"));
        assertThat(superclassParticipant.compensate().orElseThrow().getPath(),
                   is("/superclass-jax-rs-participant-test/superclass-compensate"));
        assertThat(customHttpMethodParticipant.compensate().orElseThrow().getPath(),
                   is("/custom-http-method-participant-test"));
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
        assertThat(inheritedNonJaxRsParticipant.compensate().orElseThrow().getPath(),
                   is(NonJaxRsResource.CONTEXT_PATH_DEFAULT
                              + "/compensate/"
                              + InheritedNonJaxRsParticipantResource.class.getName()
                              + "/inheritedNonJaxRsCompensate"));
        assertThat(genericNonJaxRsParticipant.compensate().orElseThrow().getPath(),
                   is(NonJaxRsResource.CONTEXT_PATH_DEFAULT
                              + "/compensate/"
                              + GenericNonJaxRsParticipantResource.class.getName()
                              + "/genericNonJaxRsCompensate"));
        assertThat(defaultNonJaxRsParticipant.compensate().orElseThrow().getPath(),
                   is(NonJaxRsResource.CONTEXT_PATH_DEFAULT
                              + "/compensate/"
                              + DefaultNonJaxRsParticipantResource.class.getName()
                              + "/defaultNonJaxRsCompensate"));
        assertThat(overrideDefaultNonJaxRsParticipant.compensate().orElseThrow().getPath(),
                   is(NonJaxRsResource.CONTEXT_PATH_DEFAULT
                              + "/compensate/"
                              + OverrideDefaultNonJaxRsParticipantResource.class.getName()
                              + "/overrideDefaultNonJaxRs"));
        assertThat(overrideDefaultNonJaxRsParticipant.complete().isEmpty(), is(true));
    }

    @Test
    void inheritedLraMethodAnnotationsAreScanned() {
        Map<Class<? extends java.lang.annotation.Annotation>, Set<Method>> interfaceMethods =
                ParticipantImpl.scanForLRAMethods(InterfaceLraParticipantResource.class);
        Map<Class<? extends java.lang.annotation.Annotation>, Set<Method>> superclassMethods =
                ParticipantImpl.scanForLRAMethods(SuperclassLraParticipantResource.class);

        assertThat(interfaceMethods.get(Complete.class).stream()
                           .anyMatch(method -> method.getName().equals("interfaceComplete")),
                   is(true));
        assertThat(superclassMethods.get(Compensate.class).stream()
                           .anyMatch(method -> method.getName().equals("superclassCompensate")),
                   is(true));
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
        return participantCallback(target, type, participantClass, methodName, entity, null);
    }

    private Response participantCallback(WebTarget target,
                                         String type,
                                         Class<?> participantClass,
                                         String methodName,
                                         String entity,
                                         URI parentId) throws Exception {
        Invocation.Builder request = target.path(CUSTOM_CONTEXT)
                .path(type)
                .path(participantClass.getName())
                .path(methodName)
                .request()
                .header(LRA_HTTP_CONTEXT_HEADER, URI.create("http://localhost/lra-coordinator/test-lra"));
        if (parentId != null) {
            request.header(LRA_HTTP_PARENT_CONTEXT_HEADER, parentId);
        }
        return request.async()
                .put(Entity.text(entity))
                .get(TIMEOUT_SEC, TimeUnit.SECONDS);
    }

    private Response jaxRsCallback(WebTarget target, String resourcePath, String methodPath) throws Exception {
        return target.path(resourcePath)
                .path(methodPath)
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
            return Response.ok(ParticipantStatus.Completed.name()).build();
        }

        @Compensate
        public Response compensate(URI lraId) {
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

    interface InterfaceLraParticipant {

        @Complete
        Response interfaceComplete(URI lraId);
    }

    static class InterfaceLraParticipantResource implements InterfaceLraParticipant {

        @Override
        public Response interfaceComplete(URI lraId) {
            return Response.ok().build();
        }
    }

    static class SuperclassLraParticipant {

        @Compensate
        public Response superclassCompensate(URI lraId) {
            return Response.ok().build();
        }
    }

    static class SuperclassLraParticipantResource extends SuperclassLraParticipant {

        @Override
        public Response superclassCompensate(URI lraId) {
            return Response.ok().build();
        }
    }

    interface InheritedNonJaxRsParticipant {

        @Compensate
        Response inheritedNonJaxRsCompensate(URI lraId);
    }

    @ApplicationScoped
    public static class InheritedNonJaxRsParticipantResource implements InheritedNonJaxRsParticipant {

        @LRA
        public void start() {
        }

        @Override
        public Response inheritedNonJaxRsCompensate(URI lraId) {
            INHERITED_NON_JAX_RS_COMPENSATE_CALLED.set(true);
            return Response.ok(ParticipantStatus.Compensated.name()).build();
        }
    }

    interface GenericNonJaxRsParticipant<T> {

        @Compensate
        Response genericNonJaxRsCompensate(T lraId);
    }

    @ApplicationScoped
    public static class GenericNonJaxRsParticipantResource implements GenericNonJaxRsParticipant<URI> {

        @Override
        public Response genericNonJaxRsCompensate(URI lraId) {
            GENERIC_NON_JAX_RS_COMPENSATE_CALLED.set(true);
            return Response.ok(ParticipantStatus.Compensated.name()).build();
        }
    }

    interface DefaultNonJaxRsParticipant {

        @Compensate
        default Response defaultNonJaxRsCompensate(URI lraId) {
            DEFAULT_NON_JAX_RS_COMPENSATE_CALLED.set(true);
            return Response.ok(ParticipantStatus.Compensated.name()).build();
        }
    }

    @ApplicationScoped
    public static class DefaultNonJaxRsParticipantResource implements DefaultNonJaxRsParticipant {
    }

    interface BaseDefaultNonJaxRsParticipant {

        @Complete
        default Response overrideDefaultNonJaxRs(URI lraId) {
            return Response.ok(ParticipantStatus.Completed.name()).build();
        }
    }

    interface OverrideDefaultNonJaxRsParticipant extends BaseDefaultNonJaxRsParticipant {

        @Override
        @Compensate
        default Response overrideDefaultNonJaxRs(URI lraId) {
            OVERRIDE_DEFAULT_NON_JAX_RS_COMPENSATE_CALLED.set(true);
            return Response.ok(ParticipantStatus.Compensated.name()).build();
        }
    }

    @ApplicationScoped
    public static class OverrideDefaultNonJaxRsParticipantResource implements OverrideDefaultNonJaxRsParticipant {
    }

    @ApplicationScoped
    @Path("/jax-rs-participant-test")
    public static class JaxRsParticipantResource {

        @PUT
        @Path("/compensate")
        @Compensate
        public Response jaxRsCompensate(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId) {
            JAX_RS_COMPENSATE_CALLED.set(true);
            return Response.ok(ParticipantStatus.Compensated.name()).build();
        }
    }

    @ApplicationScoped
    @Path("/custom-http-method-participant-test")
    public static class CustomHttpMethodParticipantResource {

        @CustomHttpMethod
        @Consumes("text/plain")
        @Compensate
        public Response customHttpMethodCompensate(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId) {
            CUSTOM_HTTP_METHOD_COMPENSATE_CALLED.set(true);
            return Response.ok(ParticipantStatus.Compensated.name()).build();
        }
    }

    interface InheritedJaxRsParticipant {

        @PUT
        @Path("/inherited-compensate")
        Response inheritedCompensate();
    }

    @ApplicationScoped
    @Path("/inherited-jax-rs-participant-test")
    public static class InheritedJaxRsParticipantResource implements InheritedJaxRsParticipant {

        @PUT
        @LRA(value = LRA.Type.REQUIRES_NEW)
        @Path("/start")
        public void start(
                @HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId,
                @HeaderParam(Work.HEADER_KEY) Work work) {
            work.doWork(lraId);
        }

        @Override
        @Compensate
        public Response inheritedCompensate() {
            INHERITED_JAX_RS_COMPENSATE_CALLED.set(true);
            return Response.ok(ParticipantStatus.Compensated.name()).build();
        }
    }

    interface BaseInterfaceJaxRsParticipant {

        @PUT
        @Path("/transitive-interface-compensate")
        @Compensate
        Response transitiveInterfaceCompensate(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId);
    }

    interface TransitiveInterfaceJaxRsParticipant extends BaseInterfaceJaxRsParticipant {
    }

    @ApplicationScoped
    @Path("/transitive-interface-jax-rs-participant-test")
    public static class TransitiveInterfaceJaxRsParticipantResource implements TransitiveInterfaceJaxRsParticipant {

        @Override
        public Response transitiveInterfaceCompensate(URI lraId) {
            if (lraId == null) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
            TRANSITIVE_INTERFACE_JAX_RS_COMPENSATE_CALLED.set(true);
            return Response.ok(ParticipantStatus.Compensated.name()).build();
        }
    }

    interface GenericInterfaceJaxRsParticipant<T> {

        @PUT
        @Path("/generic-interface-compensate")
        @Compensate
        Response genericInterfaceCompensate(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) T lraId);
    }

    @ApplicationScoped
    @Path("/generic-interface-jax-rs-participant-test")
    public static class GenericInterfaceJaxRsParticipantResource implements GenericInterfaceJaxRsParticipant<URI> {

        @Override
        public Response genericInterfaceCompensate(URI lraId) {
            if (lraId == null) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
            GENERIC_INTERFACE_JAX_RS_COMPENSATE_CALLED.set(true);
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

    @ApplicationScoped
    public static class EmptyCompletionParticipantResource {

        @org.eclipse.microprofile.lra.annotation.Forget
        public CompletableFuture<Void> forget(URI lraId) {
            EMPTY_FORGET_CALLED.set(true);
            return CompletableFuture.completedFuture(null);
        }

        @org.eclipse.microprofile.lra.annotation.AfterLRA
        public CompletableFuture<Void> afterLra(URI lraId, LRAStatus status) {
            EMPTY_AFTER_LRA_CALLED.set(true);
            return CompletableFuture.completedFuture(null);
        }
    }

    @ApplicationScoped
    public static class NestedStatusParticipantResource {

        static final AtomicBoolean STATUS_CALLED = new AtomicBoolean();
        static final AtomicReference<URI> STATUS_PARENT_ID = new AtomicReference<>();

        @Compensate
        public Response compensate(URI lraId) {
            return Response.ok(ParticipantStatus.Compensated.name()).build();
        }

        @Status
        public ParticipantStatus status(URI lraId, URI parentId) {
            STATUS_CALLED.set(true);
            STATUS_PARENT_ID.set(parentId);
            return ParticipantStatus.Active;
        }
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @HttpMethod("PUT")
    public @interface CustomHttpMethod {
    }
}
