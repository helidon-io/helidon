/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.json.codegen;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.helidon.codegen.classmodel.ClassBase;
import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;

final class StringLexerGenerator {
    private static final int MAX_GENERATED_UNITS = 32768;
    private static final int MAX_GENERATED_STATES = 512;
    private static final int MAX_STATE_EDGES = 128;
    private static final int MAX_ASCII_BRANCHES = 256;
    private static final int STATES_PER_METHOD = 32;
    private static final int MIN_PROJECTED_NAMES = 12;
    private static final int MIN_RANGE_EDGES = 8;

    private StringLexerGenerator() {
    }

    static void generate(ClassBase.Builder<?, ?> owner, TypeName type, String fieldName, List<String> names) {
        List<Map<String, Integer>> states = names.stream().mapToLong(String::length).sum() <= MAX_GENERATED_UNITS
                ? states(names) : List.of();
        boolean generate = !states.isEmpty() && states.size() <= MAX_GENERATED_STATES
                && states.stream().allMatch(state -> state.size() <= MAX_STATE_EDGES);
        String entry = switch (fieldName) {
        case "PROPERTY_NAMES" -> "lexPropertyName";
        case "POLYMORPHIC_PROPERTY" -> "lexDiscriminator";
        case "POLYMORPHIC_ALIASES" -> "lexAlias";
        default -> throw new IllegalArgumentException("Unknown lexer field: " + fieldName);
        };
        List<RawEdge> encoded = new ArrayList<>();
        boolean nonAscii = false;
        if (generate) {
            for (int i = 0; i < names.size(); i++) {
                String name = names.get(i);
                if (name.length() < 3 * Long.BYTES && name.codePoints().noneMatch(c -> c < 0x20 || c == '"' || c == '\\'
                        || c >= Character.MIN_SURROGATE && c <= Character.MAX_SURROGATE)) {
                    byte[] spelling = name.getBytes(StandardCharsets.UTF_8);
                    if (spelling.length < 3 * Long.BYTES) {
                        encoded.add(new RawEdge(spelling, i));
                        nonAscii |= spelling.length != name.length();
                    }
                }
            }
        }
        boolean utf8 = nonAscii;
        String rawMethod = entry + (utf8 ? "Utf8" : "Ascii");
        int offset = utf8 ? commonPrefix(encoded) : 0;
        List<RawEdge> ascii = offset == 0 ? encoded : encoded.stream()
                .map(edge -> new RawEdge(Arrays.copyOfRange(edge.bytes, offset, edge.bytes.length), edge.ordinal)).toList();
        String positional = ascii.size() >= STATES_PER_METHOD ? positionalOrdinal(ascii) : "";
        boolean raw = !ascii.isEmpty() && ascii.size() * 2 >= names.size()
                && (ascii.size() <= MAX_ASCII_BRANCHES || !positional.isEmpty());
        owner.addField(field -> {
            field.name(fieldName).type(JsonTypes.JSON_STRING_LEXER).isStatic(true).isFinal(true)
                    .accessModifier(AccessModifier.PRIVATE);
            if (generate && !raw) {
                field.addContent(type).addContent("::" + entry);
            } else {
                field.addContent(JsonTypes.JSON_STRING_LEXER);
                if (raw) {
                    field.addContent(utf8 ? ".withUtf8(" : ".withAscii(").addContent(type).addContent("::" + entry + ", ")
                            .addContent(type).addContent("::" + rawMethod + ", ");
                    if (utf8) {
                        field.addContent(offset + ", ");
                    }
                } else {
                    field.addContent(".create(");
                }
                for (int i = 0; i < names.size(); i++) {
                    if (i > 0) {
                        field.addContent(", ");
                    }
                    field.addContentLiteral(names.get(i));
                }
                field.addContent(")");
            }
        });
        if (!generate) {
            return;
        }
        if (raw) {
            owner.addMethod(method -> {
                method.name(rawMethod).accessModifier(AccessModifier.PRIVATE).isStatic(true)
                        .returnType(TypeNames.PRIMITIVE_INT)
                        .addParameter(TypeNames.PRIMITIVE_LONG, "first")
                        .addParameter(TypeNames.PRIMITIVE_LONG, "second");
                if (positional.isEmpty()) {
                    emitAscii(method, ascii, owner, fieldName + "_TRANSITIONS");
                } else {
                    method.addContentLine("return " + positional + ";");
                }
            });
        }
        generateDecoded(owner, entry, DecodedPlan.compact(states, names.size()));
    }

    private static int commonPrefix(List<RawEdge> edges) {
        byte[] first = edges.getFirst().bytes;
        int common = Math.min(Long.BYTES, first.length);
        for (RawEdge edge : edges) {
            common = Math.min(common, edge.bytes.length);
            for (int i = 0; i < common; i++) {
                if (first[i] != edge.bytes[i]) {
                    common = i;
                    break;
                }
            }
        }
        return common;
    }

    private static void generateDecoded(ClassBase.Builder<?, ?> owner, String entry, DecodedPlan plan) {
        List<Map<String, Integer>> states = plan.states;
        owner.addMethod(method -> {
            method.name(entry).accessModifier(AccessModifier.PRIVATE).isStatic(true)
                    .returnType(TypeNames.PRIMITIVE_INT)
                    .addParameter(TypeNames.PRIMITIVE_INT, "state")
                    .addParameter(TypeNames.PRIMITIVE_LONG, "low")
                    .addParameter(TypeNames.PRIMITIVE_LONG, "high")
                    .addParameter(TypeNames.PRIMITIVE_INT, "length");
            plan.emitSuffixes(method);
            if (states.size() > 1) {
                method.addContentLine("if (state != 0) {");
                if (states.size() <= STATES_PER_METHOD) {
                    emitStateDispatch(method, entry, 1, states.size());
                } else {
                    method.addContentLine("return switch (state / " + STATES_PER_METHOD + ") {");
                    for (int i = 0; i < states.size(); i += STATES_PER_METHOD) {
                        int group = i / STATES_PER_METHOD;
                        method.addContentLine("case " + group + " -> " + entry + "Group" + group + "(state, low, high, length);");
                    }
                    method.addContentLine("default -> -1;").addContent("}").addContentLine(";");
                }
                method.addContentLine("}");
            }
            emitState(method, states.getFirst());
        });
        if (states.size() > STATES_PER_METHOD) {
            for (int i = 0; i < states.size(); i += STATES_PER_METHOD) {
                int from = Math.max(1, i);
                int to = Math.min(states.size(), i + STATES_PER_METHOD);
                String group = entry + "Group" + (i / STATES_PER_METHOD);
                owner.addMethod(method -> {
                    method.name(group).accessModifier(AccessModifier.PRIVATE).isStatic(true).returnType(TypeNames.PRIMITIVE_INT)
                            .addParameter(TypeNames.PRIMITIVE_INT, "state").addParameter(TypeNames.PRIMITIVE_LONG, "low")
                            .addParameter(TypeNames.PRIMITIVE_LONG, "high").addParameter(TypeNames.PRIMITIVE_INT, "length");
                    emitStateDispatch(method, entry, from, to);
                });
            }
        }
        for (int i = 1; i < states.size(); i++) {
            var transitions = states.get(i);
            String methodName = entry + i;
            owner.addMethod(method -> {
                method.name(methodName).accessModifier(AccessModifier.PRIVATE).isStatic(true)
                        .returnType(TypeNames.PRIMITIVE_INT)
                        .addParameter(TypeNames.PRIMITIVE_LONG, "low")
                        .addParameter(TypeNames.PRIMITIVE_LONG, "high")
                        .addParameter(TypeNames.PRIMITIVE_INT, "length");
                emitState(method, transitions);
            });
        }
    }

    private static void emitAscii(Method.Builder method,
                                  List<RawEdge> edges,
                                  ClassBase.Builder<?, ?> owner,
                                  String tableName) {
        if (edges.size() == 1) {
            method.addContentLine("return " + edges.getFirst().ordinal + ";");
            return;
        }
        // Only positions defined for every remaining spelling can select a branch. The closing quote
        // distinguishes a short name from a longer name that shares its complete prefix.
        int shortest = edges.stream().mapToInt(edge -> edge.bytes.length).min().orElseThrow();
        int position = 0;
        long lowestCost = Long.MAX_VALUE;
        for (int i = 0; i <= Math.min(shortest, 2 * Long.BYTES - 1); i++) {
            int[] counts = new int[256];
            long cost = 0;
            for (RawEdge edge : edges) {
                cost += 2L * ++counts[edge.at(i)] - 1;
            }
            if (cost < lowestCost) {
                lowestCost = cost;
                position = i;
                if (cost == edges.size()) {
                    break;
                }
            }
        }
        if (lowestCost == (long) edges.size() * edges.size()) {
            // The first two words are identical. Continue in the decoded block automaton.
            method.addContentLine("return -1;");
            return;
        }
        if (!tableName.isEmpty() && edges.size() >= MIN_PROJECTED_NAMES && lowestCost > edges.size()) {
            Projection projection = Projection.create(edges, Long.BYTES);
            if (projection == null) {
                projection = Projection.create(edges, 2 * Long.BYTES);
            }
            if (projection != null) {
                projection.emit(method, owner, tableName);
                return;
            }
        }
        String word = position < Long.BYTES ? "first" : "second";
        String digit = "((int) (" + word + " >>> " + ((position % Long.BYTES) * Byte.SIZE) + ") & 0xff)";
        if (edges.size() == 2) {
            RawEdge first = edges.getFirst();
            method.addContentLine("return " + digit + " == " + first.at(position) + " ? " + first.ordinal
                                          + " : " + edges.getLast().ordinal + ";");
            return;
        }
        Map<Integer, List<RawEdge>> groups = new TreeMap<>();
        for (RawEdge edge : edges) {
            groups.computeIfAbsent(edge.at(position), _ -> new ArrayList<>()).add(edge);
        }
        boolean direct = !tableName.isEmpty() && groups.size() >= 4
                && groups.values().stream().filter(group -> group.size() == 1).count() * 2 >= groups.size();
        if (direct) {
            int size = groups.keySet().stream().anyMatch(value -> value >= 128) ? 256 : 128;
            int[] routes = new int[size];
            Arrays.fill(routes, -1);
            groups.forEach((value, group) -> routes[value] = group.size() == 1 ? group.getFirst().ordinal : -2 - value);
            addTable(owner, tableName, routes);
            String access = tableName + "[" + digit + " & " + (size - 1) + "]";
            if (groups.size() == edges.size()) {
                method.addContentLine("return " + access + ";");
                return;
            }
            method.addContentLine("int route = " + access + ";")
                    .addContentLine("if (route >= -1) {").addContentLine("return route;").addContentLine("}")
                    .addContentLine("switch (route) {");
        } else {
            method.addContentLine("switch (" + digit + ") {");
        }
        groups.forEach((value, group) -> {
            if (!direct || group.size() > 1) {
                method.addContentLine("case " + (direct ? -2 - value : value) + " -> {");
                emitAscii(method, group, owner, "");
                method.addContentLine("}");
            }
        });
        method.addContentLine("default -> {").addContentLine("return -1;").addContentLine("}").addContentLine("}");
    }

    private static void addTable(ClassBase.Builder<?, ?> owner, String name, int[] routes) {
        String constants = Arrays.toString(routes);
        owner.addField(field -> field.name(name).type(int[].class).accessModifier(AccessModifier.PRIVATE)
                .isStatic(true).isFinal(true).addContent("{" + constants.substring(1, constants.length() - 1) + "}"));
    }

    // Recognize an ordinal whose digits come directly from input positions. Verify every registered
    // spelling when generating the expression. The native parser still checks the complete chosen literal.
    private static String positionalOrdinal(List<RawEdge> edges) {
        int maxOrdinal = edges.stream().mapToInt(RawEdge::ordinal).max().orElseThrow();
        int width = Math.min(2 * Long.BYTES, edges.stream().mapToInt(edge -> edge.bytes.length).min().orElseThrow());
        List<String> terms = new ArrayList<>();
        int bias = 0;
        int place = 1;
        while (place <= maxOrdinal) {
            int next = 0;
            for (int position = 0; position < width; position++) {
                int lane = position;
                var values = edges.stream().mapToInt(edge -> edge.at(lane)).summaryStatistics();
                int min = values.getMin();
                int max = values.getMax();
                int radix = max - min + 1;
                int scale = place;
                if (radix == 1 || !edges.stream().allMatch(edge -> edge.at(lane) - min == edge.ordinal / scale % radix)) {
                    continue;
                }
                int mask = (Integer.highestOneBit(radix - 1) << 1) - 1;
                if ((min & ~mask) != (max & ~mask)) {
                    mask = 0xff;
                }
                String word = position < Long.BYTES ? "first" : "second";
                int shift = position % Long.BYTES * Byte.SIZE;
                String digit = "((int) (" + word + " >>> " + shift + ") & " + mask + ")";
                terms.add(place == 1 ? digit : place + " * " + digit);
                bias += place * (min & mask);
                next = place * radix;
                break;
            }
            if (next == 0) {
                return "";
            }
            place = next;
        }
        return String.join(" + ", terms) + (bias == 0 ? "" : " - " + bias);
    }

    private static void emitStateDispatch(Method.Builder method, String entry, int from, int to) {
        method.addContentLine("return switch (state) {");
        for (int i = from; i < to; i++) {
            method.addContentLine("case " + i + " -> " + entry + i + "(low, high, length);");
        }
        method.addContentLine("default -> -1;").addContent("}").addContentLine(";");
    }

    private static List<Map<String, Integer>> states(List<String> names) {
        List<Map<String, Integer>> states = new ArrayList<>();
        states.add(new LinkedHashMap<>());
        for (int ordinal = 0; ordinal < names.size(); ordinal++) {
            String name = names.get(ordinal);
            int state = 0;
            int position = 0;
            while (name.length() - position >= Long.BYTES) {
                String block = name.substring(position, position + Long.BYTES);
                Map<String, Integer> transitions = states.get(state);
                Integer next = transitions.get(block);
                if (next == null) {
                    next = states.size();
                    transitions.put(block, next);
                    states.add(new LinkedHashMap<>());
                }
                state = next;
                position += Long.BYTES;
            }
            if (states.get(state).putIfAbsent(name.substring(position), ordinal) != null) {
                throw new IllegalArgumentException("Duplicate string in generated lexer: " + name);
            }
        }
        return states;
    }

    private static void emitState(Method.Builder method, Map<String, Integer> transitions) {
        if (transitions.isEmpty()) {
            method.addContentLine("return -1;");
            return;
        }
        Map<Integer, List<Edge>> lengths = new TreeMap<>();
        transitions.forEach((key, index) -> lengths.computeIfAbsent(key.length(), _ -> new ArrayList<>())
                .add(Edge.create(key, index)));
        boolean latin = lengths.values().stream().flatMap(List::stream).allMatch(edge -> edge.high == 0);
        if (latin) {
            method.addContentLine("if (high != 0) {").addContentLine("return -1;").addContentLine("}");
        }
        if (lengths.size() == 1) {
            var only = lengths.entrySet().iterator().next();
            method.addContentLine("if (length != " + only.getKey() + ") {").addContentLine("return -1;").addContentLine("}");
            emitEdges(method, only.getValue(), latin);
            return;
        }
        method.addContentLine("switch (length) {");
        lengths.forEach((length, edges) -> {
            method.addContentLine("case " + length + " -> {");
            emitEdges(method, edges, latin);
            method.addContentLine("}");
        });
        method.addContentLine("default -> {").addContentLine("return -1;").addContentLine("}").addContentLine("}");
    }

    private static void emitEdges(Method.Builder method, List<Edge> edges, boolean latin) {
        if (latin && edges.size() >= MIN_RANGE_EDGES) {
            TransitionRange range = TransitionRange.create(edges);
            if (range != null) {
                range.emit(method);
                return;
            }
        }
        if (edges.size() == 1) {
            Edge edge = edges.getFirst();
            String condition = "low == " + literal(edge.low);
            if (!latin) {
                condition += " && high == " + literal(edge.high);
            }
            method.addContentLine("return " + condition + " ? " + edge.target + " : -1;");
            return;
        }
        // Partition by a byte that distinguishes the most remaining transitions. Every leaf checks the complete block.
        int best = 0;
        int most = 0;
        for (int lane = 0; lane < (latin ? Long.BYTES : 2 * Long.BYTES); lane++) {
            int position = lane;
            int distinct = (int) edges.stream().mapToInt(edge -> edge.digit(position)).distinct().count();
            if (distinct > most) {
                most = distinct;
                best = position;
            }
        }
        Map<Integer, List<Edge>> groups = new TreeMap<>();
        for (Edge edge : edges) {
            groups.computeIfAbsent(edge.digit(best), _ -> new ArrayList<>()).add(edge);
        }
        String word = best < Long.BYTES ? "low" : "high";
        int shift = (best % Long.BYTES) * Byte.SIZE;
        method.addContentLine("switch ((int) (" + word + " >>> " + shift + ") & 0xff) {");
        groups.forEach((digit, matches) -> {
            method.addContentLine("case " + digit + " -> {");
            emitEdges(method, matches, latin);
            method.addContentLine("}");
        });
        method.addContentLine("default -> {").addContentLine("return -1;").addContentLine("}").addContentLine("}");
    }

    private static String literal(long word) {
        return "0x" + Long.toHexString(word) + "L";
    }

    // Several prefixes can finish with the same literal but different ordinals. Put the ordinal in
    // the transition to the shared suffix so one comparison handles all those accepting states.
    private record DecodedPlan(List<Map<String, Integer>> states, List<String> suffixes, int stride) {
        private static DecodedPlan compact(List<Map<String, Integer>> states, int nameCount) {
            if (states.size() <= STATES_PER_METHOD) {
                return new DecodedPlan(states, List.of(), 0);
            }
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (int i = 1; i < states.size(); i++) {
                var edges = states.get(i);
                if (edges.size() == 1) {
                    String suffix = edges.keySet().iterator().next();
                    if (suffix.length() < Long.BYTES) {
                        counts.merge(suffix, 1, Integer::sum);
                    }
                }
            }
            Map<String, Integer> groups = new LinkedHashMap<>();
            counts.forEach((suffix, count) -> {
                if (count > 1) {
                    groups.put(suffix, groups.size() + 1);
                }
            });
            if (groups.isEmpty()) {
                return new DecodedPlan(states, List.of(), 0);
            }
            int stride = Integer.highestOneBit(Math.max(nameCount, states.size()) - 1) << 1;
            int[] ids = new int[states.size()];
            List<Map<String, Integer>> compact = new ArrayList<>();
            for (int i = 0; i < states.size(); i++) {
                var edges = states.get(i);
                if (i > 0 && edges.size() == 1) {
                    var edge = edges.entrySet().iterator().next();
                    Integer group = groups.get(edge.getKey());
                    if (group != null) {
                        ids[i] = group * stride + edge.getValue();
                        continue;
                    }
                }
                ids[i] = compact.size();
                compact.add(new LinkedHashMap<>());
            }
            for (int i = 0; i < states.size(); i++) {
                if (ids[i] < stride) {
                    var target = compact.get(ids[i]);
                    states.get(i).forEach((word, index) -> {
                        if (word.length() == Long.BYTES) {
                            target.put(word, ids[index]);
                        } else {
                            target.put(word, index);
                        }
                    });
                }
            }
            return new DecodedPlan(compact, List.copyOf(groups.keySet()), stride);
        }

        private void emitSuffixes(Method.Builder method) {
            if (suffixes.isEmpty()) {
                return;
            }
            method.addContentLine("if (state >= " + stride + ") {");
            if (suffixes.size() > 1) {
                method.addContentLine("return switch (state / " + stride + ") {");
            }
            for (int i = 0; i < suffixes.size(); i++) {
                String suffix = suffixes.get(i);
                Edge word = Edge.create(suffix, 0);
                String start = suffixes.size() == 1 ? "return " : "case " + (i + 1) + " -> ";
                method.addContentLine(start + "length == " + suffix.length() + " && low == " + literal(word.low)
                                              + " && high == " + literal(word.high) + " ? state & " + (stride - 1) + " : -1;");
            }
            if (suffixes.size() > 1) {
                method.addContentLine("default -> -1;").addContent("}").addContentLine(";");
            }
            method.addContentLine("}");
        }
    }

    // Select bits that distinguish every pair before either name ends. The parser verifies the entire candidate.
    private record Projection(long firstMask, long secondMask, int[] routes) {
        private static Projection create(List<RawEdge> edges, int width) {
            BitSet[] columns = new BitSet[width * Byte.SIZE];
            Arrays.setAll(columns, _ -> new BitSet());
            int pairs = 0;
            for (int i = 0; i < edges.size(); i++) {
                RawEdge first = edges.get(i);
                for (int j = 0; j < i; j++) {
                    RawEdge second = edges.get(j);
                    int length = Math.min(width, Math.min(first.bytes.length, second.bytes.length) + 1);
                    boolean distinct = false;
                    for (int k = 0; k < length; k++) {
                        int bits = first.at(k) ^ second.at(k);
                        while (bits != 0) {
                            columns[k * Byte.SIZE + Integer.numberOfTrailingZeros(bits)].set(pairs);
                            bits &= bits - 1;
                            distinct = true;
                        }
                    }
                    if (!distinct) {
                        return null;
                    }
                    pairs++;
                }
            }
            BitSet selected = new BitSet();
            int size = 1;
            while (pairs > 0) {
                size *= 2;
                if (size > 8 * edges.size()) {
                    return null;
                }
                int best = 0;
                int gain = 0;
                for (int bit = 0; bit < columns.length; bit++) {
                    int count = columns[bit].cardinality();
                    if (count > gain) {
                        gain = count;
                        best = bit;
                    }
                }
                selected.set(best);
                pairs -= gain;
                BitSet covered = columns[best];
                for (BitSet column : columns) {
                    if (column != covered) {
                        column.andNot(covered);
                    }
                }
                covered.clear();
            }
            int[] routes = new int[size];
            Arrays.fill(routes, -1);
            for (RawEdge edge : edges) {
                int index = 0;
                int free = 0;
                int weight = 1;
                for (int bit = selected.nextSetBit(0); bit >= 0; bit = selected.nextSetBit(bit + 1)) {
                    int position = bit / Byte.SIZE;
                    if (position > edge.bytes.length) {
                        free |= weight;
                    } else if ((edge.at(position) & (1 << (bit % Byte.SIZE))) != 0) {
                        index |= weight;
                    }
                    weight <<= 1;
                }
                // Bytes following the closing quote can have any value.
                int subset = free;
                while (true) {
                    routes[index | subset] = edge.ordinal;
                    if (subset == 0) {
                        break;
                    }
                    subset = (subset - 1) & free;
                }
            }
            long[] masks = selected.toLongArray();
            return new Projection(masks[0], masks.length > 1 ? masks[1] : 0, routes);
        }

        private void emit(Method.Builder method, ClassBase.Builder<?, ?> owner, String name) {
            addTable(owner, name, routes);
            owner.addStaticImport("java.lang.Long.compress");
            method.addContent("return " + name + "[");
            if (firstMask != 0) {
                method.addContent("(int) compress(first, " + literal(firstMask) + ")");
            }
            if (secondMask != 0) {
                if (firstMask != 0) {
                    method.addContent(" | ");
                }
                method.addContent("((int) compress(second, " + literal(secondMask) + ") << " + Long.bitCount(firstMask) + ")");
            }
            method.addContentLine("];");
        }
    }

    // Use arithmetic when every target is in a contiguous range and only one or two bytes vary.
    private record TransitionRange(long mask, long fixed, int base, int count, int capacity, List<TransitionRange.Digit> digits) {
        private static TransitionRange create(List<Edge> edges) {
            var targets = edges.stream().mapToInt(Edge::target).summaryStatistics();
            if (targets.getMax() - targets.getMin() != edges.size() - 1) {
                return null;
            }
            int base = targets.getMin();
            long first = edges.getFirst().low;
            long changes = 0;
            for (Edge edge : edges) {
                changes |= first ^ edge.low;
            }
            List<Digit> digits = new ArrayList<>(2);
            for (int position = 0; position < Long.BYTES; position++) {
                int shift = position * Byte.SIZE;
                if (((changes >>> shift) & 0xff) != 0) {
                    if (digits.size() == 2) {
                        return null;
                    }
                    int lane = position;
                    var values = edges.stream().mapToInt(edge -> edge.digit(lane)).summaryStatistics();
                    int min = values.getMin();
                    int radix = values.getMax() - min + 1;
                    int stride = edges.stream().filter(edge -> edge.digit(lane) != min)
                            .mapToInt(edge -> edge.target - base).min().orElseThrow();
                    int mask = (Integer.highestOneBit(min ^ values.getMax()) << 1) - 1;
                    digits.add(new Digit(shift, mask, min & mask, radix, stride));
                }
            }
            digits.sort((left, right) -> Integer.compare(left.stride, right.stride));
            int capacity = 1;
            for (Digit digit : digits) {
                if (digit.stride != capacity) {
                    return null;
                }
                capacity *= digit.radix;
            }
            // The invariant bits and digits must identify every complete word and its exact target.
            for (Edge edge : edges) {
                int index = 0;
                for (Digit digit : digits) {
                    index += digit.value(edge.low) * digit.stride;
                }
                if (index != edge.target - base) {
                    return null;
                }
            }
            return new TransitionRange(~changes, first & ~changes, base, edges.size(), capacity, List.copyOf(digits));
        }

        private void emit(Method.Builder method) {
            method.addContentLine("if ((low & " + literal(mask) + ") != " + literal(fixed) + ") {")
                    .addContentLine("return -1;").addContentLine("}");
            List<String> conditions = new ArrayList<>();
            List<String> terms = new ArrayList<>();
            for (int i = 0; i < digits.size(); i++) {
                Digit digit = digits.get(i);
                String name = "digit" + i;
                String offset = digit.offset == 0 ? "" : " - " + digit.offset;
                method.addContentLine("int " + name + " = ((int) (low >>> " + digit.shift + ") & " + digit.mask + ")"
                                              + offset + ";");
                if (digit.offset > 0) {
                    conditions.add(name + " < 0");
                }
                if (digit.mask - digit.offset >= digit.radix) {
                    conditions.add(name + " >= " + digit.radix);
                }
                terms.add(digit.stride == 1 ? name : digit.stride + " * " + name);
            }
            if (!conditions.isEmpty()) {
                method.addContentLine("if (" + String.join(" || ", conditions) + ") {")
                        .addContentLine("return -1;").addContentLine("}");
            }
            String index = String.join(" + ", terms);
            String offset = base == 0 ? "" : base + " + ";
            if (capacity == count) {
                method.addContentLine("return " + offset + index + ";");
            } else {
                method.addContentLine("int index = " + index + ";");
                method.addContentLine("return index < " + count + " ? " + offset + "index : -1;");
            }
        }

        private record Digit(int shift, int mask, int offset, int radix, int stride) {
            private int value(long word) {
                return ((int) (word >>> shift) & mask) - offset;
            }
        }
    }

    private record RawEdge(byte[] bytes, int ordinal) {
        private int at(int position) {
            return position == bytes.length ? '"' : bytes[position] & 0xff;
        }
    }

    private record Edge(long low, long high, int target) {
        static Edge create(String key, int target) {
            long low = 0;
            long high = 0;
            for (int i = 0; i < key.length(); i++) {
                char unit = key.charAt(i);
                low |= (unit & 0xffL) << (i * Byte.SIZE);
                high |= (long) (unit >>> Byte.SIZE) << (i * Byte.SIZE);
            }
            return new Edge(low, high, target);
        }

        int digit(int lane) {
            long word = lane < Long.BYTES ? low : high;
            return (int) (word >>> ((lane % Long.BYTES) * Byte.SIZE)) & 0xff;
        }
    }
}
