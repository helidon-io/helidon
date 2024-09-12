/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
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
     *                            (you can use {@link io.helidon.common.types.TypeInfo#originatingElementValue()})
     * @return written path, we expect to always run on local file system
     */
    Path writeSourceFile(ClassModel classModel, Object... originatingElements);

    /**
     * Write a source file using string content.
     *
     * @param type type of the file to generate
     * @param content source code to write
     * @param originatingElements elements that caused this type to be generated
     * @return written path, we expect to always run on local file system
     */
    Path writeSourceFile(TypeName type, String content, Object... originatingElements);

    /**
     * Write a resource file.
     *
     * @param resource bytes of the resource file
     * @param location location to write to in the classes output directory
     * @param originatingElements elements that caused this file to be generated
     * @return written path, we expect to always run on local file system
     */
    Path writeResource(byte[] resource, String location, Object... originatingElements);

    /**
     * A text resource that can be updated in the output.
     * Note that the resource can only be written once per processing round.
     *
     * @param location            location to read/write to in the classes output directory
     * @param originatingElements elements that caused this file to be generated
     * @return the resource that can be used to update the file
     */
    default FilerTextResource textResource(String location, Object... originatingElements) {
        throw new UnsupportedOperationException("Method textResource not implemented yet on " + getClass().getName());
    }

    /**
     * A text resource that can be updated in the output.
     * Note that the resource can only be written once per processing round.
     *
     * @param location            location to read/write to in the classes output directory
     * @param originatingElements elements that caused this file to be generated
     * @return the resource that can be used to update the file
     */
    default FilerResource resource(String location, Object... originatingElements) {
        throw new UnsupportedOperationException("Method resource not implemented yet on " + getClass().getName());
    }

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

        if (providers.isEmpty()) {
            // do not write services file if there is no provider added
            return;
        }

        String location = "META-INF/services/" + providerInterface.fqName();

        var resource = textResource(location, originatingElements);

        List<String> lines = new ArrayList<>(resource.lines());
        Set<TypeName> existingServices = lines.stream()
                .map(String::trim)
                .filter(Predicate.not(it -> it.startsWith("#")))
                .map(TypeName::create)
                .collect(Collectors.toSet());

        if (lines.isEmpty()) {
            // @Generated
            lines.add("# " + GeneratedAnnotationHandler.create(generator,
                                                               providers.getFirst(),
                                                               TypeName.create(
                                                                       "MetaInfServicesModuleComponent"),
                                                               "1",
                                                               ""));
        }

        for (TypeName provider : providers) {
            if (existingServices.add(provider)) {
                // only add the provider if it does not yet exist
                lines.add(provider.fqName());
            }
        }

        resource.lines(lines);
        resource.write();
    }
}
