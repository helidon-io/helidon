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
package io.helidon.data.model.query;

import java.util.Map;

/**
 * Interface used for the construction of queries at compilation time an implementation may optionally
 * provide an implementation of this at runtime.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface Criteria  {

    /**
     * Creates an "equals" Criterion based on the specified property name and value.
     *
     * @param parameter The parameter that provides the value
     *
     * @return The criteria
     */
    Criteria idEq(Object parameter);

    /**
     * Creates that restricts the version to the given value.
     *
     * @param parameter The parameter that provides the value
     *
     * @return The criteria
     */
    Criteria versionEq(Object parameter);

    /**
     * Creates a criterion that asserts the given property is empty (such as a blank string).
     *
     * @param propertyName The property name
     * @return The criteria
     */
    Criteria isEmpty(String propertyName);

    /**
     * Creates a criterion that asserts the given property is not empty.
     *
     * @param propertyName The property name
     * @return The criteria
     */
    Criteria isNotEmpty(String propertyName);

    /**
     * Creates a criterion that asserts the given property is null.
     *
     * @param propertyName The property name
     * @return The criteria
     */
    Criteria isNull(String propertyName);

    /**
     * Creates a criterion that asserts the given property is true.
     *
     * @param propertyName The property name
     * @return The criteria
     */
    Criteria isTrue(String propertyName);

    /**
     * Creates a criterion that asserts the given property is false.
     *
     * @param propertyName The property name
     * @return The criteria
     */
    Criteria isFalse(String propertyName);

    /**
     * Creates a criterion that asserts the given property is not null.
     *
     * @param propertyName The property name
     * @return The criteria
     */
    Criteria isNotNull(String propertyName);

    /**
     * Creates an "equals" Criterion based on the specified property name and value.
     *
     * @param propertyName The property name
     * @param parameter The parameter that provides the value
     *
     * @return The criteria
     */
    Criteria eq(String propertyName, Object parameter);

    /**
     * Creates a "not equals" Criterion based on the specified property name and value.
     *
     * @param propertyName The property name
     * @param parameter The parameter that provides the value
     *
     * @return The criteria
     */
    Criteria ne(String propertyName, Object parameter);

    /**
     * Restricts the results by the given property value range (inclusive).
     *
     * @param propertyName The property name
     *
     * @param start The start of the range
     * @param finish The end of the range
     * @return The criteria
     */
    Criteria between(String propertyName, Object start, Object finish);

    /**
     * Used to restrict a value to be greater than or equal to the given value.
     * @param property The property
     * @param parameter The parameter that provides the value
     * @return The Criterion instance
     */
    Criteria gte(String property, Object parameter);

    /**
     * Used to restrict a value to be greater than or equal to the given value.
     * @param property The property
     * @param parameter The parameter that provides the value
     * @return The Criterion instance
     */
    Criteria ge(String property, Object parameter);

    /**
     * Used to restrict a value to be greater than or equal to the given value.
     * @param property The property
     * @param parameter The parameter that provides the value
     * @return The Criterion instance
     */
    Criteria gt(String property, Object parameter);

    /**
     * Used to restrict a value to be less than or equal to the given value.
     * @param property The property
     * @param parameter The parameter that provides the value
     * @return The Criterion instance
     */
    Criteria lte(String property, Object parameter);

    /**
     * Used to restrict a value to be less than or equal to the given value.
     * @param property The property
     * @param parameter The parameter that provides the value
     * @return The Criterion instance
     */
    Criteria le(String property, Object parameter);

    /**
     * Used to restrict a value to be less than or equal to the given value.
     * @param property The property
     * @param parameter The parameter that provides the value
     * @return The Criterion instance
     */
    Criteria lt(String property, Object parameter);

    /**
     * Creates a like Criterion based on the specified property name and value.
     *
     * @param propertyName The property name
     * @param parameter The parameter that provides the value
     *
     * @return The criteria
     */
    Criteria like(String propertyName, Object parameter);

    /**
     * Restricts the property match to strings starting with the given value.
     *
     * @param propertyName The property name
     * @param parameter The parameter that provides the value
     *
     * @return The criteria
     */
    Criteria startsWith(String propertyName, Object parameter);

    /**
     * Restricts the property match to strings ending with the given value.
     *
     * @param propertyName The property name
     * @param parameter The parameter that provides the value
     *
     * @return The criteria
     */
    Criteria endsWith(String propertyName, Object parameter);

    /**
     * Restricts the property match to strings containing with the given value.
     *
     * @param propertyName The property name
     * @param parameter The parameter that provides the value
     *
     * @return The criteria
     */
    Criteria contains(String propertyName, Object parameter);

    /**
     * Creates an ilike Criterion based on the specified property name and value. Unlike a like condition, ilike is case insensitive.
     *
     * @param propertyName The property name
     * @param parameter The parameter that provides the value
     *
     * @return The criteria
     */
    Criteria ilike(String propertyName, Object parameter);

    /**
     * Creates an rlike Criterion based on the specified property name and value.
     *
     * @param propertyName The property name
     * @param parameter The parameter that provides the value
     *
     * @return The criteria
     */
    Criteria rlike(String propertyName, Object parameter);

    /**
     * Creates a logical conjunction.
     *
     * @param other The other criteria
     * @return This criteria
     */
    Criteria and(Criteria other);

    /**
     * Creates a logical disjunction.
     *
     * @param other The other criteria
     * @return This criteria
     */
    Criteria or(Criteria other);

    /**
     * Creates a logical negation.
     *
     * @param other The other criteria
     * @return This criteria
     */
    Criteria not(Criteria other);

    /**
     * Creates an "in" Criterion using a subquery.
     *
     * @param propertyName The property name
     * @param subquery The subquery
     *
     * @return The criteria
     */
    Criteria inList(String propertyName, QueryModel subquery);

    /**
     * Creates an "in" Criterion based on the specified property name and list of values.
     *
     * @param propertyName The property name
     * @param parameter The parameter that provides the value
     *
     * @return The criteria
     */
    Criteria inList(String propertyName, Object parameter);

    /**
     * Creates a negated "in" Criterion using a subquery.
     *
     * @param propertyName The property name
     * @param subquery The subquery
     *
     * @return The criteria
     */
    Criteria notIn(String propertyName, QueryModel subquery);

    /**
     * Creates a Criterion that constrains a collection property by size.
     *
     * @param propertyName The property name
     * @param size The size to constrain by
     *
     * @return This criteria
     */
    Criteria sizeEq(String propertyName, Object size) ;

    /**
     * Creates a Criterion that constrains a collection property to be greater than the given size.
     *
     * @param propertyName The property name
     * @param size The size to constrain by
     *
     * @return This criteria
     */
    Criteria sizeGt(String propertyName, Object size);

    /**
     * Creates a Criterion that constrains a collection property to be greater than or equal to the given size.
     *
     * @param propertyName The property name
     * @param size The size to constrain by
     *
     * @return This criteria
     */
    Criteria sizeGe(String propertyName, Object size);

    /**
     * Creates a Criterion that constrains a collection property to be less than or equal to the given size.
     *
     * @param propertyName The property name
     * @param size The size to constrain by
     *
     * @return This criteria
     */
    Criteria sizeLe(String propertyName, Object size);

    /**
     * Creates a Criterion that constrains a collection property to be less than to the given size.
     *
     * @param propertyName The property name
     * @param size The size to constrain by
     *
     * @return This criteria
     */
    Criteria sizeLt(String propertyName, Object size);

    /**
     * Creates a Criterion that constrains a collection property to be not equal to the given size.
     *
     * @param propertyName The property name
     * @param size The size to constrain by
     *
     * @return This criteria
     */
    Criteria sizeNe(String propertyName, Object size);

    /**
     * Constrains a property to be equal to a specified other property.
     *
     * @param propertyName The property
     * @param otherPropertyName The other property
     * @return This criteria
     */
    Criteria eqProperty(String propertyName, String otherPropertyName);

    /**
     * Constrains a property to be not equal to a specified other property.
     *
     * @param propertyName The property
     * @param otherPropertyName The other property
     * @return This criteria
     */
    Criteria neProperty(String propertyName, String otherPropertyName);

    /**
     * Constrains a property to be greater than a specified other property.
     *
     * @param propertyName The property
     * @param otherPropertyName The other property
     * @return This criteria
     */
    Criteria gtProperty(String propertyName, String otherPropertyName);

    /**
     * Constrains a property to be greater than or equal to a specified other property.
     *
     * @param propertyName The property
     * @param otherPropertyName The other property
     * @return This criteria
     */
    Criteria geProperty(String propertyName, String otherPropertyName);

    /**
     * Constrains a property to be less than a specified other property.
     *
     * @param propertyName The property
     * @param otherPropertyName The other property
     * @return This criteria
     */
    Criteria ltProperty(String propertyName, String otherPropertyName);

    /**
     * Constrains a property to be less than or equal to a specified other property.
     *
     * @param propertyName The property
     * @param otherPropertyName The other property
     * @return This criteria
     */
    Criteria leProperty(java.lang.String propertyName, String otherPropertyName);

    /**
     * Apply an "equals" constraint to each property in the key set of a <tt>Map</tt>.
     *
     * @param propertyValues a map from property names to values
     *
     * @return Criterion
     *
     */
    Criteria allEq(Map<String, Object> propertyValues);

    //===== Subquery methods

    /**
     * Creates a subquery criterion that ensures the given property is equals to all the given returned values.
     *
     * @param propertyName The property name
     * @param propertyValue A subquery
     * @return This criterion instance
     */
    Criteria eqAll(String propertyName, Criteria propertyValue);

    /**
     * Creates a subquery criterion that ensures the given property is greater than all the given returned values.
     *
     * @param propertyName The property name
     * @param propertyValue A subquery
     * @return This criterion instance
     */
    Criteria gtAll(String propertyName, Criteria propertyValue);

    /**
     * Creates a subquery criterion that ensures the given property is less than all the given returned values.
     *
     * @param propertyName The property name
     * @param propertyValue A subquery
     * @return This criterion instance
     */
    Criteria ltAll(String propertyName, Criteria propertyValue);

    /**
     * Creates a subquery criterion that ensures the given property is greater than or equals to all the given returned values.
     *
     * @param propertyName The property name
     * @param propertyValue A subquery
     * @return This criterion instance
     */
    Criteria geAll(String propertyName, Criteria propertyValue);

    /**
     * Creates a subquery criterion that ensures the given property is less than or equal to all the given returned values.
     *
     * @param propertyName The property name
     * @param propertyValue A subquery
     * @return This criterion instance
     */
    Criteria leAll(String propertyName, Criteria propertyValue);

    /**
     * Creates a subquery criterion that ensures the given property is greater than some of the given values.
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     *
     * @return This Criteria instance
     */
    Criteria gtSome(String propertyName, Criteria propertyValue);

    /**
     * Creates a subquery criterion that ensures the given property is greater than or equal to some of the given values.
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     *
     * @return This Criteria instance
     */
    Criteria geSome(String propertyName, Criteria propertyValue);

    /**
     * Creates a subquery criterion that ensures the given property is less than some of the given values.
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     *
     * @return This Criteria instance
     */
    Criteria ltSome(String propertyName, Criteria propertyValue);

    /**
     * Creates a subquery criterion that ensures the given property is less than or equal to some of the given values.
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     *
     * @return This Criteria instance
     */
    Criteria leSome(String propertyName, Criteria propertyValue);

}
