package io.helidon.integrations.micronaut.cdi;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.context.AbstractExecutableMethod;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.DefaultArgument;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.annotation.DefaultAnnotationMetadata;

class MethodInterceptorMetadata {
    private final Set<Class<? extends MethodInterceptor<?, ?>>> interceptors = new HashSet<>();
    private ExecutableMethod<?, ?> executableMethod;

    private MethodInterceptorMetadata(ExecutableMethod<?, ?> executableMethod) {
        this.executableMethod = executableMethod;
    }

    static MethodInterceptorMetadata create(Method method) {
        return create(method, Map.of());
    }

    static MethodInterceptorMetadata create(Method method,
                                            Map<Class<? extends Annotation>, Map<CharSequence, Object>> annotationsToAdd) {
        return new MethodInterceptorMetadata(executableMethod(method, annotationsToAdd));
    }

    void addInterceptors(Collection<Class<? extends MethodInterceptor<?, ?>>> interceptors) {
        checkInteceptors(interceptors);
        this.interceptors.addAll(interceptors);
    }

    Set<Class<? extends MethodInterceptor<?, ?>>> interceptors() {
        return interceptors;
    }

    ExecutableMethod<?, ?> executableMethod() {
        return executableMethod;
    }

    void executableMethod(ExecutableMethod<?, ?> executableMethod) {
        this.executableMethod = executableMethod;
    }

    private static ExecutableMethod<?, ?> executableMethod(Method method,
                                                           Map<Class<? extends Annotation>, Map<CharSequence, Object>> annotationsToAdd) {
        return new HelidonExecutableMethod(annotationMetadata(method, annotationsToAdd),
                                           method.getDeclaringClass(),
                                           method.getName(),
                                           Argument.of(method.getReturnType()),
                                           arguments(method)
        );
    }

    @SuppressWarnings("rawtypes")
    private static Argument[] arguments(Method method) {
        Parameter[] parameters = method.getParameters();
        Argument[] result = new Argument[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            result[i] = toArgument(parameter);
        }

        return result;
    }

    private static Argument<?> toArgument(Parameter parameter) {
        return new DefaultArgument(parameter.getType(),
                                   parameter.getName(),
                                   annotationMetadata(parameter, Map.of()));
    }

    private static AnnotationMetadata annotationMetadata(AnnotatedElement annotated,
                                                         Map<Class<? extends Annotation>, Map<CharSequence, Object>> annotationsToAdd) {
        Annotation[] annotatedDeclaredAnnotations = annotated.getDeclaredAnnotations();
        Annotation[] annotatedAllAnnotations = annotated.getAnnotations();

        Set<Annotation> declaredStereotypesSet = new HashSet<>();
        Set<Annotation> allStereotypesSet = new HashSet<>();
        Map<String, Set<String>> annotationsByStereotypeWithSet = new HashMap<>();

        Map<String, Map<CharSequence, Object>> declaredAnnotations = new HashMap<>();
        annotationsToAdd.forEach(((aClass, values) -> declaredAnnotations.put(aClass.getName(), values)));

        processAnnotations(annotatedDeclaredAnnotations,
                           declaredAnnotations,
                           declaredStereotypesSet,
                           annotationsByStereotypeWithSet);

        Map<String, Map<CharSequence, Object>> allAnnotations = new HashMap<>(declaredAnnotations);

        processAnnotations(annotatedAllAnnotations,
                           allAnnotations,
                           allStereotypesSet,
                           annotationsByStereotypeWithSet);

        Map<String, Map<CharSequence, Object>> declaredStereotypes = new HashMap<>();
        for (Annotation annotation : declaredStereotypesSet) {
            declaredStereotypes.put(annotation.annotationType().getName(), annotationValues(annotation));
        }

        Map<String, Map<CharSequence, Object>> allStereotypes = new HashMap<>(declaredStereotypes);
        for (Annotation annotation : allStereotypesSet) {
            allStereotypes.put(annotation.annotationType().getName(), annotationValues(annotation));
        }

        Map<String, List<String>> annotationsByStereotype = new HashMap<>();
        annotationsByStereotypeWithSet
                .forEach((stereotype, set) -> annotationsByStereotype.put(stereotype, new ArrayList<>(set)));

        return new DefaultAnnotationMetadata(declaredAnnotations,
                                             declaredStereotypes,
                                             allStereotypes,
                                             allAnnotations,
                                             annotationsByStereotype);
    }

    private static void processAnnotations(Annotation[] reflectAnnotations,
                                           Map<String, Map<CharSequence, Object>> annotationMap,
                                           Set<Annotation> stereotypeSet,
                                           Map<String, Set<String>> annotationsByStereotype) {
        for (Annotation annotation : reflectAnnotations) {
            String annotName = annotation.annotationType().getName();
            annotationMap.put(annotName, annotationValues(annotation));

            Set<Annotation> stereotypes = getStereotypes(annotation);
            stereotypeSet.addAll(stereotypes);

            for (Annotation stereotype : stereotypes) {
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
                    result.put(declaredMethod.getName(), value);
                } catch (Exception e) {
                    throw new MicronautCdiException(e);
                }
            }
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private static Set<Class<? extends MethodInterceptor<?, ?>>> checkInteceptors(Collection<Class<? extends MethodInterceptor<
            ?, ?>>> interceptors) {
        Set<Class<? extends MethodInterceptor<?, ?>>> result = new HashSet<>();

        interceptors.stream()
                .peek(it -> {
                    if (!MethodInterceptor.class.isAssignableFrom(it)) {
                        throw new MicronautCdiException("Wrong interceptor defined: " + it.getName());
                    }
                })
                .forEach(it -> result.add((Class<? extends MethodInterceptor<?, ?>>) it));

        return result;
    }

    private static final class HelidonExecutableMethod extends AbstractExecutableMethod {
        private final AnnotationMetadata annotationMetadata;

        private HelidonExecutableMethod(AnnotationMetadata methodAnnotationMetadata,
                                        Class<?> declaringType,
                                        String methodName,
                                        Argument genericReturnType,
                                        Argument... arguments) {
            super(declaringType, methodName, genericReturnType, arguments);
            this.annotationMetadata = methodAnnotationMetadata;
        }

        @Override
        protected AnnotationMetadata resolveAnnotationMetadata() {
            return annotationMetadata;
        }

        @Override
        protected Object invokeInternal(Object instance, Object[] arguments) {
            throw new MicronautCdiException("invokeInternal should not be called in interceptor");
        }
    }
}
