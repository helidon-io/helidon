/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.builder.processor.tools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.pico.builder.processor.spi.TypeInfo;
import io.helidon.pico.builder.spi.BeanUtils;
import io.helidon.pico.types.AnnotationAndValue;
import io.helidon.pico.types.DefaultAnnotationAndValue;
import io.helidon.pico.types.TypeName;
import io.helidon.pico.types.TypedElementName;

import static io.helidon.pico.builder.processor.tools.DefaultBuilderCreator.BUILDER_ANNO_TYPE_NAME;
import static io.helidon.pico.builder.processor.tools.DefaultBuilderCreator.DEFAULT_INCLUDE_META_ATTRIBUTES;
import static io.helidon.pico.builder.processor.tools.DefaultBuilderCreator.DEFAULT_LIST_TYPE;
import static io.helidon.pico.builder.processor.tools.DefaultBuilderCreator.DEFAULT_MAP_TYPE;
import static io.helidon.pico.builder.processor.tools.DefaultBuilderCreator.DEFAULT_REQUIRE_LIBRARY_DEPENDENCIES;
import static io.helidon.pico.builder.processor.tools.DefaultBuilderCreator.DEFAULT_SET_TYPE;
import static io.helidon.pico.builder.processor.tools.DefaultBuilderCreator.SUPPORT_STREAMS_ON_BUILDER;
import static io.helidon.pico.builder.processor.tools.DefaultBuilderCreator.SUPPORT_STREAMS_ON_IMPL;

/**
 * Represents the context of the body being code generated.
 */
public class BodyContext {
    private final boolean doingConcreteType;
    private final TypeName implTypeName;
    private final TypeInfo typeInfo;
    private final AnnotationAndValue builderAnnotation;
    private final Map<String, TypedElementName> map = new LinkedHashMap<>();
    private final List<TypedElementName> allTypeInfos = new ArrayList<>();
    private final List<String> allAttributeNames = new ArrayList<>();
    private final AtomicReference<TypeName> parentTypeName = new AtomicReference<>();
    private final AtomicReference<TypeName> parentAnnotationType = new AtomicReference<>();
    private final boolean hasStreamSupportOnImpl;
    private final boolean hasStreamSupportOnBuilder;
    private final boolean includeMetaAttributes;
    private final boolean requireLibraryDependencies;
    private final boolean isBeanStyleRequired;
    private final String listType;
    private final String mapType;
    private final String setType;
    private final boolean hasParent;
    private final TypeName ctorBuilderAcceptTypeName;
    private final String genericBuilderClassDecl;
    private final String genericBuilderAliasDecl;
    private final String genericBuilderAcceptAliasDecl;

    /**
     * Constructor.
     *
     * @param doingConcreteType true if the concrete type is being generated, otherwise the abstract class
     * @param implTypeName      the impl type name
     * @param typeInfo          the type info
     * @param builderAnnotation the builder annotation
     */
    BodyContext(boolean doingConcreteType,
                          TypeName implTypeName,
                          TypeInfo typeInfo,
                          AnnotationAndValue builderAnnotation) {
        this.doingConcreteType = doingConcreteType;
        this.implTypeName = implTypeName;
        this.typeInfo = typeInfo;
        this.builderAnnotation = builderAnnotation;
        this.hasStreamSupportOnImpl = hasStreamSupportOnImpl(doingConcreteType, builderAnnotation);
        this.hasStreamSupportOnBuilder = hasStreamSupportOnBuilder(doingConcreteType, builderAnnotation);
        this.includeMetaAttributes = toIncludeMetaAttributes(builderAnnotation, typeInfo);
        this.requireLibraryDependencies = toRequireLibraryDependencies(builderAnnotation, typeInfo);
        this.isBeanStyleRequired = toRequireBeanStyle(builderAnnotation, typeInfo);
        this.listType = toListImplType(builderAnnotation, typeInfo);
        this.mapType = toMapImplType(builderAnnotation, typeInfo);
        this.setType = toSetImplType(builderAnnotation, typeInfo);
        gatherAllAttributeNames(this, typeInfo);
        assert (allTypeInfos.size() == allAttributeNames.size());
        this.hasParent = Objects.nonNull(parentTypeName.get());
        this.ctorBuilderAcceptTypeName = (hasParent)
                ? typeInfo.typeName()
                : (
                        Objects.nonNull(parentAnnotationType.get()) && typeInfo.elementInfo().isEmpty()
                                ? typeInfo.superTypeInfo().get().typeName() : typeInfo.typeName());
        this.genericBuilderClassDecl = "Builder";
        this.genericBuilderAliasDecl = ("B".equals(typeInfo.typeName().className())) ? "BU" : "B";
        this.genericBuilderAcceptAliasDecl = ("T".equals(typeInfo.typeName().className())) ? "TY" : "T";
    }

    /**
     * Returns true if we are currently processing the concrete builder type.
     *
     * @return true if we are processing the concrete type
     */
    protected boolean doingConcreteType() {
        return doingConcreteType;
    }

    /**
     * Returns the impl type name.
     *
     * @return the type name
     */
    protected TypeName implTypeName() {
        return implTypeName;
    }

    /**
     * Returns the type info.
     *
     * @return the type info
     */
    protected TypeInfo typeInfo() {
        return typeInfo;
    }

    /**
     * Returns the builder annotation that triggers things.
     *
     * @return the builder annotation
     */
    protected AnnotationAndValue builderAnnotation() {
        return builderAnnotation;
    }

    /**
     * Returns the map of all type elements in the entire hierarchy.
     *
     * @return the map of elements by name
     */
    protected Map<String, TypedElementName> map() {
        return map;
    }

    /**
     * Returns the list of all type elements.
     *
     * @return the list of type elements
     */
    protected List<TypedElementName> allTypeInfos() {
        return allTypeInfos;
    }

    /**
     * Returns the list of all attributes names.
     *
     * @return the list of attribute names
     */
    protected List<String> allAttributeNames() {
        return allAttributeNames;
    }

    /**
     * Returns the parent type name of the builder.
     *
     * @return the parent type name
     */
    protected AtomicReference<TypeName> parentTypeName() {
        return parentTypeName;
    }

    /**
     * Returns the parent annotation type.
     *
     * @return the parent annotation type
     */
    protected AtomicReference<TypeName> parentAnnotationType() {
        return parentAnnotationType;
    }

    /**
     * Returns true if there is stream support included on the generated class.
     *
     * @return true if stream support enabled
     */
    protected boolean hasStreamSupportOnImpl() {
        return hasStreamSupportOnImpl;
    }

    /**
     * Returns true if there is stream support included on the builder generated class.
     *
     * @return true if stream support enabled
     */
    protected boolean hasStreamSupportOnBuilder() {
        return hasStreamSupportOnBuilder;
    }

    /**
     * Returns true if meta attributes should be generated.
     *
     * @return true if meta attributes should be generated
     */
    protected boolean includeMetaAttributes() {
        return includeMetaAttributes;
    }

    /**
     * Returns true if Helidon library dependencies should be expected.
     *
     * @return true if Helidon library dependencies are expected
     */
    protected boolean requireLibraryDependencies() {
        return requireLibraryDependencies;
    }

    /**
     * Returns true if bean "getter" and "is" style is required.
     *
     * @return true if bean style is required
     */
    protected boolean isBeanStyleRequired() {
        return isBeanStyleRequired;
    }

    /**
     * Returns the list type generated.
     *
     * @return the list type
     */
    protected String listType() {
        return listType;
    }

    /**
     * Returns the map type generated.
     *
     * @return the map type
     */
    protected String mapType() {
        return mapType;
    }

    /**
     * Returns the set type generated.
     *
     * @return the set type
     */
    protected String setType() {
        return setType;
    }

    /**
     * Returns true if the current type has a parent.
     *
     * @return true if current has parent
     */
    protected boolean hasParent() {
        return hasParent;
    }

    /**
     * Returns the streamable accept type of the builder and constructor.
     *
     * @return the builder accept type
     */
    protected TypeName ctorBuilderAcceptTypeName() {
        return ctorBuilderAcceptTypeName;
    }

    /**
     * Returns the generic declaration for the builder class type.
     *
     * @return the generic declaration
     */
    protected String genericBuilderClassDecl() {
        return genericBuilderClassDecl;
    }

    /**
     * Returns the builder generics alias name for the type being built.
     *
     * @return the builder generics alias name
     */
    protected String genericBuilderAliasDecl() {
        return genericBuilderAliasDecl;
    }

    /**
     * Returns the builder generics alias name for the builder itself.
     *
     * @return the builder generics alias name
     */
    protected String genericBuilderAcceptAliasDecl() {
        return genericBuilderAcceptAliasDecl;
    }

    /**
     * returns the bean attribute name of a particular method.
     *
     * @param method                the method
     * @param isBeanStyleRequired   is bean style required
     * @return the bean attribute name
     */
    protected static String toBeanAttributeName(TypedElementName method,
                                                boolean isBeanStyleRequired) {
        AtomicReference<Optional<List<String>>> refAttrNames = new AtomicReference<>();
        BeanUtils.validateAndParseMethodName(method.elementName(), method.typeName().name(), isBeanStyleRequired, refAttrNames);
        List<String> attrNames = (refAttrNames.get().isEmpty()) ? Collections.emptyList() : refAttrNames.get().get();
        if (!isBeanStyleRequired) {
            return (!attrNames.isEmpty()) ? attrNames.get(0) : method.elementName();
        }
        return Objects.requireNonNull(attrNames.get(0));
    }

    private static boolean hasStreamSupportOnImpl(boolean ignoreDoingConcreteClass,
                                                  AnnotationAndValue ignoreBuilderAnnotation) {
        return SUPPORT_STREAMS_ON_IMPL;
    }

    private static boolean hasStreamSupportOnBuilder(boolean ignoreDoingConcreteClass,
                                                     AnnotationAndValue ignoreBuilderAnnotation) {
        return SUPPORT_STREAMS_ON_BUILDER;
    }

    /**
     * In support of {@link io.helidon.pico.builder.Builder#includeMetaAttributes()}.
     */
    private static boolean toIncludeMetaAttributes(AnnotationAndValue builderAnnotation,
                                                   TypeInfo typeInfo) {
        String val = searchForBuilderAnnotation("includeMetaAttributes", builderAnnotation, typeInfo);
        return val == null ? DEFAULT_INCLUDE_META_ATTRIBUTES : Boolean.parseBoolean(val);
    }

    /**
     * In support of {@link io.helidon.pico.builder.Builder#requireLibraryDependencies()}.
     */
    private static boolean toRequireLibraryDependencies(AnnotationAndValue builderAnnotation,
                                                        TypeInfo typeInfo) {
        String val = searchForBuilderAnnotation("requireLibraryDependencies", builderAnnotation, typeInfo);
        return val == null ? DEFAULT_REQUIRE_LIBRARY_DEPENDENCIES : Boolean.parseBoolean(val);
    }

    /**
     * In support of {@link io.helidon.pico.builder.Builder#requireBeanStyle()}.
     */
    private static boolean toRequireBeanStyle(AnnotationAndValue builderAnnotation,
                                              TypeInfo typeInfo) {
        String val = searchForBuilderAnnotation("requireBeanStyle", builderAnnotation, typeInfo);
        return Boolean.parseBoolean(val);
    }

    /**
     * In support of {@link io.helidon.pico.builder.Builder#listImplType()}.
     */
    private static String toListImplType(AnnotationAndValue builderAnnotation,
                                         TypeInfo typeInfo) {
        String type = searchForBuilderAnnotation("listImplType", builderAnnotation, typeInfo);
        return (!BuilderTypeTools.hasNonBlankValue(type)) ? DEFAULT_LIST_TYPE : type;
    }

    /**
     * In support of {@link io.helidon.pico.builder.Builder#mapImplType()} ()}.
     */
    private static String toMapImplType(AnnotationAndValue builderAnnotation,
                                        TypeInfo typeInfo) {
        String type = searchForBuilderAnnotation("mapImplType", builderAnnotation, typeInfo);
        return (!BuilderTypeTools.hasNonBlankValue(type)) ? DEFAULT_MAP_TYPE : type;
    }

    /**
     * In support of {@link io.helidon.pico.builder.Builder#setImplType()}.
     */
    private static String toSetImplType(AnnotationAndValue builderAnnotation,
                                        TypeInfo typeInfo) {
        String type = searchForBuilderAnnotation("setImplType", builderAnnotation, typeInfo);
        return (!BuilderTypeTools.hasNonBlankValue(type)) ? DEFAULT_SET_TYPE : type;
    }

    private static String searchForBuilderAnnotation(String key,
                                                     AnnotationAndValue builderAnnotation,
                                                     TypeInfo typeInfo) {
        String val = builderAnnotation.value(key).orElse(null);
        if (val != null) {
            return val;
        }

        if (!builderAnnotation.typeName().equals(BUILDER_ANNO_TYPE_NAME)) {
            builderAnnotation = DefaultAnnotationAndValue
                    .findFirst(BUILDER_ANNO_TYPE_NAME, typeInfo.annotations(), false).orElse(null);
            if (Objects.nonNull(builderAnnotation)) {
                val = builderAnnotation.value(key).orElse(null);
            }
        }

        return val;
    }

    private static void gatherAllAttributeNames(BodyContext ctx,
                                                TypeInfo typeInfo) {
        TypeInfo superTypeInfo = typeInfo.superTypeInfo().orElse(null);
        if (Objects.nonNull(superTypeInfo)) {
            Optional<? extends AnnotationAndValue> superBuilderAnnotation = DefaultAnnotationAndValue
                    .findFirst(ctx.builderAnnotation.typeName(), superTypeInfo.annotations(), false);
            if (superBuilderAnnotation.isEmpty()) {
                gatherAllAttributeNames(ctx, superTypeInfo);
            } else {
                populateMap(ctx.map, superTypeInfo, ctx.isBeanStyleRequired);
            }

            if (Objects.isNull(ctx.parentTypeName.get())
                    && superTypeInfo.typeKind().equals("INTERFACE")) {
                ctx.parentTypeName.set(superTypeInfo.typeName());
            } else if (Objects.isNull(ctx.parentAnnotationType.get())
                    && superTypeInfo.typeKind().equals("ANNOTATION_TYPE")) {
                ctx.parentAnnotationType.set(superTypeInfo.typeName());
            }
        }

        for (TypedElementName method : typeInfo.elementInfo()) {
            String beanAttributeName = toBeanAttributeName(method, ctx.isBeanStyleRequired);
            TypedElementName existing = ctx.map.get(beanAttributeName);
            if (Objects.nonNull(existing)
                    && BeanUtils.isBooleanType(method.typeName().name())
                    && method.elementName().startsWith("is")) {
                AtomicReference<Optional<List<String>>> alternateNames = new AtomicReference<>();
                BeanUtils.validateAndParseMethodName(method.elementName(),
                                                     method.typeName().name(), true, alternateNames);
                assert (Objects.nonNull(alternateNames.get()));
                final String currentAttrName = beanAttributeName;
                Optional<String> alternateName = alternateNames.get().orElse(Collections.emptyList()).stream()
                        .filter(it -> !it.equals(currentAttrName))
                        .findFirst();
                if (alternateName.isPresent() && !ctx.map.containsKey(alternateName.get())) {
                    beanAttributeName = alternateName.get();
                    existing = ctx.map.get(beanAttributeName);
                }
            }

            if (Objects.nonNull(existing)) {
                if (!existing.typeName().equals(method.typeName())) {
                    throw new IllegalStateException(method + " cannot redefine types from super for " + beanAttributeName);
                }

                // allow the subclass to override the defaults, etc.
                Objects.requireNonNull(ctx.map.put(beanAttributeName, method));
                int pos = ctx.allAttributeNames.indexOf(beanAttributeName);
                if (pos >= 0) {
                    ctx.allTypeInfos.set(pos, method);
                }
                continue;
            }

            Object prev = ctx.map.put(beanAttributeName, method);
            assert (Objects.isNull(prev));

            ctx.allTypeInfos.add(method);
            if (ctx.allAttributeNames.contains(beanAttributeName)) {
                throw new AssertionError("duplicate attribute name: " + beanAttributeName + " processing " + typeInfo);
            }
            ctx.allAttributeNames.add(beanAttributeName);
        }
    }

    private static void populateMap(Map<String, TypedElementName> map,
                                    TypeInfo typeInfo,
                                    boolean isBeanStyleRequired) {
        if (typeInfo.superTypeInfo().isPresent()) {
            populateMap(map, typeInfo.superTypeInfo().get(), isBeanStyleRequired);
        }

        for (TypedElementName method : typeInfo.elementInfo()) {
            String beanAttributeName = toBeanAttributeName(method, isBeanStyleRequired);
            TypedElementName existing = map.get(beanAttributeName);
            if (Objects.nonNull(existing)) {
                if (!existing.typeName().equals(method.typeName())) {
                    throw new IllegalStateException(method + " cannot redefine types from super for " + beanAttributeName);
                }

                // allow the subclass to override the defaults, etc.
                Objects.requireNonNull(map.put(beanAttributeName, method));
            } else {
                Object prev = map.put(beanAttributeName, method);
                assert (Objects.isNull(prev));
            }
        }
    }

}
