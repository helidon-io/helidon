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
package io.helidon.lra.rest;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JandexAnnotationResolver {

    public static Map<DotName, List<AnnotationInstance>> getAllAnnotationsFromClassInfoHierarchy(DotName name, Index index) {
        Map<DotName, List<AnnotationInstance>> annotations = new HashMap<>();

        if (name == null || name.equals(DotNames.OBJECT)) {
            return annotations;
        }

        ClassInfo classInfo = index.getClassByName(name);
        annotations.putAll(classInfo.annotations());
        annotations.putAll(getInterfaceAnnotations(classInfo.interfaceNames(), index));
        annotations.putAll(getAllAnnotationsFromClassInfoHierarchy(classInfo.superName(), index));

        return annotations;
    }

    private static Map<DotName, List<AnnotationInstance>> getInterfaceAnnotations(List<DotName> interfaceNames, Index index) {
        Map<DotName, List<AnnotationInstance>> annotations = new HashMap<>();
        ClassInfo interfaceClassInfo = null;

        for (DotName interfaceName : interfaceNames) {
            interfaceClassInfo = index.getClassByName(interfaceName);
            Map<DotName, List<AnnotationInstance>> interfaceAnnotations = interfaceClassInfo.annotations();
            annotations.forEach((k, v) -> interfaceAnnotations.merge(k, v, (v1, v2) -> {
                v1.addAll(v2);
                return v1;
            }));
        }

        return annotations;
    }
}
