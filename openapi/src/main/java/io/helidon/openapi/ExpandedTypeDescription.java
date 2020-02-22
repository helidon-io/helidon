/*
 * Copyright (c) 2019-2020 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.openapi;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.microprofile.openapi.models.Extensible;
import org.eclipse.microprofile.openapi.models.PathItem;
import org.eclipse.microprofile.openapi.models.media.Schema;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.introspector.MethodProperty;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.introspector.PropertySubstitute;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;

/**
 * Extension of {@link TypeDescription} that handles of:
 * <ul>
 *     <li>nested enums,</li>
 *     <li>extensible types, and</li>
 *     <li>references.</li>
 * </ul>
 * <p>
 *     The OpenAPI document format uses lower-case enum names and values, while the SmallRye
 *     definitions use upper-case. This class simplifies adding the special handling for enums
 *     declared within a particular class.
 * </p>
 * <p>
 *     Some of the MP OpenAPI items are extensible, meaning they accept sub-item keys with the
 *     "x-" prefix. This class supports extensions. For scalars it delegates to the normal
 *     SnakeYAML processing to correctly type and parse the scalar. For sequences it
 *     creates {@code List}s. For mappings it creates {@code Map}s. The subnodes of the lists and
 *     maps are handled by the normal SnakeYAML parsing, so the resulting elements in lists and
 *     maps are of the SnakeYAML-inferred types.
 * </p>
 * <p>
 *     A subnode {@code $ref} maps to the {@code ref} property on the MP OpenAPI types. This type
 *     description simplifies defining the {@code $ref} property to those types that support it.
 * </p>
 */
class ExpandedTypeDescription extends TypeDescription {

    private static final String EXTENSION_PROPERTY_PREFIX = "x-";

    private final Map<String, SnakeYAMLParserHelper.EnumType<?>> enums = new HashMap<>();

    private Class<?> impl;

    /**
     * Factory method for ease of chaining other method invocations.
     *
     * @param clazz interface type to describe
     * @param impl implementation class for the interface
     * @return resulting TypeDescription
     */
    static ExpandedTypeDescription create(Class<? extends Object> clazz, Class<?> impl) {

        ExpandedTypeDescription result = clazz.equals(Schema.class)
            ? new SchemaTypeDescription(clazz, impl) : new ExpandedTypeDescription(clazz, impl);
        return result;
    }

    static ExpandedTypeDescription addEnum(ExpandedTypeDescription type, SnakeYAMLParserHelper.EnumType<?> enumType) {
        type.addEnum(enumType.propertyName(), enumType);
        return type;
    }

    private ExpandedTypeDescription(Class<? extends Object> clazz, Class<?> impl) {
        super(clazz, null, impl);
        this.impl = impl;
    }

    /**
     * Adds an enum to the type description, by property name and the enum type def
     * in the generated helper class.
     *
     * @param propertyName property name for the enum
     * @param enumType type for the Enum
     * @return this type description
     */
    ExpandedTypeDescription addEnum(String propertyName, SnakeYAMLParserHelper.EnumType enumType) {
        enums.put(propertyName, enumType);
        return this;
    }

    /**
     * Adds property handling for a {@code $ref} reference.
     *
     * @return this type description
     */
    ExpandedTypeDescription addRef() {
        PropertySubstitute sub = new PropertySubstitute("$ref", String.class, "getRef", "setRef");
        sub.setTargetType(impl);
        substituteProperty(sub);
        return this;
    }

    /**
     * Adds property handling for extensions.
     *
     * @return this type description
     */
    ExpandedTypeDescription addExtensions() {
        PropertySubstitute sub = new PropertySubstitute("extensions", Map.class, "getExtensions", "setExtensions");
        sub.setTargetType(impl);
        substituteProperty(sub);
        return this;
    }

    @Override
    public Property getProperty(String name) {
        return isExtension(name) ? new ExtensionProperty(name) : super.getProperty(name);
    }

    @Override
    public Set<Property> getProperties() {
        /*
         * The YAML/JSON contains lower-case PathItem.HttpMethod names, and we provide
         * property substitutions (declarations) for those lower-case versions. But the OpenAPI
         * bean analysis will give upper-case property names. We need to remove those from the
         * property list.
         */
        Set<Property> result = super.getProperties();
        Set<Property> propsToRemove = new HashSet<Property>();

        if (getType().equals(PathItem.class)) {
            for (Property p : result) {
                for (PathItem.HttpMethod m : PathItem.HttpMethod.values()) {
                    if (p.getName().equals(m.name())) {
                        propsToRemove.add(p);
                    }
                }
            }
            result.removeAll(propsToRemove);
        }
        return result;
    }

    @Override
    public Object newInstance(String propertyName, Node node) {
        if (enums.containsKey(propertyName)) {
            String valueText = ((ScalarNode) node).getValue().toUpperCase();

            return enums.get(propertyName).valueOf(valueText);
        }
        return super.newInstance(propertyName, node);
    }

    @Override
    public boolean setupPropertyType(String key, Node valueNode) {
        return setupExtensionType(key, valueNode) || super.setupPropertyType(key, valueNode);
    }

    /**
     * Returns the implementation class associated with this type descr.
     *
     * @return implementation class
     */
    Class<?> impl() {
        return impl;
    }

    SnakeYAMLParserHelper.EnumType<?> enumType(String propertyName) {
        return enums.get(propertyName);
    }

    private boolean setupExtensionType(String key, Node valueNode) {
        if (isExtension(key)) {
            switch (valueNode.getNodeId()) {
                case sequence:
                    valueNode.setType(List.class);
                    return true;

                case anchor:
                    break;

                case mapping:
                    valueNode.setType(Map.class);
                    return true;

                case scalar:
                    break;

                default:

            }
        }
        return false;
    }

    private boolean isExtension(String name) {
        return name.startsWith(EXTENSION_PROPERTY_PREFIX);
    }

    /**
     * Specific type description for {@code Schema}.
     * <p>
     *     The {@code Schema} node allows the {@code additionalProperties} subnode to be either
     *     {@code Boolean} or another {@code Schema}. This type description provides a customized
     *     property description for {@code additionalProperties} that infers which variant a
     *     specific node in the document actually uses and then processes it accordingly.
     * </p>
     */
    static class SchemaTypeDescription extends ExpandedTypeDescription {

        private static final PropertyDescriptor ADDL_PROPS_PROP_DESCRIPTOR = preparePropertyDescriptor();

        private static final Property ADDL_PROPS_PROPERTY =
                new MethodProperty(ADDL_PROPS_PROP_DESCRIPTOR) {

                    @Override
                    public void set(Object object, Object value) throws Exception {
                        Schema s = Schema.class.cast(object);
                        if (value instanceof Schema) {
                            s.setAdditionalPropertiesSchema((Schema) value);
                        } else {
                            s.setAdditionalPropertiesBoolean((Boolean) value);
                        }
                    }

                    @Override
                    public Object get(Object object) {
                        Schema s = Schema.class.cast(object);
                        Boolean b = s.getAdditionalPropertiesBoolean();
                        return b != null ? b : s.getAdditionalPropertiesSchema();
                    }
                };

        private static PropertyDescriptor preparePropertyDescriptor() {
            try {
                return new PropertyDescriptor("additionalProperties",
                        Schema.class.getMethod("getAdditionalPropertiesSchema"),
                        Schema.class.getMethod("setAdditionalPropertiesSchema", Schema.class));
            } catch (IntrospectionException | NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }

        private SchemaTypeDescription(Class<? extends Object> clazz, Class<?> impl) {
            super(clazz, impl);
        }

        @Override
        public Property getProperty(String name) {
            return name.equals("additionalProperties") ? ADDL_PROPS_PROPERTY : super.getProperty(name);
        }
    }

    /**
     * Property description for an extension subnode.
     */
    static class ExtensionProperty extends Property {

        ExtensionProperty(String name) {
            super(name, Object.class);
        }

        @Override
        public Class<?>[] getActualTypeArguments() {
            return new Class[0];
        }

        @Override
        public void set(Object object, Object value) throws Exception {
            asExt(object).addExtension(getName(), value);
        }

        @Override
        public Object get(Object object) {
            return asExt(object).getExtensions().get(getName());
        }

        @Override
        public List<Annotation> getAnnotations() {
            return null;
        }

        @Override
        public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
            return null;
        }

        private Extensible<?> asExt(Object object) {
            if (!(object instanceof Extensible<?>)) {
                throw new IllegalArgumentException(String.format(
                        "Cannot assign extension %s to object of type %s that does not implement %s", getName(),
                        object.getClass().getName(), Extensible.class.getName()));
            }
            return (Extensible<?>) object;
        }
    }
}
