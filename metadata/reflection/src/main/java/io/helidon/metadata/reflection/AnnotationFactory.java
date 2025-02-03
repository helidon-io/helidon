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

package io.helidon.metadata.reflection;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;

/**
 * Reflection based handler of {@link io.helidon.common.types.Annotation} and {@link java.lang.annotation.Annotation}.
 */
public final class AnnotationFactory {
    private AnnotationFactory() {
    }

    /**
     * Create Helidon annotations from Java annotated element (method, field, class).
     *
     * @param annotated annotated element to analyze
     * @return list of annotation instances
     */
    public static List<Annotation> create(AnnotatedElement annotated) {
        List<Annotation> annotations = new ArrayList<>();
        java.lang.annotation.Annotation[] declared = annotated.getDeclaredAnnotations();
        for (java.lang.annotation.Annotation annotation : declared) {
            annotations.add(create(annotation));
        }
        return List.copyOf(annotations);
    }

    /**
     * Create a Helidon annotation from Java annotation instance.
     *
     * @param annotation annotation to analyze
     * @return annotation instance
     */
    public static Annotation create(java.lang.annotation.Annotation annotation) {
        TypeName type = TypeName.create(annotation.annotationType());
        var set = new HashSet<TypeName>();
        set.add(TypeNames.INHERITED);
        set.add(TypeNames.TARGET);
        set.add(TypeNames.RETENTION);
        set.add(TypeNames.DOCUMENTED);
        set.remove(type);

        // it must return, as we removed our type from the processed type
        return createAnnotation(annotation, set)
                .orElseThrow();

    }

    /**
     * Creates an instance of the provided annotation using reflection (and proxying).
     * In case the annotation is not on the classpath, returns an empty optional.
     *
     * @param annotation annotation to materialize
     * @param <T>        type of the annotation
     * @return annotation instance, or empty option in case the annotation type is not on classpath
     */
    @SuppressWarnings("unchecked")
    public static <T extends java.lang.annotation.Annotation> Optional<T> synthesize(Annotation annotation) {
        Class<java.lang.annotation.Annotation> annotationType;
        try {
            annotationType = (Class<java.lang.annotation.Annotation>) TypeFactory.toClass(annotation.typeName());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        // type is available, we can synthesize the annotation
        return Optional.of((T) Proxy.newProxyInstance(classLoader(),
                                                      new Class[] {annotationType},
                                                      new AnnotationInvocationHandler(annotationType, annotation)));
    }

    private static ClassLoader classLoader() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            return loader;
        }
        return AnnotationFactory.class.getClassLoader();
    }

    // basically the same semantics as in `AptAnnotationFactory` (and scan based annotation factory)
    private static Optional<Annotation> createAnnotation(java.lang.annotation.Annotation annotation,
                                                         Set<TypeName> processedTypes) {
        TypeName type = TypeName.create(annotation.annotationType());

        if (processedTypes.contains(type)) {
            // this was already processed when handling this annotation, no need to add it
            return Optional.empty();
        }

        Annotation.Builder builder = Annotation.builder();

        Stream.of(annotation.annotationType()
                          .getDeclaredAnnotations())
                .map(it -> {
                    var newProcessed = new HashSet<>(processedTypes);
                    newProcessed.add(type);
                    return createAnnotation(it, newProcessed);
                })
                .flatMap(Optional::stream)
                .forEach(builder::addMetaAnnotation);

        return Optional.of(builder
                                   .typeName(type)
                                   .values(extractAnnotationValues(annotation))
                                   .build());
    }

    private static List<Method> valueMethods(Class<? extends java.lang.annotation.Annotation> annotationType) {
        return Stream.of(annotationType.getDeclaredMethods())
                .filter(it -> Modifier.isPublic(it.getModifiers()))
                .collect(Collectors.toUnmodifiableList());
    }

    private static Map<String, Object> extractAnnotationValues(java.lang.annotation.Annotation annotation) {
        Map<String, Object> result = new LinkedHashMap<>();

        valueMethods(annotation.annotationType())
                .forEach(method -> {
                    String name = method.getName();
                    Object value;
                    try {
                        value = method.invoke(annotation);
                    } catch (Exception e) {
                        throw new IllegalStateException("Failed to invoke annotation method, cannot analyze it", e);
                    }
                    if (value instanceof java.lang.annotation.Annotation annot) {
                        result.put(name, create(annot));
                    } else if (value.getClass().isArray()) {
                        Class<?> componentType = value.getClass().getComponentType();
                        if (componentType.isPrimitive()) {
                            result.put(name, toPrimitiveArray(componentType, value));
                        } else if (java.lang.annotation.Annotation.class.isAssignableFrom(componentType)) {
                            result.put(name, Stream.of((java.lang.annotation.Annotation[]) value)
                                    .map(AnnotationFactory::create)
                                    .collect(Collectors.toUnmodifiableList()));
                        } else {
                            result.put(name, List.of((Object[]) value));
                        }
                    } else {
                        result.put(name, value);
                    }
                });

        return result;
    }

    private static Object toPrimitiveArray(Class<?> componentType, Object value) {
        if (componentType.equals(int.class)) {
            return Arrays.stream((int[]) value).boxed().collect(Collectors.toUnmodifiableList());
        } else if (componentType.equals(long.class)) {
            return Arrays.stream((long[]) value).boxed().collect(Collectors.toUnmodifiableList());
        } else if (componentType.equals(double.class)) {
            return Arrays.stream((double[]) value).boxed().collect(Collectors.toUnmodifiableList());
        }

        if (componentType.equals(short.class)) {
            List<Short> result = new ArrayList<>();
            short[] array = (short[]) value;
            for (short i : array) {
                result.add(i);
            }
            return result;
        }
        if (componentType.equals(byte.class)) {
            List<Byte> result = new ArrayList<>();
            byte[] array = (byte[]) value;
            for (byte i : array) {
                result.add(i);
            }
            return result;
        }
        if (componentType.equals(char.class)) {
            List<Character> result = new ArrayList<>();
            char[] array = (char[]) value;
            for (char i : array) {
                result.add(i);
            }
            return result;
        }
        if (componentType.equals(float.class)) {
            List<Float> result = new ArrayList<>();
            float[] array = (float[]) value;
            for (float i : array) {
                result.add(i);
            }
            return result;
        }
        if (componentType.equals(boolean.class)) {
            List<Boolean> result = new ArrayList<>();
            boolean[] array = (boolean[]) value;
            for (boolean i : array) {
                result.add(i);
            }
            return result;
        }
        throw new IllegalArgumentException("Unknown primitive type: " + componentType.getName());
    }

    private static class AnnotationInvocationHandler implements InvocationHandler {
        private final Class<? extends java.lang.annotation.Annotation> annotationType;
        private final Annotation annotation;

        private int cachedHash;

        private AnnotationInvocationHandler(Class<? extends java.lang.annotation.Annotation> annotationType,
                                            Annotation annotation) {

            this.annotationType = annotationType;
            this.annotation = annotation;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if (args == null || args.length == 0) {
                // handle methods that do not have a parameter
                switch (name) {
                case "annotationType" -> {
                    return annotationType;
                }
                case "hashCode" -> {
                    return handleHashCode(proxy);
                }
                case "toString" -> {
                    return annotation.toString();
                }
                default -> {
                }
                }
                Class<?> returnType = method.getReturnType();
                if (returnType.equals(Void.TYPE) || returnType.equals(Void.class)) {
                    return null;
                }

                return invokeProperty(method, name, returnType);
            } else if (args.length == 1) {
                // handle equals (the only that we support that has a parameter
                if ("equals".equals(name)) {
                    return handleEquals(args[0]);
                }
            }

            return method.getDefaultValue();
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private Object invokeProperty(Method method, String name, Class<?> returnType) {
            // we expect this to be an annotation property method
            if (Class.class.equals(returnType)) {
                return annotation.typeValue(name)
                        .map(TypeFactory::toClass)
                        .orElseGet(() -> (Class) method.getDefaultValue());
            }
            if (String.class.equals(returnType)) {
                return annotation.stringValue(name)
                        .orElseGet(() -> (String) method.getDefaultValue());
            }
            if (int.class.equals(returnType)) {
                return annotation.intValue(name)
                        .orElseGet(() -> (Integer) method.getDefaultValue());
            }
            if (long.class.equals(returnType)) {
                return annotation.longValue(name)
                        .orElseGet(() -> (Long) method.getDefaultValue());
            }
            if (boolean.class.equals(returnType)) {
                return annotation.booleanValue(name)
                        .orElseGet(() -> (Boolean) method.getDefaultValue());
            }
            if (double.class.equals(returnType)) {
                return annotation.doubleValue(name)
                        .orElseGet(() -> (Double) method.getDefaultValue());
            }
            if (float.class.equals(returnType)) {
                return annotation.floatValue(name)
                        .orElseGet(() -> (Float) method.getDefaultValue());
            }
            if (byte.class.equals(returnType)) {
                return annotation.byteValue(name)
                        .orElseGet(() -> (Byte) method.getDefaultValue());
            }
            if (char.class.equals(returnType)) {
                return annotation.charValue(name)
                        .orElseGet(() -> (Character) method.getDefaultValue());
            }
            if (short.class.equals(returnType)) {
                return annotation.shortValue(name)
                        .orElseGet(() -> (Short) method.getDefaultValue());
            }
            if (returnType.isEnum()) {
                return annotation.enumValue(name, (Class) returnType)
                        .orElseGet(method::getDefaultValue);
            }
            if (java.lang.annotation.Annotation.class.isAssignableFrom(returnType)) {
                return annotation.annotationValue(name)
                        .flatMap(AnnotationFactory::synthesize)
                        .orElseGet(method::getDefaultValue);
            }
            if (!returnType.isArray()) {
                return method.getDefaultValue();
            }

            return invokeArrayProperty(method, name, returnType.getComponentType());
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private Object invokeArrayProperty(Method method, String name, Class<?> componentType) {
            if (Class.class.equals(componentType)) {
                var values = annotation.typeValues(name);
                if (values.isEmpty()) {
                    return method.getDefaultValue();
                }

                return values.get()
                        .stream()
                        .map(TypeFactory::toClass)
                        .toArray(Class[]::new);
            }
            if (String.class.equals(componentType)) {
                return annotation.stringValues(name)
                        .orElseGet(List::of)
                        .stream()
                        .toArray(String[]::new);
            }
            if (int.class.equals(componentType)) {
                List<Integer> values = annotation.intValues(name)
                        .orElseGet(List::of);
                int[] response = new int[values.size()];
                for (int i = 0; i < response.length; i++) {
                    response[i] = values.get(i);
                }
                return response;
            }
            if (short.class.equals(componentType)) {
                List<Short> values = annotation.shortValues(name)
                        .orElseGet(List::of);
                short[] response = new short[values.size()];
                for (int i = 0; i < response.length; i++) {
                    response[i] = values.get(i);
                }
                return response;
            }
            if (long.class.equals(componentType)) {
                List<Long> values = annotation.longValues(name)
                        .orElseGet(List::of);
                long[] response = new long[values.size()];
                for (int i = 0; i < response.length; i++) {
                    response[i] = values.get(i);
                }
                return response;
            }
            if (boolean.class.equals(componentType)) {
                List<Boolean> values = annotation.booleanValues(name)
                        .orElseGet(List::of);
                boolean[] response = new boolean[values.size()];
                for (int i = 0; i < response.length; i++) {
                    response[i] = values.get(i);
                }
                return response;
            }
            if (double.class.equals(componentType)) {
                List<Double> values = annotation.doubleValues(name)
                        .orElseGet(List::of);
                double[] response = new double[values.size()];
                for (int i = 0; i < response.length; i++) {
                    response[i] = values.get(i);
                }
                return response;
            }
            if (float.class.equals(componentType)) {
                List<Float> values = annotation.floatValues(name)
                        .orElseGet(List::of);
                float[] response = new float[values.size()];
                for (int i = 0; i < response.length; i++) {
                    response[i] = values.get(i);
                }
                return response;
            }
            if (byte.class.equals(componentType)) {
                List<Byte> values = annotation.byteValues(name)
                        .orElseGet(List::of);
                byte[] response = new byte[values.size()];
                for (int i = 0; i < response.length; i++) {
                    response[i] = values.get(i);
                }
                return response;
            }
            if (char.class.equals(componentType)) {
                List<Character> values = annotation.charValues(name)
                        .orElseGet(List::of);
                char[] response = new char[values.size()];
                for (int i = 0; i < response.length; i++) {
                    response[i] = values.get(i);
                }
                return response;
            }
            if (componentType.isEnum()) {
                Optional<List<Enum>> values = annotation.enumValues(name, (Class) componentType);
                return values.orElseGet(List::of)
                        .stream()
                        .toArray(length -> (Object[]) Array.newInstance(componentType, length));

            }
            if (java.lang.annotation.Annotation.class.isAssignableFrom(componentType)) {
                return annotation.annotationValues(name)
                        .orElseGet(List::of)
                        .stream()
                        .map(AnnotationFactory::synthesize)
                        .flatMap(Optional::stream)
                        .toArray(it -> (Object[]) Array.newInstance(componentType, it));
            }

            return method.getDefaultValue();
        }

        private static int hashValue(Object value) {
            if (value instanceof boolean[]) {
                return Arrays.hashCode((boolean[]) value);
            } else if (value instanceof short[]) {
                return Arrays.hashCode((short[]) value);
            } else if (value instanceof int[]) {
                return Arrays.hashCode((int[]) value);
            } else if (value instanceof long[]) {
                return Arrays.hashCode((long[]) value);
            } else if (value instanceof float[]) {
                return Arrays.hashCode((float[]) value);
            } else if (value instanceof double[]) {
                return Arrays.hashCode((double[]) value);
            } else if (value instanceof byte[]) {
                return Arrays.hashCode((byte[]) value);
            } else if (value instanceof char[]) {
                return Arrays.hashCode((char[]) value);
            } else if (value instanceof Object[]) {
                return Arrays.hashCode((Object[]) value);
            }
            return value.hashCode();
        }

        private boolean handleEquals(Object other) {
            if (this == other) {
                return true;
            }
            if (other == null) {
                return false;
            }
            if (!annotationType.isInstance(other)) {
                return false;
            }
            Annotation otherAnnotation = create((java.lang.annotation.Annotation) other);
            return annotation.equals(otherAnnotation);
        }

        private int handleHashCode(Object proxy) {
            // this is prescribed by Java specification, to have same hash code as instance from reflection
            if (cachedHash != 0) {
                return cachedHash;
            }

            int hashCode = 0;

            List<Method> methods = valueMethods(annotationType);
            for (Method method : methods) {
                Object value = invoke(proxy, method, null);

                int nameHash = 127 * method.getName().hashCode();
                int valueHash = hashValue(value);
                hashCode += nameHash ^ valueHash;
            }
            this.cachedHash = hashCode;
            return hashCode;
        }
    }
}
