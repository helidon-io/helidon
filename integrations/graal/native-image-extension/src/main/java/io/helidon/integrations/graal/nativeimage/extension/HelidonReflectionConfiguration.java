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

import java.lang.reflect.Array;
import java.net.URL;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReaderFactory;
import javax.json.stream.JsonParsingException;

import org.graalvm.nativeimage.hosted.Feature;

final class HelidonReflectionConfiguration {
    private final Set<Class<?>> annotations = new LinkedHashSet<>();
    private final Set<Class<?>> hierarchy = new LinkedHashSet<>();
    private final Set<Class<?>> fullHierarchy = new LinkedHashSet<>();
    private final Set<Class<?>> classes = new LinkedHashSet<>();
    private final Set<Class<?>> excluded = new HashSet<>();

    static HelidonReflectionConfiguration load(Feature.BeforeAnalysisAccess access,
                                               ClassLoader cl,
                                               NativeTrace tracer) {
        try {
            Enumeration<URL> resources = cl.getResources("META-INF/helidon/native-image/reflection-config.json");
            HelidonReflectionConfiguration config = new HelidonReflectionConfiguration();
            JsonReaderFactory readerFactory = Json.createReaderFactory(Map.of());
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                try {
                    JsonObject configurationJson = readerFactory.createReader(url.openStream()).readObject();
                    jsonArray(tracer, access, config.annotations, configurationJson.getJsonArray("annotated"), "Annotation");
                    jsonArray(tracer,
                              access,
                              config.hierarchy,
                              configurationJson.getJsonArray("class-hierarchy"),
                              "Class hierarchy");
                    jsonArray(tracer, access,
                              config.fullHierarchy,
                              configurationJson.getJsonArray("full-class-hierarchy"),
                              "Full class hierarchy");
                    jsonArray(tracer, access, config.classes, configurationJson.getJsonArray("classes"), "Single");
                    jsonArray(tracer, access, config.excluded, configurationJson.getJsonArray("exclude"), "Exclude");
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

    private static void jsonArray(NativeTrace tracer,
                                  Feature.BeforeAnalysisAccess access,
                                  Collection<Class<?>> classList,
                                  JsonArray classNames,
                                  String desc) {
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
                tracer.parsing(() -> desc + " class \"" + logName + "\" configured for reflection is not on classpath");
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

    Set<Class<?>> annotations() {
        return annotations;
    }

    Set<Class<?>> hierarchy() {
        return hierarchy;
    }

    Set<Class<?>> fullHierarchy() {
        return fullHierarchy;
    }

    Set<Class<?>> classes() {
        return classes;
    }

    Set<Class<?>> excluded() {
        return excluded;
    }
}
