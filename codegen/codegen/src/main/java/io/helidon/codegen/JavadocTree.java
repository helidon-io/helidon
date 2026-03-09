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
package io.helidon.codegen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.helidon.codegen.JavadocReader.BlockTagImpl;
import io.helidon.codegen.JavadocReader.CdataImpl;
import io.helidon.codegen.JavadocReader.CommentImpl;
import io.helidon.codegen.JavadocReader.DoctypeImpl;
import io.helidon.codegen.JavadocReader.EltCloseImpl;
import io.helidon.codegen.JavadocReader.EltStartImpl;
import io.helidon.codegen.JavadocReader.EscapeImpl;
import io.helidon.codegen.JavadocReader.InlineTagImpl;
import io.helidon.codegen.JavadocReader.TextImpl;

/**
 * Javadoc tree node.
 * <p>
 * <b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or deletion without notice.</b>
 * </p>
 */
public sealed interface JavadocTree permits JavadocTree.EltStart,
                                            JavadocTree.InlineTag,
                                            JavadocTree.BlockTag,
                                            JavadocTree.EltClose,
                                            JavadocTree.Text,
                                            JavadocTree.Escape,
                                            JavadocTree.Comment,
                                            JavadocTree.Cdata,
                                            JavadocTree.Doctype {

    /**
     * Root node.
     */
    sealed interface Document permits JavadocReader.DocumentImpl {
        /**
         * Get the first sentence of a documentation comment.
         *
         * @return list of nodes
         */
        List<JavadocTree> firstSentence();

        /**
         * Get the body of a documentation comment, appearing after the first sentence, and before any block tags.
         *
         * @return list of nodes
         */
        List<JavadocTree> body();

        /**
         * Get the full body.
         *
         * @return elements
         */
        default List<JavadocTree> fullBody() {
            var elements = new ArrayList<>(firstSentence());
            elements.addAll(body());
            return elements;
        }

        /**
         * Get the block tags of a documentation comment.
         *
         * @return list of block tag nodes
         */
        List<BlockTag> blockTags();
    }

    /**
     * HTML start element.
     */
    sealed interface EltStart extends JavadocTree permits EltStartImpl {

        /**
         * Name of the HTML element.
         *
         * @return name, never {@code null}
         */
        String name();

        /**
         * Whether the element is self-closing.
         *
         * @return {@code true} if self-closing
         */
        boolean selfClosing();

        /**
         * Attributes of the element.
         *
         * @return immutable attributes map, never {@code null}
         */
        Map<String, AttrValue> attributes();
    }

    /**
     * HTML attribute value.
     *
     * @param value value
     * @param kind  kind
     */
    record AttrValue(String value, AttrValue.Kind kind) implements JavadocParser.Event {

        /**
         * Constant for the empty attribute value.
         */
        public static final AttrValue EMPTY = new AttrValue("", AttrValue.Kind.EMPTY);

        /**
         * Attribute value kind.
         */
        public enum Kind {
            /**
             * Empty.
             */
            EMPTY,
            /**
             * Single quoted.
             */
            SINGLE,
            /**
             * Double quoted.
             */
            DOUBLE,
            /**
             * Unquoted.
             */
            UNQUOTED
        }
    }

    /**
     * Text element.
     */
    sealed interface Text extends JavadocParser.Event, JavadocTree permits TextImpl {

        /**
         * Text value.
         *
         * @return value, never {@code null}
         */
        String value();
    }

    /**
     * Escape sequence.
     */
    sealed interface Escape extends JavadocParser.Event, JavadocTree permits EscapeImpl {

        /**
         * Escape value.
         *
         * @return value, never {@code null}
         */
        String value();
    }

    /**
     * HTML comment.
     */
    sealed interface Comment extends JavadocParser.Event, JavadocTree permits CommentImpl {

        /**
         * Comment value.
         *
         * @return value, never {@code null}
         */
        String value();
    }

    /**
     * HTML CDATA section.
     */
    sealed interface Cdata extends JavadocParser.Event, JavadocTree permits CdataImpl {

        /**
         * CDATA value.
         *
         * @return value, never {@code null}
         */
        String value();
    }

    /**
     * HTML doctype declaration.
     */
    sealed interface Doctype extends JavadocParser.Event, JavadocTree permits DoctypeImpl {

        /**
         * Doctype value.
         *
         * @return value, never {@code null}
         */
        String value();
    }

    /**
     * HTML element close.
     */
    sealed interface EltClose extends JavadocParser.Event, JavadocTree permits EltCloseImpl {

        /**
         * Closing element name.
         *
         * @return name, never {@code null}
         */
        String name();
    }

    /**
     * Inline tag.
     */
    sealed interface InlineTag extends JavadocTree permits InlineTagImpl {

        /**
         * Inline tag name.
         *
         * @return tag, never {@code null}
         */
        String tag();

        /**
         * Inline tag body.
         *
         * @return body, never {@code null}
         */
        String body();
    }

    /**
     * Block tag.
     */
    sealed interface BlockTag extends JavadocTree permits BlockTagImpl {

        /**
         * Block tag name.
         *
         * @return tag, never {@code null}
         */
        String tag();

        /**
         * Block tag body.
         *
         * @return immutable body list, never {@code null}
         */
        List<JavadocTree> body();
    }

}
