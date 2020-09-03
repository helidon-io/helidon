/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.graphql.server;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexReader;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.ParameterizedType;
import org.jboss.jandex.Type;

/**
 * Utilities for working with Jandex indexes.
 */
public class JandexUtils {

    private static final Logger LOGGER = Logger.getLogger(JandexUtils.class.getName());

    /**
     * Default Jandex index file.
     */
    protected static final String DEFAULT_INDEX_FILE = "META-INF/jandex.idx";

    /**
     * Property to override the default index file. (Normally used for functional tests)
     */
    public static final String PROP_INDEX_FILE = "io.helidon.microprofile.graphql.indexfile";

    /**
     * The loaded index or null if none was found.
     */
    private Index index;

    /**
     * The file used to load the index.
     */
    private String indexFile;

    /**
     * Construct an instance of the utilities class..
     */
    public JandexUtils() {
        indexFile = System.getProperty(PROP_INDEX_FILE, DEFAULT_INDEX_FILE);
    }

    /**
     * Load the index file.
     */
    public void loadIndex() {
        File file = new File(indexFile);
        String actualFile;
        if (file.isAbsolute()) {
            actualFile = indexFile;
        } else {
            URL resource = JandexUtils.class.getClassLoader().getResource(indexFile);
            if (resource == null) {
                return;
            }
            actualFile = resource.getFile();
        }
        try (FileInputStream input = new FileInputStream(actualFile)) {
            IndexReader reader = new IndexReader(input);
            index = reader.read();
        } catch (Exception e) {
            LOGGER.warning("Unable to load default Jandex index file: " + indexFile
                                   + ": " + e.getMessage());
        }
    }

    /**
     * Return a {@link Collection} of {@link Class}es which are implementors of a given class/interface.
     *
     * @param clazz           {@link Class} to check for implementors
     * @param includeAbstract indicates if abstract classes should be included
     * @return a {@link Collection} of {@link Class}es
     */
    public Collection<Class<?>> getKnownImplementors(String clazz, boolean includeAbstract) {
        if (index == null) {
            return null;
        }
        Set<ClassInfo> allKnownImplementors = index.getAllKnownImplementors(DotName.createSimple(clazz));
        Set<Class<?>> setResults = new HashSet<>();

        for (ClassInfo classInfo : allKnownImplementors) {
            Class<?> clazzName = null;
            try {
                clazzName = Class.forName(classInfo.toString());
            } catch (ClassNotFoundException e) {
                // ignore as class should exist
            }
            if (includeAbstract || !Modifier.isAbstract(clazzName.getModifiers())) {
                setResults.add(clazzName);
            }
        }
        return setResults;
    }

    /**
     * Return true if the given class, method and parameter has the specified annotation class name for a generic type.
     *
     * @param clazz           {@link Class} to check for annotation
     * @param methodName      method name to check
     * @param paramNumber     parameter number to check
     * @param annotationClazz the annotation {@link Class} to check
     * @return true if the given class, method and parameter has the specified annotation class name
     */
    protected boolean methodParameterHasAnnotation(String clazz, String methodName, int paramNumber, String annotationClazz) {
        return getMethodParameterAnnotation(clazz, methodName, paramNumber, annotationClazz) != null;
    }

    /**
     * Return the {@link AnnotationInstance} for the given class, method and parameter has the specified annotation class name for a generic type.
     *
     * @param clazz           {@link Class} to check for annotation
     * @param methodName      method name to check
     * @param paramNumber     parameter number to check
     * @param annotationClazz the annotation {@link Class} to check
     * @return {@link AnnotationInstance} or null if none present
     */
    protected AnnotationInstance getMethodParameterAnnotation(String clazz, String methodName,
                                                              int paramNumber, String annotationClazz) {
        if (hasIndex()) {
            ClassInfo classByName = index.getClassByName(DotName.createSimple(clazz));
            if (classByName != null) {
                MethodInfo methodInfo = classByName.firstMethod(methodName);
                if (methodInfo != null) {
                    ClassType classType = retrieveInnerMostType(methodInfo.parameters().get(paramNumber));
                     return classType.annotations().stream()
                            .filter(a -> a.name().equals(DotName.createSimple(annotationClazz)))
                            .findFirst().orElse(null);
                }
            }
        }
        return null;
    }

    /**
     * Return true if the given field in a class has the specified annotation class name for a generic type.
     *
     * @param clazz           {@link Class} to check for annotation
     * @param fieldName       field name to check
     * @param annotationClazz the annotation {@link Class} to check
     * @return true if the given class, method and parameter has the specified annotation class name
     */
    protected boolean fieldHasAnnotation(String clazz, String fieldName, String annotationClazz) {
        return getFieldAnnotation(clazz, fieldName, annotationClazz) != null;
    }

    /**
     * Return the {@link AnnotationInstance} for the given field in a class has the specified annotation class name for a generic type.
     *
     * @param clazz           {@link Class} to check for annotation
     * @param fieldName       field name to check
     * @param annotationClazz the annotation {@link Class} to check
     * @return {@link AnnotationInstance} or null if none present
     */
    protected AnnotationInstance getFieldAnnotation(String clazz, String fieldName, String annotationClazz) {
        if (hasIndex()) {
            ClassInfo classByName = index.getClassByName(DotName.createSimple(clazz));
            if (classByName != null) {
                FieldInfo field = classByName.field(fieldName);
                if (field != null) {
                    ClassType classType = retrieveInnerMostType(field.type());
                    if (classType != null) {
                        return classType.annotations().stream()
                                .filter(a -> a.name().equals(DotName.createSimple(annotationClazz)))
                                .findFirst().orElse(null);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Return true if the given method in a class has the specified annotation class name or a generic.
     *
     * @param clazz           {@link Class} to check for annotation
     * @param methodName      method name to check
     * @param annotationClazz the annotation {@link Class} to check
     * @return true if the given class, method and parameter has the specified annotation class name
     */
    protected boolean methodHasAnnotation(String clazz, String methodName, String annotationClazz) {
        return getMethodAnnotation(clazz, methodName, annotationClazz) != null;
    }

    /**
     * Return the {@link AnnotationInstance} for the given method in a class has the specified annotation class name or a generic.
     *
     * @param clazz           {@link Class} to check for annotation
     * @param methodName      method name to check
     * @param annotationClazz the annotation {@link Class} to check
     * @return {@link AnnotationInstance} or null if none present
     */
    protected AnnotationInstance getMethodAnnotation(String clazz, String methodName, String annotationClazz) {
        if (hasIndex()) {
            ClassInfo classByName = index.getClassByName(DotName.createSimple(clazz));
            if (classByName != null) {
                MethodInfo method = classByName.firstMethod(methodName);
                if (method != null) {
                    ClassType classType = retrieveInnerMostType(method.returnType());
                        if (classType == null) {
                            return null;
                        }
                        return classType.annotations().stream()
                                .filter(a -> a.name().equals(DotName.createSimple(annotationClazz)))
                                .findFirst().orElse(null);
                }
            }
        }
        return null;
    }

    /**
     * Retrieve the inner most type for a generic type.
     *
     * @param initialType the {@link Type} to add
     * @return a {@link ClassType} representing the inner most type for a generic type
     */
    private ClassType retrieveInnerMostType(Type initialType) {
        Type type = initialType;
        while (type instanceof ParameterizedType) {
            ParameterizedType pType = (ParameterizedType) type;
            List<Type> arguments = pType.arguments();
            int argumentSize = arguments.size();
            Type newType = arguments.size() > 0 ? arguments.get(argumentSize - 1) : null;
            if (newType instanceof ClassType) {
                return (ClassType) newType;
            }
            type = newType;
        }
        return null;
    }

    /**
     * Return a {@link Collection} of {@link Class}es which are implementors of a given class/interface and are not abstract.
     *
     * @param clazz {@link Class} to check for implementors
     * @return a {@link Collection} of {@link Class}es
     */
    public Collection<Class<?>> getKnownImplementors(String clazz) {
        return getKnownImplementors(clazz, false);
    }

    /**
     * Indicates if an index was found.
     *
     * @return true if an index was found
     */
    public boolean hasIndex() {
        return index != null;
    }

    /**
     * Return the generated {@link Index}.
     *
     * @return the generated {@link Index}
     */
    public Index getIndex() {
        return index;
    }

    /**
     * The index file used to load the index. (may not exist).
     *
     * @return index file used to load the index
     */
    public String getIndexFile() {
        return indexFile;
    }
}
