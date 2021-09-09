/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ClassRefTypeSignature;
import io.github.classgraph.MethodInfo;
import io.github.classgraph.MethodInfoList;
import io.github.classgraph.MethodParameterInfo;
import io.github.classgraph.TypeArgument;
import io.github.classgraph.TypeSignature;

/*
 * Analyzes JAX-RS resource method signatures and returns all types to be added for reflection.
 */
class JaxRsMethodAnalyzer {
    /*
     what we ignore:
     - JsonObject and JsonArray - part of JSON-P handling, which works without reflection
     - Response - part of JAX-RS and works already
     - String - no need to add it
     - Map, List, Set - basic collection classes - we only care about the type parameter, not the collection itself
     */
    private static final Set<String> IGNORED_TYPES = Set.of("javax.json.JsonObject",
                                                            "javax.json.JsonArray",
                                                            "javax.ws.rs.core.Response",
                                                            "java.lang.String",
                                                            Map.class.getName(),
                                                            List.class.getName(),
                                                            Set.class.getName());

    private static final String HTTP_METHOD_ANNOTATION = "javax.ws.rs.HttpMethod";

    private final Set<String> classesToAdd = new HashSet<>();
    private final HelidonReflectionFeature.BeforeAnalysisContext context;
    private final NativeUtil nativeUtil;

    JaxRsMethodAnalyzer(HelidonReflectionFeature.BeforeAnalysisContext context,
                        NativeUtil nativeUtil) {
        this.context = context;
        this.nativeUtil = nativeUtil;
    }

    Set<Class<?>> find() {
        ClassInfoList classes = context.scan()
                .getClassesWithMethodAnnotation(HTTP_METHOD_ANNOTATION);

        for (ClassInfo aClass : classes) {
            MethodInfoList methods = aClass.getMethodInfo();
            for (MethodInfo method : methods) {
                if (method.hasAnnotation(HTTP_METHOD_ANNOTATION)) {
                    add(method.getTypeSignatureOrTypeDescriptor()
                                .getResultType());

                    MethodParameterInfo[] parameterInfo = method.getParameterInfo();
                    for (MethodParameterInfo param : parameterInfo) {
                        if (param.getAnnotationInfo().isEmpty()) {
                            add(param.getTypeSignatureOrTypeDescriptor());
                        }
                    }
                }
            }
        }
        Set<String> result = Set.copyOf(classesToAdd);
        classesToAdd.clear();

        return result.stream()
                .map(nativeUtil.classMapper("jaxrs-result-or-param"))
                .filter(Objects::nonNull)
                .filter(nativeUtil.inclusionFilter("jaxrs-result-or-param"))
                .collect(Collectors.toSet());
    }

    void add(TypeSignature type) {
        if (type instanceof ClassRefTypeSignature) {
            ClassRefTypeSignature crts = (ClassRefTypeSignature) type;
            if (add(crts.getFullyQualifiedClassName())) {
                List<TypeArgument> typeArgs = crts.getTypeArguments();
                if (typeArgs != null && !typeArgs.isEmpty()) {
                    typeArgs.forEach(it -> add(it.getTypeSignature()));
                }
            }
        } else {
            add(type.toString());
        }
    }

    boolean add(String type) {
        if (!IGNORED_TYPES.contains(type)) {
            return classesToAdd.add(type);
        }
        return true;
    }
}
