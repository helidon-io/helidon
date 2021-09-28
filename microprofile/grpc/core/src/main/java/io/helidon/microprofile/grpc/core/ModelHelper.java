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

package io.helidon.microprofile.grpc.core;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.stream.StreamSupport;

import javax.enterprise.inject.Instance;
import javax.enterprise.inject.spi.CDI;
import javax.enterprise.util.AnnotationLiteral;
import javax.inject.Named;

import io.helidon.grpc.core.MarshallerSupplier;

/**
 * Common model helper methods.
 */
public final class ModelHelper {

    /**
     * Get the class in the provided resource class ancestor hierarchy that
     * is actually annotated with the specified annotation.
     * <p>
     * If the annotation is not present in the class hierarchy the resource class
     * is returned.
     *
     * @param resourceClass resource class
     * @param annotation  the annotation to look for
     *
     * @return resource class or it's ancestor that is annotated with
     *         the specified annotation.
     */
    public static Class<?> getAnnotatedResourceClass(Class<?> resourceClass, Class<? extends Annotation> annotation) {

        Class<?> foundInterface = null;

        // traverse the class hierarchy to find the annotation
        // Annotation in the super-classes must take precedence over annotation in the
        // implemented interfaces
        Class<?> cls = resourceClass;
        do {
            if (cls.getDeclaredAnnotation(annotation) != null) {
                return cls;
            }

            // if no annotation found on the class currently traversed, check for annotation in the interfaces on this
            // level - if not already previously found
            if (foundInterface == null) {
                for (final Class<?> i : cls.getInterfaces()) {
                    if (i.getDeclaredAnnotation(annotation) != null) {
                        // store the interface reference in case no annotation will be found in the super-classes
                        foundInterface = i;
                        break;
                    }
                }
            }
            cls = cls.getSuperclass();
        } while (cls != null);

        if (foundInterface != null) {
            return foundInterface;
        }

        return resourceClass;
    }

    /**
     * Get collection of methods declared on given class.
     *
     * @param clazz class for which to get the declared methods.
     * @return methods declared on the {@code clazz} class.
     */
    public static Collection<? extends Method> getDeclaredMethods(final Class<?> clazz) {
        return Arrays.asList(clazz.getDeclaredMethods());
    }

    /**
     * Find a method in a class. If there exists a public method on the class
     * that has the same name and parameters then that public method is
     * returned.
     * <p>
     * Otherwise, if there exists a public method on the class that has
     * the same name and the same number of parameters,
     * and each generic parameter type, in order, of the public method is equal
     * to the generic parameter type, in the same order or is an instance of
     * {@link TypeVariable} then that public method is returned.
     *
     * @param cls the class to search for a public method
     * @param m the method to find
     * @return public method found.
     */
    public static Method findMethodOnClass(final Class<?> cls, final Method m) {
        try {
            return cls.getMethod(m.getName(), m.getParameterTypes());
        } catch (final NoSuchMethodException e) {
            for (final Method method : cls.getMethods()) {
                if (method.getName().equals(m.getName())
                        && method.getParameterTypes().length == m.getParameterTypes().length) {
                    if (compareParameterTypes(m.getGenericParameterTypes(),
                            method.getGenericParameterTypes())) {
                        return method;
                    }
                }
            }
            return null;
        }
    }

    /**
     * Compare generic parameter types of two methods.
     *
     * @param first  generic parameter types of the first method.
     * @param second generic parameter types of the second method.
     * @return {@code true} if the given types are understood to be equal, {@code false} otherwise.
     * @see #compareParameterTypes(java.lang.reflect.Type, java.lang.reflect.Type)
     */
    private static boolean compareParameterTypes(final Type[] first, final Type[] second) {
        for (int i = 0; i < first.length; i++) {
            if (!first[i].equals(second[i])) {
                if (!compareParameterTypes(first[i], second[i])) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Compare respective generic parameter types of two methods.
     *
     * @param first  generic parameter type of the first method.
     * @param second generic parameter type of the second method.
     * @return {@code true} if the given types are understood to be equal, {@code false} otherwise.
     */
    @SuppressWarnings("unchecked")
    private static boolean compareParameterTypes(final Type first, final Type second) {
        if (first instanceof Class) {
            final Class<?> clazz = (Class<?>) first;

            if (second instanceof Class) {
                return ((Class) second).isAssignableFrom(clazz);
            } else if (second instanceof TypeVariable) {
                return checkTypeBounds(clazz, ((TypeVariable) second).getBounds());
            }
        }
        return second instanceof TypeVariable;
    }

    @SuppressWarnings("unchecked")
    private static boolean checkTypeBounds(final Class type, final Type[] bounds) {
        for (final Type bound : bounds) {
            if (bound instanceof Class) {
                if (!((Class) bound).isAssignableFrom(type)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Gets the component type of the array.
     *
     * @param type must be an array.
     * @return array component type.
     * @throws IllegalArgumentException in case the type is not an array type.
     */
    public static Type getArrayComponentType(final Type type) {
        if (type instanceof Class) {
            final Class c = (Class) type;
            return c.getComponentType();
        }
        if (type instanceof GenericArrayType) {
            return ((GenericArrayType) type).getGenericComponentType();
        }

        throw new IllegalArgumentException();
    }

    /**
     * Get Array class of component type.
     *
     * @param c the component class of the array
     * @return the array class.
     */
    public static Class<?> getArrayForComponentType(final Class<?> c) {
        try {
            final Object o = Array.newInstance(c, 0);
            return o.getClass();
        } catch (final Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Obtain the named {@link MarshallerSupplier} specified by the annotation.
     *
     * @param annotation  the annotation specifying the {@link MarshallerSupplier}.
     *
     * @return the {@link MarshallerSupplier} specified by the annotation
     */
    public static MarshallerSupplier getMarshallerSupplier(GrpcMarshaller annotation) {
        String name = annotation == null ? MarshallerSupplier.DEFAULT : annotation.value();

        Instance<MarshallerSupplier> instance = CDI.current().select(MarshallerSupplier.class, new NamedLiteral(name));
        if (instance.isUnsatisfied()) {
            // fall back to service loader discovery
            return StreamSupport.stream(ServiceLoader.load(MarshallerSupplier.class).spliterator(), false)
                    .filter(s -> hasName(s, name))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Could not load MarshallerSupplier from annotation "
                                                                    + annotation));
        } else if (instance.isAmbiguous()) {
            throw new IllegalArgumentException("There are multiple MarshallerSupplier beans with name '" + name + "'");
        }

        return instance.get();
    }

    private static boolean hasName(MarshallerSupplier supplier, String name) {
        Class<?> cls = supplier.getClass();
        Named named = cls.getAnnotation(Named.class);

        return named != null && Objects.equals(named.value(), name);
    }

    /**
     * Obtain the generic type for a {@link Type}.
     *
     * @param type  the type to obtain the generic type of
     * @return  the generic type
     */
    public static Class<?> getGenericType(Type type) {
        if (type instanceof Class) {
            return (Class<?>) type;
        } else if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            if (parameterizedType.getRawType() instanceof Class) {
                Type t = parameterizedType.getActualTypeArguments()[0];
                if (t instanceof Class) {
                    return (Class<?>) t;
                } else if (t instanceof ParameterizedType) {
                    // the type is a nested generic e.g. List<Map<String, Integer>>
                    // we're only interested in the outer type, in the example above List
                    return (Class<?>) ((ParameterizedType) t).getRawType();
                } else {
                    throw new IllegalArgumentException("Type parameter " + type.toString() + " not a class or "
                            + "parameterized type whose raw type is a class");
                }
            }
        } else if (type instanceof GenericArrayType) {
            GenericArrayType array = (GenericArrayType) type;
            final Class<?> componentRawType = getGenericType(array.getGenericComponentType());
            return getArrayClass(componentRawType);
        }
        throw new IllegalArgumentException("Type parameter " + type.toString() + " not a class or "
                + "parameterized type whose raw type is a class");
    }

    private static Class getArrayClass(Class c) {
        try {
            Object o = Array.newInstance(c, 0);
            return o.getClass();
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * An annotation literal for {@link Named}.
     */
    static class NamedLiteral extends AnnotationLiteral<Named> implements Named {

        private final String value;

        NamedLiteral(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return this.value;
        }
    }

    /**
     * Private constructor for utility classes.
     */
    private ModelHelper() {
        throw new AssertionError("Instantiation not allowed.");
    }
}
