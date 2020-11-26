/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2020, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package io.narayana.lra.arquillian.spi;

import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;
import org.jboss.logging.Logger;

import javax.ws.rs.core.Response;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Map;

/**
 * Helper class to do internal changes of annotations during runtime.
 */
public class LRAAnnotationAdjuster {
    private static final Logger log = Logger.getLogger(LRAAnnotationAdjuster.class);
    private static final String ANNOTATIONS_FIELD_NAME = "annotations";
    private static final String ANNOTATION_DATA_METHOD_NAME = "annotationData";
    private static final String ANNOTATION_CACHE_FIELD_NAME = "annotationCache";
    private static final String ANNOTATION_MAP_FIELD_NAME = "annotationMap";

    /**
     * Take the clazz, check if contains the {@link LRA} annotation.
     * The LRA annotation is then replaced by wrapped {@link LRAWrapped}.
     */
    static void processWithClass(Class<?> clazz) {
        LRA lraAnnotation = clazz.getDeclaredAnnotation(LRA.class);
        if (lraAnnotation != null) {
            LRAAnnotationAdjuster.adjustLRAAnnotation(clazz, lraAnnotation);
        }
        Arrays.stream(clazz.getMethods()).forEach(method -> {
            LRA lraAnnotationMethod = method.getDeclaredAnnotation(LRA.class);
            if (lraAnnotationMethod != null) {
                LRAAnnotationAdjuster.adjustLRAAnnotation(method, lraAnnotationMethod);
            }
        });
    }

    /**
     * Changing the LRA annotation declared on class by wrapping it with {@link LRAWrapped}.
     */
    static void adjustLRAAnnotation(Class<?> clazzToLookFor, LRA originalLRAAnnotation) {
        if (doesJDKDefineFieldName(ANNOTATIONS_FIELD_NAME)) {
            // Open JDK7 has "annotations" field
            try {
                Field annotations = Class.class.getDeclaredField(ANNOTATIONS_FIELD_NAME);
                annotations.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<Class<? extends Annotation>, Annotation> map =
                        (Map<Class<? extends Annotation>, Annotation>) annotations.get(clazzToLookFor);
                map.put(LRA.class, new LRAWrapped(originalLRAAnnotation));
            } catch (Exception  e) {
                throw new IllegalStateException("Cannot change annotation " + originalLRAAnnotation
                        + " of class " + clazzToLookFor + " in JDK7 way", e);
            }
        } else if (doesJDKDefineMethodName(ANNOTATION_DATA_METHOD_NAME)) {
            try {
                // Open JDK8+ has "annotationData" private method
                // obtaining reference to private class AnnotationData
                Method method = Class.class.getDeclaredMethod(ANNOTATION_DATA_METHOD_NAME);
                method.setAccessible(true);
                // AnnotationData is private need to work with Object
                Object annotationData = method.invoke(clazzToLookFor);
                // AnnotationData works with map annotations
                Field annotations = annotationData.getClass().getDeclaredField(ANNOTATIONS_FIELD_NAME);
                annotations.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<Class<? extends Annotation>, Annotation> map =
                        (Map<Class<? extends Annotation>, Annotation>) annotations.get(annotationData);
                log.debugf("Adjusting LRA annotation %s for class %s%n", originalLRAAnnotation, clazzToLookFor.getName());
                map.put(LRA.class, new LRAWrapped(originalLRAAnnotation));
            } catch (Exception  e) {
                throw new IllegalStateException("Cannot change annotation " + originalLRAAnnotation
                        + " of class " + clazzToLookFor + " in JDK8 way", e);
            }
        } else if (doesJDKDefineFieldName(ANNOTATION_CACHE_FIELD_NAME)) {
            // OpenJ9
            try {
                Field cacheField = Class.class.getDeclaredField(ANNOTATION_CACHE_FIELD_NAME);
                cacheField.setAccessible(true);
                Object cache = cacheField.get(clazzToLookFor);

                Class<? extends Object> cacheClass = cache.getClass();
                Field mapField = cacheClass.getDeclaredField(ANNOTATION_MAP_FIELD_NAME);
                mapField.setAccessible(true);
                Map<Class<? extends Annotation>, Annotation> annotationMap = (Map<Class<? extends Annotation>, Annotation>) mapField.get(cache);
                annotationMap.put(LRA.class, new LRAWrapped(originalLRAAnnotation));
            } catch (Exception e) {
                throw new IllegalStateException("Cannot change annotation " + originalLRAAnnotation
                        + " of class " + clazzToLookFor + " in JDK OpenJ9 way", e);
            }
        } else {
            log.warnf("Cannot adjust the timeout value of the %s annotation of class %s. The processing of %s is skipped.",
                    originalLRAAnnotation, clazzToLookFor, LRAAnnotationAdjuster.class.getName());
        }
    }

    /**
     * Changing the LRA annotation declared on method by wrapping it with {@link LRAWrapped}.
     */
    static void adjustLRAAnnotation(Method method, LRA originalLRAAnnotation) {
        Field field;
        try {
            Class<?> executableClass = Class.forName("java.lang.reflect.Executable");
            field = executableClass.getDeclaredField("declaredAnnotations");
            field.setAccessible(true);
        } catch (ClassNotFoundException cnfe) {
            throw new IllegalStateException("Cannot instantiate class java.lang.reflect.Executable", cnfe);
        } catch (NoSuchFieldException nsfe) {
            throw new IllegalStateException("Cannot find field 'declaredAnnotations' under instantiate class java.lang.reflect.Executable", nsfe);
        }
        Map<Class<? extends Annotation>, Annotation> annotations;
        try {
            annotations = (Map<Class<? extends Annotation>, Annotation>) field.get(method);
        } catch (IllegalAccessException iae) {
            throw new IllegalStateException("Cannnot access field 'declaredAnnotations' of the method instance " + method, iae);
        }
        log.debugf("Adjusting LRA annotation %s for method %s of class %s%n",
                originalLRAAnnotation, method, method.getDeclaringClass().getName());
        annotations.put(LRA.class, new LRAWrapped(originalLRAAnnotation));
    }

    private static boolean doesJDKDefineFieldName(String fieldName) {
        try {
            Class.class.getDeclaredField(fieldName);
        } catch (NoSuchFieldException ignore) {
            return false;
        }
        return true;
    }

    private static boolean doesJDKDefineMethodName(String methodName) {
        try {
            Class.class.getDeclaredMethod(methodName);
        } catch (NoSuchMethodException ignore) {
            return false;
        }
        return true;
    }

    private static class LRAWrapped implements LRA {
        private static final String TIMEOUT_FACTOR_PROPERTY = "lra.tck.timeout.factor";
        private final LRA wrapped;

        LRAWrapped(LRA lraInstance) {
            this.wrapped = lraInstance;
        }

        @Override
        public LRA.Type value() {
            return wrapped.value();
        }

        @Override
        public long timeLimit() {
            return getTimeout(wrapped.timeLimit());
        }

        @Override
        public ChronoUnit timeUnit() {
            return wrapped.timeUnit();
        }

        @Override
        public boolean end() {
            return wrapped.end();
        }

        @Override
        public Response.Status.Family[] cancelOnFamily() {
            return wrapped.cancelOnFamily();
        }

        @Override
        public Response.Status[] cancelOn() {
            return wrapped.cancelOn();
        }

        @Override
        public boolean equals(Object obj) {
            return wrapped.equals(obj);
        }

        @Override
        public int hashCode() {
            return wrapped.hashCode();
        }

        @Override
        public String toString() {
            return wrapped.toString();
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return wrapped.annotationType();
        }

        private long getTimeout(long originalTimeout) {
            if (originalTimeout <= 0) {
                return 0;
            }
            String timeoutFactorString = System.getProperty(TIMEOUT_FACTOR_PROPERTY, "1.0");
            double timeoutFactor = Double.parseDouble(timeoutFactorString);
            if (timeoutFactor <= 0) {
                return originalTimeout;
            }
            return (long) Math.ceil(originalTimeout * timeoutFactor);
        }
    }
}
