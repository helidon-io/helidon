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

package io.helidon.integrations.graal.nativeimage.extension;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.github.classgraph.BaseTypeSignature;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassRefTypeSignature;
import io.github.classgraph.FieldInfo;
import io.github.classgraph.MethodParameterInfo;
import io.github.classgraph.ReferenceTypeSignature;
import io.github.classgraph.Resource;
import io.github.classgraph.ResourceList;
import io.github.classgraph.ScanResult;
import io.github.classgraph.TypeArgument;
import io.github.classgraph.TypeSignature;

/**
 * Utilities to help with discovery and analysis of the image.
 */
public final class NativeUtil {
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

    private final NativeTrace tracer;
    private final ScanResult scan;
    private final Function<String, Class<?>> classResolver;
    private final Function<Class<?>, Boolean> exclusion;

    NativeUtil(NativeTrace tracer,
               ScanResult scan,
               Function<String, Class<?>> classResolver,
               Function<Class<?>, Boolean> exclusion) {
        this.tracer = tracer;
        this.scan = scan;
        this.classResolver = classResolver;
        this.exclusion = exclusion;
    }

    /**
     * Create a new instance.
     *
     * @param tracer        tracer to log messages
     * @param scan          classpath scan result
     * @param classResolver resolver of class names to classes
     * @param exclusion     excluded classes
     * @return a new utility
     */
    public static NativeUtil create(NativeTrace tracer,
                                    ScanResult scan,
                                    Function<String, Class<?>> classResolver,
                                    Function<Class<?>, Boolean> exclusion) {
        return new NativeUtil(tracer, scan, classResolver, exclusion);
    }

    /**
     * Get the type of the field.
     *
     * @param classResolver resolver of names to classes
     * @param fieldInfo     field info to get type from
     * @return class of the field
     */
    public Class<?> getSimpleType(Function<String, Class<?>> classResolver, FieldInfo fieldInfo) {
        return getSimpleType(classResolver, fieldInfo::getTypeSignature, fieldInfo::getTypeDescriptor);
    }

    /**
     * Find all classes annotated with the provided annotation.
     *
     * @param annotation annotation to look for
     * @return set of annotated types
     */
    public Set<Class<?>> findAnnotated(String annotation) {
        InclusionFilter inclusionFilter = new InclusionFilter(tracer, exclusion, "annotated by " + annotation);
        ClassResolverMapper mapper = new ClassResolverMapper(tracer, classResolver, "annotated by " + annotation);

        return scan.getClassesWithAnnotation(annotation)
                .stream()
                .map(mapper)
                .filter(Objects::nonNull)
                .filter(inclusionFilter)
                .collect(Collectors.toSet());
    }

    /**
     * Filter to exclude types that are marked as excluded.
     *
     * @param description filter description
     * @return a new predicate
     */
    public Predicate<Class<?>> inclusionFilter(String description) {
        return new InclusionFilter(tracer, exclusion, description);
    }

    /**
     * Class mapper.
     *
     * @param description description of this instance
     * @return a new mapper from class names to classes
     */
    public Function<String, Class<?>> classMapper(String description) {
        return new StringClassResolverMapper(tracer, classResolver, description);
    }

    Class<?> box(Class<?> primitiveClass) {
        Class<?> type = PRIMITIVES_TO_OBJECT.get(primitiveClass);

        if (type == null) {
            tracer.parsing(() -> "Failed to understand primitive type: " + primitiveClass);
            type = Object.class;
        }
        return type;
    }

    /**
     * Get the type of the parameter.
     *
     * @param classResolver resolver of names to classes
     * @param paramInfo     parameter info to get type from
     * @return class of the parameter
     */
    public Class<?> getSimpleType(Function<String, Class<?>> classResolver, MethodParameterInfo paramInfo) {
        return getSimpleType(classResolver, paramInfo::getTypeSignature, paramInfo::getTypeDescriptor);
    }

    Class<?> getSimpleType(Function<String, Class<?>> classResolver,
                           Supplier<TypeSignature> typeSignatureSupplier,
                           Supplier<TypeSignature> typeDescriptorSupplier) {
        TypeSignature typeSignature = typeSignatureSupplier.get();
        if (typeSignature == null) {
            // not a generic type
            TypeSignature typeDescriptor = typeDescriptorSupplier.get();
            return getSimpleType(classResolver, typeDescriptor);
        }

        if (typeSignature instanceof ClassRefTypeSignature) {
            ClassRefTypeSignature refType = (ClassRefTypeSignature) typeSignature;
            List<TypeArgument> typeArguments = refType.getTypeArguments();
            if (typeArguments.size() == 1) {
                TypeArgument typeArgument = typeArguments.get(0);
                ReferenceTypeSignature ref = typeArgument.getTypeSignature();
                return getSimpleType(classResolver, ref);
            }
        }
        return getSimpleType(classResolver, typeSignature);
    }

    Class<?> getSimpleType(Function<String, Class<?>> classResolver, TypeSignature typeSignature) {
        // this is the type used
        // may be: array, primitive type
        if (typeSignature instanceof BaseTypeSignature) {
            // primitive types
            BaseTypeSignature bts = (BaseTypeSignature) typeSignature;
            return box(bts.getType());
        }
        if (typeSignature instanceof ClassRefTypeSignature) {
            ClassRefTypeSignature crts = (ClassRefTypeSignature) typeSignature;
            return classResolver.apply(crts.getFullyQualifiedClassName());
        }

        return Object.class;
    }

    @SuppressWarnings("unchecked")
    <T> Class<T> cast(Class<?> clazz, Class<T> expected) {
        try {
            return (Class<T>) clazz;
        } catch (Exception e) {
            throw new IllegalStateException("Class configured as " + expected.getName() + " is " + clazz.getName(), e);
        }
    }

    /**
     * Check if method has the required parameters.
     *
     * @param method method to check
     * @param params parameters to expect
     * @return {@code true} if parameters of the method match the expected parameters
     */
    public boolean hasParams(Method method, Class<?>... params) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        return Arrays.equals(params, parameterTypes);
    }

    /**
     * Find all superclasses of this type.
     *
     * @param aClass type
     * @return all super types
     */
    public Set<Class<?>> findSuperclasses(Class<?> aClass) {
        Set<Class<?>> result = new LinkedHashSet<>();

        Class<?> nextSuper = aClass.getSuperclass();
        while (null != nextSuper) {
            if (exclusion.apply(nextSuper)) {
                Class<?> toLog = nextSuper;
                tracer.parsing(() -> "  Class " + toLog.getName() + " is explicitly excluded");
            }

            result.add(nextSuper);
            nextSuper = nextSuper.getSuperclass();
        }

        return result;
    }

    /**
     * Get all interfaces of the type.
     *
     * @param aClass class to get interfaces for
     * @return set of interfaces with proper exclusions
     */
    public Set<Class<?>> findInterfaces(Class<?> aClass) {
        Set<Class<?>> result = new LinkedHashSet<>();

        for (Class<?> anInterface : aClass.getInterfaces()) {
            // unless excluded
            if (exclusion.apply(anInterface)) {
                tracer.parsing(() -> "  Interface " + anInterface.getName() + " is explicitly excluded");
            } else {
                result.add(anInterface);
            }
        }

        return result;
    }

    List<String> findResources(String name) {
        ResourceList allResources = scan.getResourcesWithPath(name);
        List<String> list = new ArrayList<>();
        for (Resource resource : allResources) {
            String contentAsString = null;
            try {
                contentAsString = resource.getContentAsString();
            } catch (IOException e) {
                tracer.parsing(() -> "Failed to load resource " + resource.getPath(), e);
            }
            list.add(contentAsString);
        }
        return list;
    }

    void processAnnotatedFields(String annotation, BiConsumer<Class<?>, Field> fieldProcessor) {
        InclusionFilter inclusionFilter = new InclusionFilter(tracer, exclusion, "field annotated by " + annotation);
        ClassResolverMapper mapper = new ClassResolverMapper(tracer, classResolver, "field annotated by " + annotation);

        scan.getClassesWithFieldAnnotation(annotation)
                .forEach(it -> {
                    Class<?> theClass = mapper.apply(it);
                    if (inclusionFilter.test(theClass)) {
                        it.getFieldInfo().forEach(field -> {
                            if (field.hasAnnotation(annotation)) {
                                Class<?> clazz = it.loadClass();
                                try {
                                    fieldProcessor.accept(clazz, field.loadClassAndGetField());
                                } catch (Exception e) {
                                    tracer.parsing(() -> "Failed to load field " + field, e);
                                }
                            }
                        });
                    }
                });
    }

    void processAnnotatedExecutables(String annotation,
                                     BiConsumer<Class<?>, Constructor<?>> constructorProcessor,
                                     BiConsumer<Class<?>, Method> methodProcessor) {
        InclusionFilter inclusionFilter = new InclusionFilter(tracer, exclusion, "executable annotated by " + annotation);
        ClassResolverMapper mapper = new ClassResolverMapper(tracer, classResolver, "executable annotated by " + annotation);

        scan.getClassesWithMethodAnnotation(annotation)
                .forEach(it -> {
                    Class<?> theClass = mapper.apply(it);
                    if (inclusionFilter.test(theClass)) {
                        it.getMethodAndConstructorInfo().forEach(method -> {
                            if (method.hasAnnotation(annotation)) {
                                Class<?> clazz = it.loadClass();
                                if (method.isConstructor()) {
                                    try {
                                        constructorProcessor.accept(clazz, method.loadClassAndGetConstructor());
                                    } catch (Exception e) {
                                        tracer.parsing(() -> "Failed to load constructor " + method, e);
                                    }
                                } else {
                                    try {
                                        methodProcessor.accept(clazz, method.loadClassAndGetMethod());
                                    } catch (Exception e) {
                                        tracer.parsing(() -> "Failed to load method " + method, e);
                                    }
                                }
                            }
                        });
                    }
                });
    }

    /**
     * Find all direct subclasses of the provided type.
     *
     * @param superclassName type
     * @return subclasses
     */
    public Set<Class<?>> findSubclasses(String superclassName) {
        ClassInfo superclass = scan.getClassInfo(superclassName);

        if (null == superclass) {
            tracer.parsing(() -> "Class " + superclassName + " is not on classpath, cannot find subclasses.");
            return Set.of();
        }

        InclusionFilter inclusionFilter = new InclusionFilter(tracer, exclusion, "subclass of " + superclassName);
        ClassResolverMapper mapper = new ClassResolverMapper(tracer, classResolver, "subclass of " + superclassName);

        Set<Class<?>> subclasses = scan
                .getSubclasses(superclassName)
                .stream()
                .map(mapper)
                .filter(inclusionFilter)
                .collect(Collectors.toSet());

        if (superclass.isInterface()) {
            inclusionFilter = new InclusionFilter(tracer, exclusion, "implementation of " + superclassName);
            mapper = new ClassResolverMapper(tracer, classResolver, "implementation of " + superclassName);
            Set<Class<?>> implementations = scan
                    .getClassesImplementing(superclassName)
                    .stream()
                    .map(mapper)
                    .filter(inclusionFilter)
                    .collect(Collectors.toSet());

            Set<Class<?>> result = new HashSet<>(subclasses);
            result.addAll(implementations);
            return result;
        } else {
            return subclasses;
        }
    }

    private static class InclusionFilter implements Predicate<Class<?>> {
        private final NativeTrace tracer;
        private final Function<Class<?>, Boolean> exclusion;
        private final String message;

        InclusionFilter(NativeTrace tracer, Function<Class<?>, Boolean> exclusion, String message) {
            this.tracer = tracer;
            this.exclusion = exclusion;
            this.message = message;
        }

        @Override
        public boolean test(Class<?> aClass) {
            if (aClass == null) {
                return false;
            }
            if (exclusion.apply(aClass)) {
                tracer.parsing(() -> " class " + aClass.getName() + " " + message + " is excluded.");
                return false;
            }
            return true;
        }
    }

    private static class StringClassResolverMapper implements Function<String, Class<?>> {
        private final NativeTrace tracer;
        private final Function<String, Class<?>> classResolver;
        private final String message;

        StringClassResolverMapper(NativeTrace tracer, Function<String, Class<?>> classResolver, String message) {
            this.tracer = tracer;
            this.classResolver = classResolver;
            this.message = message;
        }

        @Override
        public Class<?> apply(String className) {
            Class<?> clazz = classResolver.apply(className);
            if (clazz == null) {
                tracer.parsing(() -> " class " + className + " " + message + " is not on classpath.");
            }
            return clazz;
        }
    }

    private static class ClassResolverMapper implements Function<ClassInfo, Class<?>> {
        private final NativeTrace tracer;
        private final Function<String, Class<?>> classResolver;
        private final String message;

        ClassResolverMapper(NativeTrace tracer, Function<String, Class<?>> classResolver, String message) {
            this.tracer = tracer;
            this.classResolver = classResolver;
            this.message = message;
        }

        @Override
        public Class<?> apply(ClassInfo classInfo) {
            Class<?> clazz = classResolver.apply(classInfo.getName());
            if (clazz == null) {
                tracer.parsing(() -> " class " + classInfo.getName() + " " + message + " is not on classpath.");
            }
            return clazz;
        }
    }
}
