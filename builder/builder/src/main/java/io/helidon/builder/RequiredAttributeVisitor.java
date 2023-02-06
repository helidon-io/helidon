/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.builder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * An implementation of {@link AttributeVisitor} that will validate each attribute to enforce not-null in accordance with
 * {@link io.helidon.config.metadata.ConfiguredOption#required()}.
 * <p>
 * Note that the source type having the {@link io.helidon.builder.Builder} must be annotated with
 * {@code ConfiguredOption(required=true)} for this to be enforced.
 * Also note that this implementation will be used only when
 * {@link io.helidon.builder.Builder#requireLibraryDependencies()} is enabled. If not enabled then an implementation
 * similar to this type will be inlined directly into the code-generated builder type.
 *
 * @deprecated This class is subject to change at any time - Helidon users should not use this directly. It will be referenced in
 *         code generated sources that Helidon generates.
 */
@Deprecated
public class RequiredAttributeVisitor implements AttributeVisitor<Object> {
    private final List<String> errors = new ArrayList<>();
    private final boolean allowNullsByDefault;

    /**
     * Default constructor.
     */
    // important note: this class is also code generated - please keep this in synch with generated code
    public RequiredAttributeVisitor() {
        this(Builder.DEFAULT_ALLOW_NULLS);
    }

    /**
     * Constructor.
     *
     * @param allowNullsByDefault true if nulls should be allowed
     */
    // important note: this class is also code generated - please keep this in synch with generated code
    public RequiredAttributeVisitor(boolean allowNullsByDefault) {
        this.allowNullsByDefault = allowNullsByDefault;
    }

    @Override
    // important note: this class is also code generated - please keep this in synch with generated code
    public void visit(String attrName,
                      Supplier<Object> valueSupplier,
                      Map<String, Object> meta,
                      Object userDefinedCtx,
                      Class<?> type,
                      Class<?>... typeArgument) {
        String requiredStr = (String) meta.get("required");
        boolean requiredPresent = Objects.nonNull(requiredStr);
        boolean required = Boolean.parseBoolean(requiredStr);
        if (!required && requiredPresent) {
            return;
        }

        if (allowNullsByDefault && !requiredPresent) {
            return;
        }

        Object val = valueSupplier.get();
        if (val != null) {
            return;
        }

        errors.add("'" + attrName + "' is a required attribute and should not be null");
    }

    /**
     * Performs the validation. Any errors will result in a thrown error.
     *
     * @throws java.lang.IllegalStateException when any attributes are in violation with the validation policy
     */
    // important note: this class is also code generated - please keep this in synch with generated code
    public void validate() {
        if (!errors.isEmpty()) {
            throw new IllegalStateException(String.join(", ", errors));
        }
    }

}
