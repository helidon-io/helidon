/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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
package io.helidon.microprofile.openapi;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.microprofile.server.JaxRsApplication;

import io.smallrye.openapi.api.OpenApiConfigImpl;
import io.smallrye.openapi.runtime.scanner.FilteredIndexView;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Feature;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.Config;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.CompositeIndex;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexReader;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Indexer;

/**
 * Utility that computes the list of filtered index views, one for each JAX-RS application,
 * sorted by the Application class name to help keep the list of endpoints in the OpenAPI document in a stable order.
 */
class FilteredIndexViewsBuilder {

    private static final System.Logger LOGGER = System.getLogger(FilteredIndexViewsBuilder.class.getName());

    private final Config config;
    private final FilteredIndexView view;
    private final List<JaxRsApplication> apps;
    private final Set<String> requiredClasses;
    private final boolean useJaxRsSemantics;

    FilteredIndexViewsBuilder(Config config,
                              List<JaxRsApplication> apps,
                              Set<Class<?>> types,
                              List<String> indexPaths,
                              boolean useJaxRsSemantics) {

        this.config = config;
        this.view = new FilteredIndexView(indexView(indexPaths, apps, types), new OpenApiConfigImpl(config));
        this.apps = apps;
        this.requiredClasses = requiredClassNames(view);
        this.useJaxRsSemantics = useJaxRsSemantics;
    }

    /**
     * Creates a {@link FilteredIndexView} tailored to each JAX-RS application.
     *
     * @return the list of filtered index views
     */
    List<FilteredIndexView> buildViews() {
        return apps.stream()
                .filter(app -> app.applicationClass().isPresent())
                .sorted(Comparator.comparing(app -> app.applicationClass().get().getName()))
                .map(this::map)
                .toList();
    }

    private FilteredIndexView map(JaxRsApplication app) {

        Application application = app.resourceConfig().getApplication();

        @SuppressWarnings("deprecation")
        Set<String> singletons = application.getSingletons()
                .stream()
                .map(Object::getClass)
                .map(Class::getName)
                .collect(Collectors.toSet());

        Set<String> classes = application.getClasses()
                .stream()
                .map(Class::getName)
                .collect(Collectors.toSet());

        String appClassName = className(app);

        Set<String> explicitClassNames = new HashSet<>(classes);
        explicitClassNames.addAll(singletons);

        if (explicitClassNames.isEmpty() && apps.size() == 1) {
            // No need to do filtering at all.
            if (LOGGER.isLoggable(Level.TRACE)) {
                LOGGER.log(Level.TRACE, String.format(
                        "No filtering required for %s which reports no explicitly referenced classes and "
                                + "is the only JAX-RS application",
                        appClassName));
            }
            return view;
        }

        // Note that the MP OpenAPI TCK does not follow JAX-RS behavior wen getSingletons returns a non-empty set.
        // The TCK incorrectly expects the endpoints defined by other resources as well to appear in the OpenAPI document.
        if ((classes.isEmpty() && (singletons.isEmpty() || !useJaxRsSemantics)) && apps.size() == 1) {
            if (LOGGER.isLoggable(Level.TRACE)) {
                LOGGER.log(Level.TRACE, String.format(
                        "No filtering required for %s because JAX-RS semantics is disabled",
                        appClassName));
            }
            // Perform no further filtering if all the following conditions are met:
            // - there is exactly one application,
            // - we found no classes from getClasses
            // - we found no classes from getSingletons or the JAX-RS semantic is disabled.
            return view;
        }

        Set<String> excludedClasses = excludedClasses(app, explicitClassNames);
        FilteringOpenApiConfigImpl filteringOpenApiConfig = new FilteringOpenApiConfigImpl(config, excludedClasses);

        // Create a new filtered index view for this application which excludes the irrelevant classes we just identified.
        // Its delegate is the previously-created view based only on the MP configuration.
        FilteredIndexView result = new FilteredIndexView(view, filteringOpenApiConfig);

        if (LOGGER.isLoggable(Level.TRACE)) {
            LOGGER.log(Level.TRACE, String.format(
                    "FilteredIndexView for %n"
                            + "  application class %s%n"
                            + "  with explicitly-referenced classes %s%n"
                            + "  yields exclude list: %s%n  and known classes: %n  %s",
                    appClassName,
                    explicitClassNames,
                    excludedClasses,
                    String.join("," + System.lineSeparator() + "    ", knownClassNames(result))));
        }

        return result;
    }

    private Set<String> excludedClasses(JaxRsApplication app, Set<String> explicitClasses) {

        String appClass = className(app);

        // Start with all other JAX-RS app names.
        Set<String> result = apps.stream()
                .map(FilteredIndexViewsBuilder::className)
                .filter(name -> !name.equals("<unknown>") && !name.equals(appClass))
                .collect(Collectors.toSet());

        if (!explicitClasses.isEmpty()) {
            // This class identified resource, provider, or feature classes it uses.
            // Ignore all ancillary classes that this app does not explicitly reference.
            result.addAll(requiredClasses);
            result.removeAll(explicitClasses);
        }

        return result;
    }

    private static String className(JaxRsApplication app) {
        return app.applicationClass().map(Class::getName).orElse("<unknown>");
    }

    private static Set<String> requiredClassNames(IndexView indexView) {
        Set<String> result = new HashSet<>(annotatedClassNames(indexView, Path.class));
        result.addAll(annotatedClassNames(indexView, Provider.class));
        result.addAll(annotatedClassNames(indexView, Feature.class));
        if (LOGGER.isLoggable(Level.DEBUG)) {
            LOGGER.log(Level.DEBUG, "Ancillary classes: {0}", result);
        }
        return result;
    }

    private static Set<String> annotatedClassNames(IndexView indexView, Class<?> annotationClass) {
        return indexView
                .getAnnotations(DotName.createSimple(annotationClass.getName()))
                .stream()
                .map(AnnotationInstance::target)
                .filter(target -> target.kind() == AnnotationTarget.Kind.CLASS)
                .map(AnnotationTarget::asClass)
                .filter(classInfo -> hasImplementationOrIsIncluded(indexView, classInfo))
                .map(ClassInfo::toString)
                .collect(Collectors.toSet());
    }

    private static boolean hasImplementationOrIsIncluded(IndexView indexView, ClassInfo classInfo) {
        if (!Modifier.isInterface(classInfo.flags())) {
            return true;
        }
        return indexView.getAllKnownImplementors(classInfo.name()).stream()
                .anyMatch(info -> !Modifier.isAbstract(info.flags()));
    }

    private static List<String> knownClassNames(FilteredIndexView filteredIndexView) {
        return filteredIndexView
                .getKnownClasses()
                .stream()
                .map(ClassInfo::toString)
                .sorted()
                .toList();
    }

    private static IndexView indexView(List<String> indexPaths, List<JaxRsApplication> apps, Set<Class<?>> types) {
        try {
            List<URL> urls = findIndexFiles(indexPaths);
            if (urls.isEmpty()) {
                LOGGER.log(Level.INFO, """
                        Could not locate the Jandex index file META-INF/jandex.idx, building an in-memory index...
                        Consider using the Jandex maven plug-in during your build to add it to your app.""");
                return buildIndex(apps, types);
            }
            return loadIndex(indexPaths);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static IndexView loadIndex(List<String> indexPaths) throws IOException {
        List<IndexView> indices = new ArrayList<>();
        for (URL url : findIndexFiles(indexPaths)) {
            try (InputStream is = url.openStream()) {
                LOGGER.log(Level.TRACE, "Adding Jandex index at {0}", url.toString());
                indices.add(new IndexReader(is).read());
            } catch (Exception ex) {
                throw new IOException(String.format(
                        "Attempted to read from previously-located index file %s but the index cannot be read",
                        url), ex);
            }
        }
        return indices.size() == 1 ? indices.get(0) : CompositeIndex.create(indices);
    }

    private static IndexView buildIndex(List<JaxRsApplication> apps, Set<Class<?>> types) throws IOException {
        Indexer indexer = new Indexer();
        for (Class<?> c : types) {
            indexClass(indexer, c);
        }

        // Some apps might be added dynamically, not via annotation processing.
        // Add those classes to the index if they are not already present.
        apps.stream()
                .map(JaxRsApplication::applicationClass)
                .flatMap(Optional::stream)
                .forEach(cls -> indexClass(indexer, cls));

        LOGGER.log(Level.TRACE, "Using internal Jandex index created from CDI bean discovery");
        Index result = indexer.complete();
        dumpIndex(result);
        return result;
    }

    private static void indexClass(Indexer indexer, Class<?> c) {
        try {
            indexer.indexClass(c);
        } catch (IOException ex) {
            throw new UncheckedIOException(
                    String.format("Cannot load bytecode from class %s for annotation processing", c),
                    ex);
        }
    }

    private static void dumpIndex(Index index) {
        if (LOGGER.isLoggable(Level.DEBUG)) {
            LOGGER.log(Level.DEBUG, "Dump of internal Jandex index:");
            PrintStream oldStdout = System.out;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (PrintStream newPS = new PrintStream(baos, true, Charset.defaultCharset())) {
                System.setOut(newPS);
                index.printAnnotations();
                index.printSubclasses();
                LOGGER.log(Level.DEBUG, baos.toString(Charset.defaultCharset()));
            } finally {
                System.setOut(oldStdout);
            }
        }
    }

    private static List<URL> findIndexFiles(List<String> paths) {
        List<URL> result = new ArrayList<>();
        for (String path : paths) {
            Enumeration<URL> urls;
            try {
                urls = Thread.currentThread().getContextClassLoader().getResources(path);
                while (urls.hasMoreElements()) {
                    result.add(urls.nextElement());
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return result;
    }

    private static class FilteringOpenApiConfigImpl extends OpenApiConfigImpl {

        private final Set<String> classesToExclude;

        FilteringOpenApiConfigImpl(org.eclipse.microprofile.config.Config config, Set<String> classesToExclude) {
            super(config);
            this.classesToExclude = classesToExclude;
        }

        @Override
        public Set<String> scanExcludeClasses() {
            return classesToExclude;
        }
    }
}
