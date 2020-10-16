/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.integrations.micronaut.cdi;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.AnnotatedParameter;

import io.micronaut.context.AbstractExecutableMethod;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.DefaultArgument;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.annotation.DefaultAnnotationMetadata;

@SuppressWarnings("rawtypes")
final class CdiExecutableMethod extends AbstractExecutableMethod {
    private final AnnotationMetadata annotationMetadata;

    private CdiExecutableMethod(AnnotationMetadata methodAnnotationMetadata,
                                Class<?> declaringType,
                                String methodName,
                                Argument<?> genericReturnType,
                                Argument... arguments) {
        super(declaringType, methodName, genericReturnType, arguments);
        this.annotationMetadata = methodAnnotationMetadata;
    }

    public static ExecutableMethod<?, ?> create(AnnotatedMethod<?> cdiMethod, ExecutableMethod<?, ?> micronautMethod) {
        return create(cdiMethod,
                      annotationMetadata(cdiMethod, micronautMethod.getAnnotationMetadata()),
                      arguments(cdiMethod.getParameters(), micronautMethod.getArguments()));
    }

    public static ExecutableMethod<?, ?> create(AnnotatedMethod<?> cdiMethod) {
        return create(cdiMethod,
                      annotationMetadata(cdiMethod),
                      arguments(cdiMethod.getParameters()));
    }

    private static ExecutableMethod<?, ?> create(AnnotatedMethod<?> cdiMethod,
                                                 AnnotationMetadata annotationMetadata,
                                                 Argument... arguments) {
        Class<?> declaringType = cdiMethod.getDeclaringType().getJavaClass();
        Argument<?> returnType = Argument.of(cdiMethod.getJavaMember().getReturnType());

        return new CdiExecutableMethod(annotationMetadata,
                                       declaringType,
                                       cdiMethod.getJavaMember().getName(),
                                       returnType,
                                       arguments);
    }

    @Override
    protected AnnotationMetadata resolveAnnotationMetadata() {
        return annotationMetadata;
    }

    @Override
    protected Object invokeInternal(Object instance, Object[] arguments) {
        throw new MicronautCdiException("invokeInternal should not be called in interceptor");
    }

    @SuppressWarnings("rawtypes")
    private static Argument[] arguments(List<? extends AnnotatedParameter<?>> parameters, Argument[] miParameters) {
        Argument[] result = new Argument[parameters.size()];

        for (int i = 0; i < parameters.size(); i++) {
            AnnotatedParameter<?> parameter = parameters.get(i);
            result[i] = toArgument(parameter, miParameters[i]);
        }

        return result;
    }

    @SuppressWarnings("rawtypes")
    private static Argument[] arguments(List<? extends AnnotatedParameter<?>> parameters) {
        Argument[] result = new Argument[parameters.size()];

        for (int i = 0; i < parameters.size(); i++) {
            AnnotatedParameter<?> parameter = parameters.get(i);
            result[i] = toArgument(parameter);
        }

        return result;
    }

    private static Argument<?> toArgument(AnnotatedParameter<?> parameter, Argument argument) {
        Parameter javaParameter = parameter.getJavaParameter();
        return new DefaultArgument(javaParameter.getType(),
                                   javaParameter.getName(),
                                   annotationMetadata(parameter, argument.getAnnotationMetadata()));
    }

    private static Argument<?> toArgument(AnnotatedParameter<?> parameter) {
        Parameter javaParameter = parameter.getJavaParameter();
        return new DefaultArgument(javaParameter.getType(),
                                   javaParameter.getName(),
                                   annotationMetadata(parameter));
    }



    @SuppressWarnings("unchecked")
    private static AnnotationMetadata annotationMetadata(Annotated annotated, AnnotationMetadata miAnnotated) {
        Map<Class<? extends Annotation>, Annotation> annotations = new HashMap<>();
        Set<String> miAnnotationNames = miAnnotated.getAnnotationNames();
        // add micronaut annotations
        for (String miAnnotationName : miAnnotationNames) {
            try {
                Annotation annotation = miAnnotated.synthesize((Class<? extends Annotation>) Class.forName(miAnnotationName));
                annotations.put(annotation.annotationType(), annotation);
            } catch (Throwable ignored) {
                // this annotation is not on the classpath, we can ignore it
            }
        }

        // then overwrite with CDI annotations (more significant for us)
        annotated.getAnnotations()
                .forEach(it -> annotations.put(it.annotationType(), it));

        return annotationMetadata(annotations);
    }
    private static AnnotationMetadata annotationMetadata(Annotated annotated) {
        Map<Class<? extends Annotation>, Annotation> annotations = new HashMap<>();
        annotated.getAnnotations()
                .forEach(it -> annotations.put(it.annotationType(), it));

        return annotationMetadata(annotations);
    }

    private static AnnotationMetadata annotationMetadata(Map<Class<? extends Annotation>, Annotation> annotations) {
        Map<Class<? extends Annotation>, Annotation> stereotypes = new HashMap<>();
        Map<String, Set<String>> byStereotype = new HashMap<>();
        Map<String, Map<CharSequence, Object>> miAnnotations = new HashMap<>();

        processAnnotations(annotations,
                           miAnnotations,
                           stereotypes,
                           byStereotype);

        Map<String, Map<CharSequence, Object>> miStereotypes = new HashMap<>();
        for (var entry : stereotypes.entrySet()) {
            miStereotypes.put(entry.getKey().getName(), annotationValues(entry.getValue()));
        }

        Map<String, List<String>> byStereotypeWithList = new HashMap<>();
        byStereotype
                .forEach((stereotype, set) -> byStereotypeWithList.put(stereotype, new ArrayList<>(set)));

        return new DefaultAnnotationMetadata(miAnnotations,
                                             miStereotypes,
                                             miStereotypes,
                                             miAnnotations,
                                             byStereotypeWithList);
    }

    private static void processAnnotations(Map<Class<? extends Annotation>, Annotation> declaredAnnotations,
                                           Map<String, Map<CharSequence, Object>> miAnnotations,
                                           Map<Class<? extends Annotation>, Annotation> stereotypeMap,
                                           Map<String, Set<String>> annotationsByStereotype) {
        for (var entry : declaredAnnotations.entrySet()) {
            if (stereotypeMap.containsKey(Repeatable.class)) {
                // I need to ignore this (used only when there is just one repetition)
                // this gets processed as part of teh Repeatable container
                continue;
            }
            String annotName = entry.getKey().getName();
            miAnnotations.put(annotName, annotationValues(entry.getValue()));
            Set<Annotation> stereotypes = getStereotypes(entry.getValue());

            for (Annotation stereotype : stereotypes) {
                if (Target.class.equals(stereotype.annotationType())) {
                    continue;
                }
                if (Documented.class.equals(stereotype.annotationType())) {
                    continue;
                }
                if (Retention.class.equals(stereotype.annotationType())) {
                    continue;
                }

                stereotypeMap.put(stereotype.annotationType(), stereotype);
                String stereotypeName = stereotype.annotationType().getName();

                annotationsByStereotype.computeIfAbsent(stereotypeName, it -> new HashSet<>())
                        .add(annotName);
            }
        }
    }

    private static Set<Annotation> getStereotypes(Annotation annotation) {
        return Set.of(annotation.annotationType().getAnnotations());
    }

    private static Map<CharSequence, Object> annotationValues(Annotation annotation) {
        Class<? extends Annotation> aClass = annotation.annotationType();
        Method[] declaredMethods = aClass.getDeclaredMethods();
        Map<CharSequence, Object> result = new HashMap<>();

        for (Method declaredMethod : declaredMethods) {
            int mod = declaredMethod.getModifiers();
            if (Modifier.isPublic(mod) && !Modifier.isStatic(mod)) {
                try {
                    Object value = declaredMethod.invoke(annotation);
                    if (value.getClass().isArray() && Annotation.class.isAssignableFrom(value.getClass().getComponentType())) {
                        // this is a repeatable annotation
                        int len = Array.getLength(value);
                        List<AnnotationValue> values = new ArrayList<>(len);
                        for (int i = 0; i < len; i++) {
                            Annotation element = (Annotation) Array.get(value, i);
                            values.add(new AnnotationValue(element.annotationType().getName(), annotationValues(element)));
                        }
                        result.put(declaredMethod.getName(), values);
                    } else {
                        result.put(declaredMethod.getName(), value);
                    }
                } catch (Exception e) {
                    throw new MicronautCdiException(e);
                }
            }
        }

        return result;
    }
}
