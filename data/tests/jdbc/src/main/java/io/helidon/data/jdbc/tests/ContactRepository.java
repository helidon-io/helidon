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
package io.helidon.data.jdbc.tests;

import java.util.List;
import java.util.Optional;

import io.helidon.data.Data;
import io.helidon.data.jdbc.Jdbc;

/** Declarative repository exercised through generated service metadata. */
@Data.Repository
@Data.Provider("jdbc")
public interface ContactRepository {

    /**
     * Returns records while proving label-based mapping is independent of
     * projection order and case.
     *
     * @return contacts
     */
    @Jdbc.Statement("SELECT NAME AS name, EMAIL AS EMAIL, ID AS Id FROM CONTACT ORDER BY ID")
    List<ContactView> findAll();

    /**
     * Returns a nullable scalar.
     *
     * @param id contact identifier
     * @return email or empty for SQL NULL
     */
    @Jdbc.Statement("SELECT EMAIL FROM CONTACT WHERE ID = :id")
    Optional<String> email(long id);

    /**
     * Maps column one while ignoring duplicate labels in unused columns.
     *
     * @return contact identifiers
     */
    @Jdbc.Statement("""
            SELECT ID,
                   NAME AS detail,
                   EMAIL AS DETAIL
            FROM CONTACT
            ORDER BY ID
            """)
    List<Long> idsWithDuplicateUnusedLabels();

    /**
     * Declares an intentionally ambiguous record projection for runtime
     * validation.
     *
     * @param id contact identifier
     * @return mapped contact
     */
    @Jdbc.Statement("""
            SELECT ID AS id,
                   NAME AS name,
                   EMAIL AS NAME
            FROM CONTACT
            WHERE ID = :id
            """)
    ContactView ambiguousRecordLabels(long id);

    /**
     * Declares a record projection without the required {@code name} label.
     *
     * @param id contact identifier
     * @return mapped contact
     */
    @Jdbc.Statement("""
            SELECT ID AS id,
                   NAME AS display_name,
                   EMAIL AS email
            FROM CONTACT
            WHERE ID = :id
            """)
    ContactView missingRecordLabel(long id);

    /**
     * Uses the marker mapper selected by generic service contract.
     *
     * @param id contact identifier
     * @return mapped contact
     */
    @Jdbc.Statement("SELECT ID, NAME FROM CONTACT WHERE ID = :id")
    @Jdbc.RowMapper
    ContactLabel mapped(long id);

    /**
     * Uses the sole mapper service matching its generic contract.
     *
     * @param id contact identifier
     * @return mapped contact
     */
    @Jdbc.Statement("SELECT NAME FROM CONTACT WHERE ID = :id")
    @Jdbc.RowMapper
    SingleMapperContact singleMapped(long id);

    /**
     * Returns zero or one marker-mapped contact.
     *
     * @param id contact identifier
     * @return mapped contact
     */
    @Jdbc.Statement("SELECT ID, NAME FROM CONTACT WHERE ID = :id")
    @Jdbc.RowMapper
    Optional<ContactLabel> mappedOptional(long id);

    /**
     * Returns all marker-mapped contacts.
     *
     * @return mapped contacts
     */
    @Jdbc.Statement("SELECT ID, NAME FROM CONTACT ORDER BY ID")
    @Jdbc.RowMapper
    List<ContactLabel> mappedList();

    /**
     * Uses a concrete mapper even when it has lower registry weight.
     *
     * @param id contact identifier
     * @return mapped contact
     */
    @Jdbc.Statement("SELECT ID, NAME FROM CONTACT WHERE ID = :id")
    @Jdbc.RowMapper(ExplicitContactMapper.class)
    ContactLabel explicitlyMapped(long id);

    /**
     * Returns zero or one explicitly mapped contact.
     *
     * @param id contact identifier
     * @return mapped contact
     */
    @Jdbc.Statement("SELECT ID, NAME FROM CONTACT WHERE ID = :id")
    @Jdbc.RowMapper(ExplicitContactMapper.class)
    Optional<ContactLabel> explicitlyMappedOptional(long id);

    /**
     * Returns all explicitly mapped contacts.
     *
     * @return mapped contacts
     */
    @Jdbc.Statement("SELECT ID, NAME FROM CONTACT ORDER BY ID")
    @Jdbc.RowMapper(ExplicitContactMapper.class)
    List<ContactLabel> explicitlyMappedList();

    /**
     * Invokes an application mapper that fails deliberately.
     *
     * @param id contact identifier
     * @return mapped contact
     */
    @Jdbc.Statement("SELECT NAME FROM CONTACT WHERE ID = :id")
    @Jdbc.RowMapper(ThrowingContactMapper.class)
    MapperFailureContact mapperFailure(long id);

    /**
     * Omits the email filter when the argument is null.
     *
     * @param email optional filter
     * @return matching contacts
     */
    @Jdbc.Statement("""
            SELECT NAME AS name, EMAIL AS email, ID AS id
            FROM CONTACT
            WHERE (:email IS NULL OR EMAIL = :email)
            ORDER BY ID
            """)
    List<ContactView> optionalEmailFilter(String email);

    /**
     * Implements null-safe equality explicitly in SQL.
     *
     * @param email nullable comparison value
     * @return matching contacts
     */
    @Jdbc.Statement("""
            SELECT NAME AS name, EMAIL AS email, ID AS id
            FROM CONTACT
            WHERE ((:email IS NULL AND EMAIL IS NULL) OR EMAIL = :email)
            ORDER BY ID
            """)
    List<ContactView> nullSafeEmail(String email);

    /**
     * Inserts and returns one scalar key.
     *
     * @param name contact name
     * @return generated identifier
     */
    @Jdbc.Statement("INSERT INTO CONTACT(NAME) VALUES (:name)")
    @Jdbc.GeneratedKeys("ID")
    long insert(String name);

    /**
     * Inserts and returns an optional scalar key.
     *
     * @param name contact name
     * @return generated identifier
     */
    @Jdbc.Statement("INSERT INTO CONTACT(NAME) VALUES (:name)")
    @Jdbc.GeneratedKeys("ID")
    Optional<Long> insertOptional(String name);

    /**
     * Inserts and returns generated keys as a list.
     *
     * @param name contact name
     * @return generated identifiers
     */
    @Jdbc.Statement("INSERT INTO CONTACT(NAME) VALUES (:name)")
    @Jdbc.GeneratedKeys("ID")
    List<Long> insertList(String name);

    /**
     * Inserts and returns a generated record projection.
     *
     * @param name contact name
     * @param email contact email
     * @return generated contact projection
     */
    @Jdbc.Statement("INSERT INTO CONTACT(NAME, EMAIL) VALUES (:name, :email)")
    @Jdbc.GeneratedKeys({"ID", "NAME", "EMAIL"})
    ContactView insertRecord(String name, String email);

    /**
     * Inserts and maps generated columns through the marker mapper.
     *
     * @param name contact name
     * @return mapped generated values
     */
    @Jdbc.Statement("INSERT INTO CONTACT(NAME) VALUES (:name)")
    @Jdbc.GeneratedKeys({"ID", "NAME"})
    @Jdbc.RowMapper
    ContactLabel insertMapped(String name);

    /**
     * Inserts and optionally maps generated columns through the marker mapper.
     *
     * @param name contact name
     * @return mapped generated values
     */
    @Jdbc.Statement("INSERT INTO CONTACT(NAME) VALUES (:name)")
    @Jdbc.GeneratedKeys({"ID", "NAME"})
    @Jdbc.RowMapper
    Optional<ContactLabel> insertMappedOptional(String name);

    /**
     * Inserts and maps generated columns through the marker mapper as a list.
     *
     * @param name contact name
     * @return mapped generated values
     */
    @Jdbc.Statement("INSERT INTO CONTACT(NAME) VALUES (:name)")
    @Jdbc.GeneratedKeys({"ID", "NAME"})
    @Jdbc.RowMapper
    List<ContactLabel> insertMappedList(String name);

    /**
     * Inserts and maps generated columns through the explicit mapper.
     *
     * @param name contact name
     * @return mapped generated values
     */
    @Jdbc.Statement("INSERT INTO CONTACT(NAME) VALUES (:name)")
    @Jdbc.GeneratedKeys({"ID", "NAME"})
    @Jdbc.RowMapper(ExplicitContactMapper.class)
    ContactLabel insertExplicitlyMapped(String name);

    /**
     * Inserts and optionally maps generated columns through the explicit mapper.
     *
     * @param name contact name
     * @return mapped generated values
     */
    @Jdbc.Statement("INSERT INTO CONTACT(NAME) VALUES (:name)")
    @Jdbc.GeneratedKeys({"ID", "NAME"})
    @Jdbc.RowMapper(ExplicitContactMapper.class)
    Optional<ContactLabel> insertExplicitlyMappedOptional(String name);

    /**
     * Inserts and maps generated columns through the explicit mapper as a list.
     *
     * @param name contact name
     * @return mapped generated values
     */
    @Jdbc.Statement("INSERT INTO CONTACT(NAME) VALUES (:name)")
    @Jdbc.GeneratedKeys({"ID", "NAME"})
    @Jdbc.RowMapper(ExplicitContactMapper.class)
    List<ContactLabel> insertExplicitlyMappedList(String name);

    /**
     * Renames one contact and returns a checked narrow count.
     *
     * @param name replacement name
     * @param id contact identifier
     * @return update count
     */
    @Jdbc.Statement("UPDATE CONTACT SET NAME = :name WHERE ID = :id")
    @Jdbc.Execution(Jdbc.ExecutionType.UPDATE)
    int rename(String name, long id);

    /**
     * Deletes one contact and returns a large count.
     *
     * @param id contact identifier
     * @return update count
     */
    @Jdbc.Statement("DELETE FROM CONTACT WHERE ID = :id")
    @Jdbc.Execution(Jdbc.ExecutionType.UPDATE)
    long delete(long id);
}
