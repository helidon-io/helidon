/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.service.codegen;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenFiler;
import io.helidon.codegen.FilerTextResource;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;

import static io.helidon.codegen.CodegenUtil.generatedAnnotation;

/**
 * Support for reading and writing Helidon services to the resource.
 * <p>
 * Helidon replacement for Java {@link java.util.ServiceLoader}.
 * Each service annotated with appropriate annotation
 * ({@link io.helidon.service.codegen.ServiceCodegenTypes#SERVICE_ANNOTATION_PROVIDER})
 * will have a service descriptor generated at build time.
 * <p>
 * The service descriptor is then discoverable at runtime through our own resource in {@value #SERVICES_RESOURCE}.
 */
public class HelidonMetaInfServices {
    /**
     * Location of the Helidon meta services file.
     */
    static final String SERVICES_RESOURCE = "META-INF/helidon/service.registry";

    private final FilerTextResource services;
    private final List<String> comments;
    private final Set<DescriptorMeta> descriptors;

    private HelidonMetaInfServices(FilerTextResource services, List<String> comments, Set<DescriptorMeta> descriptors) {
        this.services = services;
        this.comments = comments;
        this.descriptors = descriptors;
    }

    /**
     * Create new instance from the current filer.
     *
     * @param filer      filer to find the file, and to write it
     * @param generator  generator used in generated comment if we are creating a new file
     * @param trigger    trigger that caused this file to be created (can be same as generator)
     * @param moduleName module that is being built
     * @return a new instance of the service metadata manager
     */
    public static HelidonMetaInfServices create(CodegenFiler filer, TypeName generator, TypeName trigger, String moduleName) {
        FilerTextResource services = filer.textResource(SERVICES_RESOURCE);

        List<String> comments = new ArrayList<>();
        Set<DescriptorMeta> descriptors = new TreeSet<>(Comparator.comparing(DescriptorMeta::descriptor));

        for (String line : services.lines()) {
            String trimmedLine = line.trim();
            if (trimmedLine.startsWith("#")) {
                comments.add(line);
            } else if (trimmedLine.isEmpty()) {
                // ignore empty lines
                continue;
            } else {
                descriptors.add(DescriptorMeta.parse(trimmedLine));
            }
        }

        if (comments.isEmpty()) {
            // @Generated
            comments.add("# Generated list of service descriptors in module " + moduleName);
            comments.add("# " + toAnnotationText(generatedAnnotation(generator,
                                                                     trigger,
                                                                     TypeName.create("io.helidon.services.ServicesMeta"),
                                                                     "1",
                                                                     "")));
        }
        return new HelidonMetaInfServices(services, comments, descriptors);
    }

    /**
     * Add all descriptors to the file. This never produces duplicate records.
     * Descriptor type name is always unique within a file.
     *
     * @param services service descriptor metadata to add
     */
    public void addAll(Collection<DescriptorMeta> services) {
        services.forEach(this::add);
    }

    /**
     * Add a single descriptors to the file. This never produces duplicate records.
     * Descriptor type name is always unique within a file.
     *
     * @param service service descriptor metadata to add
     */
    public void add(DescriptorMeta service) {
        // if it is the same descriptor class, remove it
        descriptors.removeIf(it -> it.descriptor().equals(service.descriptor()));

        // always add the new descriptor (either it does not exist, or it was deleted)
        descriptors.add(service);
    }

    /**
     * Write the file to output.
     */
    public void write() {
        List<String> lines = new ArrayList<>(comments);
        descriptors.stream()
                .map(DescriptorMeta::toLine)
                .forEach(lines::add);
        services.lines(lines);
        services.write();
    }

    private static String toAnnotationText(Annotation annotation) {
        List<String> valuePairs = new ArrayList<>();
        Map<String, Object> annotationValues = annotation.values();
        annotationValues.forEach((key, value) -> valuePairs.add(key + "=\"" + value + "\""));
        return "@" + annotation.typeName().fqName() + "(" + String.join(", ", valuePairs) + ")";
    }

    /**
     * Metadata of a single service descriptor.
     * This information is stored within the Helidon specific {code META-INF} services file.
     *
     * @param registryType type of registry, such as {@code core}
     * @param descriptor   descriptor type
     * @param weight       weight of the service
     * @param contracts    contracts the service implements/provides
     */
    public record DescriptorMeta(String registryType, TypeName descriptor, double weight, Set<TypeName> contracts) {
        private static DescriptorMeta parse(String line) {
            String[] elements = line.split(":");
            if (elements.length < 4) {
                throw new CodegenException("Failed to parse line from existing META-INF/helidon/service.registry: "
                                                   + line + ", expecting registry-type:serviceDescriptor:weight:contracts");
            }
            Set<TypeName> contracts = Stream.of(elements[3].split(","))
                    .map(String::trim)
                    .map(TypeName::create)
                    .collect(Collectors.toSet());

            try {
                return new DescriptorMeta(elements[0],
                                          TypeName.create(elements[1]),
                                          Double.parseDouble(elements[2]),
                                          contracts);
            } catch (NumberFormatException e) {
                throw new CodegenException("Unexpected line structure in service.registry. Third element should be weight "
                                                   + "(double). Got: " + line + ", message: " + e.getMessage(), e);
            }
        }

        private String toLine() {
            return registryType + ":"
                    + descriptor.fqName() + ":"
                    + weight + ":"
                    + contracts.stream()
                    .map(TypeName::fqName)
                    .collect(Collectors.joining(","));
        }
    }
}
