/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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

import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReaderFactory;
import javax.json.stream.JsonParsingException;

import io.helidon.common.HelidonFeatures;
import io.helidon.common.LogConfig;
import io.helidon.common.Reflected;
import io.helidon.config.mp.MpConfigProviderResolver;

import com.oracle.svm.core.jdk.Resources;
import com.oracle.svm.core.jdk.proxy.DynamicProxyRegistry;
import io.github.classgraph.BaseTypeSignature;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassRefTypeSignature;
import io.github.classgraph.FieldInfo;
import io.github.classgraph.MethodParameterInfo;
import io.github.classgraph.ReferenceTypeSignature;
import io.github.classgraph.ScanResult;
import io.github.classgraph.TypeArgument;
import io.github.classgraph.TypeSignature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

/**
 * Feature to add reflection configuration to the image for Helidon, CDI and Jersey.
 * Override the one in dependencies (native-image-extension from Helidon)
 */
public class HelidonReflectionFeature implements Feature {
    private static final boolean ENABLED = NativeConfig.option("reflection.enable-feature", true);
    private static final boolean TRACE_PARSING = NativeConfig.option("reflection.trace-parsing", false);
    private static final boolean TRACE = NativeConfig.option("reflection.trace", false);

    private static final String AT_ENTITY = "javax.persistence.Entity";
    private static final String AT_MAPPED_SUPERCLASS = "javax.persistence.MappedSuperclass";
    private static final String AT_REGISTER_REST_CLIENT = "org.eclipse.microprofile.rest.client.inject.RegisterRestClient";

    private static final Map<Class<?>, Class<?>> PRIMITIVES_TO_OBJECT = new HashMap<>();

    static {
        PRIMITIVES_TO_OBJECT.put(byte.class, Byte.class);
        PRIMITIVES_TO_OBJECT.put(char.class, Character.class);
        PRIMITIVES_TO_OBJECT.put(double.class, Double.class);
        PRIMITIVES_TO_OBJECT.put(float.class, Float.class);
        PRIMITIVES_TO_OBJECT.put(int.class, Integer.class);
        PRIMITIVES_TO_OBJECT.put(long.class, Long.class);
        PRIMITIVES_TO_OBJECT.put(short.class, Short.class);
        PRIMITIVES_TO_OBJECT.put(boolean.class, Boolean.class);
        PRIMITIVES_TO_OBJECT.put(void.class, Void.class);
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return ENABLED;
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        // need the application classloader
        Class<?> logConfigClass = access.findClassByName(LogConfig.class.getName());
        ClassLoader classLoader = logConfigClass.getClassLoader();

        // initialize logging (if on classpath)
        try {
            logConfigClass.getMethod("initClass")
                    .invoke(null);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // make sure we print all the warnings for native image
        HelidonFeatures.nativeBuildTime(classLoader);

        // load configuration
        HelidonReflectionConfiguration config = loadConfiguration(access, classLoader);

        // classpath scanning using the correct classloader
        ScanResult scan = new ClassGraph()
                .overrideClassLoaders(classLoader)
                .enableAllInfo()
                .scan();

        // create context (to know what was processed and what should be registered)
        BeforeAnalysisContext context = new BeforeAnalysisContext(access, scan, config.excluded);

        // process each configured annotation
        config.annotations().forEach(it -> processAnnotated(context, it));
        // process each configured interface or class
        config.hierarchy().forEach(it -> processClassHierarchy(context, it));
        // process each configured class
        config.classes().forEach(it -> addSingleClass(context, it));

        // rest client registration (proxy support)
        processRegisterRestClient(context);

        // JPA Entity registration
        processEntity(context);

        // all classes, fields and methods annotated with @Reflected
        addAnnotatedWithReflected(context);

        // JAX-RS types required for headers, query params etc.
        addJaxRsConversions(context);

        // and finally register with native image
        registerForReflection(context);
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        MpConfigProviderResolver.buildTimeEnd();
    }

    private List<Class<?>> findSubclasses(BeforeAnalysisContext context, String className) {
        ScanResult scan = context.scan();
        ClassInfo superclass = scan.getClassInfo(className);

        if (null == superclass) {
            traceParsing(() -> "Class " + className + " is not on classpath, cannot find subclasses.");
            return List.of();
        }

        List<Class<?>> subclasses = scan
                .getSubclasses(className)
                .stream()
                .map(classInfo -> context.access().findClassByName(classInfo.getName()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (superclass.isInterface()) {
            List<Class<?>> implementations = scan
                    .getClassesImplementing(className)
                    .stream()
                    .map(classInfo -> context.access().findClassByName(classInfo.getName()))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            List<Class<?>> result = new ArrayList<>(subclasses);
            result.addAll(implementations);
            return result;
        } else {
            return subclasses;
        }

    }

    private List<Class<?>> findAnnotated(BeforeAnalysisContext context, String annotation) {
        return context.scan()
                .getClassesWithAnnotation(annotation)
                .stream()
                .map(classInfo -> {
                    Class<?> clazz = null;
                    try {
                        clazz = classInfo.loadClass();
                    } catch (Throwable e) {
                        traceParsing(() -> "Class " + classInfo.getName() + " annotated by " + annotation + " cannot be loaded");
                        traceParsing(() -> "\tException class: " + e.getClass().getName() + ", message: " + e.getMessage());
                    }

                    return clazz;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private void addJaxRsConversions(BeforeAnalysisContext context) {
        addJaxRsConversions(context, "javax.ws.rs.QueryParam");
        addJaxRsConversions(context, "javax.ws.rs.PathParam");
        addJaxRsConversions(context, "javax.ws.rs.HeaderParam");
        addJaxRsConversions(context, "javax.ws.rs.MatrixParam");
        addJaxRsConversions(context, "javax.ws.rs.BeanParam");
    }

    private void addJaxRsConversions(BeforeAnalysisContext context, String annotation) {
        traceParsing(() -> "Looking up annotated by " + annotation);

        Set<Class<?>> allTypes = new HashSet<>();

        // we need fields and method parameters
        context.scan()
                .getClassesWithFieldAnnotation(annotation)
                .stream()
                .flatMap(theClass -> theClass.getFieldInfo().stream())
                .filter(field -> field.hasAnnotation(annotation))
                .map(fieldInfo -> getSimpleType(context, fieldInfo))
                .filter(Objects::nonNull)
                .forEach(allTypes::add);

        // method annotations
        context.scan()
                .getClassesWithMethodParameterAnnotation(annotation)
                .stream()
                .flatMap(theClass -> theClass.getMethodInfo().stream())
                .flatMap(theMethod -> Stream.of(theMethod.getParameterInfo()))
                .filter(param -> param.hasAnnotation(annotation))
                .map(param -> getSimpleType(context, param))
                .filter(Objects::nonNull)
                .forEach(allTypes::add);

        // now let's find all static methods `valueOf` and `fromString`
        for (Class<?> type : allTypes) {
            try {
                Method valueOf = type.getDeclaredMethod("valueOf", String.class);
                RuntimeReflection.register(valueOf);
                traceParsing(() -> "Registering " + valueOf);
            } catch (NoSuchMethodException ignored) {
                try {
                    Method fromString = type.getDeclaredMethod("fromString", String.class);
                    RuntimeReflection.register(fromString);
                    traceParsing(() -> "Registering " + fromString);
                } catch (NoSuchMethodException ignored2) {
                }
            }
        }
    }

    private Class<?> getSimpleType(BeforeAnalysisContext context, MethodParameterInfo paramInfo) {
        return getSimpleType(context, paramInfo::getTypeSignature, paramInfo::getTypeDescriptor);
    }

    private Class<?> getSimpleType(BeforeAnalysisContext context, FieldInfo fieldInfo) {
        return getSimpleType(context, fieldInfo::getTypeSignature, fieldInfo::getTypeDescriptor);
    }

    private Class<?> getSimpleType(BeforeAnalysisContext context,
                                   Supplier<TypeSignature> typeSignatureSupplier,
                                   Supplier<TypeSignature> typeDescriptorSupplier) {
        TypeSignature typeSignature = typeSignatureSupplier.get();
        if (typeSignature == null) {
            // not a generic type
            TypeSignature typeDescriptor = typeDescriptorSupplier.get();
            return getSimpleType(context, typeDescriptor);
        }

        if (typeSignature instanceof ClassRefTypeSignature) {
            ClassRefTypeSignature refType = (ClassRefTypeSignature) typeSignature;
            List<TypeArgument> typeArguments = refType.getTypeArguments();
            if (typeArguments.size() == 1) {
                TypeArgument typeArgument = typeArguments.get(0);
                ReferenceTypeSignature ref = typeArgument.getTypeSignature();
                return getSimpleType(context, ref);
            }
        }
        return getSimpleType(context, typeSignature);
    }

    private Class<?> getSimpleType(BeforeAnalysisContext context, TypeSignature typeSignature) {
        // this is the type used
        // may be: array, primitive type
        if (typeSignature instanceof BaseTypeSignature) {
            // primitive types
            BaseTypeSignature bts = (BaseTypeSignature) typeSignature;
            return toObjectType(bts.getType());
        }
        if (typeSignature instanceof ClassRefTypeSignature) {
            ClassRefTypeSignature crts = (ClassRefTypeSignature) typeSignature;
            return context.access().findClassByName(crts.getFullyQualifiedClassName());
        }

        return Object.class;
    }

    private static Class<?> toObjectType(Class<?> primitiveClass) {
        Class<?> type = PRIMITIVES_TO_OBJECT.get(primitiveClass);

        if (type == null) {
            traceParsing(() -> "Failed to understand primitive type: " + primitiveClass);
            type = Object.class;
        }
        return type;
    }

    private void addAnnotatedWithReflected(BeforeAnalysisContext context) {
        // want to make sure we use the correct classloader
        String annotation = Reflected.class.getName();

        traceParsing(() -> "Looking up annotated by " + annotation);

        // all annotated classes
        findAnnotated(context, annotation)
                .forEach(it -> {
                    if (context.isExcluded(it)) {
                        traceParsing(() -> " class " + it.getName() + " annotated by " + annotation + " is excluded.");
                    } else {
                        traceParsing(() -> " class " + it.getName());
                        context.register(it).addAll();
                    }
                });

        // all annotated methods and constructors
        context.scan()
                .getClassesWithMethodAnnotation(annotation)
                .forEach(it -> {
                    it.getMethodAndConstructorInfo().forEach(method -> {
                        if (method.hasAnnotation(annotation)) {
                            Class<?> clazz = it.loadClass();
                            if (method.isConstructor()) {
                                try {
                                    context.register(clazz).add(method.loadClassAndGetConstructor());
                                } catch (Exception e) {
                                    traceParsing(() -> "Failed to load constructor " + method);
                                    if (TRACE_PARSING) {
                                        e.printStackTrace();
                                    }
                                }
                            } else {
                                try {
                                    context.register(clazz).add(method.loadClassAndGetMethod());
                                } catch (Exception e) {
                                    traceParsing(() -> "Failed to load method " + method);
                                    if (TRACE_PARSING) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        }
                    });
                });

        // fields
        context.scan()
                .getClassesWithFieldAnnotation(annotation)
                .forEach(it -> {
                    it.getFieldInfo().forEach(field -> {
                        if (field.hasAnnotation(annotation)) {
                            try {
                                context.register(it.loadClass()).add(field.loadClassAndGetField());
                            } catch (Exception e) {
                                traceParsing(() -> "Failed to load field " + field);
                                if (TRACE_PARSING) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    });
                });
    }

    @SuppressWarnings("unchecked")
    private void processEntity(BeforeAnalysisContext context) {
        final Class<? extends Annotation> entityAnnotation = (Class<? extends Annotation>) context.access()
                .findClassByName(AT_ENTITY);
        final Class<? extends Annotation> superclassAnnotation = (Class<? extends Annotation>) context.access()
                .findClassByName(AT_MAPPED_SUPERCLASS);
        Set<Class<?>> annotatedSet = null;
        traceParsing(() -> "Looking up annotated by " + AT_ENTITY);
        if (entityAnnotation != null) {
            annotatedSet = new HashSet<>(findAnnotated(context, AT_ENTITY));
        }
        traceParsing(() -> "Looking up annotated by " + AT_MAPPED_SUPERCLASS);
        if (superclassAnnotation != null) {
            if (annotatedSet == null) {
                annotatedSet = new HashSet<>(findAnnotated(context, AT_MAPPED_SUPERCLASS));
            } else {
                annotatedSet.addAll(findAnnotated(context, AT_MAPPED_SUPERCLASS));
            }
        }
        if (annotatedSet == null || annotatedSet.isEmpty()) {
            return;
        }
        annotatedSet.forEach(aClass -> {
            traceParsing(() -> "Processing annotated class " + aClass.getName());
            String resourceName = aClass.getName().replace('.', '/') + ".class";
            InputStream resourceStream = aClass.getClassLoader().getResourceAsStream(resourceName);
            Resources.registerResource(resourceName, resourceStream);
            for (Field declaredField : aClass.getDeclaredFields()) {
                if (!Modifier.isPublic(declaredField.getModifiers()) && declaredField.getAnnotations().length == 0) {
                    RuntimeReflection.register(declaredField);
                    traceParsing(() -> "    added non annotated field " + declaredField);
                }
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void processRegisterRestClient(BeforeAnalysisContext context) {

        Class<? extends Annotation> restClientAnnotation = (Class<? extends Annotation>) context.access()
                .findClassByName(AT_REGISTER_REST_CLIENT);

        if (null == restClientAnnotation) {
            return;
        }

        traceParsing(() -> "Looking up annotated by " + AT_REGISTER_REST_CLIENT);

        List<Class<?>> annotatedList = findAnnotated(context, AT_REGISTER_REST_CLIENT);
        DynamicProxyRegistry proxyRegistry = ImageSingletons.lookup(DynamicProxyRegistry.class);
        Class<?> autoCloseable = context.access().findClassByName("java.lang.AutoCloseable");
        Class<?> closeable = context.access().findClassByName("java.io.Closeable");

        annotatedList.forEach(it -> {
            if (context.isExcluded(it)) {
                traceParsing(() -> "Class " + it.getName() + " annotated by " + AT_REGISTER_REST_CLIENT + " is excluded");
            } else {
                // we need to add it for reflection
                processClassHierarchy(context, it);
                // and we also need to create a proxy
                traceParsing(() -> "Registering a proxy for class " + it.getName());
                proxyRegistry.addProxyClass(it, autoCloseable, closeable);
            }
        });
    }

    private void registerForReflection(BeforeAnalysisContext context) {
        Collection<Register> toRegister = context.toRegister();

        if (TRACE) {
            System.out.println("***********************************");
            System.out.println("** Registering " + toRegister.size() + " classes for reflection");
            System.out.println("***********************************");
        }

        // register for reflection
        for (Register register : toRegister) {
            // first validate if all fields are on classpath
            if (!register.validated) {
                register.validate();
            }
            // only register classes on the image classpath (not necessarily discovered by the scanning)
            if (register.valid) {
                register(register.clazz);

                if (!register.clazz.isInterface()) {
                    register.fields.forEach(this::register);
                    register.constructors.forEach(this::register);
                }

                register.methods.forEach(this::register);
            } else {
                if (TRACE) {
                    System.out.println(register.clazz.getName() + " is not registered, as it had failed fields or superclass.");
                }
            }
        }
    }

    private void register(Constructor<?> constructor) {
        if (TRACE) {
            System.out.println("    " + constructor.getDeclaringClass().getSimpleName()
                                       + "("
                                       + params(constructor.getParameterTypes())
                                       + ")");
        }
        RuntimeReflection.register(constructor);
    }

    private String params(Class<?>[] parameterTypes) {
        if (parameterTypes.length == 0) {
            return "";
        }
        return Arrays.stream(parameterTypes)
                .map(Class::getName)
                .collect(Collectors.joining(", "));
    }

    private void register(Field field) {
        if (TRACE) {
            System.out.println("    " + field.getType() + " " + field.getName());
        }

        RuntimeReflection.register(field);
    }

    private void register(Method method) {
        if (TRACE) {
            System.out.println("    " + method.getReturnType().getName() + " " + method
                    .getName() + "(" + params(method.getParameterTypes()) + ")");
        }
        RuntimeReflection.register(method);
    }

    private void register(Class<?> clazz) {
        if (TRACE) {
            System.out.println("Registering " + clazz.getName() + " for reflection");
        }

        RuntimeReflection.register(clazz);
    }

    private static boolean hasParams(Method method, Class<?>... params) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        return Arrays.equals(params, parameterTypes);
    }

    private void addSingleClass(BeforeAnalysisContext context,
                                Class<?> theClass) {
        if (context.process(theClass)) {
            traceParsing(theClass::getName);
            traceParsing(() -> "  Added for registration");
            superclasses(context, theClass);
            context.register(theClass).addDefaults();
        }
    }

    private void processClassHierarchy(BeforeAnalysisContext context,
                                       Class<?> superclass) {

        // this class is always registered (interface or class)
        context.register(superclass).addDefaults();

        traceParsing(() -> "Looking up implementors of " + superclass.getName());

        findSubclasses(context, superclass);
        for (Class<?> anInterface : superclass.getInterfaces()) {
            // unless excluded
            if (context.isExcluded(anInterface)) {
                traceParsing(() -> "  Interface " + anInterface.getName() + " is explicitly excluded");
            } else {
                addSingleClass(context, anInterface);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void processAnnotated(BeforeAnalysisContext context,
                                  Class<?> annotationClass) {

        Class<? extends Annotation> annotation;
        try {
            annotation = (Class<? extends Annotation>) annotationClass;
        } catch (ClassCastException e) {
            throw new IllegalStateException("Class configured as annotation is not an annotation: " + annotationClass.getName(),
                                            e);
        }

        traceParsing(() -> "Looking up annotated by " + annotation.getName());

        List<Class<?>> annotatedList = findAnnotated(context, annotationClass.getName());

        annotatedList.forEach(it -> {
            if (context.isExcluded(it)) {
                traceParsing(() -> "Class " + it.getName() + " annotated by " + annotationClass.getName() + " is excluded");
            } else {
                processClassHierarchy(context, it);
            }
        });
    }

    private void findSubclasses(BeforeAnalysisContext context, Class<?> aClass) {
        List<Class<?>> subclasses = findSubclasses(context, aClass.getName());

        processClasses(context, subclasses);
    }

    private void processClasses(BeforeAnalysisContext context, List<Class<?>> classes) {
        for (Class<?> aClass : classes) {
            if (context.process(aClass)) {
                if (context.isExcluded(aClass)) {
                    traceParsing(() -> "    Excluding " + aClass.getName() + " from registration");
                    continue;
                }

                traceParsing(() -> "    " + aClass.getName());

                int modifiers = aClass.getModifiers();

                traceParsing(() -> "        Added for registration");

                superclasses(context, aClass);
                context.register(aClass).addDefaults();

                if (!Modifier.isFinal(modifiers)) {
                    findSubclasses(context, aClass);
                }
            }
        }
    }

    private void superclasses(BeforeAnalysisContext context, Class<?> aClass) {
        Class<?> nextSuper = aClass.getSuperclass();
        while (null != nextSuper) {
            if (context.process(nextSuper)) {
                if (context.isExcluded(nextSuper)) {
                    Class<?> toLog = nextSuper;
                    traceParsing(() -> "  Class " + toLog.getName() + " is explicitly excluded");
                    nextSuper = null;
                } else {
                    traceParsing(nextSuper::getName);
                    traceParsing(() -> "  Added for registration");

                    context.register(nextSuper).addDefaults();
                    nextSuper = nextSuper.getSuperclass();
                }
            } else {
                nextSuper = nextSuper.getSuperclass();
            }
        }
    }

    private HelidonReflectionConfiguration loadConfiguration(BeforeAnalysisAccess access, ClassLoader cl) {
        try {
            Enumeration<URL> resources = cl.getResources("META-INF/helidon/native-image/reflection-config.json");
            HelidonReflectionConfiguration config = new HelidonReflectionConfiguration();
            JsonReaderFactory readerFactory = Json.createReaderFactory(Map.of());
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                try {
                    JsonObject configurationJson = readerFactory.createReader(url.openStream()).readObject();
                    jsonArray(access, config.annotations, configurationJson.getJsonArray("annotated"), "Annotation");
                    jsonArray(access, config.hierarchy, configurationJson.getJsonArray("class-hierarchy"), "Class hierarchy");
                    jsonArray(access, config.classes, configurationJson.getJsonArray("classes"), "Single");
                    jsonArray(access, config.excluded, configurationJson.getJsonArray("exclude"), "Exclude");
                } catch (JsonParsingException e) {
                    System.err.println("Failed to process configuration file: " + url);
                    throw e;
                }
            }

            return config;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to process configuration from helidon-reflection-config.json files", e);
        }
    }

    private void jsonArray(BeforeAnalysisAccess access, Collection<Class<?>> classList, JsonArray classNames, String desc) {
        if (null == classNames) {
            return;
        }
        for (int i = 0; i < classNames.size(); i++) {
            String className = classNames.getString(i);
            boolean isArray = false;
            if (className.endsWith("[]")) {
                // an array
                isArray = true;
                className = className.substring(0, className.length() - 2);
            }
            Class<?> clazz = access.findClassByName(className);
            if (null == clazz) {
                final String logName = className;
                traceParsing(() -> desc + " class \"" + logName + "\" configured for reflection is not on classpath");
                continue;
            } else {
                classList.add(clazz);
            }

            if (isArray) {
                Object anArray = Array.newInstance(clazz, 0);
                classList.add(anArray.getClass());
            }
        }
    }

    private static void traceParsing(Supplier<String> message) {
        if (TRACE_PARSING) {
            System.out.println(message.get());
        }
    }

    private static final class HelidonReflectionConfiguration {
        private final List<Class<?>> annotations = new LinkedList<>();
        private final List<Class<?>> hierarchy = new LinkedList<>();
        private final List<Class<?>> classes = new LinkedList<>();
        private final Set<Class<?>> excluded = new HashSet<>();

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
        private final BeforeAnalysisAccess access;
        private ScanResult scan;
        private final Set<Class<?>> processed = new HashSet<>();
        private final Set<Class<?>> excluded = new HashSet<>();
        private final Map<Class<?>, Register> registers = new HashMap<>();

        private BeforeAnalysisContext(BeforeAnalysisAccess access, ScanResult scan, Set<Class<?>> excluded) {
            this.access = access;
            this.scan = scan;
            this.excluded.addAll(excluded);
        }

        BeforeAnalysisAccess access() {
            return access;
        }

        ScanResult scan() {
            return scan;
        }

        public boolean process(Class<?> theClass) {
            return processed.add(theClass);
        }

        public Register register(Class<?> theClass) {
            return registers.computeIfAbsent(theClass, Register::new);
        }

        public Collection<Register> toRegister() {
            return registers.values();
        }

        boolean isExcluded(Class<?> theClass) {
            return excluded.contains(theClass);
        }
    }

    private static class Register {
        private final Set<Method> methods = new HashSet<>();
        private final Set<Field> fields = new HashSet<>();
        private final Set<Constructor<?>> constructors = new HashSet<>();

        private final Class<?> clazz;

        private boolean validated;
        private boolean valid = true;

        private Register(Class<?> clazz) {
            this.clazz = clazz;
        }

        void validate() {
            validated = true;
            validateTypeParams();
            if (!valid) {
                return;
            }
            addFields(true, true);
        }

        boolean add(Method m) {
            return methods.add(m);
        }

        boolean add(Field f) {
            return fields.add(f);
        }

        boolean add(Constructor<?> c) {
            return constructors.add(c);
        }

        void addAll() {
            validated = true;
            validateTypeParams();
            if (!valid) {
                return;
            }
            addFields(true, false);
            if (!valid) {
                return;
            }
            addMethods();
            if (clazz.isInterface()) {
                return;
            }
            addConstructors();
        }

        void addDefaults() {
            validated = true;
            validateTypeParams();
            if (!valid) {
                return;
            }
            addFields(false, false);
            if (!valid) {
                return;
            }
            addMethods();
            if (clazz.isInterface()) {
                return;
            }
            addConstructors();
        }

        private void validateTypeParams() {
            try {
                clazz.getGenericSuperclass();
            } catch (Exception e) {
                System.out.println("Type parameter of superclass is not on classpath of "
                                           + clazz.getName()
                                           + " error: "
                                           + e.getMessage());
                valid = false;
            }
        }

        private void addConstructors() {
            try {
                Constructor<?>[] constructors = clazz.getConstructors();
                for (Constructor<?> constructor : constructors) {
                    add(constructor);
                }
            } catch (NoClassDefFoundError e) {
                if (TRACE) {
                    System.out.println("Public constructors of "
                                               + clazz.getName()
                                               + " not added to reflection, as a type is not on classpath: "
                                               + e.getMessage());
                }
            }
            try {
                // add all declared
                Constructor<?>[] constructors = clazz.getDeclaredConstructors();
                for (Constructor<?> constructor : constructors) {
                    add(constructor);
                }
            } catch (NoClassDefFoundError e) {
                if (TRACE) {
                    System.out.println("Constructors of "
                                               + clazz.getName()
                                               + " not added to reflection, as a type is not on classpath: "
                                               + e.getMessage());
                }
            }
        }

        void addFields(boolean all, boolean validateOnly) {
            try {
                Field[] fields = clazz.getFields();
                // add all public fields
                for (Field field : fields) {
                    if (!validateOnly) {
                        add(field);
                    }
                }
            } catch (NoClassDefFoundError e) {
                this.valid = false;
                if (TRACE) {
                    if (validateOnly) {
                        System.out.println("Validation of fields of "
                                                   + clazz.getName()
                                                   + " failed, as a type is not on classpath: "
                                                   + e.getMessage());
                    } else {
                        System.out.println("Public fields of "
                                                   + clazz.getName()
                                                   + " not added to reflection, as a type is not on classpath: "
                                                   + e.getMessage());
                    }
                }
            }
            try {
                for (Field declaredField : clazz.getDeclaredFields()) {
                    // there may be fields referencing classes not on the classpath
                    if (!Modifier.isPublic(declaredField.getModifiers())) {
                        // public already registered
                        if (all || declaredField.getAnnotations().length > 0) {
                            if (!validateOnly) {
                                add(declaredField);
                            }
                        }
                    }
                }
            } catch (NoClassDefFoundError e) {
                this.valid = false;
                if (TRACE) {
                    if (validateOnly) {
                        System.out.println("Validation of fields of "
                                                   + clazz.getName()
                                                   + " failed, as a type is not on classpath: "
                                                   + e.getMessage());
                    } else {
                        System.out.println("Fields of "
                                                   + clazz.getName()
                                                   + " not added to reflection, as a type is not on classpath: "
                                                   + e.getMessage());
                    }
                }
            }
        }

        void addMethods() {
            try {
                Method[] methods = clazz.getMethods();
                for (Method method : methods) {
                    boolean register;

                    // we do not want wait, notify etc
                    register = (method.getDeclaringClass() != Object.class);

                    if (register) {
                        // we do not want toString(), hashCode(), equals(java.lang.Object)
                        switch (method.getName()) {
                        case "hashCode":
                        case "toString":
                            register = !hasParams(method);
                            break;
                        case "equals":
                            register = !hasParams(method, Object.class);
                            break;
                        default:
                            // do nothing
                        }
                    }

                    if (register) {
                        if (TRACE) {
                            System.out.println("  " + method.getName() + "(" + Arrays.toString(method.getParameterTypes()) + ")");
                        }
                        add(method);
                    }
                }
            } catch (Throwable e) {
                if (TRACE) {
                    System.out
                            .println("   Cannot register methods of " + clazz.getName() + ": " + e.getClass().getName() + ": " + e
                                    .getMessage());
                }
            }
        }
    }
}
