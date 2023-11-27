/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.codegen;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.common.types.TypeName;

/**
 * An abstraction for writing out source files and resource files.
 * Always attempts to create a new file and replace its content (as it is impossible to update files in annotation processing).
 */
public interface CodegenFiler {
    /**
     * Write a source file from its {@link io.helidon.codegen.classmodel.ClassModel}.
     *
     * @param classModel          class model to write out
     * @param originatingElements elements that caused this type to be generated
     *                            (you can use {@link io.helidon.common.types.TypeInfo#originatingElement()} for example
     * @return written path, we expect to always run on local file system
     */
    Path writeSourceFile(ClassModel classModel, Object... originatingElements);

    /**
     * Write a resource file.
     *
     * @param resource bytes of the resource file
     * @param location location to write to in the classes output directory
     * @param originatingElements elements that caused this type to be generated
     * @return written path, we expect to always run on local file system
     */
    Path writeResource(byte[] resource, String location, Object... originatingElements);

    /**
     * Write a {@code META-INF/services} file for a specific provider interface and implementation(s).
     *
     * @param generator type of the generator (to mention in the generated code)
     * @param providerInterface type of the provider interface (and also name of the file to be generated)
     * @param providers list of provider implementations to add to the file
     * @param originatingElements elements that caused this type to be generated
     */
    default void services(TypeName generator,
                          TypeName providerInterface,
                          List<TypeName> providers,
                          Object... originatingElements) {
        Objects.requireNonNull(generator);
        Objects.requireNonNull(providerInterface);
        Objects.requireNonNull(providers);

        String location = "META-INF/services/" + providerInterface.fqName();
        if (providers.isEmpty()) {
            throw new CodegenException("List of providers is empty, cannot generate " + location);
        }
        byte[] resourceBytes = (
                "# " + GeneratedAnnotationHandler.create(generator,
                                                         providers.getFirst(),
                                                         TypeName.create(
                                                                 "MetaInfServicesModuleComponent"),
                                                         "1",
                                                         "")
                        + "\n"
                        + providers.stream()
                        .map(TypeName::declaredName)
                        .collect(Collectors.joining("\n")))
                .getBytes(StandardCharsets.UTF_8);

        writeResource(resourceBytes,
                      location,
                      originatingElements);
    }
}
