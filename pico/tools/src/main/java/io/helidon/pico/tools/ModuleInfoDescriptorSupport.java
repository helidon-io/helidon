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

package io.helidon.pico.tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.helidon.builder.api.Prototype;
import io.helidon.common.types.TypeName;

final class ModuleInfoDescriptorSupport {
    private ModuleInfoDescriptorSupport() {
    }

    /**
     * Loads and creates the {@code module-info} descriptor given its source file location.
     *
     * @param path the source path location for the module-info descriptor
     * @return the module-info descriptor
     * @throws ToolsException if there is any exception encountered
     */
    @Prototype.FactoryMethod
    static ModuleInfoDescriptor create(Path path) {
        return create(path, ModuleInfoOrdering.NATURAL_PRESERVE_COMMENTS);
    }

    /**
     * Loads and creates the {@code module-info} descriptor given its source file location and preferred ordering scheme.
     *
     * @param path      the source path location for the module-info descriptor
     * @param ordering  the ordering to apply
     * @return the module-info descriptor
     * @throws ToolsException if there is any exception encountered
     */
    @Prototype.FactoryMethod
    static ModuleInfoDescriptor create(Path path,
                                       ModuleInfoOrdering ordering) {
        try {
            String moduleInfo = Files.readString(path);
            return create(moduleInfo, ordering);
        } catch (IOException e) {
            throw new ToolsException("Unable to load: " + path, e);
        }
    }

    /**
     * Loads and creates the {@code module-info} descriptor given its literal source.
     *
     * @param moduleInfo the source
     * @return the module-info descriptor
     * @throws io.helidon.pico.tools.ToolsException if there is any exception encountered
     */
    @Prototype.FactoryMethod
    static ModuleInfoDescriptor create(String moduleInfo) {
        return create(moduleInfo, ModuleInfoOrdering.NATURAL_PRESERVE_COMMENTS);
    }

    /**
     * Loads and creates the {@code module-info} descriptor given its literal source and preferred ordering scheme.
     *
     * @param moduleInfo    the source
     * @param ordering      the ordering to apply
     * @return the module-info descriptor
     * @throws io.helidon.pico.tools.ToolsException if there is any exception encountered
     */
    @Prototype.FactoryMethod
    static ModuleInfoDescriptor create(String moduleInfo,
                                       ModuleInfoOrdering ordering) {
        ModuleInfoDescriptor.Builder descriptor = ModuleInfoDescriptor.builder();

        String clean = moduleInfo;
        List<String> comments = null;
        if (ModuleInfoOrdering.NATURAL_PRESERVE_COMMENTS == ordering) {
            comments = new ArrayList<>();
        } else {
            clean = moduleInfo.replaceAll("/\\*[^*]*(?:\\*(?!/)[^*]*)*\\*/|//.*", "");
        }

        // remove annotations
        clean = cleanModuleAnnotations(clean);

        boolean firstLine = true;
        Map<String, TypeName> importAliases = new LinkedHashMap<>();
        String line = null;
        try (BufferedReader reader = new BufferedReader(new StringReader(clean))) {
            while (null != (line = cleanLine(reader, comments, importAliases))) {
                if (firstLine && (comments != null) && comments.size() > 0) {
                    descriptor.headerComment(String.join("\n", comments));
                }
                firstLine = false;

                String[] split = line.split("\\s+");
                if (line.startsWith("module ")) {
                    descriptor.name(split[1]);
                } else if (line.startsWith("requires ")) {
                    int start = 1;
                    boolean isStatic = (split[start].equals("static"));
                    boolean isTransitive = (split[start].equals("transitive"));
                    if (isStatic || isTransitive) {
                        start++;
                    }
                    for (int i = start; i < split.length; i++) {
                        descriptor.addItem(requiresModuleName(cleanLine(split[i]), isTransitive, isStatic,
                                                              (comments != null) ? comments : List.of()));
                    }
                } else if (line.startsWith("exports ")) {
                    ModuleInfoItem.Builder exports = ModuleInfoItem.builder()
                            .exports(true)
                            .target(resolve(split[1], importAliases))
                            .precomments((comments != null) ? comments : List.of());
                    for (int i = 2; i < split.length; i++) {
                        if (!"to".equalsIgnoreCase(split[i])) {
                            exports.addWithOrTo(resolve(cleanLine(split[i]), importAliases));
                        }
                    }
                    descriptor.addItem(exports.build());
                } else if (line.startsWith("uses ")) {
                    ModuleInfoItem.Builder uses = ModuleInfoItem.builder()
                            .uses(true)
                            .target(resolve(split[1], importAliases))
                            .precomments((comments != null) ? comments : List.of());
                    descriptor.addItem(uses.build());
                } else if (line.startsWith("provides ")) {
                    ModuleInfoItem.Builder provides = ModuleInfoItem.builder()
                            .provides(true)
                            .target(resolve(split[1], importAliases))
                            .precomments((comments != null) ? comments : List.of());
                    if (split.length < 3) {
                        throw new ToolsException("Unable to process module-info's use of: " + line);
                    }
                    if (split[2].equals("with")) {
                        for (int i = 3; i < split.length; i++) {
                            provides.addWithOrTo(resolve(cleanLine(split[i]), importAliases));
                        }
                    }
                    descriptor.addItem(provides.build());
                } else if (line.equals("}")) {
                    break;
                } else {
                    throw new ToolsException("Unable to process module-info's use of: " + line);
                }

                if (comments != null) {
                    comments = new ArrayList<>();
                }
            }
        } catch (ToolsException e) {
            throw e;
        } catch (Exception e) {
            if (line != null) {
                throw new ToolsException("Failed on line: " + line + ";\n"
                                                 + "unable to load or parse module-info: " + moduleInfo, e);
            }
            throw new ToolsException("Unable to load or parse module-info: " + moduleInfo, e);
        }

        return descriptor.build();
    }

    /**
     * Loads and creates the {@code module-info} descriptor given its source input stream.
     *
     * @param is the source file input stream
     * @return the module-info descriptor
     * @throws io.helidon.pico.tools.ToolsException if there is any exception encountered
     */
    @Prototype.FactoryMethod
    static ModuleInfoDescriptor create(InputStream is) {
        return create(is, ModuleInfoOrdering.NATURAL_PRESERVE_COMMENTS);
    }

    /**
     * Loads and creates the {@code module-info} descriptor given its input stream.
     *
     * @param is the source file location
     * @param ordering the ordering to apply
     * @return the module-info descriptor
     * @throws io.helidon.pico.tools.ToolsException if there is any exception encountered
     */
    @Prototype.FactoryMethod
    static ModuleInfoDescriptor create(InputStream is,
                                       ModuleInfoOrdering ordering) {
        try {
            String moduleInfo = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return create(moduleInfo, ordering);
        } catch (IOException e) {
            throw new ToolsException("Unable to load from stream", e);
        }
    }

    /**
     * Creates a new item declaring a {@code requires} on an external module usage from this module descriptor, that is
     * extended to use additional item attributes.
     *
     * @param moduleName    the module name to require
     * @param isTransitive  true if the requires declaration is transitive
     * @param isStatic      true if the requires declaration is static
     * @param comments      any comments to ascribe to the item
     * @return the item created
     */
    private static ModuleInfoItem requiresModuleName(String moduleName,
                                                     boolean isTransitive,
                                                     boolean isStatic,
                                                     List<String> comments) {
        return ModuleInfoItem.builder()
                .requires(true)
                .precomments(comments)
                .isTransitiveUsed(isTransitive)
                .isStaticUsed(isStatic)
                .target(moduleName)
                .build();
    }

    private static String cleanModuleAnnotations(String moduleText) {
        StringBuilder response = new StringBuilder();

        try (BufferedReader br = new BufferedReader(new StringReader(moduleText))) {
            boolean inModule = false;
            String line;
            while ((line = br.readLine()) != null) {
                if (inModule) {
                    response.append(line).append("\n");
                    continue;
                }
                String trimmed = line.trim();
                if (trimmed.startsWith("/*")) {
                    // beginning of comments
                    response.append(line).append("\n");
                } else if (trimmed.startsWith("*")) {
                    // comment line
                    response.append(line).append("\n");
                } else if (trimmed.startsWith("import ")) {
                    // import line
                    response.append(line).append("\n");
                } else if (trimmed.startsWith("module")) {
                    // now just add the rest (we do not cover annotations within module text)
                    inModule = true;
                    response.append(line).append("\n");
                } else if (trimmed.isBlank()) {
                    // empty line
                    response.append("\n");
                }
            }
        } catch (IOException ignored) {
            // ignored, we cannot get an exception when closing string reader
        }

        return response.toString();
    }

    private static String cleanLine(String line) {
        if (line == null) {
            return null;
        }

        while (line.endsWith(";") || line.endsWith(",")) {
            line = line.substring(0, line.length() - 1).trim();
        }

        if (line.contains("/*") || line.contains("*/")) {
            throw new ToolsException("Unable to parse lines that have inner comments: '" + line + "'");
        }

        return line.trim();
    }

    private static String cleanLine(BufferedReader reader,
                                    List<String> preComments,
                                    Map<String, TypeName> importAliases) throws IOException {
        String line = reader.readLine();
        if (line == null) {
            return null;
        }

        String trimmedline = line.trim();
        if (preComments != null) {
            boolean incomment = trimmedline.startsWith("//") || trimmedline.startsWith("/*");
            boolean inempty = trimmedline.isEmpty();
            while (incomment || inempty) {
                preComments.add(line);
                incomment = incomment && !trimmedline.endsWith("*/") && !trimmedline.startsWith("//");

                line = reader.readLine();
                if (line == null) {
                    return null;
                }
                trimmedline = line.trim();

                inempty = trimmedline.isEmpty();
                if (!inempty && !incomment) {
                    incomment = trimmedline.startsWith("//") || trimmedline.startsWith("/*");
                }
            }
        }

        StringBuilder result = new StringBuilder(trimmedline);
        String tmp;
        while (!trimmedline.endsWith(";") && !trimmedline.endsWith("}") && !trimmedline.endsWith("{")
                && (null != (tmp = reader.readLine()))) {
            if (tmp.contains("/*") || tmp.contains("*/") || tmp.contains("//")) {
                throw new IOException("Unable to parse line-level comments: '" + line + "'");
            }
            tmp = tmp.trim();
            result.append(" ").append(tmp);
            if (tmp.endsWith(";") || tmp.endsWith("}") || tmp.endsWith("{")) {
                break;
            }
        }

        line = cleanLine(result.toString());
        if (line.startsWith("import ")) {
            String[] split = line.split("\\s+");
            TypeName typeName = TypeName.create(split[split.length - 1]);
            importAliases.put(typeName.className(), typeName);
            line = cleanLine(reader, preComments, importAliases);
        }
        return line;
    }

    private static String resolve(String name,
                                  Map<String, TypeName> importAliases) {
        TypeName typeName = importAliases.get(name);
        return (typeName == null) ? name : typeName.name();
    }
}
