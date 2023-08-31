/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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
package io.helidon.microprofile.openapi;

import java.io.PrintWriter;
import java.io.Writer;
import java.lang.System.Logger.Level;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.helidon.openapi.OpenApiFormat;

import io.smallrye.openapi.api.models.OpenAPIImpl;
import org.eclipse.microprofile.openapi.models.Extensible;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.Reference;
import org.eclipse.microprofile.openapi.models.media.Schema;
import org.eclipse.microprofile.openapi.models.parameters.Parameter;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.DumperOptions.ScalarStyle;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeId;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

/**
 * Expresses an existing {@code OpenAPI} instance as an OpenAPI document. This implementation uses
 * SnakeYAML to write OpenAPI documents from the SmallRye MP OpenAPI model interfaces and classes
 * while suppressing tags that would indicate the SmallRye classes -- we don't want to
 * suggest that the output can only be read into the SmallRye implementation.
 */
final class OpenApiSerializer {

    private static final DumperOptions YAML_DUMPER_OPTIONS = new DumperOptions();
    private static final DumperOptions JSON_DUMPER_OPTIONS = new DumperOptions();

    private static final System.Logger LOGGER = System.getLogger(OpenApiSerializer.class.getName());

    private OpenApiSerializer() {
    }

    static {
        YAML_DUMPER_OPTIONS.setIndent(2);
        YAML_DUMPER_OPTIONS.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);

        JSON_DUMPER_OPTIONS.setDefaultFlowStyle(DumperOptions.FlowStyle.FLOW);
        JSON_DUMPER_OPTIONS.setPrettyFlow(true);
        JSON_DUMPER_OPTIONS.setDefaultScalarStyle(ScalarStyle.DOUBLE_QUOTED);
        JSON_DUMPER_OPTIONS.setSplitLines(false);
    }

    /**
     * Serialize using the selected format.
     *
     * @param types types
     * @param openAPI Open API document to serialize
     * @param format OpenAPI media type to use
     * @param writer writer to serialize to
     */
    static void serialize(Map<Class<?>, ExpandedTypeDescription> types,
                          OpenAPI openAPI,
                          OpenApiFormat format,
                          Writer writer) {
        if (format.equals(OpenApiFormat.JSON)) {
            serialize(types, openAPI, writer, JSON_DUMPER_OPTIONS, ScalarStyle.DOUBLE_QUOTED);
        } else {
            serialize(types, openAPI, writer, YAML_DUMPER_OPTIONS, ScalarStyle.PLAIN);
        }
    }

    private static void serialize(Map<Class<?>, ExpandedTypeDescription> types, OpenAPI openAPI, Writer writer,
            DumperOptions dumperOptions,
            ScalarStyle stringStyle) {

        Yaml yaml = new Yaml(new CustomRepresenter(types, dumperOptions, stringStyle), dumperOptions);
        yaml.dump(openAPI, new TagSuppressingWriter(writer));
    }

    /**
     * Represents the nodes in the OpenAPI document output by:
     * <ul>
     *     <li>adjusting the output of enum names and values to conform to the OpenAPI spec
     *     (lower-case) rather than the SmallRye implementations (upper-case),</li>
     *     <li>promotes the children of the property "extensions" up one level in the output
     *     document as required by the OpenAPI spec, and</li>
     *     <li>format scalar string nodes with double-quotes for JSON but not for YAML.</li>
     * </ul>
     */
    static class CustomRepresenter extends Representer {

        private static final String EXTENSIONS = "extensions";

        private final ScalarStyle stringStyle;


        CustomRepresenter(Map<Class<?>, ExpandedTypeDescription> types, DumperOptions dumperOptions, ScalarStyle stringStyle) {
            super(dumperOptions);
            this.stringStyle = stringStyle;
            types.values().stream()
                    .map(ImplTypeDescription::new)
                    .forEach(this::addTypeDescription);
        }

        @Override
        protected Node representScalar(Tag tag, String value, ScalarStyle style) {
            return super.representScalar(tag, value, isExemptedFromQuotes(tag) ? ScalarStyle.PLAIN : style);
        }

        @Override
        protected Node representSequence(Tag tag, Iterable<?> sequence, DumperOptions.FlowStyle flowStyle) {
            Node result = super.representSequence(tag, sequence, flowStyle);
            representedObjects.clear();
            return result;
        }

        @Override
        protected Node representMapping(Tag tag, Map<?, ?> mapping, DumperOptions.FlowStyle flowStyle) {
            Node result = super.representMapping(tag, mapping, flowStyle);
            if (mapping instanceof Extensible<?> extensible) {
                List<NodeTuple> tuples = ((MappingNode) result).getValue();
                if (extensible.getExtensions() != null) {
                    extensible.getExtensions().forEach((k, v) -> {
                        NodeTuple extensionTuple = new NodeTuple(new ScalarNode(Tag.STR, k, null, null, stringStyle),
                                                                 represent(v));
                        tuples.add(extensionTuple);
                    });
                }
            }
            return result;
        }

        private boolean isExemptedFromQuotes(Tag tag) {
            return tag.equals(Tag.BINARY) || tag.equals(Tag.BOOL) || tag.equals(Tag.FLOAT)
                    || tag.equals(Tag.INT);
        }

        @Override
        protected NodeTuple representJavaBeanProperty(Object javaBean, Property property, Object propertyValue,
                Tag customTag) {
            if (propertyValue == null) {
                return null;
            }

            Object v = adjustPropertyValue(propertyValue);
            if (propertyValue instanceof Enum<?> e) {
                v = e.toString();
            }
            return okToProcess(javaBean, property)
                    ? doRepresentJavaBeanProperty(javaBean, property, v, customTag) : null;
        }

        private NodeTuple doRepresentJavaBeanProperty(Object javaBean, Property property, Object propertyValue, Tag customTag) {
            NodeTuple defaultTuple = super.representJavaBeanProperty(javaBean, property, propertyValue, customTag);
            if ((javaBean instanceof Reference) && property.getName().equals("ref")) {
                return new NodeTuple(representData("$ref"), defaultTuple.getValueNode());
            }
            if (javaBean instanceof Schema) {
                // At most one of additionalPropertiesBoolean and additionalPropertiesSchema will return a non-null value.
                // Whichever one does (if either), replace the name with "additionalProperties" for output. Skip whatever is
                // returned from the deprecated additionalProperties method itself.
                String propertyName = property.getName();
                if (propertyName.equals("additionalProperties")) {
                    return null;
                } else if (propertyName.startsWith("additionalProperties")) {
                    return new NodeTuple(representData("additionalProperties"), defaultTuple.getValueNode());
                }
            }
            return defaultTuple;
        }

        private Object adjustPropertyValue(Object propertyValue) {
            // Some MP OpenAPI TCK tests expect an integer-style format, even for BigDecimal types, if the
            // value is an integer. Because the formatting is done in SnakeYAML code based on the type of the value,
            // we need to replace a for example BigDecimal that happen to be an integer value, with an Integer.
            // See https://github.com/eclipse/microprofile-open-api/issues/412
            if (propertyValue instanceof Number n && !Boolean.getBoolean("io.helidon.openapi.skipTCKWorkaround")) {
                float diff = n.floatValue() - n.intValue();
                if (diff == 0) {
                    propertyValue = n.intValue();
                } else if (Math.abs(diff) < 0.1) {
                    LOGGER.log(Level.WARNING, String.format(
                            "Integer approximation of %f did not match but the difference was only %e",
                            n.floatValue(), diff));
                }
            }
            return propertyValue;
        }

        @Override
        protected MappingNode representJavaBean(Set<Property> properties, Object javaBean) {
            // First, let SnakeYAML prepare the node normally. If the JavaBean is Extensible and has extension properties, it
            // will contain a sub-node called "extensions" which itself has one or more sub-nodes, one for each extension
            // property assigned.
            MappingNode result = super.representJavaBean(properties, javaBean);

            // Now promote the individual sub-nodes for each extension property (if any) up one level so that they are peers of
            // the other properties. Also remove the "extensions" node.
            processExtensions(result, javaBean);

            // Clearing representedObjects is an awkward but effective way of preventing SnakeYAML from using anchors and
            // aliases, which apparently the Jackson parser used in the TCK (as of this writing) does not handle properly.
            representedObjects.clear();
            return result;
        }

        private void processExtensions(MappingNode node, Object javaBean) {
            if (!Extensible.class.isAssignableFrom(javaBean.getClass())) {
                return;
            }

            List<NodeTuple> tuples = node.getValue();

            if (tuples.isEmpty()) {
                return;
            }
            List<NodeTuple> updatedTuples = processExtensions(tuples);
            node.setValue(updatedTuples);
        }

        /**
         * Returns a (possibly) updated list of {@code NodeTuple} objects, promoting any node under the node {@code extensions}
         * one level higher and removing the {@code extensions} node itself.
         *
         * @param tuples {@code NodeTuple} objects to process
         * @return possibly updated list of node tuples
         */
        private List<NodeTuple> processExtensions(List<NodeTuple> tuples) {
            List<NodeTuple> updatedTuples = new ArrayList<>();

            tuples.forEach(tuple -> {
                // Returns either null (if the tuple is not for "extensions") or a list of tuples for each extension property.
                List<NodeTuple> extensionTuples = processExtensions(tuple);
                if (extensionTuples == null) {
                    updatedTuples.add(tuple);
                } else {
                    updatedTuples.addAll(extensionTuples);
                }
            });
            return updatedTuples;
        }

        private List<NodeTuple> processExtensions(NodeTuple tuple) {
            Node keyNode = tuple.getKeyNode();
            return keyNode.getTag().equals(Tag.STR)
                        && ((ScalarNode) keyNode).getValue().equals(EXTENSIONS)
                        && tuple.getValueNode() instanceof MappingNode extensions
                        && extensions.getNodeId().equals(NodeId.mapping)
                    ? extensions.getValue()
                    : null;
        }

        /**
         * Some SmallRye implementation classes have properties not supported by the implemented interface, so ignore those or
         * the serialized YAML will contain SmallRye-only properties.
         *
         * @param javaBean the bean being serialized
         * @param property the property being serialized
         * @return true if the property should be processes; false otherwise
         */
        @SuppressWarnings("ConstantValue")
        private boolean okToProcess(Object javaBean, Property property) {
            // The following construct might look awkward - and it is. But if SmallRye adds additional properties to its
            // implementation classes that are not in the corresponding interfaces - and therefore we want to skip processing
            // them - then we can just add additional lines like the "reject |= ..." one, testing for the new case, without
            // having to change any other lines in the method.
            boolean reject = false;
            reject |= Parameter.class.isAssignableFrom(javaBean.getClass()) && property.getName().equals("hidden");
            return !reject;
        }
    }

    /**
     * Suppress the tag output for SmallRye implementation classes so the resulting document can be read into any MP OpenAPI
     * implementation, not just SmallRye's.
     */
    static class TagSuppressingWriter extends PrintWriter {

        private static final Pattern SMALLRYE_IMPL_TAG_PATTERN =
                Pattern.compile("!!" + Pattern.quote(OpenAPIImpl.class.getPackage().getName()) + ".+$");

        TagSuppressingWriter(Writer out) {
            super(out);
        }

        @Override
        public void write(char[] cbuf, int off, int len) {
            int effLen = detag(CharBuffer.wrap(cbuf), off, len);
            if (effLen > 0) {
                super.write(cbuf, off, effLen);
            }
        }

        @Override
        public void write(String s, int off, int len) {
            int effLen = detag(s, off, len);
            if (effLen > 0) {
                super.write(s, off, effLen);
            }
        }

        private int detag(CharSequence cs, int off, int len) {
            int result = len;
            Matcher m = SMALLRYE_IMPL_TAG_PATTERN.matcher(cs.subSequence(off, off + len));
            if (m.matches()) {
                result = len - (m.end() - m.start());
            }

            return result;
        }
    }
}
