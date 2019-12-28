/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.integrations.graal.nativeimage.extension;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReaderFactory;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.hosted.FeatureImpl;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

/**
 * Feature to add reflection configuration to the image for Helidon, CDI and Jersey.
 */
@AutomaticFeature
public class HelidonReflectionFeature implements Feature {
    // The following options should be configurable. Now cannot use the native-image configuration options
    private static final Set<String> EXCLUDES = new HashSet<>();

    private static boolean enabled = true;
    private static boolean trace = false;
    private static boolean traceParsing = false;

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return enabled;
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        // load configuration
        HelidonReflectionConfiguration config = loadConfiguration(access);
        // create context (to know what was processed and what should be registered)
        BeforeAnalysisContext context = new BeforeAnalysisContext(access);

        // process each configured annotation
        config.annotations().forEach(it -> processAnnotated(context, it));
        // process each configured interface or class
        config.hierarchy().forEach(it -> processClassHierarchy(context, it));
        // process each configured class
        config.classes().forEach(it -> addSingleClass(context, it));

        // Jersey classes mostly moved to this extension, as they are valid both for client and server
        // See org.glassfish.jersey.internal.inject.Providers
        // see META-INF/native-image/helidon/reflection-config.json

        registerForReflection(context);
    }

    @SuppressWarnings("unchecked")
    private void registerForReflection(BeforeAnalysisContext context) {
        Set<Class<?>> toRegister = context.toRegister();
        FeatureImpl.BeforeAnalysisAccessImpl access = context.access();

        if (trace) {
            System.out.println("***********************************");
            System.out.println("** Registering " + toRegister.size() + " classes for reflection");
            System.out.println("***********************************");
        }

        Class<? extends Annotation> atInject = (Class<? extends Annotation>) access.findClassByName("javax.inject.Inject");
        Class<? extends Annotation> atContext = (Class<? extends Annotation>) access.findClassByName("javax.ws.rs.core.Context");

        // register for reflection
        for (Class<?> aClass : toRegister) {
            if (EXCLUDES.contains(aClass.getName())) {
                if (trace) {
                    System.out.println("Excluding " + aClass.getName() + " from registration");
                }
                continue;
            }
            if (trace) {
                System.out.println("Registering " + aClass.getName() + " for reflection");
            }
            RuntimeReflection.register(aClass);
            RuntimeReflection.register(aClass.getMethods());

            if (aClass.isInterface()) {
                continue;
            }

            RuntimeReflection.register(aClass.getFields());

            registerFields(atInject, atContext, aClass);

            // find all public constructors
            Set<Constructor<?>> constructors = new LinkedHashSet<>(Arrays.asList(aClass.getConstructors()));
            // add all declared
            constructors.addAll(Arrays.asList(aClass.getDeclaredConstructors()));

            for (Constructor<?> constructor : constructors) {
                RuntimeReflection.register(constructor);
                if (trace) {
                    System.out.println("    " + constructor);
                }
            }
        }
    }

    private void registerFields(Class<? extends Annotation> atInject, Class<? extends Annotation> atContext, Class<?> aClass) {
        try {
            for (Field declaredField : aClass.getDeclaredFields()) {
                // there may be fields referencing classes not on the classpath
                if (!Modifier.isPublic(declaredField.getModifiers())) {
                    // public already registered
                    if (null != atInject) {
                        if (declaredField.isAnnotationPresent(atInject)) {
                            RuntimeReflection.register(declaredField);
                            if (trace) {
                                System.out.println("    @Inject " + declaredField);
                            }
                            continue;
                        }
                    }
                    if (null != atContext) {
                        if (declaredField.isAnnotationPresent(atContext)) {
                            RuntimeReflection.register(declaredField);
                            if (trace) {
                                System.out.println("    @Context " + declaredField);
                            }
                        }
                    }
                }
            }
        } catch (NoClassDefFoundError e) {
            if (trace) {
                System.out.println("Fields of "
                                           + aClass.getName()
                                           + " not added to reflection, as a type is not on classpath: "
                                           + e.getMessage());
            }
        }
    }

    private void processClassHierarchy(BeforeAnalysisContext context, Class<?> superclass) {
        if (!Modifier.isInterface(superclass.getModifiers())) {
            context.register(superclass);
        }

        if (traceParsing) {
            System.out.println("Looking up implementors of " + superclass.getName());
        }
        findSubclasses(context, superclass);
        for (Class<?> anInterface : superclass.getInterfaces()) {
            addSingleClass(context, anInterface);
        }

        Class<?> nextSuper = superclass.getSuperclass();
        while (null != nextSuper) {
            addSingleClass(context, nextSuper);
            nextSuper = nextSuper.getSuperclass();
        }
    }

    @SuppressWarnings("unchecked")
    private void processAnnotated(BeforeAnalysisContext context, Class<?> annotationClass) {

        Class<? extends Annotation> annotation;
        try {
            annotation = (Class<? extends Annotation>) annotationClass;
        } catch (ClassCastException e) {
            throw new IllegalStateException("Class configured as annotation is not an annotation: " + annotationClass.getName(),
                                            e);
        }

        if (traceParsing) {
            System.out.println("Looking up annotated by " + annotationClass.getName());
        }
        List<Class<?>> annotatedList = context.access().findAnnotatedClasses(annotation);

        annotatedList.forEach(it -> processClassHierarchy(context, it));
    }

    private void addSingleClass(BeforeAnalysisContext context, Class<?> theClass) {
        if (context.processed(theClass)) {
            return;
        }
        if (traceParsing) {
            System.out.println(theClass.getName());
            System.out.println("  Added for registration");
        }
        context.register(theClass);
    }

    @SuppressWarnings("unchecked")
    private void findSubclasses(BeforeAnalysisContext context, Class<?> aClass) {
        if (EXCLUDES.contains(aClass.getName())) {
            if (trace) {
                System.out.println("Excluding " + aClass.getName() + " from registration");
            }
            return;
        }
        List<Class<?>> subclasses = context.access().findSubclasses((Class<Object>) aClass);

        processClasses(context, subclasses);
    }

    private void processClasses(BeforeAnalysisContext context, List<Class<?>> classes) {

        for (Class<?> aClass : classes) {
            if (context.process(aClass)) {
                if (traceParsing) {
                    System.out.println("    " + aClass.getName());
                }
                int modifiers = aClass.getModifiers();

                if (traceParsing) {
                    System.out.println("        Added for registration");
                }
                context.register(aClass);

                if (!Modifier.isFinal(modifiers)) {
                    findSubclasses(context, aClass);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void findSubclasses(FeatureImpl.BeforeAnalysisAccessImpl access,
                                Set<Class<?>> toRegister,
                                Set<Class<?>> processed,
                                Class<?> aClass) {

        List<Class<?>> subclasses = access.findSubclasses((Class<Object>) aClass);

        processClasses(access,
                       toRegister,
                       processed,
                       subclasses);
    }

    private void processClasses(FeatureImpl.BeforeAnalysisAccessImpl access,
                                Set<Class<?>> toRegister,
                                Set<Class<?>> processed,
                                List<Class<?>> classes) {

        for (Class<?> aClass : classes) {
            if (processed.contains(aClass)) {
                continue;
            }
            processed.add(aClass);

            if (traceParsing) {
                System.out.println("    " + aClass.getName());
            }
            int modifiers = aClass.getModifiers();
            if (!Modifier.isInterface(modifiers)) {
                if (traceParsing) {
                    System.out.println("        Added for registration");
                }
                toRegister.add(aClass);
            }

            if (!Modifier.isFinal(modifiers)) {
                findSubclasses(access, toRegister, processed, aClass);
            }
        }
    }

    private HelidonReflectionConfiguration loadConfiguration(BeforeAnalysisAccess access) {
        // load a known class (config is required by all components)
        Class<?> configClass = access.findClassByName("io.helidon.config.Config");
        // to get the classloader to retrieve our configuration
        ClassLoader cl = configClass.getClassLoader();
        try {
            Enumeration<URL> resources = cl.getResources("META-INF/native-image/helidon/reflection-config.json");
            HelidonReflectionConfiguration config = new HelidonReflectionConfiguration();
            JsonReaderFactory readerFactory = Json.createReaderFactory(Map.of());
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                JsonObject configurationJson = readerFactory.createReader(url.openStream()).readObject();
                jsonArray(access, config.annotations, configurationJson.getJsonArray("annotated"), "Annotation");
                jsonArray(access, config.hierarchy, configurationJson.getJsonArray("class-hierarchy"), "Class hierarchy");
                jsonArray(access, config.classes, configurationJson.getJsonArray("classes"), "Single");
            }

            return config;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to process configuration from helidon-reflection-config.json files", e);
        }
    }

    private void jsonArray(BeforeAnalysisAccess access, List<Class<?>> classList, JsonArray classNames, String desc) {
        if (null == classNames) {
            return;
        }
        for (int i = 0; i < classNames.size(); i++) {
            String className = classNames.getString(i);
            Class<?> clazz = access.findClassByName(className);
            if (null == clazz) {
                if (traceParsing) {
                    System.out.println(desc + " class \"" + className + "\" configured for reflection is not on classpath");
                }
            } else {
                classList.add(clazz);
            }
        }
    }

    private static final class HelidonReflectionConfiguration {
        private final List<Class<?>> annotations = new LinkedList<>();
        private final List<Class<?>> hierarchy = new LinkedList<>();
        private final List<Class<?>> classes = new LinkedList<>();

        private List<Class<?>> annotations() {
            return annotations;
        }

        private List<Class<?>> hierarchy() {
            return hierarchy;
        }

        private List<Class<?>> classes() {
            return classes;
        }
    }

    private static final class BeforeAnalysisContext {
        private final FeatureImpl.BeforeAnalysisAccessImpl access;
        private final Set<Class<?>> processed = new HashSet<>();
        private final Set<Class<?>> toRegister = new LinkedHashSet<>();

        private BeforeAnalysisContext(BeforeAnalysisAccess access) {
            this.access = (FeatureImpl.BeforeAnalysisAccessImpl) access;
        }

        public FeatureImpl.BeforeAnalysisAccessImpl access() {
            return access;
        }

        public boolean process(Class<?> theClass) {
            return processed.add(theClass);
        }

        public boolean processed(Class<?> theClass) {
            return processed.contains(theClass);
        }

        public void register(Class<?> theClass) {
            this.toRegister.add(theClass);
        }

        public Set<Class<?>> toRegister() {
            return toRegister;
        }
    }
}
