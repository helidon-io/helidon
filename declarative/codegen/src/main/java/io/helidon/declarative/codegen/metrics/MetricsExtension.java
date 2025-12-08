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

package io.helidon.declarative.codegen.metrics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.common.types.Annotated;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.service.codegen.RegistryCodegenContext;
import io.helidon.service.codegen.RegistryRoundContext;
import io.helidon.service.codegen.spi.RegistryCodegenExtension;

import static io.helidon.declarative.codegen.metrics.MetricsTypes.ANNOTATION_COUNTED;
import static io.helidon.declarative.codegen.metrics.MetricsTypes.ANNOTATION_GAUGE;
import static io.helidon.declarative.codegen.metrics.MetricsTypes.ANNOTATION_TAG;
import static io.helidon.declarative.codegen.metrics.MetricsTypes.ANNOTATION_TAGS;
import static io.helidon.declarative.codegen.metrics.MetricsTypes.ANNOTATION_TIMED;
import static io.helidon.declarative.codegen.metrics.MetricsTypes.TAG;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_SINGLETON;

class MetricsExtension implements RegistryCodegenExtension {
    static final TypeName GENERATOR = TypeName.create(MetricsExtension.class);

    private final RegistryCodegenContext ctx;

    MetricsExtension(RegistryCodegenContext ctx) {
        this.ctx = ctx;
    }

    static String scope(Annotation annotation) {
        return annotation.stringValue("scope").orElse("application");
    }

    static List<Tag> tags(RegistryRoundContext roundContext,
                          TypeName enclosingType,
                          TypedElementInfo element,
                          Annotation annotation) {
        List<Tag> tags = new ArrayList<>(typeTags(roundContext, enclosingType));
        tags.addAll(elementTags(element));
        tags.addAll(toTags(annotation.annotationValues("tags").orElseGet(List::of)));

        return tags;
    }

    static String name(Annotation annotation, TypeName enclosingType, TypedElementInfo element) {
        String name = annotation.stringValue()
                .filter(Predicate.not(String::isBlank))
                .orElse(element.elementName());

        boolean absolute = annotation.booleanValue("absoluteName").orElse(false);
        return absolute ? name : enclosingType.className() + "." + name;
    }

    static String description(Annotation annotation,
                              TypeName annotationType,
                              TypeName enclosingType,
                              TypedElementInfo element) {
        return annotation.stringValue("description")
                .filter(Predicate.not(String::isBlank))
                .orElseGet(() -> annotationType.classNameWithEnclosingNames()
                        + " annotation on method " + enclosingType.className()
                        + "." + element.signature().text());
    }

    static void addTagsToBuilder(ContentBuilder<?> contentBuilder, List<Tag> tags) {
        if (tags.isEmpty()) {
            return;
        }

        contentBuilder.addContent(".tags(")
                .addContent(List.class)
                .addContent(".of(");

        Iterator<Tag> it = tags.iterator();
        while (it.hasNext()) {
            var tag = it.next();
            contentBuilder
                    .addContent(TAG)
                    .addContent(".create(")
                    .addContentLiteral(tag.key())
                    .addContent(", ")
                    .addContentLiteral(tag.value())
                    .addContent(")");
            if (it.hasNext()) {
                contentBuilder.addContent(", ");
            }
        }
        contentBuilder.addContentLine("))");

    }

    @Override
    public void process(RegistryRoundContext roundContext) {
        Map<TypeName, TypeInfo> types = new HashMap<>();
        for (TypeInfo info : roundContext.types()) {
            types.put(info.typeName(), info);
        }

        generateGaugeRegistrars(roundContext, types);
        generateCountedInterceptors(roundContext, types);
        generateTimedInterceptors(roundContext, types);
    }

    private static List<Tag> typeTags(RegistryRoundContext roundContext, TypeName enclosingType) {
        Optional<TypeInfo> maybeType = roundContext.typeInfo(enclosingType);
        if (maybeType.isEmpty()) {
            return List.of();
        }
        return elementTags(maybeType.get());
    }

    private static List<Tag> elementTags(Annotated annotated) {
        if (annotated.hasAnnotation(ANNOTATION_TAG)) {
            return toTags(List.of(annotated.annotation(ANNOTATION_TAG)));
        }

        if (annotated.hasAnnotation(ANNOTATION_TAGS)) {
            return toTags(annotated.annotation(ANNOTATION_TAGS)
                                  .annotationValues()
                                  .orElseGet(List::of));
        }
        return List.of();
    }

    private static List<Tag> toTags(List<Annotation> tags) {
        return tags.stream()
                .map(tag -> new Tag(tag.stringValue("key").orElse(""),
                                    tag.stringValue("value").orElse("")))
                .toList();
    }

    private void generateCountedInterceptors(RegistryRoundContext roundContext, Map<TypeName, TypeInfo> types) {
        Collection<TypedElementInfo> elements = roundContext.annotatedElements(ANNOTATION_COUNTED);
        Map<TypeName, AtomicInteger> counters = new HashMap<>();
        types.keySet()
                .forEach(it -> counters.put(it, new AtomicInteger()));

        CountedHandler countedHandler = new CountedHandler(roundContext);

        for (TypedElementInfo element : elements) {
            TypeName enclosingType = enclosingType(element);
            int counter = counters.computeIfAbsent(enclosingType, k -> new AtomicInteger())
                    .getAndIncrement();

            TypeInfo typeInfo = types.get(enclosingType);
            if (typeInfo == null) {
                typeInfo = ctx.typeInfo(enclosingType).orElseThrow(() -> new CodegenException("No type info found for type "
                                                                                                      + enclosingType.fqName()));
            }
            checkTypeIsService(roundContext, typeInfo);
            countedHandler.handle(typeInfo, element, counter);
        }
    }

    private void generateTimedInterceptors(RegistryRoundContext roundContext, Map<TypeName, TypeInfo> types) {
        Collection<TypedElementInfo> elements = roundContext.annotatedElements(ANNOTATION_TIMED);
        Map<TypeName, AtomicInteger> counters = new HashMap<>();
        types.keySet()
                .forEach(it -> counters.put(it, new AtomicInteger()));

        TimedHandler handler = new TimedHandler(roundContext);

        for (TypedElementInfo element : elements) {
            TypeName enclosingType = enclosingType(element);
            int counter = counters.computeIfAbsent(enclosingType, k -> new AtomicInteger())
                    .getAndIncrement();

            TypeInfo typeInfo = types.get(enclosingType);
            if (typeInfo == null) {
                typeInfo = ctx.typeInfo(enclosingType).orElseThrow(() -> new CodegenException("No type info found for type "
                                                                                                      + enclosingType.fqName()));
            }
            checkTypeIsService(roundContext, typeInfo);
            handler.handle(typeInfo, element, counter);
        }
    }

    private void generateGaugeRegistrars(RegistryRoundContext roundContext, Map<TypeName, TypeInfo> types) {
        Map<TypeName, List<Gauge>> gaugeByType = new HashMap<>();

        addGauges(roundContext, gaugeByType);

        GaugeHandler handler = new GaugeHandler(roundContext);

        gaugeByType.forEach((type, gauges) -> {
            TypeInfo typeInfo = types.get(type);
            if (typeInfo == null) {
                typeInfo = roundContext.typeInfo(type).orElseThrow(() -> new CodegenException("No type info found for type "
                                                                                                      + type.fqName()));
            }
            checkTypeIsService(roundContext, typeInfo);
            handler.handle(typeInfo, gauges);
        });
    }

    private void checkTypeIsService(RegistryRoundContext roundContext, TypeInfo typeInfo) {
        Optional<ClassModel.Builder> descriptor = roundContext.generatedType(ctx.descriptorType(typeInfo.typeName()));
        if (descriptor.isEmpty()) {
            // we may be in CDI (as we support the annotations on both CDI beans and Helidon Declarative Services)
            // let's just check there is any annotation on the type
            if (typeInfo.annotations().isEmpty()) {
                throw new CodegenException("Type annotated with one of the metrics annotations is not a service itself."
                                                   + " It must be annotated with "
                                                   + SERVICE_ANNOTATION_SINGLETON.classNameWithEnclosingNames() + ","
                                                   + " or it must be a CDI bean (in Helidon MP).",
                                           typeInfo.originatingElementValue());
            }
        }
    }

    private TypeName enclosingType(TypedElementInfo element) {
        Optional<TypeName> enclosingType = element.enclosingType();
        if (enclosingType.isEmpty()) {
            throw new CodegenException("Element " + element + " does not have an enclosing type",
                                       element.originatingElementValue());
        }
        return enclosingType.get();
    }

    private void addGauges(RegistryRoundContext roundContext,
                           Map<TypeName, List<Gauge>> gaugesByType) {
        Collection<TypedElementInfo> elements = roundContext.annotatedElements(ANNOTATION_GAUGE);
        for (TypedElementInfo element : elements) {
            TypeName enclosingType = enclosingType(element);
            var allGauges = gaugesByType.computeIfAbsent(enclosingType, k -> new ArrayList<>());
            processGauge(roundContext, allGauges, enclosingType, element);
        }
    }

    private void processGauge(RegistryRoundContext roundContext,
                              List<Gauge> allGauges,
                              TypeName enclosingType,
                              TypedElementInfo element) {
        /*
        Tags are collected from:
        - the type
        - the method
        - annotation tags (so we can have different tags for different annotations on a single method)
         */

        String methodName = element.elementName();

        // read annotation values
        Annotation annotation = element.annotation(ANNOTATION_GAUGE);
        String scope = scope(annotation);
        String description = description(annotation, ANNOTATION_GAUGE, enclosingType, element);
        String unit = annotation.stringValue("unit").orElse("none");

        String gaugeName = name(annotation, enclosingType, element);
        List<Tag> tags = tags(roundContext, enclosingType, element, annotation);

        TypeName typeName = element.typeName();

        // add for processing
        allGauges.add(new Gauge(methodName,
                                typeName,
                                gaugeName,
                                description,
                                unit,
                                scope,
                                tags));
    }

    record Gauge(
            String methodName,
            TypeName returnType,
            String name,
            String description,
            String unit,
            String scope,
            List<Tag> tags) {
    }

    record Tag(String key, String value) {
    }
}
