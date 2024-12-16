/*
 * Copyright (c) 2021, 2024 Oracle and/or its affiliates.
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger.Level;
import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import io.helidon.common.Reflected;
import io.helidon.config.Config;
import io.helidon.config.mp.MpConfig;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.webserver.http.HttpService;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AnnotatedParameter;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.DeploymentException;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.enterprise.inject.spi.ProcessManagedBean;
import jakarta.enterprise.inject.spi.WithAnnotations;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.OPTIONS;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.lra.annotation.AfterLRA;
import org.eclipse.microprofile.lra.annotation.Compensate;
import org.eclipse.microprofile.lra.annotation.Complete;
import org.eclipse.microprofile.lra.annotation.Forget;
import org.eclipse.microprofile.lra.annotation.LRAStatus;
import org.eclipse.microprofile.lra.annotation.Status;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.CompositeIndex;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexReader;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Indexer;

import static jakarta.interceptor.Interceptor.Priority.PLATFORM_AFTER;

/**
 * MicroProfile Long Running Actions CDI extension.
 */
@Reflected
public class LraCdiExtension implements Extension {

    private static final System.Logger LOGGER = System.getLogger(LraCdiExtension.class.getName());
    private static final String CONFIG_PREFIX = "helidon.lra.participant";

    private static final Set<Class<? extends Annotation>> EXPECTED_ANNOTATIONS = Set.of(
            AfterLRA.class,
            Complete.class,
            Compensate.class,
            Forget.class
    );

    private static final Set<Class<? extends Annotation>> EXCLUDED_ANNOTATIONS = Set.of(PUT.class, Path.class);

    private final Set<Class<?>> beanTypesWithCdiLRAMethods = new HashSet<>();
    private final Map<Class<?>, Bean<?>> lraCdiBeanReferences = new HashMap<>();
    private final Indexer indexer;
    private final ClassLoader classLoader;
    private final Config config;
    private IndexView index;


    /**
     * Initialize MicroProfile Long Running Actions CDI extension.
     */
    public LraCdiExtension() {
        config = MpConfig.toHelidonConfig(ConfigProvider.getConfig()).get(CONFIG_PREFIX);
        indexer = new Indexer();
        classLoader = Thread.currentThread().getContextClassLoader();
        // Needs to be always indexed
        Set.of(LRA.class,
                AfterLRA.class,
                Compensate.class,
                Complete.class,
                Forget.class,
                Status.class,
                Application.class,
                NonJaxRsResource.class).forEach(c -> runtimeIndex(DotName.createSimple(c.getName())));

        Boolean useBuildTimeIndex = config.get("use-build-time-index").asBoolean().orElse(Boolean.TRUE);
        if (useBuildTimeIndex) {
            resolveBuildTimeIndex();
        }
    }

    private void index(
            @Observes
            @WithAnnotations({
                    Path.class,
                    GET.class, POST.class, PUT.class, OPTIONS.class, PATCH.class, DELETE.class,
                    LRA.class,
                    AfterLRA.class, Compensate.class, Complete.class, Forget.class, Status.class
            }) ProcessAnnotatedType<?> pat) {
        // compile time built index
        if (index != null) return;
        // create runtime index when pre-built index is not available
        runtimeIndex(DotName.createSimple(pat.getAnnotatedType().getJavaClass().getName()));
    }

    private void registerInternalBeans(@Observes BeforeBeanDiscovery event) {
        Stream.of(
                CoordinatorLocatorService.class,
                HandlerService.class,
                InspectionService.class,
                NonJaxRsResource.class,
                ParticipantService.class
        )
                .forEach(clazz -> event
                        .addAnnotatedType(clazz, "lra-" + clazz.getName())
                        .add(ApplicationScoped.Literal.INSTANCE)
                );
    }

    private void validateCdiLRASignatures(@Observes
                                          @WithAnnotations(
                                                  {
                                                          Complete.class,
                                                          Compensate.class,
                                                          Forget.class,
                                                          AfterLRA.class,
                                                          Status.class
                                                  })
                                                  ProcessAnnotatedType<?> pat) {
        AnnotatedType<?> annotatedType = pat.getAnnotatedType();
        beanTypesWithCdiLRAMethods.add(annotatedType.getJavaClass());
        annotatedType.getMethods().stream()
                .filter(m -> m.getAnnotations().stream()
                        .map(Annotation::annotationType)
                        .anyMatch(EXPECTED_ANNOTATIONS::contains))
                .filter(m -> m.getAnnotations().stream()
                        .map(Annotation::annotationType)
                        .noneMatch(EXCLUDED_ANNOTATIONS::contains))
                .forEach(m -> {
                    List<? extends AnnotatedParameter<?>> parameters = m.getParameters();
                    if (parameters.size() > 2) {
                        throw new DeploymentException("Too many arguments on compensate method "
                                + m.getJavaMember());
                    }
                    if (parameters.size() == 1 && !parameters.get(0).getBaseType().equals(URI.class)) {
                        throw new DeploymentException("First argument of LRA method "
                                + m.getJavaMember() + " must be of type URI");
                    }
                    if (parameters.size() == 2) {
                        if (!parameters.get(0).getBaseType().equals(URI.class)) {
                            throw new DeploymentException("First argument of LRA method "
                                    + m.getJavaMember() + " must be of type URI");
                        }
                        if (!Set.of(URI.class, LRAStatus.class).contains(parameters.get(1).getBaseType())) {
                            throw new DeploymentException("Second argument of LRA method "
                                    + m.getJavaMember() + " must be of type URI or LRAStatus");
                        }
                    }
                });
    }

    private void cdiLRABeanReferences(@Observes ProcessManagedBean<?> event) {
        if (beanTypesWithCdiLRAMethods.contains(event.getBean().getBeanClass())) {
            lraCdiBeanReferences.put(event.getBean().getBeanClass(), event.getBean());
        }
    }

    private void beforeServerStart(
            @Observes
            @Priority(PLATFORM_AFTER + 99)
            @Initialized(ApplicationScoped.class) Object event,
            BeanManager beanManager) {

        NonJaxRsResource nonJaxRsResource = resolve(NonJaxRsResource.class, beanManager);
        HttpService nonJaxRsParticipantService = nonJaxRsResource.createNonJaxRsParticipantResource();
        beanManager.getExtension(ServerCdiExtension.class)
                .serverRoutingBuilder()
                .register(nonJaxRsResource.contextPath(), nonJaxRsParticipantService);

    }

    private void ready(
            @Observes
            @Priority(PLATFORM_AFTER + 101)
            @Initialized(ApplicationScoped.class) Object event,
            BeanManager beanManager) {

        if (index == null) {
            // compile time built index
            index = indexer.complete();
        }

        // ------------- Validate LRA participant methods -------------
        InspectionService inspectionService = resolve(InspectionService.class, beanManager);

        for (ClassInfo classInfo : index.getKnownClasses()) {

            if (Modifier.isInterface(classInfo.flags()) || Modifier.isAbstract(classInfo.flags())) {
                // skip
                continue;
            }

            ParticipantValidationModel validationModel = inspectionService.lookUpLraMethods(classInfo);

            if (validationModel.isParticipant()) {
                validationModel.validate();
            }
        }
    }

    Map<Class<?>, Bean<?>> lraCdiBeanReferences() {
        return lraCdiBeanReferences;
    }

    void runtimeIndex(DotName fqdn) {
        if (fqdn == null) return;
        LOGGER.log(Level.DEBUG, "Indexing " + fqdn);
        ClassInfo classInfo;
        try (InputStream classStream = classLoader.getResourceAsStream(fqdn.toString().replace('.', '/') + ".class")) {

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int bytesRead;
            byte[] buffer = new byte[512];
            while ((bytesRead = classStream.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());

            indexer.index(bais);
            classInfo = Index.singleClass(new ByteArrayInputStream(baos.toByteArray())); // look also for extended classes
            runtimeIndex(classInfo.superName());
            // and implemented interfaces
            classInfo.interfaceNames().forEach(this::runtimeIndex);
        } catch (IOException e) {
            LOGGER.log(Level.ERROR, "Unable to index referenced class.", e);
        }
    }

    private void resolveBuildTimeIndex() {
        List<URL> indexFiles;
        try {
            indexFiles = findIndexFiles(config.get("index-resource").asString().orElse("META-INF/jandex.idx"));
            if (!indexFiles.isEmpty()) {
                index = CompositeIndex.create(indexer.complete(), existingIndexFileReader(indexFiles));
            } else {
                index = null;
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error when locating Jandex index, fall-back to runtime computed index.", e);
            index = null;
        }
    }

    private IndexView existingIndexFileReader(List<URL> indexUrls) throws IOException {
        List<IndexView> indices = new ArrayList<>();
        for (URL indexURL : indexUrls) {
            try (InputStream indexIS = indexURL.openStream()) {
                LOGGER.log(Level.INFO, "Adding Jandex index at {0}", indexURL.toString());
                indices.add(new IndexReader(indexIS).read());
            } catch (IOException ex) {
                throw new IOException("Attempted to read from previously-located index file "
                        + indexURL + " but the index cannot be found", ex);
            }
        }
        return indices.size() == 1 ? indices.get(0) : CompositeIndex.create(indices);
    }

    private List<URL> findIndexFiles(String... indexPaths) throws IOException {
        List<URL> result = new ArrayList<>();
        for (String indexPath : indexPaths) {
            Enumeration<URL> urls = classLoader.getResources(indexPath);
            while (urls.hasMoreElements()) {
                result.add(urls.nextElement());
            }
        }
        return result;
    }

    public IndexView getIndex() {
        return index;
    }

    static <T> T resolve(Class<T> beanType, BeanManager bm) {
        return lookup(bm.resolve(bm.getBeans(beanType)), bm);
    }

    @SuppressWarnings("unchecked")
    static <T> T lookup(Bean<?> bean, BeanManager beanManager) {
        jakarta.enterprise.context.spi.Context context = beanManager.getContext(bean.getScope());
        Object instance = context.get(bean);
        if (instance == null) {
            CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);
            instance = beanManager.getReference(bean, bean.getBeanClass(), creationalContext);
        }
        if (instance == null) {
            throw new DeploymentException("Instance of bean " + bean.getName() + " not found");
        }
        return (T) instance;
    }
}
