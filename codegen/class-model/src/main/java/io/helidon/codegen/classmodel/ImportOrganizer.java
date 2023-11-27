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
package io.helidon.codegen.classmodel;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.common.types.TypeName;

class ImportOrganizer {

    private final List<List<String>> importsToWrite;
    private final List<List<String>> staticImportsToWrite;
    //Set of all imports to make it easier to go through when checking what import name should be used.
    private final Set<String> imports;
    private final Set<String> noImport;
    private final Set<String> forcedFullImports;
    private final Map<String, String> identifiedInnerClasses;

    private ImportOrganizer(Builder builder) {
        this.importsToWrite = ImportSorter.sortImports(builder.finalImports.values());
        this.staticImportsToWrite = ImportSorter.sortImports(builder.staticImports.stream()
                                                                     .map(Type::fqTypeName)
                                                                     .toList());
        this.imports = Set.copyOf(builder.finalImports.values());
        this.noImport = builder.noImports.values()
                .stream()
                .map(Type::fqTypeName)
                .collect(Collectors.toSet());
        this.forcedFullImports = Set.copyOf(builder.forcedFullImports);
        this.identifiedInnerClasses = Map.copyOf(builder.identifiedInnerClasses);
    }

    static Builder builder() {
        return new Builder();
    }

    String typeName(Type type, boolean includedImport) {
        if (type instanceof TypeArgument) {
            return type.fqTypeName();
        }
        Type checkedType = type.declaringClass().orElse(type);
        String fullTypeName = checkedType.fqTypeName();
        String simpleTypeName = checkedType.simpleTypeName();

        if (!includedImport) {
            return fullTypeName;
        }
        if (forcedFullImports.contains(fullTypeName)) {
            return type.fqTypeName();
        } else if (noImport.contains(fullTypeName) || imports.contains(fullTypeName)) {
            return identifiedInnerClasses.getOrDefault(type.fqTypeName(), simpleTypeName);
        }
        return identifiedInnerClasses.getOrDefault(type.fqTypeName(), type.fqTypeName());
    }

    void writeImports(ModelWriter writer) throws IOException {
        if (!importsToWrite.isEmpty()) {
            for (List<String> importGroup : importsToWrite) {
                for (String importName : importGroup) {
                    writer.writeLine("import " + importName + ";");
                }
                if (!importGroup.isEmpty()) {
                    writer.writeSeparatorLine();
                }
            }
        }
    }

    void writeStaticImports(ModelWriter writer) throws IOException {
        if (!staticImportsToWrite.isEmpty()) {
            for (List<String> importGroup : staticImportsToWrite) {
                for (String importName : importGroup) {
                    writer.writeLine("import static " + importName + ";");
                }
                if (!importGroup.isEmpty()) {
                    writer.writeSeparatorLine();
                }
            }
        }
    }

    List<String> imports() {
        return importsToWrite.stream()
                .flatMap(List::stream)
                .toList();
    }

    static final class Builder implements io.helidon.common.Builder<Builder, ImportOrganizer> {

        private final Set<Type> imports = new HashSet<>();
        private final Set<Type> staticImports = new HashSet<>();

        /**
         * Class imports.
         */
        private final Map<String, String> finalImports = new HashMap<>();

        /**
         * Imports from "java.lang" package or classes within the same package.
         * They should be monitored for name collisions, but not included in class imports.
         */
        private final Map<String, Type> noImports = new HashMap<>();

        /**
         * Collection for class names with colliding simple names.
         * The first registered will be used as import. The later ones have to be used as full names.
         */
        private final Set<String> forcedFullImports = new HashSet<>();

        /**
         * Map of known inner classes.
         */
        private final Map<String, String> identifiedInnerClasses = new HashMap<>();

        private String packageName = "";
        private String typeName;

        private Builder() {
        }

        Builder packageName(String packageName) {
            this.packageName = packageName;
            return this;
        }

        Builder typeName(String typeName) {
            this.typeName = typeName;
            return this;
        }

        Builder type(TypeName type) {
            this.typeName = type.className();
            this.packageName = type.packageName();
            return this;
        }

        Builder addImport(String type) {
            return addImport(TypeName.create(type));
        }

        Builder addImport(Class<?> type) {
            return addImport(TypeName.create(type));
        }

        Builder addImport(TypeName type) {
            return addImport(Type.fromTypeName(type.genericTypeName()));
        }

        Builder addImport(Type type) {
            imports.add(type);
            return this;
        }

        Builder addStaticImport(String type) {
            return addStaticImport(TypeName.create(type));
        }

        Builder addStaticImport(Class<?> type) {
            return addStaticImport(TypeName.create(type));
        }

        Builder addStaticImport(TypeName type) {
            staticImports.add(Type.fromTypeName(type));
            return this;
        }

        Builder from(ImportOrganizer.Builder builder) {
            this.imports.addAll(builder.imports);
            this.staticImports.addAll(builder.staticImports);
            return this;
        }

        @Override
        public ImportOrganizer build() {
            if (typeName == null) {
                throw new ClassModelException("Import organizer requires to have built type name specified.");
            }
            finalImports.clear();
            forcedFullImports.clear();
            noImports.clear();
            resolveFinalImports();
            return new ImportOrganizer(this);
        }

        private void resolveFinalImports() {
            for (Type type : imports) {
                //If processed type is inner class, we will be importing parent class
                Type typeToProcess = type.declaringClass().orElse(type);
                String fqTypeName = typeToProcess.fqTypeName();
                String typePackage = typeToProcess.packageName();
                String typeSimpleName = typeToProcess.simpleTypeName();

                if (type.innerClass()) {
                    if (typeToProcess.innerClass()) {
                        identifiedInnerClasses.put(type.fqTypeName(), fqTypeName + "." + type.simpleTypeName());
                    } else {
                        identifiedInnerClasses.put(type.fqTypeName(), typeSimpleName + "." + type.simpleTypeName());
                    }
                }

                if (typePackage.equals("java.lang")) {
                    //imported class is from java.lang package -> automatically imported
                    processImportJavaLang(type, fqTypeName, typeSimpleName);
                } else if (this.packageName.equals(typePackage)) {
                    processImportSamePackage(type, fqTypeName, typeSimpleName);
                } else if (finalImports.containsKey(typeSimpleName)
                        && !finalImports.get(typeSimpleName).equals(fqTypeName)) {
                    //If there is imported class with this simple name already, but it is not in the same package as this one
                    //add this newly added among the forced full names
                    forcedFullImports.add(fqTypeName);
                } else if (noImports.containsKey(typeSimpleName)) {
                    //There is already class with the same name present in the package we are generating to
                    //or imported from java.lang
                    forcedFullImports.add(fqTypeName);
                } else if (typeName.equals(typeSimpleName)) {
                    //If the processed class name is the same as the one currently built.
                    forcedFullImports.add(fqTypeName);
                } else if (!typePackage.isEmpty()) {
                    finalImports.put(typeSimpleName, fqTypeName);
                }
            }
        }

        private void processImportJavaLang(Type type, String typeName, String typeSimpleName) {
            //new class is from java.lang package
            if (finalImports.containsKey(typeSimpleName)) {
                //some other class with the same name is already being imported (but with the different package)
                //remove that previously added class from imports and place it to the list of forced full class names
                forcedFullImports.add(finalImports.remove(typeSimpleName));
            } else if (noImports.containsKey(typeSimpleName)
                    && !noImports.get(typeSimpleName).fqTypeName().equals(typeName)) {
                //if there is already class with the same name, but different package, added among the imports,
                // and it does not need import specified (java.lang and the same package), remove it from the exception
                // list and add it among forced imports.
                forcedFullImports.add(typeName);
                return;
            }
            noImports.put(typeSimpleName, type);
        }

        private void processImportSamePackage(Type type, String typeName, String typeSimpleName) {
            String simpleName = typeSimpleName;
            if (this.typeName.equals(simpleName)) {
                simpleName = type.simpleTypeName();
                if (noImports.containsKey(simpleName)
                        && !noImports.get(simpleName).fqTypeName().equals(type.fqTypeName())) {
                    forcedFullImports.add(noImports.remove(simpleName).fqTypeName());
                }
            }
            if (finalImports.containsKey(simpleName)) {
                //There is a class among general imports which match the currently added class name.
                forcedFullImports.add(finalImports.remove(simpleName));
                noImports.put(simpleName, type);
            } else if (noImports.containsKey(simpleName)) {
                //There is already specialized handling of a class with this name
                if (!noImports.get(simpleName).fqTypeName().equals(typeName)) {
                    forcedFullImports.add(noImports.remove(simpleName).fqTypeName());
                    noImports.put(simpleName, type);
                }
            } else {
                noImports.put(simpleName, type);
            }
        }
    }

}
