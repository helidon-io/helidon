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
package io.helidon.data.jakarta.persistence.codegen;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import io.helidon.common.types.TypeName;
import io.helidon.data.Data;
import io.helidon.data.jakarta.persistence.JpaRepositoryExecutor;
import io.helidon.data.jakarta.persistence.spi.JpaEntityProvider;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Root;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.junit.jupiter.api.Assertions.fail;

public class JakartaPersistenceTypesTest {

    private static final Set<Field> types = new HashSet<>();
    private static final Set<String> toCheck = new HashSet<>();
    private static final Set<String> checked = new HashSet<>();
    private static final Map<String, Field> fields = new HashMap<>();

    @BeforeAll
    static void before() {
        Field[] declaredFields = JakartaPersistenceTypes.class.getDeclaredFields();
        for (Field declaredField : declaredFields) {
            if (declaredField.getType() == TypeName.class) {
                types.add(declaredField);
            }
        }
        for (Field declaredField : types) {
            String name = declaredField.getName();
            toCheck.add(name);
            fields.put(name, declaredField);
        }
    }

    @AfterAll
    static void after() {
        // Verify that all fields were checked
        assertThat(toCheck, empty());
    }

    @Test
    void allFieldsTest() {
        for (Field declaredField : types) {
            String name = declaredField.getName();
            assertThat(name + " must be a TypeName", declaredField.getType(), CoreMatchers.sameInstance(TypeName.class));
            assertThat(name + " must be static", Modifier.isStatic(declaredField.getModifiers()), is(true));
            assertThat(name + " must be package local, not public",
                       Modifier.isPublic(declaredField.getModifiers()),
                       is(false));
            assertThat(name + " must be package local, not private",
                       Modifier.isPrivate(declaredField.getModifiers()),
                       is(false));
            assertThat(name + " must be package local, not protected",
                       Modifier.isProtected(declaredField.getModifiers()),
                       is(false));
            assertThat(name + " must be final", Modifier.isFinal(declaredField.getModifiers()), is(true));
        }
    }

    @Test
    void testDataPersistenceUnit() {
        checkField("PU_NAME_ANNOTATION", Data.PersistenceUnit.class);
    }

    @Test
    void testJpaRepositoryExecutor() {
        checkField("BASE_REPOSITORY_EXECUTOR", JpaRepositoryExecutor.class);
        checkField("EXECUTOR", JpaRepositoryExecutor.class);
    }

    @Test
    void testEntity() {
        checkField("ENTITY", Entity.class);
    }

    @Test
    void testEntityManager() {
        checkField("ENTITY_MANAGER", EntityManager.class);
    }

    @Test
    void testJpaEntityProvider() {
        checkField("ENTITY_PROVIDER", JpaEntityProvider.class);
    }

    @Test
    void testDataSessionRepository() {
        checkField("SESSION_REPOSITORY", Data.SessionRepository.class);
    }

    @Test
    void testConsumer() {
        checkField("SESSION_CONSUMER", Consumer.class);
    }

    @Test
    void testFunction() {
        checkField("SESSION_FUNCTION", Function.class);
    }

    @Test
    void testCriteriaBuilder() {
        checkField("CRITERIA_BUILDER", CriteriaBuilder.class);
    }

    @Test
    void testCriteriaQuery() {
        checkField("RAW_CRITERIA_QUERY", CriteriaQuery.class);
    }

    @Test
    void testCriteriaDelete() {
        checkField("RAW_CRITERIA_DELETE", CriteriaDelete.class);
    }

    @Test
    void testRoot() {
        checkField("RAW_ROOT", Root.class);
    }

    @Test
    void testOrder() {
        checkField("ORDER", Order.class);
    }

    @Test
    void testExpression() {
        checkField("RAW_EXPRESSION", Expression.class);
    }

    private static void checkField(String name, Class<?> expectedType) {
        Field field = fields.get(name);
        assertThat("Field " + name + " does not exist in the class", field, notNullValue());
        try {
            toCheck.remove(name);
            if (checked.add(name)) {
                TypeName value = (TypeName) field.get(null);
                assertThat("Field " + name, value.fqName(), is(expectedType.getCanonicalName()));
            } else {
                fail("Field " + name + " is checked more than once.class");
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

}
