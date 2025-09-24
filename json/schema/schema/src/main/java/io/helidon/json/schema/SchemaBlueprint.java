/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.json.schema;

import java.net.URI;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Json schema object.
 */
@Prototype.Blueprint(decorator = SchemaSupport.SchemaDecorator.class, createEmptyPublic = false)
@Prototype.CustomMethods(SchemaSupport.SchemaCustomMethods.class)
interface SchemaBlueprint {

    /**
     * The base URI for resolving relative references.
     *
     * @return configured base URI
     */
    Optional<URI> id();

    /**
     * Root schema.
     *
     * @return root json schema
     */
    @Option.Access("")
    SchemaItem root();

    /**
     * Root of the schema should be validated as an object.
     *
     * @return object root schema
     */
    Optional<SchemaObject> rootObject();

    /**
     * Root of the schema should be validated as an array.
     *
     * @return array root schema
     */
    Optional<SchemaArray> rootArray();

    /**
     * Root of the schema should be validated as a number.
     *
     * @return number root schema
     */
    Optional<SchemaNumber> rootNumber();

    /**
     * Root of the schema should be validated as an integer.
     *
     * @return integer root schema
     */
    Optional<SchemaInteger> rootInteger();

    /**
     * Root of the schema should be validated as a string.
     *
     * @return string root schema
     */
    Optional<SchemaString> rootString();

    /**
     * Root of the schema should be validated as a boolean.
     *
     * @return boolean root schema
     */
    Optional<SchemaBoolean> rootBoolean();

    /**
     * Root of the schema should be validated as a null.
     *
     * @return null root schema
     */
    Optional<SchemaNull> rootNull();

}
