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

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.hosted.FeatureImpl;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

/**
 * Feature to add reflection configuration to the image for Helidon, CDI and Jersey.
 */
@AutomaticFeature
public class HelidonReflectionFeature implements Feature {
    // The following options should be configurable. Now cannot use the native-image configuraiton options
    private static final boolean ENABLED = true;
    private static final boolean TRACE = false;
    private static final boolean TRACE_PARSING = false;
    private static final Set<String> EXCLUSIONS = new HashSet<>();

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return ENABLED;
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        FeatureImpl.BeforeAnalysisAccessImpl accessImpl = (FeatureImpl.BeforeAnalysisAccessImpl) access;

        Set<Class<?>> processed = new HashSet<>();
        Set<Class<?>> toRegister = new LinkedHashSet<>();

        processAnnotated(accessImpl, toRegister, processed, "javax.inject.Singleton");
        processAnnotated(accessImpl, toRegister, processed, "javax.enterprise.context.Dependent");
        processAnnotated(accessImpl, toRegister, processed, "javax.enterprise.context.RequestScoped");
        processAnnotated(accessImpl, toRegister, processed, "javax.enterprise.context.ApplicationScoped");
        processAnnotated(accessImpl, toRegister, processed, "javax.ws.rs.Path");
        processAnnotated(accessImpl, toRegister, processed, "org.jvnet.hk2.annotations.Service");
        processAnnotated(accessImpl, toRegister, processed, "org.jvnet.hk2.annotations.Contract");
        processAnnotated(accessImpl, toRegister, processed, "org.glassfish.hk2.api.PerLookup");
        processAnnotated(accessImpl, toRegister, processed, "org.glassfish.jersey.process.internal.RequestScoped");
        processAnnotated(accessImpl, toRegister, processed, "org.glassfish.jersey.spi.Contract");
        processAnnotated(accessImpl, toRegister, processed, "org.glassfish.jersey.internal.inject.PerLookup");

        // See org.glassfish.jersey.internal.inject.Providers
        processClassHierarchy(accessImpl, toRegister, processed, "javax.ws.rs.ext.ContextResolver");
        processClassHierarchy(accessImpl, toRegister, processed, "javax.ws.rs.ext.ExceptionMapper");
        processClassHierarchy(accessImpl, toRegister, processed, "javax.ws.rs.ext.MessageBodyReader");
        processClassHierarchy(accessImpl, toRegister, processed, "javax.ws.rs.ext.MessageBodyWriter");
        processClassHierarchy(accessImpl, toRegister, processed, "javax.ws.rs.ext.ReaderInterceptor");
        processClassHierarchy(accessImpl, toRegister, processed, "javax.ws.rs.ext.WriterInterceptor");
        processClassHierarchy(accessImpl, toRegister, processed, "javax.ws.rs.ext.ParamConverterProvider");

        processClassHierarchy(accessImpl, toRegister, processed, "javax.ws.rs.container.ContainerRequestFilter");
        processClassHierarchy(accessImpl, toRegister, processed, "javax.ws.rs.container.ContainerResponseFilter");
        processClassHierarchy(accessImpl, toRegister, processed, "javax.ws.rs.container.DynamicFeature");

        processClassHierarchy(accessImpl, toRegister, processed, "javax.ws.rs.client.ClientRequestFilter");
        processClassHierarchy(accessImpl, toRegister, processed, "javax.ws.rs.client.ClientResponseFilter");
        processClassHierarchy(accessImpl, toRegister, processed, "javax.ws.rs.client.RxInvokerProvider");

        // Helidon added:
        processClassHierarchy(accessImpl, toRegister, processed, "javax.inject.Provider");
        processClassHierarchy(accessImpl, toRegister, processed, "javax.ws.rs.core.Application");
        processClassHierarchy(accessImpl, toRegister, processed, "javax.ws.rs.core.Feature");
        processClassHierarchy(accessImpl, toRegister, processed, "javax.ws.rs.ext.Providers");
        processClassHierarchy(accessImpl, toRegister, processed, "org.glassfish.jersey.internal.inject.Providers");
        processClassHierarchy(accessImpl, toRegister, processed, "org.glassfish.jersey.process.Inflector");
        processClassHierarchy(accessImpl, toRegister, processed, "org.glassfish.jersey.process.internal.Inflecting");
        // classes
        // implementation of Jersey injection
        processClassHierarchy(accessImpl, toRegister, processed, "org.glassfish.jersey.process.internal.RequestScope");
        // implementation of json-p does not use service loader :(
        processClassHierarchy(accessImpl, toRegister, processed, "org.glassfish.json.JsonProviderImpl");
        processClassHierarchy(accessImpl, toRegister, processed, "java.util.logging.Formatter");

        registerForReflection(accessImpl, toRegister);
    }

    @SuppressWarnings("unchecked")
    private void registerForReflection(FeatureImpl.BeforeAnalysisAccessImpl access,
                                       Set<Class<?>> toRegister) {
        Class<? extends Annotation> atInject = (Class<? extends Annotation>) access.findClassByName("javax.inject.Inject");

        // register for reflection
        for (Class<?> aClass : toRegister) {
            if (EXCLUSIONS.contains(aClass.getName())) {
                if (TRACE) {
                    System.out.println("Not registering " + aClass.getName() + " for reflection (excluded)");
                }
                continue;
            }
            if (TRACE) {
                System.out.println("Registering " + aClass.getName() + " for reflection");
            }
            RuntimeReflection.register(aClass);
            RuntimeReflection.register(aClass.getMethods());
            RuntimeReflection.register(aClass.getFields());

            registerFields(atInject, aClass);

            // find all public constructors
            Set<Constructor<?>> constructors = new LinkedHashSet<>(Arrays.asList(aClass.getConstructors()));
            // add all declared
            constructors.addAll(Arrays.asList(aClass.getDeclaredConstructors()));

            for (Constructor<?> constructor : constructors) {
                RuntimeReflection.register(constructor);
                if (TRACE) {
                    System.out.println("    " + constructor);
                }
            }
        }
    }

    private void registerFields(Class<? extends Annotation> atInject, Class<?> aClass) {
        try {
            for (Field declaredField : aClass.getDeclaredFields()) {
                // there may be fields referencing classes not on the classpath
                if (!Modifier.isPublic(declaredField.getModifiers())) {
                    // public already registered
                    if (null != atInject) {
                        if (declaredField.isAnnotationPresent(atInject)) {
                            RuntimeReflection.register(declaredField);
                        }
                    }
                }
            }
        } catch (NoClassDefFoundError e) {
            if (TRACE) {
                System.out.println("Fields of "
                                           + aClass.getName()
                                           + " not added to reflection, as a type is not on classpath: "
                                           + e.getMessage());
            }
        }
    }

    private void processClassHierarchy(FeatureImpl.BeforeAnalysisAccessImpl access,
                                       Set<Class<?>> toRegister,
                                       Set<Class<?>> processed,
                                       String superclassName) {
        Class<?> superclass = access.findClassByName(superclassName);
        if (null == superclass) {
            if (TRACE_PARSING) {
                System.out.println("Class " + superclassName + " is not on classpath");
            }
            return;
        }

        if (!Modifier.isInterface(superclass.getModifiers())) {
            toRegister.add(superclass);
        }

        if (TRACE_PARSING) {
            System.out.println("Looking up implementors of " + superclassName);
        }
        findSubclasses(access, toRegister, processed, superclass);
    }

    @SuppressWarnings("unchecked")
    private void processAnnotated(FeatureImpl.BeforeAnalysisAccessImpl access,
                                  Set<Class<?>> toRegister,
                                  Set<Class<?>> processed,
                                  String annotationClassName) {
        Class<? extends Annotation> annotationClass = (Class<? extends Annotation>) access.findClassByName(annotationClassName);

        if (null == annotationClass) {
            if (TRACE_PARSING) {
                System.out.println("Annotation " + annotationClassName + " is not on classpath");
            }
            return;
        }
        if (TRACE_PARSING) {
            System.out.println("Looking up annotated by " + annotationClassName);
        }
        List<Class<?>> annotatedList = access.findAnnotatedClasses(annotationClass);

        processClasses(access, toRegister, processed, annotatedList);
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

            if (TRACE_PARSING) {
                System.out.println("    " + aClass.getName());
            }
            int modifiers = aClass.getModifiers();
            if (!Modifier.isInterface(modifiers)) {
                if (TRACE_PARSING) {
                    System.out.println("        Added for registration");
                }
                toRegister.add(aClass);
            }

            if (!Modifier.isFinal(modifiers)) {
                findSubclasses(access, toRegister, processed, aClass);
            }
        }
    }
}
