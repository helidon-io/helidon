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

package io.helidon.pico.processor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;

import io.helidon.pico.tools.ToolsException;

/**
 * This class adds persistent tracking state to allow seamless full and/or incremental processing to be tracked.
 * <p>
 * For example, when incremental processing occurs the elements passed to process in all rounds will just be a subset of
 * all of the annotated services. The {@link PicoAnnotationProcessor}, however, will see this reduced subset and create
 * a {@link io.helidon.pico.api.ModuleComponent} only representative of that subset of classes (a mistake).
 * <p>
 * We use this processing state tracker to persist the list of generated activators much in the same way that
 * {@code META-INF/services} are tracked. A target scratch directory (i.e., target/pico) is used instead of the jar's target
 * classes directory for obvious reasons that we do not want to package this file, just use it for scratch keeping.
 * <p>
 * Usage:
 * <ol>
 *     <li>From the anno processor during its initialization use {@link #initializeFrom}</li>
 *     <li>As the processor processes any type that requires code generation, call {@link #processing(String)}</li>
 *     <li>Use {@link #removedTypeNames()} or {@link #remainingTypeNames()} as needed</li>
 *     <li>Finish by calling {@link #close()} to force persistence state to be (re)written out to disk</li>
 * </ol>
 */
class ProcessingTracker implements AutoCloseable {
    static final String DEFAULT_SCRATCH_FILE_NAME = "activators.lst";

    private final Path path;
    private final Set<String> allTypeNames;
    private final TypeElementFinder typeElementFinder;
    private final Set<String> foundOrProcessed = new LinkedHashSet<>();

    /**
     * Creates an instance using the given path to keep persistent state.
     *
     * @param persistentScratchPath the fully qualified path to carry the state
     * @param allLines all lines read at initialization
     * @param typeElementFinder the type element finder (e.g., {@link ProcessingEnvironment#getElementUtils})
     */
    ProcessingTracker(Path persistentScratchPath,
                      List<String> allLines,
                      TypeElementFinder typeElementFinder) {
        this.path = persistentScratchPath;
        this.allTypeNames = new LinkedHashSet<>(allLines);
        this.typeElementFinder = typeElementFinder;
    }

    public static ProcessingTracker initializeFrom(Path persistentScratchPath,
                                                   ProcessingEnvironment processingEnv) {
        List<String> allLines = List.of();
        File file = persistentScratchPath.toFile();
        if (file.exists() && file.canRead()) {
            try {
                allLines = Files.readAllLines(persistentScratchPath, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new ToolsException(e.getMessage(), e);
            }
        }
        return new ProcessingTracker(persistentScratchPath, allLines, toTypeElementFinder(processingEnv));
    }

    public ProcessingTracker processing(String typeName) {
        foundOrProcessed.add(Objects.requireNonNull(typeName));
        return this;
    }

    public Set<String> allTypeNamesFromInitialization() {
        return allTypeNames;
    }

    public Set<String> removedTypeNames() {
        Set<String> typeNames = new LinkedHashSet<>(allTypeNamesFromInitialization());
        typeNames.removeAll(remainingTypeNames());
        return typeNames;
    }

    public Set<String> remainingTypeNames() {
        Set<String> typeNames = new LinkedHashSet<>(allTypeNamesFromInitialization());
        typeNames.addAll(foundOrProcessed);
        typeNames.removeIf(typeName -> !found(typeName));
        return typeNames;
    }

    @Override
    public void close() throws IOException {
        path.getParent().toFile().mkdirs();
        Files.write(path, remainingTypeNames(), StandardCharsets.UTF_8);
    }

    private boolean found(String typeName) {
        return (typeElementFinder.apply(typeName) != null);
    }

    private static TypeElementFinder toTypeElementFinder(ProcessingEnvironment processingEnv) {
        return typeName -> processingEnv.getElementUtils().getTypeElement(typeName);
    }

    @FunctionalInterface
    interface TypeElementFinder extends Function<CharSequence, TypeElement> {
    }

}
