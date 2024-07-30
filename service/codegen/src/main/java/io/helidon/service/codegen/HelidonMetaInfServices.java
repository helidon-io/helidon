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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import io.helidon.codegen.CodegenFiler;
import io.helidon.codegen.FilerResource;
import io.helidon.metadata.hson.Hson;
import io.helidon.service.metadata.DescriptorMetadata;
import io.helidon.service.metadata.Descriptors;

import static io.helidon.service.metadata.Descriptors.SERVICE_REGISTRY_LOCATION;

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
class HelidonMetaInfServices {
    private final FilerResource services;
    private final String moduleName;
    private final Set<DescriptorMetadata> descriptors;

    private HelidonMetaInfServices(FilerResource services, String moduleName, Set<DescriptorMetadata> descriptors) {
        this.services = services;
        this.moduleName = moduleName;
        this.descriptors = descriptors;
    }

    /**
     * Create new instance from the current filer.
     *
     * @param filer      filer to find the file, and to write it
     * @param moduleName module that is being built
     * @return a new instance of the service metadata manager
     */
    static HelidonMetaInfServices create(CodegenFiler filer, String moduleName) {
        FilerResource serviceRegistryMetadata = filer.resource(SERVICE_REGISTRY_LOCATION);
        byte[] bytes = serviceRegistryMetadata.bytes();

        Set<DescriptorMetadata> descriptors = new TreeSet<>(Comparator.comparing(DescriptorMetadata::descriptorType));

        if (bytes.length != 0) {
            Hson.Array moduleRegistry = Hson.parse(new ByteArrayInputStream(bytes))
                    .asArray();
            descriptors.addAll(Descriptors.descriptors("helidon-service-codegen for " + moduleName,
                                                       moduleRegistry));
        }

        return new HelidonMetaInfServices(serviceRegistryMetadata, moduleName, descriptors);
    }

    /**
     * Add all descriptors to the file. This never produces duplicate records.
     * Descriptor type name is always unique within a file.
     *
     * @param services service descriptor metadata to add
     */
    void addAll(Collection<DescriptorMetadata> services) {
        services.forEach(this::add);
    }

    /**
     * Add a single descriptors to the file. This never produces duplicate records.
     * Descriptor type name is always unique within a file.
     *
     * @param service service descriptor metadata to add
     */
    void add(DescriptorMetadata service) {
        // if it is the same descriptor class, remove it
        descriptors.removeIf(it -> it.descriptorType().equals(service.descriptorType()));

        // always add the new descriptor (either it does not exist, or it was deleted)
        descriptors.add(service);
    }

    /**
     * Write the file to output.
     */
    void write() {
        var root = Hson.objectBuilder()
                .set("module", moduleName);
        List<Hson.Object> servicesHson = new ArrayList<>();

        descriptors.stream()
                .map(DescriptorMetadata::toHson)
                .forEach(servicesHson::add);

        root.setObjects("services", servicesHson);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (var pw = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8))) {
            Hson.Array.create(List.of(root.build()))
                    .write(pw);
        }

        services.bytes(baos.toByteArray());
        services.write();
    }
}
