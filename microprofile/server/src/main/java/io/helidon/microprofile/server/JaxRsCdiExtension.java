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
package io.helidon.microprofile.server;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.WithAnnotations;
import javax.ws.rs.Path;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

import io.helidon.common.HelidonFeatures;
import io.helidon.common.HelidonFlavor;
import io.helidon.webserver.jersey.JerseySupport;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.glassfish.jersey.server.ResourceConfig;

import static javax.interceptor.Interceptor.Priority.PLATFORM_BEFORE;

/**
 * Configure Jersey related things.
 */
public class JaxRsCdiExtension implements Extension {
    private static final Logger LOGGER = Logger.getLogger(JaxRsCdiExtension.class.getName());

    static {
        HelidonFeatures.register(HelidonFlavor.MP, "JAX-RS");
    }

    private final List<JaxRsApplication> applicationMetas = new LinkedList<>();

    private final Set<Class<? extends Application>> applications = new LinkedHashSet<>();
    private final Set<Class<?>> resources = new HashSet<>();
    private final AtomicBoolean setInStone = new AtomicBoolean(false);

    private void collectApplications(@Observes ProcessAnnotatedType<? extends Application> applicationType) {
        applications.add(applicationType.getAnnotatedType().getJavaClass());
    }

    private void collectResourceClasses(@Observes @WithAnnotations(Path.class) ProcessAnnotatedType<?> resourceType) {
        Class<?> resourceClass = resourceType.getAnnotatedType().getJavaClass();
        if (resourceClass.isInterface()) {
            // we are only interested in classes - interface is most likely a REST client API
            return;
        }
        LOGGER.finest(() -> "Discovered resource class " + resourceClass.getName());
        resources.add(resourceClass);
    }

    // once application scoped starts, we do not allow modification of applications
    void fixApps(@Observes @Priority(PLATFORM_BEFORE) @Initialized(ApplicationScoped.class) Object event) {
        this.setInStone.set(true);
    }

    /**
     * List of applications including discovered and explicitly configured applications.
     * <p>
     * This method should only be called in {@code Initialized(ApplicationScoped.class)} observer methods,
     *  that have a higher priority than {@link io.helidon.microprofile.server.ServerCdiExtension} start server
     *  method.
     *
     * @return list of applications found by CDI
     * @throws java.lang.IllegalStateException in case the list of applications is not yet fixed
     */
    public List<JaxRsApplication> applicationsToRun() throws IllegalStateException {
        if (!setInStone.get()) {
            throw new IllegalStateException("Applications are not yet fixed. This method is only available in "
                                                    + "@Initialized(ApplicationScoped.class) event, before server is started");
        }
        if (applications.isEmpty() && applicationMetas.isEmpty()) {
            // create a synthetic application from all resource classes
            // the classes set must be created before the lambda, as resources are cleared later on
            if (!resources.isEmpty()) {
                Set<Class<?>> classes = new HashSet<>(resources);
                applicationMetas.add(JaxRsApplication.builder()
                                             .synthetic(true)
                                             .applicationClass(Application.class)
                                             .config(ResourceConfig.forApplication(new Application() {
                                                 @Override
                                                 public Set<Class<?>> getClasses() {
                                                     return classes;
                                                 }
                                             }))
                                             .appName("SyntheticApplication")
                                             .build());
            }
        }

        // make sure the resources are used as a default if application does not define any
        applicationMetas.addAll(applications
                                        .stream()
                                        .map(appClass -> JaxRsApplication.builder()
                                                .applicationClass(appClass)
                                                .config(ResourceConfig.forApplicationClass(appClass, resources))
                                                .build())
                                        .collect(Collectors.toList()));

        applications.clear();
        resources.clear();

        return applicationMetas;
    }

    /**
     * Remove all discovered applications (configured applications are not removed).
     *
     * @throws java.lang.IllegalStateException in case applications are already started
     */
    public void removeApplications() throws IllegalStateException {
        mutateApps();
        this.applications.clear();
    }

    /**
     * Remove all discovered and configured resource classes.
     *
     * @throws java.lang.IllegalStateException in case applications are already started
     */
    public void removeResourceClasses() throws IllegalStateException {
        mutateApps();
        this.resources.clear();
    }

    /**
     * Add all resource classes from the list to the list of resource classes discovered through CDI.
     * These may be added to an existing application, or may create a synthetic application, depending
     * on other configuration.
     *
     * @param resourceClasses resource classes to add
     * @throws java.lang.IllegalStateException in case applications are already started
     */
    public void addResourceClasses(List<Class<?>> resourceClasses) throws IllegalStateException {
        mutateApps();
        this.resources.addAll(resourceClasses);
    }

    /**
     * Add all application metadata from the provided list.
     *
     * @param applications application metadata
     * @throws java.lang.IllegalStateException in case applications are already started
     *
     * @see io.helidon.microprofile.server.JaxRsApplication
     */
    public void addApplications(List<JaxRsApplication> applications) throws IllegalStateException {
        mutateApps();
        this.applicationMetas.addAll(applications);
    }

    /**
     * Add a jersey application to the server. Context will be introspected from {@link javax.ws.rs.ApplicationPath} annotation.
     * You can also use {@link #addApplication(String, Application)}.
     *
     * @param application configured as needed
     * @throws java.lang.IllegalStateException in case applications are already started
     */
    public void addApplication(Application application) throws IllegalStateException {
        mutateApps();
        this.applicationMetas.add(JaxRsApplication.create(application));
    }

    /**
     * Add a jersey application to the server with an explicit context path.
     *
     * @param contextRoot Context root to use for this application ({@link javax.ws.rs.ApplicationPath} is ignored)
     * @param application configured as needed
     * @throws java.lang.IllegalStateException in case applications are already started
     */
    public void addApplication(String contextRoot, Application application) throws IllegalStateException {
        mutateApps();
        this.applicationMetas.add(JaxRsApplication.builder()
                                          .application(application)
                                          .contextRoot(contextRoot)
                                          .build());
    }

    /**
     * Makes an attempt to "guess" the service name.
     * <p>
     * Service name is determined as follows:
     * <ul>
     *     <li>A configuration key {@code service.name}</li>
     *     <li>A configuration key {@code tracing.service}</li>
     *     <li>Name of the first JAX-RS application, if any</li>
     *     <li>{@code helidon-mp}</li>
     * </ul>
     * @return name of this service
     */
    public String serviceName() {
        Config config = ConfigProvider.getConfig();
        return config.getOptionalValue("service.name", String.class)
                .or(() -> config.getOptionalValue("tracing.service", String.class))
                .or(this::guessServiceName)
                .orElse("helidon-mp");
    }

    private Optional<String> guessServiceName() {
        if (applicationMetas.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(applicationMetas.get(0).appName());
    }

    /**
     * Create an application from the provided resource classes and add it to the list of applications.
     *
     * @param resourceClasses resource classes to create a synthetic application from
     * @throws java.lang.IllegalStateException in case applications are already started
     */
    public void addSyntheticApplication(List<Class<?>> resourceClasses) throws IllegalStateException {
        mutateApps();
        this.applicationMetas.add(JaxRsApplication.builder()
                                          .synthetic(true)
                                          .applicationClass(Application.class)
                                          .config(ResourceConfig.forApplication(new Application() {
                                              @Override
                                              public Set<Class<?>> getClasses() {
                                                  return new LinkedHashSet<>(resourceClasses);
                                              }
                                          }))
                                          .appName("SyntheticApplication")
                                          .build());
    }

    JerseySupport toJerseySupport(Supplier<? extends ExecutorService> defaultExecutorService, JaxRsApplication jaxRsApplication) {
        JerseySupport.Builder builder = JerseySupport.builder(jaxRsApplication.resourceConfig());
        builder.config(((io.helidon.config.Config) ConfigProvider.getConfig()).get("server.jersey"));
        builder.executorService(jaxRsApplication.executorService().orElseGet(defaultExecutorService));
        builder.register(new ExceptionMapper<Exception>() {
            @Override
            public Response toResponse(Exception exception) {
                if (exception instanceof WebApplicationException) {
                    return ((WebApplicationException) exception).getResponse();
                } else {
                    LOGGER.log(Level.WARNING, exception, () -> "Internal server error");
                    return Response.serverError().build();
                }
            }
        });
        return builder.build();
    }

    Optional<String> findContextRoot(io.helidon.config.Config config, JaxRsApplication jaxRsApplication) {
        return config.get(jaxRsApplication.appClassName() + "." + RoutingPath.CONFIG_KEY_PATH)
                .asString()
                .or(jaxRsApplication::contextRoot)
                .map(path -> (path.startsWith("/") ? path : ("/" + path)));
    }

    Optional<String> findNamedRouting(io.helidon.config.Config config, JaxRsApplication jaxRsApplication) {
        return config.get(jaxRsApplication.appClassName() + "." + RoutingName.CONFIG_KEY_NAME)
                .asString()
                .or(jaxRsApplication::routingName)
                .flatMap(it -> RoutingName.DEFAULT_NAME.equals(it) ? Optional.empty() : Optional.of(it));
    }

    boolean isNamedRoutingRequired(io.helidon.config.Config config, JaxRsApplication jaxRsApplication) {
        return config.get(jaxRsApplication.appClassName() + "." + RoutingName.CONFIG_KEY_REQUIRED)
                .asBoolean()
                .orElseGet(jaxRsApplication::routingNameRequired);
    }

    private void mutateApps() {
        if (setInStone.get()) {
            throw new IllegalStateException("You are attempting to modify applications in JAX-RS after they were registered "
                                                    + "with the server");
        }
    }
}
