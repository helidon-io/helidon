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

package io.helidon.builder.testing.utils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.builder.AttributeVisitor;
import io.helidon.builder.BuilderInterceptor;

/**
 * Utilities for {@link io.helidon.builder.Builder}-generated targets.
 */
@SuppressWarnings("rawtypes")
public final class BuilderUtils {

    private BuilderUtils() {
    }

    /**
     * Expand a {@link io.helidon.builder.Builder}-generated target to a flattened map of String-based, key-value tuples.
     * The keys of the map will use dotted names.
     *
     * @param builtTarget the built target value
     * @return the flattened map of key-value pairs
     * @throws IllegalStateException if the provided argument is not a builder-generated target instance
     */
    public static Map<String, String> expand(Object builtTarget) {
        return expand(builtTarget, DefaultExpandOptions.builder().build());
    }

    /**
     * Expand a {@link io.helidon.builder.Builder}-generated target to a flattened map of String-based, key-value tuples. Also,
     * optionally includes the type information for each attribute if that attribute is found to be a builder target type.
     *
     * @param builtTarget  the built target value
     * @param expandOptions the expand options
     * @return the flattened map of key-value pairs
     * @throws IllegalStateException if the provided argument is not a builder-generated target instance
     */
    public static Map<String, String> expand(Object builtTarget,
                                             ExpandOptions expandOptions) {
        Objects.requireNonNull(builtTarget);

        Map<String, String> result = new LinkedHashMap<>();
        AtomicReference<AttributeVisitor<String>> ref = new AtomicReference<>();
        AttributeVisitor<String> mapCollectorVisitor = (attrName, valueSupplier, meta, prefix, type, typeArgument) -> {
            Object val = valueSupplier.get();
            if (!prefix.isEmpty()) {
                prefix += ".";
            }
            prefix += attrName;
            VisitAttributes<String> visitAttributes = toVisitAttributes(val, false);
            if (visitAttributes != null) {
                if (expandOptions.includeTypeInformation()) {
                    result.put(prefix, val.getClass().getName());
                }
                visitAttributes.visitAttributes(ref.get(), prefix);
                return;
            }

            if (val instanceof Collection) {
                int i = 0;
                for (Object subVal : (Collection<?>) val) {
                    VisitAttributes<String> subVisitAttributes = toVisitAttributes(subVal, false);
                    String tag = prefix + "[" + i + "]";
                    if (subVisitAttributes != null) {
                        if (expandOptions.includeTypeInformation()) {
                            result.put(tag, subVal.getClass().getName());
                        }
                        subVisitAttributes.visitAttributes(ref.get(), tag);
                    } else {
                        result.put(tag, stringValueOf(subVal, expandOptions));
                    }
                    i++;
                }
            } else if (val instanceof Map) {
                if (expandOptions.sortCollections()) {
                    try {
                        val = (val instanceof TreeMap) ? (TreeMap<?, ?>) val : new TreeMap<>((Map<?, ?>) val);
                    } catch (Exception e) {
                        // swallow it - this was just a best effort anyway
                    }
                }

                for (Map.Entry<?, ?> e : ((Map<?, ?>) val).entrySet()) {
                    Object subVal = e.getValue();
                    VisitAttributes<String> subVisitAttributes = toVisitAttributes(subVal, false);
                    String tag = prefix + "[\"" + e.getKey() + "\"]";
                    if (subVisitAttributes != null) {
                        if (expandOptions.includeTypeInformation()) {
                            result.put(tag, subVal.getClass().getName());
                        }
                        subVisitAttributes.visitAttributes(ref.get(), tag);
                        return;
                    } else {
                        result.put(tag, stringValueOf(subVal, expandOptions));
                    }
                }
            } else if (val instanceof Optional<?>) {
                Optional<?> optVal = (Optional<?>) val;
                if (optVal.isPresent()) {
                    result.put(prefix, stringValueOf(optVal.get(), expandOptions));
                }
            } else {
                result.put(prefix, stringValueOf(val, expandOptions));
            }
        };
        ref.set(mapCollectorVisitor);
        VisitAttributes<String> visitAttributes = toVisitAttributes(builtTarget, true);
        visitAttributes.visitAttributes(ref.get(), "");
        return result;
    }

    @SuppressWarnings("unchecked")
    private static String stringValueOf(Object val,
                                        ExpandOptions expandOptions) {
        if (val == null) {
            return null;
        }

        if (val instanceof Collection<?>) {
            if (expandOptions.emptyCollectionMapsToNull() && ((Collection<?>) val).isEmpty()) {
                return null;
            }

            if (expandOptions.sortCollections()) {
                try {
                    if (val instanceof Set<?>) {
                        val = new TreeSet<>((Collection<?>) val);
                    } else if (val instanceof List<?>) {
                        List list = new ArrayList<>((Collection<?>) val);
                        Collections.sort(list);
                        val = list;
                    }
                } catch (Exception e) {
                    // swallow it - this was just a best effort anyway
                }
            }
        }

        return expandOptions.toStringFunction().orElseThrow().apply(val);
    }

    /**
     * Computes the set of difference between two {@link io.helidon.builder.Builder}-generated target instances.
     *
     * @param leftSide the left builder target
     * @param rightSide the right builder target to compare to the left side
     * @return the list of differences
     */
    public static List<Diff> diff(Object leftSide,
                                  Object rightSide) {
        return diff(leftSide, rightSide, DefaultDiffOptions.builder().build());
    }

    /**
     * Computes the set of difference between two {@link io.helidon.builder.Builder}-generated target instances.
     *
     * @param leftSide the left builder target
     * @param rightSide the right builder target to compare to the left side
     * @param diffOptions the diff options
     * @return the list of differences
     */
    public static List<Diff> diff(Object leftSide,
                                  Object rightSide,
                                  DiffOptions diffOptions) {
        Objects.requireNonNull(leftSide);
        Objects.requireNonNull(rightSide);

        if (!leftSide.equals(rightSide)) {
            VisitAttributes<?> visitLeft = toVisitAttributes(leftSide, false);
            VisitAttributes<?> visitRight = toVisitAttributes(rightSide, false);

            if (visitLeft != null && visitRight != null) {
                Map<String, String> leftExpand = expand(leftSide, diffOptions);
                Map<String, String> rightExpand = expand(rightSide, diffOptions);
                return diff(leftExpand, rightExpand);
            } else {
                Diff diff = DefaultDiff.builder()
                        .leftSide(diffOptions.toStringFunction().orElseThrow().apply(leftSide))
                        .rightSide(diffOptions.toStringFunction().orElseThrow().apply(rightSide))
                        .build();
                return List.of(diff);
            }
        }

        return List.of();
    }

    private static List<Diff> diff(Map<String, String> left,
                                   Map<String, String> right) {
        TreeSet<String> allKeys = new TreeSet<>();
        allKeys.addAll(left.keySet());
        allKeys.addAll(right.keySet());

        List<Diff> result = new ArrayList<>();
        for (String key : allKeys) {
            String leftSide = left.get(key);
            String rightSide = right.get(key);
            if (!Objects.equals(leftSide, rightSide)) {
                Diff diff = DefaultDiff.builder()
                        .key(key)
                        .leftSide(Optional.ofNullable(leftSide))
                        .rightSide(Optional.ofNullable(rightSide))
                        .build();
                result.add(diff);
            }
        }

        return result;
    }

    static VisitAttributes<String> toVisitAttributes(Object val,
                                                     boolean expected) {
        try {
            Method m = val.getClass().getMethod("visitAttributes", AttributeVisitor.class, Object.class);
            m.setAccessible(true);
            return (visitor, userDefinedCtx) -> {
                try {
                    m.invoke(val, visitor, userDefinedCtx);
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to invoke visitAttributes on " + val, e);
                }
            };
        } catch (Exception e) {
            if (expected) {
                throw new IllegalStateException("Expected to find a usable visitAttributes method", e);
            }
            return null;
        }
    }


    static final class ExpandOptionsInterceptor implements BuilderInterceptor<AbstractExpandOptions.Builder> {
        @Override
        @SuppressWarnings("unchecked")
        public AbstractExpandOptions.Builder intercept(AbstractExpandOptions.Builder target) {
            if (target.toStringFunction().isEmpty()) {
                target.toStringFunction(String::valueOf);
            }
            return target;
        }
    }

}
