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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.helidon.common.types.TypeName;

/**
 * Support for parsing module-info.java sources.
 */
public final class ModuleInfoSourceParser {
    private static final String PROVIDES = "provides";
    private static final String OPENS = "opens";
    private static final Pattern ANNOTATION = Pattern.compile("(@\\w+)(.*)");
    private static final Pattern OPENS_PATTERN = Pattern.compile("opens (.*?)(?:\\s+to\\s+(.*?))?");
    private static final Pattern EXPORTS_PATTERN = Pattern.compile("exports (.*?)(?:\\s+to\\s+(.*?))?");

    private final Map<String, TypeName> importAliases = new LinkedHashMap<>();
    private final List<String> currentComments = new ArrayList<>();

    // state of the parser
    private State state = State.PRE_IMPORTS;
    // state of the parser outside of current comments
    private State outState = State.PRE_IMPORTS;
    // in progress string
    private String current;
    // opened brackets counter (used in annotation parsing)
    private int bracketsOpened;

    private ModuleInfoSourceParser() {
    }

    /**
     * Parse the module info from its input stream.
     *
     * @param inputStream input stream to parse
     * @return module info
     */
    public static ModuleInfo parse(InputStream inputStream) {
        return parse(new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)));
    }

    /**
     * Parse the module info from its source file.
     *
     * @param path path to parse
     * @return module info
     */
    public static ModuleInfo parse(Path path) {
        try (InputStream inputStream = Files.newInputStream(path)) {
            return parse(inputStream);
        } catch (IOException e) {
            throw new CodegenException("Failed to read module info from " + path.toAbsolutePath(), e);
        }

    }

    static ModuleInfo parse(BufferedReader reader) {
        ModuleInfoSourceParser parser = new ModuleInfoSourceParser();
        try {
            return parser.doParse(reader);
        } catch (IOException e) {
            throw new CodegenException("Failed to parse module info", e, ModuleInfoSourceParser.class);
        }
    }

    private ModuleInfo doParse(BufferedReader reader) throws IOException {
        ModuleInfo.Builder builder = ModuleInfo.builder();

        String line;
        while ((line = reader.readLine()) != null && state != State.DONE) {
            String inProgress = line;
            while (!inProgress.isEmpty() && state != State.DONE) {
                inProgress =
                        switch (state) {
                            case PRE_IMPORTS, POST_IMPORTS, MODULE_CONTENT, UNKNOWN -> nextState(builder, inProgress);
                            case M_COMMENTS -> mComments(inProgress);
                            case IMPORTS -> imports(inProgress);
                            case ANNOTATION -> annotation(builder, inProgress);
                            case MODULE_NAME -> moduleName(builder, inProgress);
                            case REQUIRES -> contentToSemi(builder, inProgress, this::parseRequires);
                            case EXPORTS -> contentToSemi(builder, inProgress, this::parseExports);
                            case USES -> contentToSemi(builder, inProgress, this::parseUses);
                            case PROVIDES -> contentToSemi(builder, inProgress, this::parseProvides);
                            case OPENS -> contentToSemi(builder, inProgress, this::parseOpens);
                            default -> throw new CodegenException("Unexpected parsing state: " + state);
                        };
            }
        }

        return builder.build();
    }

    private String annotation(ModuleInfo.Builder builder, String inProgress) {
        // we have processed @Something
        if (inProgress.isBlank()) {
            // next line
            return "";
        }
        if (inProgress.startsWith("(")) {
            bracketsOpened++;
        }
        int lastIndex = -1;
        while (bracketsOpened > 0) {
            int index = inProgress.indexOf(')', lastIndex + 1);
            if (index == -1) {
                break;
            }
            lastIndex = index;
            bracketsOpened--;
        }
        if (bracketsOpened > 0) {
            current = current + " " + inProgress;
            return "";
        }
        if (lastIndex >= 0) {
            return newState(State.POST_IMPORTS, inProgress.substring(lastIndex + 1));
        }
        return newState(State.POST_IMPORTS, inProgress);
    }

    private String moduleName(ModuleInfo.Builder builder, String inProgress) {
        int index = inProgress.indexOf('{');
        if (index > -1) {
            parseModuleName(builder, current + " " + inProgress.substring(0, index));

            return newState(State.MODULE_CONTENT, inProgress.substring(index + 1));
        }
        current = current + " " + inProgress;
        return "";
    }

    private void parseModuleName(ModuleInfo.Builder builder, String moduleNameString) {
        // such as `open module io.helidon.config`
        String[] split = moduleNameString.split("\\s+");

        boolean isOpen = false;
        String name = null;
        for (String nameElement : split) {
            if ("open".equals(nameElement)) {
                isOpen = true;
                continue;
            }
            if ("module".equals(nameElement)) {
                continue;
            }
            // last element is expected to be the module name
            name = nameElement;
        }
        if (name == null) {
            throw new CodegenException("Cannot discover module name from: " + moduleNameString);
        }
        builder.name(name);
        builder.isOpen(isOpen);
    }

    private String contentToSemi(ModuleInfo.Builder builder,
                                 String inProgress,
                                 BiConsumer<ModuleInfo.Builder, String> parseMethod) {
        int index = inProgress.indexOf(';');
        if (index > -1) {
            parseMethod.accept(builder, current + "  " + inProgress.substring(0, index));

            return newState(State.MODULE_CONTENT, inProgress.substring(index + 1));
        }
        current = current + " " + inProgress;
        return "";
    }

    private String imports(String inProgress) {
        int index = inProgress.indexOf(';');
        if (index > -1) {
            parseImport(current + "  " + inProgress.substring(0, index));

            return newState(State.POST_IMPORTS, inProgress.substring(index + 1));
        }
        current = current + " " + inProgress;
        return "";
    }

    private String mComments(String inProgress) {
        int index = inProgress.indexOf("*/");
        if (index > 0) {
            state = outState;
            String comment = inProgress.substring(0, index).trim();
            if (!comment.isEmpty()) {
                currentComments.add(comment);
            }
            return inProgress.substring(index + 2);
        }
        currentComments.add(inProgress);
        return "";
    }

    private String nextState(ModuleInfo.Builder builder, String inProgress) {
        String trimmed = inProgress.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        // now we know we have something, let's handle it
        if (trimmed.startsWith("/*")) {
            // M_COMMENTS
            if (trimmed.startsWith("/**/")) {
                // empty comment
                return trimmed.substring(4);
            }

            int begin = 2;

            if (trimmed.startsWith("/**")) {
                // javadoc
                begin = 3;
            }

            int endOfComments = trimmed.indexOf("*/");
            if (endOfComments > 0) {
                // end on the same line
                String comment = trimmed.substring(begin, endOfComments);
                currentComments.add(comment);
                return trimmed.substring(endOfComments + 2);
            }
            String comment = trimmed.substring(begin);
            if (!comment.isEmpty()) {
                currentComments.add(comment);
            }
            outState = state;
            state = State.M_COMMENTS;
            return "";
        }
        if (trimmed.startsWith("//")) {
            currentComments.add(trimmed.substring(2));
            return "";
        }
        if (trimmed.startsWith("import") && (state == State.PRE_IMPORTS || state == State.POST_IMPORTS)) {
            if (state == State.PRE_IMPORTS) {
                // builder.headerComment(String.join("\n", currentComments));
                currentComments.clear();
            }

            int index = trimmed.indexOf(';');
            if (index > 0) {
                // single line import statement
                parseImport(trimmed.substring(0, index).trim());
                return newState(State.POST_IMPORTS, trimmed.substring(index + 1));
            }
            // beginning of multiline import
            return stateContinuation(State.IMPORTS, trimmed);
        }

        if (trimmed.startsWith("@")) {
            return analyzeAnnotation(builder, trimmed);
        }

        if (state == State.PRE_IMPORTS || state == State.POST_IMPORTS) {
            // whatever we have, it must be module declaration
            // we handle multiline comments, comments, imports, and annotations above this section
            if (!currentComments.isEmpty()) {
                //                builder.descriptionComment(String.join("\n", currentComments));
                currentComments.clear();
            }
            int index = trimmed.indexOf('{');
            if (index > 0) {
                parseModule(builder, trimmed.substring(0, index).trim());
                return trimmed.substring(index + 1);
            }
            return stateContinuation(State.MODULE_NAME, trimmed);
        }

        if (trimmed.startsWith("requires") && state == State.MODULE_CONTENT) {
            int index = trimmed.indexOf(';');
            if (index > 0) {
                parseRequires(builder, trimmed.substring(0, index).trim());
                return trimmed.substring(index + 1);
            }

            return stateContinuation(State.REQUIRES, trimmed);
        }
        if (trimmed.startsWith("exports") && state == State.MODULE_CONTENT) {
            int index = trimmed.indexOf(';');
            if (index > 0) {
                parseExports(builder, trimmed.substring(0, index).trim());
                return trimmed.substring(index + 1);
            }

            return stateContinuation(State.EXPORTS, trimmed);
        }
        if (trimmed.startsWith("uses") && state == State.MODULE_CONTENT) {
            int index = trimmed.indexOf(';');
            if (index > 0) {
                parseUses(builder, trimmed.substring(0, index).trim());
                return trimmed.substring(index + 1);
            }

            return stateContinuation(State.USES, trimmed);
        }
        if (trimmed.startsWith("provides") && state == State.MODULE_CONTENT) {
            int index = trimmed.indexOf(';');
            if (index > 0) {
                parseProvides(builder, trimmed.substring(0, index).trim());
                return trimmed.substring(index + 1);
            }

            return stateContinuation(State.PROVIDES, trimmed);
        }
        if (trimmed.startsWith("opens") && state == State.MODULE_CONTENT) {
            int index = trimmed.indexOf(';');
            if (index > 0) {
                parseOpens(builder, trimmed.substring(0, index).trim());
                return trimmed.substring(index + 1);
            }

            return stateContinuation(State.OPENS, trimmed);
        }

        if (trimmed.startsWith("}")) {
            // we are done
            return stateContinuation(State.DONE, "");
        }

        //        builder.addUnhandledLine(trimmed);
        return "";
    }

    /**
     * Set new current state, and use the string as the new current value.
     *
     * @param newState   new state
     * @param newCurrent value to continue with parsing
     * @return empty string
     */
    private String stateContinuation(State newState, String newCurrent) {
        state = newState;
        outState = newState;
        current = newCurrent;
        return "";
    }

    /**
     * Set new current state, and return the remaining line.
     *
     * @param newState   new state
     * @param inProgress remainder of the line after parsing current value
     * @return inProgress
     */
    private String newState(State newState, String inProgress) {
        state = newState;
        outState = newState;
        current = "";
        return inProgress;
    }

    private String analyzeAnnotation(ModuleInfo.Builder builder, String annotationString) {
        Matcher matcher = ANNOTATION.matcher(annotationString);
        if (matcher.matches()) {
            current = matcher.group(1);
            return newState(State.ANNOTATION, matcher.group(2));
        } else {
            throw new CodegenException("Invalid annotation in module-info.java: " + annotationString);
        }
    }

    private void parseOpens(ModuleInfo.Builder builder, String opensString) {
        // opens X to Y, Z

        Matcher m = OPENS_PATTERN.matcher(opensString);
        if (m.matches()) {
            String first = m.group(1);
            String second = m.group(2);
            if (second == null) {
                builder.putOpen(first, List.of());
            } else {
                builder.putOpen(first, Arrays.stream(second.split(","))
                        .map(String::trim)
                        .toList());
            }
        } else {
            String inProgress = opensString.substring(OPENS.length()).trim();
            int toIndex = inProgress.indexOf("to");
            if (toIndex < 0) {
                throw new CodegenException("Cannot parse opens in module-info.java: " + opensString);
            }

            String what = inProgress.substring(0, toIndex).trim();
            String to = inProgress.substring(toIndex + 2).trim();
            List<String> toList = Arrays.stream(to.split(","))
                    .map(String::trim)
                    .toList();

            builder.putOpen(what, toList);
        }
    }

    private void parseProvides(ModuleInfo.Builder builder, String providesString) {
        // provides X with Y, Z

        String inProgress = providesString.substring(PROVIDES.length()).trim();
        int withIndex = inProgress.indexOf("with");
        if (withIndex < 0) {
            throw new CodegenException("Cannot parse provides in module-info.java: " + providesString);
        }

        String what = checkImports(inProgress.substring(0, withIndex).trim());
        String with = inProgress.substring(withIndex + 5).trim();
        List<TypeName> withList = Arrays.stream(with.split(","))
                .map(String::trim)
                .map(this::checkImports)
                .map(TypeName::create)
                .toList();

        builder.putProvide(TypeName.create(what), withList);
    }

    private void parseUses(ModuleInfo.Builder builder, String usesString) {
        String usedType = checkImports(usesString.substring(4).trim());

        builder.addUse(TypeName.create(usedType));
    }

    private String checkImports(String typeName) {
        TypeName imported = importAliases.get(typeName);
        return imported == null ? typeName : imported.fqName();
    }

    private void parseExports(ModuleInfo.Builder builder, String exportsString) {
        // either "exports package"
        Matcher m = EXPORTS_PATTERN.matcher(exportsString);
        if (m.matches()) {
            String first = m.group(1);
            String second = m.group(2);
            if (second == null) {
                builder.putExports(first, List.of());
            } else {
                builder.putExports(first, Arrays.stream(second.split(","))
                        .map(String::trim)
                        .toList());
            }
        } else {
            // or "exports package to module name (fallback if does not match)
            builder.putExports(exportsString.substring(7).trim(), List.of());
        }
    }

    private void parseRequires(ModuleInfo.Builder builder, String requiresString) {
        boolean isStatic = false;
        boolean isTransitive = false;
        String target = null;
        String[] split = requiresString.split("\\s+"); // split by one or more whitespaces

        for (String element : split) {
            if (element.equals("static")) {
                isStatic = true;
                continue;
            }
            if (element.equals("transitive")) {
                isTransitive = true;
                continue;
            }
            target = element;
        }

        if (target == null) {
            throw new CodegenException("Failed to parse module-info.java line " + requiresString);
        }

        // requires static/transitive something;
        builder.addRequire(new ModuleInfoRequires(target, isTransitive, isStatic));
    }

    private void parseModule(ModuleInfo.Builder builder, String moduleString) {
        // module some.name
        builder.name(moduleString.substring(6).trim());
        state = State.MODULE_CONTENT;
        outState = State.MODULE_CONTENT;
    }

    private void parseImport(String importStatement) {
        // expects import a.b.c

        String importString = importStatement.substring(6).trim();
        String importedType = importString.substring(importString.lastIndexOf('.') + 1);
        importAliases.put(importedType, TypeName.create(importString));
    }

    enum State {
        PRE_IMPORTS,
        POST_IMPORTS,
        M_COMMENTS,
        IMPORTS,
        ANNOTATION,
        MODULE_NAME,
        MODULE_CONTENT,
        REQUIRES,
        EXPORTS,
        USES,
        PROVIDES,
        OPENS,
        DONE,
        UNKNOWN
    }
}
