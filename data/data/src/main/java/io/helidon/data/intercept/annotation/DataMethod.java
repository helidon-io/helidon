/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.helidon.data.intercept.annotation;

import io.helidon.data.intercept.DataInterceptor;
import io.helidon.data.model.DataType;

import java.io.Serializable;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.Inherited;

/**
 * Internal annotation used to configure execution handling for {@code io.micronaut.data.intercept.DataIntroductionAdvice}.
 *
 * @author graemerocher
 * @since 1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Inherited
public @interface DataMethod {

    /**
     * The annotation name.
     */
    String NAME = DataMethod.class.getName();

    /**
     * The member that holds expandable query parts.
     */
    String META_MEMBER_EXPANDABLE_QUERY = "expandableQuery";

    /**
     * The member that holds expandable count query parts.
     */
    String META_MEMBER_EXPANDABLE_COUNT_QUERY = "expandableCountQuery";

    /**
     * The member that holds the count query.
     */
    String META_MEMBER_COUNT_QUERY = "countQuery";

    /**
     * The member name that holds the result type.
     */
    String META_MEMBER_RESULT_TYPE = "resultType";

    /**
     * The member name that holds the result type.
     */
    String META_MEMBER_RESULT_DATA_TYPE = "resultDataType";

    /**
     * The member name that holds the root entity type.
     */
    String META_MEMBER_ROOT_ENTITY = "rootEntity";

    /**
     * The member name that holds the interceptor type.
     */
    String META_MEMBER_INTERCEPTOR = "interceptor";

    /**
     * The member name that holds parameter binding.
     */
    String META_MEMBER_PARAMETER_BINDING = "parameterBinding";

    /**
     * The member name that holds parameter binding paths.
     */
    String META_MEMBER_PARAMETER_BINDING_PATHS = META_MEMBER_PARAMETER_BINDING + "Paths";

    /**
     * The member name that holds parameter auto populated property paths.
     */
    String META_MEMBER_PARAMETER_AUTO_POPULATED_PROPERTY_PATHS = META_MEMBER_PARAMETER_BINDING + "AutoPopulatedPaths";

    /**
     * The member name that holds parameter auto populated property paths.
     */
    String META_MEMBER_PARAMETER_AUTO_POPULATED_PREVIOUS_PROPERTY_PATHS = META_MEMBER_PARAMETER_BINDING + "AutoPopulatedPreviousPaths";

    /**
     * The member name that holds parameter auto populated property paths.
     */
    String META_MEMBER_PARAMETER_AUTO_POPULATED_PREVIOUS_PROPERTY_INDEXES = META_MEMBER_PARAMETER_BINDING + "AutoPopulatedPrevious";

    /**
     * The ID type.
     */
    String META_MEMBER_ID_TYPE = "idType";

    /**
     * The parameter that holds the pageSize value.
     */
    String META_MEMBER_PAGE_SIZE = "pageSize";

    /**
     * The parameter that holds the offset value.
     */
    String META_MEMBER_PAGE_INDEX = "pageIndex";

    /**
     * The parameter that references the entity.
     */
    String META_MEMBER_ENTITY = "entity";

    /**
     * The parameter that references the ID.
     */
    String META_MEMBER_ID = "id";

    /**
     * Does the query result in a DTO object.
     */
    String META_MEMBER_DTO = "dto";

    /**
     * Does the query contains optimistic lock.
     */
    String META_MEMBER_OPTIMISTIC_LOCK = "optimisticLock";

    /**
     * The query builder to use.
     */
    String META_MEMBER_QUERY_BUILDER = "queryBuilder";

    /**
     * Whether the user is a raw user specified query.
     */
    String META_MEMBER_RAW_QUERY = "rawQuery";

    /**
     * Whether the user is a raw user specified query.
     */
    String META_MEMBER_RAW_COUNT_QUERY = "rawCountQuery";

    /**
     * Meta member for storing the parameter type defs.
     */
    String META_MEMBER_PARAMETER_TYPE_DEFS = "parameterTypeDefs";

    /**
     * Meta member for storing the parameter converters.
     */
    String META_MEMBER_PARAMETER_CONVERTERS = "parameterConverters";

    /**
     * Meta member for storing the parameters.
     */
    String META_MEMBER_PARAMETERS = "parameters";

    /**
     * The member name that holds the root entity type.
     */
    String META_MEMBER_OPERATION_TYPE = "opType";

    /**
     * @return The child interceptor to use for the method execution.
     */
    Class<? extends DataInterceptor> interceptor();

    /**
     * The root entity this method applies to.
     * @return The root entity
     */
    Class<?> rootEntity() default void.class;

    /**
     * The computed result type. This represents the type that is to be read from the database. For example for a {@link java.util.List}
     * this would return the value of the generic type parameter {@code E}. Or for an entity result the return type itself.
     *
     * @return The result type
     */
    Class<?> resultType() default void.class;

    /**
     * @return The result data type.
     */
    DataType resultDataType() default DataType.OBJECT;

    /**
     * The identifier type for the method being executed.
     *
     * @return The ID type
     */
    Class<?> idType() default Serializable.class;

// FIXME: Dependencies are not available

//    /**
//     * The parameter binding defines which method arguments bind to which
//     * query parameters. The {@code Property#name()} is used to define the query parameter name and the
//     * {@code Property#value()} is used to define method argument name to bind.
//     *
//     * @return The parameter binding.
//     */
//    Property[] parameterBinding() default {};

    /**
     * The argument that defines the pageable object.
     *
     * @return The pageable.
     */
    String pageable() default "";

    /**
     * The argument that represents the entity for save, update, query by example operations etc.
     *
     * @return The entity argument
     */
    String entity() default "";

    /**
     * The member that defines the ID for lookup, delete, update by ID.
     * @return The ID
     */
    String id() default "";

    /**
     * An explicit pageSize (in absence of a pageable).
     * @return The pageSize
     */
    int pageSize() default -1;

    /**
     * An explicit offset (in absence of a pageable).
     *
     * @return The offset
     */
    long pageIndex() default 0;

// FIXME: Dependencies are not available

//    /**
//     * @return The query parameters
//     */
//    DataMethodQueryParameter[] parameters() default {};

    /**
     * Describes the operation type.
     */
    enum OperationType {
        /**
         * A query operation.
         */
        QUERY,
        /**
         * A count operation.
         */
        COUNT,
        /**
         * A exists operation.
         */
        EXISTS,
        /**
         * An update operation.
         */
        UPDATE,
        /**
         * A delete operation.
         */
        DELETE,
        /**
         * An insert operation.
         */
        INSERT
    }
}
