/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.helidon.common.CollectionsHelper;

/**
 * Generates Asciidoc documentation for some API areas.
 * The output of this generator is manually copy-pasted into Prime SDK DevGuide.
 */
public class ConfigAsciidocGenerator {

    private static final String TEMP_DIR = "helidon-config-dev-guide-";
    private static final String BUILTIN_MAPPERS_FILE = "mapping-builtin_mappers.adoc";
    private static final String CONFIG_ACCESSORS_FILE = "advanced-config_accessors.adoc";

    public static void main(String... args) throws IOException {
        processArgs(CollectionsHelper.setOf(args));
    }

    public static void processArgs(Set<String> args) throws IOException {
        if (args.isEmpty()) {
            args = CollectionsHelper.setOf("-am", "-bm");
        }
        Path dir = Files.createTempDirectory(TEMP_DIR);

        if (args.contains("--accessor-methods") || args.contains("-am")) {
            String configAccessors = generateAccessorMethods();
            writeFile(configAccessors, dir, CONFIG_ACCESSORS_FILE);
        }
        if (args.contains("--builtin-mappers") || args.contains("-bm")) {
            String builtinMappers = generateBuiltinMappers();
            writeFile(builtinMappers, dir, BUILTIN_MAPPERS_FILE);
        }
        System.out.println();
        System.out.println("Move generated files from " + dir
                                   + " to Helidon SDK DevGuide sources, to docs/src/docs/asciidoc/config/.");
    }

    private static void writeFile(String content, Path dir, String filename) throws IOException {
        String timestamp = new SimpleDateFormat("yyyy.MM.dd, HH:mm:ss", Locale.US).format(new Date());
        content = "// File is automatically generated, do not modify it by yourself!\n"
                + "//\n"
                + "// Generator: " + ConfigAsciidocGenerator.class.getName() + " class (see Config test sources)\n"
                + "// Timestamp: " + timestamp + "\n"
                + "// User:  " + System.getProperty("user.name") + "\n"
                + "\n"
                + content;

        Path file = Files.write(dir.resolve(filename), content.getBytes(StandardCharsets.UTF_8));
        System.out.println("File " + filename + " has been generated to " + file + " .");
    }

    public static String generateBuiltinMappers() {
        StringBuffer buffer = new StringBuffer();

        int cols = 3;
        final AtomicInteger count = new AtomicInteger(0);
        buffer.append(".List of all types supported by built-in config mappers:\n");
        buffer.append("[width=\"99%\",cols=\"" + cols + "\"]\n");
        buffer.append("|===\n");
        builtinMappers()
                .forEach(type -> {
                    buffer.append("| `" + type + "`\n");
                    count.incrementAndGet();
                });
        //number of cells must be product of `cols`
        int missing = (cols - (count.get() % cols)) % cols;
        for (int i = 0; i < missing; i++) {
            buffer.append("|\n");
        }
        buffer.append("|===\n");

        return buffer.toString();
    }

    private static List<String> builtinMappers() {
        Map<Class<?>, ConfigMapper<?>> mappers = new HashMap<>();
        mappers.putAll(ConfigMappers.essentialMappers());
        mappers.putAll(ConfigMappers.builtInMappers());

        return mappers.keySet().stream()
                .map(Class::getName)
                .sorted()
                .collect(Collectors.toList());
    }

    public static String generateAccessorMethods() {
        StringBuffer buffer = new StringBuffer();

        Predicate<Method> asMethodsFilter = (Method method) -> method.getName().startsWith("as");
        Predicate<Method> mapMethodsFilter = (Method method) -> method.getName().startsWith("map");

        Set<Method> allProcessedMethods = new HashSet<>();
        Set<Method> allAccessorMethods = Arrays.stream(Config.class.getDeclaredMethods())
                .filter(method -> !method.isSynthetic())
                .filter(method -> !Modifier.isStatic(method.getModifiers()))
                .filter(method -> !Void.class.equals(method.getReturnType()))
                .filter(method -> asMethodsFilter.test(method) || mapMethodsFilter.test(method))
                //.peek(method -> System.out.println(methodToString(method)))
                .collect(Collectors.toSet());

        buffer.append("= Common Accessor Methods\n");

        processMethods(buffer,
                       asMethodsFilter,
                       mapMethodsFilter,
                       allProcessedMethods,
                       allAccessorMethods.stream()
                               .filter(method -> !method.getName().endsWith("Supplier"))
                               .collect(Collectors.toSet()),
                       "");

        buffer.append("\n");
        buffer.append("= Supplier Accessor Methods\n");

        processMethods(buffer,
                       asMethodsFilter,
                       mapMethodsFilter,
                       allProcessedMethods,
                       allAccessorMethods.stream()
                               .filter(method -> method.getName().endsWith("Supplier"))
                               .collect(Collectors.toSet()),
                       "Supplier");

        allAccessorMethods.removeAll(allProcessedMethods);

        if (!allAccessorMethods.isEmpty()) {
            throw new RuntimeException("Not all accessor methods has been documented! Unknown: " + allAccessorMethods);
        }

        return buffer.toString();
    }

    private static void processMethods(StringBuffer buffer,
                                       Predicate<Method> asMethodsFilter,
                                       Predicate<Method> mapMethodsFilter,
                                       Set<Method> allProcessedMethods,
                                       Set<Method> allAccessorMethods,
                                       String methodSuffix) {
        {
            //get a single value
            SortedMap<KindDesc, Map<AccessType, MethodDesc>> methods = new TreeMap<>();
            final List<KindDesc> kinds = CollectionsHelper.listOf(new KindDesc("Generic (`T`)", Class.class),
                                                 new KindDesc("`String`", "String" + methodSuffix),
                                                 new KindDesc("`Int`", "Int" + methodSuffix),
                                                 new KindDesc("`Long`", "Long" + methodSuffix),
                                                 new KindDesc("`Double`", "Double" + methodSuffix),
                                                 new KindDesc("`Boolean`", "Boolean" + methodSuffix),
                                                 new KindDesc("`Map`", "Map" + methodSuffix));

            allAccessorMethods.stream()
                    .filter(asMethodsFilter)
                    .filter(method -> !method.getName().endsWith("List" + methodSuffix))
                    .peek(allProcessedMethods::add)
                    .forEach(method -> processMethods(methods, method, kinds));

            formatTable(buffer, ".Config accessor methods to get a single value", methods);
        }
        {
            //get a list of values
            SortedMap<KindDesc, Map<AccessType, MethodDesc>> methods = new TreeMap<>();
            final List<KindDesc> kinds = CollectionsHelper.listOf(new KindDesc("Generic (`List<T>`)", Class.class),
                                                 new KindDesc("`List<String>`", "StringList" + methodSuffix),
                                                 new KindDesc("`List<Config>`", "NodeList" + methodSuffix));

            allAccessorMethods.stream()
                    .filter(asMethodsFilter)
                    .filter(method -> method.getName().endsWith("List" + methodSuffix))
                    .peek(allProcessedMethods::add)
                    .forEach(method -> processMethods(methods, method, kinds));

            formatTable(buffer, ".Config accessor methods to get a list of values", methods);
        }
        {
            //map a single value
            SortedMap<KindDesc, Map<AccessType, MethodDesc>> methods = new TreeMap<>();
            final List<KindDesc> kinds = CollectionsHelper.listOf(new KindDesc("Simple `Function` (`T`)", Function.class),
                                                 new KindDesc("Complex `ConfigMapper` (`T`)", ConfigMapper.class));

            allAccessorMethods.stream()
                    .filter(mapMethodsFilter)
                    .filter(method -> !method.getName().endsWith("List" + methodSuffix))
                    .peek(allProcessedMethods::add)
                    .forEach(method -> processMethods(methods, method, kinds));

            formatTable(buffer, ".Config accessor methods to map a single value", methods);
        }
        {
            //map a list of values
            SortedMap<KindDesc, Map<AccessType, MethodDesc>> methods = new TreeMap<>();
            final List<KindDesc> kinds = CollectionsHelper.listOf(new KindDesc("Simple `Function` (`List<T>`)", Function.class),
                                                 new KindDesc("Complex `ConfigMapper` (`List<T>`)", ConfigMapper.class));

            allAccessorMethods.stream()
                    .filter(mapMethodsFilter)
                    .filter(method -> method.getName().endsWith("List" + methodSuffix))
                    .peek(allProcessedMethods::add)
                    .forEach(method -> processMethods(methods, method, kinds));

            formatTable(buffer, ".Config accessor methods to map a list of values", methods);
        }
    }

    private static void formatTable(StringBuffer buffer, String title, SortedMap<KindDesc, Map<AccessType, MethodDesc>> methods) {
        buffer.append("\n");
        buffer.append(title + "\n");
        buffer.append("[width=\"99%\",options=\"header\"]\n");
        buffer.append("|===\n");

        StringBuilder sb = new StringBuilder("|Type");
        sb.append(" |").append(AccessType.COMMON.desc);
        sb.append(" |").append(AccessType.WITH_DEFAULT.desc);
        sb.append(" |").append(AccessType.OPTIONAL.desc);
        buffer.append(sb + "\n");

        for (Map.Entry<KindDesc, Map<AccessType, MethodDesc>> typeMethods : methods.entrySet()) {
            sb = new StringBuilder();
            sb.append("|").append(typeMethods.getKey().desc);
            formatMethodDesc(sb, typeMethods.getValue().get(AccessType.COMMON));
            formatMethodDesc(sb, typeMethods.getValue().get(AccessType.WITH_DEFAULT));
            formatMethodDesc(sb, typeMethods.getValue().get(AccessType.OPTIONAL));

            buffer.append(sb + "\n");
        }
        buffer.append("|===\n");
    }

    private static void formatMethodDesc(StringBuilder sb, MethodDesc methodDesc) {
        sb.append(" |").append(Optional.ofNullable(methodDesc)
                                       .map(MethodDesc::format)
                                       .orElse(""));
    }

    private static void processMethods(SortedMap<KindDesc, Map<AccessType, MethodDesc>> methods, Method method,
                                       List<KindDesc> kinds) {
        String methodName = method.getName();

        KindDesc kindDesc = null;
        for (KindDesc kind : kinds) {
            if (kind.accept(method)) {
                kindDesc = kind;
                break;
            }
        }

        Objects.requireNonNull(kindDesc, () -> "Not found appropriate method kind for \n" + method + " \nin\n"
                + kinds.stream().map(KindDesc::toString).collect(Collectors.joining("\n"))
                + ".");

        boolean withDefault;
        if (kindDesc.generic) {
            withDefault = method.getParameterCount() == 2;
        } else {
            withDefault = method.getParameterCount() == 1;
        }

        boolean optional = methodName.contains("Optional");

        String params = Arrays.stream(method.getGenericParameterTypes())
                .map(Type::getTypeName)
                .map(ConfigAsciidocGenerator::formatParams)
                .collect(Collectors.joining(","));

        AccessType accessType = AccessType.of(withDefault, optional);
        MethodDesc methodDesc = new MethodDesc(kindDesc, methodName, params, accessType);

        Map<AccessType, MethodDesc> typeMethods = methods.computeIfAbsent(kindDesc, (tn) -> new HashMap<>());
        if (typeMethods.containsKey(accessType)) {
            throw new RuntimeException("Duplicate access-type " + accessType + " for type " + kindDesc.desc
                                               + ". Original: " + typeMethods.get(accessType) + " | New: " + methodDesc);
        } else {
            typeMethods.put(accessType, methodDesc);
        }
    }

    private static String formatParams(String text) {
        return text.replaceAll("java\\.lang\\.", "")
                .replaceAll("java\\.util\\.function\\.", "")
                .replaceAll("java\\.util\\.", "")
                .replaceAll("io\\.helidon\\.config\\.", "")
                .replaceAll("\\? extends T", "T")
                .replaceAll(" ", "")
                ;
    }

    private static String methodToString(Method method) {
        StringBuilder sb = new StringBuilder(method.getName());
        sb.append(" (").append(Arrays.toString(method.getParameters())).append(" ) : " + method.getGenericReturnType());
        return sb.toString();
    }

    private static class KindDesc implements Comparable<KindDesc> {
        private boolean generic;
        private String methodSuffix;
        private String desc;
        private String comparable;
        private Class<?> firstParamClass;

        public KindDesc(String desc, String methodSuffix) {
            this.generic = false;
            this.desc = desc;
            this.methodSuffix = methodSuffix;
            this.comparable = (generic ? "0" : "1") + desc;
        }

        public KindDesc(String desc, Class<?> firstParamClass) {
            this.generic = true;
            this.desc = desc;
            this.firstParamClass = firstParamClass;
            this.comparable = (generic ? "0" : "1") + desc;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            KindDesc kindDesc = (KindDesc) o;
            return generic == kindDesc.generic &&
                    Objects.equals(methodSuffix, kindDesc.methodSuffix) &&
                    Objects.equals(desc, kindDesc.desc) &&
                    Objects.equals(firstParamClass, kindDesc.firstParamClass);
        }

        @Override
        public int hashCode() {
            return Objects.hash(generic, methodSuffix, desc, firstParamClass);
        }

        @Override
        public String toString() {
            return "KindDesc{" +
                    "generic=" + generic +
                    ", methodSuffix='" + methodSuffix + '\'' +
                    ", desc='" + desc + '\'' +
                    ", firstParamClass=" + firstParamClass +
                    '}';
        }

        @Override
        public int compareTo(KindDesc peer) {
            return comparable.compareTo(peer.comparable);
        }

        public boolean accept(Method method) {
            if (methodSuffix != null && method.getName().endsWith(methodSuffix)) {
                return true;
            }
            if (firstParamClass != null && method.getParameterCount() > 0) {
                Class<?> currentFirstParameterClass = method.getParameters()[0].getType();
                if (firstParamClass.isAssignableFrom(currentFirstParameterClass)) {
                    return true;
                } else {
                    //System.out.println("Not acceptable class: " + currentFirstParameterClass);
                }
            }
            return false;
        }
    }

    private static class MethodDesc {
        private KindDesc kind;
        private String name;
        private String params;
        private AccessType accessType;

        public MethodDesc(KindDesc kind, String name, String params, AccessType accessType) {
            this.kind = kind;
            this.name = name;
            this.params = params;
            this.accessType = accessType;
        }

        public String format() {
            StringBuilder sb = new StringBuilder();
            sb.append("`")
                    .append(name)
                    .append("(")
                    .append(params)
                    .append(")`");
            return sb.toString();
        }

    }

    private enum AccessType {
        COMMON("Common", false, false),
        WITH_DEFAULT("With Default", true, false),
        OPTIONAL("Optional", false, true);

        private String desc;
        private boolean withDefault;
        private boolean optional;

        AccessType(String desc, boolean withDefault, boolean optional) {
            this.desc = desc;
            this.withDefault = withDefault;
            this.optional = optional;
        }

        public static AccessType of(boolean withDefault, boolean optional) {
            if (withDefault) {
                if (optional) {
                    throw new RuntimeException("WithDefault & Optional is wrong combination.");
                }
                return WITH_DEFAULT;
            } else {
                if (optional) {
                    return OPTIONAL;
                } else {
                    return COMMON;
                }
            }
        }
    }

}
