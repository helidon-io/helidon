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

import java.util.List;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

/**
 * A component capable of holding content.
 *
 * @param <T> type of the component, to support fluent API
 * @see io.helidon.codegen.classmodel.Method
 * @see io.helidon.codegen.classmodel.Constructor
 * @see io.helidon.codegen.classmodel.Field
 */
public interface ContentBuilder<T extends ContentBuilder<T>> {
    /**
     * Set new content.
     * This method replaces previously created content in this builder.
     *
     * @param content content to be set
     * @return updated builder instance
     */
    default T content(String content) {
        return content(List.of(content));
    }

    /**
     * Set new content.
     * This method replaces previously created content in this builder.
     *
     * @param content content to be set
     * @return updated builder instance
     */
    T content(List<String> content);

    /**
     * Add text line to the content.
     * New line character is added after this line.
     *
     * @param line line to add
     * @return updated builder instance
     */
    default T addContentLine(String line) {
        return addContent(line).addContent("\n");
    }

    /**
     * Add text line to the content.
     * New line character is not added after this line, so all newly added text will be appended to the same line.
     *
     * @param line line to add
     * @return updated builder instance
     */
    T addContent(String line);

    /**
     * Add type name to content, correctly handling imports.
     * In case the type should not contain any type parameters, use {@link io.helidon.common.types.TypeName#genericTypeName()}.
     *
     * @param typeName type name to add
     * @return updated component builder
     */
    default T addContent(TypeName typeName) {
        return addTypeToContent(typeName.resolvedName());
    }

    /**
     * Obtained type is enclosed between {@link ClassModel#TYPE_TOKEN} tokens.
     * Class names in such a format are later recognized as class names for import handling.
     *
     * @param type type to import
     * @return updated builder instance
     */
    default T addContent(Class<?> type) {
        return addTypeToContent(type.getCanonicalName());
    }

    /**
     * Add content that creates a new {@link io.helidon.common.types.TypeName} in the generated code that is the same as the
     * type name provided.
     * <p>
     * To create a type name without type arguments (such as when used with {@code .class}), use
     * {@link io.helidon.common.types.TypeName#genericTypeName()}.
     * <p>
     * The generated content will be similar to: {@code TypeName.create("some.type.Name")}
     *
     * @param typeName type name to code generate
     * @return updated builder instance
     */
    default T addContentCreate(TypeName typeName) {
        ContentSupport.addCreateTypeName(this, typeName);
        return addContent("");
    }

    /**
     * Add content that creates a new {@link io.helidon.common.types.Annotation} in the generated code that is the same as the
     * annotation provided.
     *
     * @param annotation annotation to code generate
     * @return updated builder instance
     */
    default T addContentCreate(Annotation annotation) {
        ContentSupport.addCreateAnnotation(this, annotation);
        return addContent("");
    }

    /**
     * Add content that creates a new {@link io.helidon.common.types.TypedElementInfo} in the generated code that is
     * the same as the element provided.
     *
     * @param element element to code generate
     * @return updated builder instance
     */
    default T addContentCreate(TypedElementInfo element) {
        ContentSupport.addCreateElement(this, element);
        return addContent("");
    }

    /**
     * Obtained fully qualified type name is enclosed between {@link ClassModel#TYPE_TOKEN} tokens.
     * Class names in such a format are later recognized as class names for import handling.
     *
     * @param typeName fully qualified class name to import
     * @return updated builder instance
     */
    T addTypeToContent(String typeName);

    /**
     * Adds single padding.
     * This extra padding is added only once. If more permanent padding increment is needed use
     * {{@link #increaseContentPadding()}}.
     *
     * @return updated builder instance
     */
    T padContent();

    /**
     * Adds padding with number of repetitions.
     * This extra padding is added only once. If more permanent padding increment is needed use
     * {{@link #increaseContentPadding()}}.
     *
     * @param repetition number of padding repetitions
     * @return updated builder instance
     */
    T padContent(int repetition);

    /**
     * Method for manual padding increment.
     * This method will affect padding of the later added content.
     *
     * @return updated builder instance
     */
    T increaseContentPadding();

    /**
     * Method for manual padding decrement.
     * This method will affect padding of the later added content.
     *
     * @return updated builder instance
     */
    T decreaseContentPadding();

    /**
     * Clears created content.
     *
     * @return updated builder instance
     */
    T clearContent();
}
