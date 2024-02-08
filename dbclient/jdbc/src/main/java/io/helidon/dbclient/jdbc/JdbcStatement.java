/*
 * Copyright (c) 2019, 2024 Oracle and/or its affiliates.
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
package io.helidon.dbclient.jdbc;

import java.io.ByteArrayInputStream;
import java.io.CharArrayReader;
import java.lang.System.Logger.Level;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLXML;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.helidon.dbclient.DbClientException;
import io.helidon.dbclient.DbClientServiceContext;
import io.helidon.dbclient.DbIndexedStatementParameters;
import io.helidon.dbclient.DbNamedStatementParameters;
import io.helidon.dbclient.DbStatement;
import io.helidon.dbclient.DbStatementBase;
import io.helidon.dbclient.DbStatementParameters;

/**
 * JDBC statement base implementation.
 *
 * @param <S> type of subclass
 */
public abstract class JdbcStatement<S extends DbStatement<S>> extends DbStatementBase<S> {

    private static final System.Logger LOGGER = System.getLogger(JdbcStatement.class.getName());

    private final JdbcConnectionPool connectionPool;
    private Connection connection;

    /**
     * Create a new instance.
     *
     * @param connectionPool connection pool
     * @param context        context
     */
    JdbcStatement(JdbcConnectionPool connectionPool, JdbcExecuteContext context) {
        super(context);
        this.connectionPool = connectionPool;
    }

    /**
     * Close the connection.
     */
    void closeConnection() {
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, String.format("Could not close connection: %s", e.getMessage()), e);
        }
    }

    private JdbcExecuteContext jdbcContext() {
        return context(JdbcExecuteContext.class);
    }

    /**
     * Create the {@link PreparedStatement}.
     *
     * @param serviceContext client service context
     * @return PreparedStatement
     */
    protected PreparedStatement prepareStatement(DbClientServiceContext serviceContext) {
        String stmtName = serviceContext.statementName();
        String stmt = serviceContext.statement();
        DbStatementParameters stmtParams = serviceContext.statementParameters();
        LOGGER.log(Level.DEBUG, () -> String.format("Building SQL statement: %s", stmt));
        if (stmtParams instanceof DbIndexedStatementParameters indexed) {
            List<Object> params = indexed.parameters();
            return prepareIndexedStatement(stmtName, stmt, params);
        } else if (stmtParams instanceof DbNamedStatementParameters named) {
            Map<String, Object> params = named.parameters();
            return prepareNamedStatement(stmtName, stmt, params);
        }
        return prepareStatement(stmtName, stmt);
    }

    /**
     * Create the {@link PreparedStatement}.
     *
     * @param stmtName statement name
     * @param stmt     statement text
     * @return statement
     */
    protected PreparedStatement prepareStatement(String stmtName, String stmt) {
        Connection connection = connectionPool.connection();
        try {
            connection.setAutoCommit(true);
        } catch (SQLException e) {
            throw new DbClientException("Failed to set autocommit to true", e);
        }
        return prepareStatement(connection, stmtName, stmt);
    }

    /**
     * Create the {@link PreparedStatement}.
     *
     * @param connection connection
     * @param stmtName   statement name
     * @param stmt       statement text
     * @return statement
     */
    protected PreparedStatement prepareStatement(Connection connection, String stmtName, String stmt) {
        try {
            this.connection = connection;
            return connection.prepareStatement(stmt);
        } catch (SQLException e) {
            throw new DbClientException(String.format("Failed to prepare statement: %s", stmtName), e);
        }
    }

    private PreparedStatement prepareNamedStatement(String stmtName, String stmt, Map<String, Object> parameters) {
        PreparedStatement preparedStatement = null;
        try {
            // Parameters names must be replaced with ? and names occurrence order must be stored.
            NamedStatementParser parser = new NamedStatementParser(stmt);
            String convertedStmt = parser.convert();
            LOGGER.log(Level.TRACE, () -> String.format("Converted statement: %s", convertedStmt));
            preparedStatement = prepareStatement(stmtName, convertedStmt);
            List<String> namesOrder = parser.namesOrder();
            // Set parameters into prepared statement
            int i = 1; // JDBC set position parameter starts from 1.
            for (String name : namesOrder) {
                if (parameters.containsKey(name)) {
                    Object value = parameters.get(name);
                    LOGGER.log(Level.TRACE, String.format("Mapped parameter %d: %s -> %s", i, name, value));
                    setParameter(preparedStatement, i, value);
                    i++;
                } else {
                    throw new DbClientException(namedStatementErrorMessage(namesOrder, parameters));
                }
            }
            return preparedStatement;
        } catch (SQLException e) {
            closePreparedStatement(preparedStatement);
            throw new DbClientException("Failed to prepare statement with named parameters: " + stmtName, e);
        }
    }

    private PreparedStatement prepareIndexedStatement(String stmtName, String stmt, List<Object> parameters) {
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = prepareStatement(stmtName, stmt);
            int i = 1; // JDBC set position parameter starts from 1.
            for (Object value : parameters) {
                LOGGER.log(Level.TRACE, String.format("Indexed parameter %d: %s", i, value));
                setParameter(preparedStatement, i, value);
                i++;
            }
            return preparedStatement;
        } catch (SQLException e) {
            closePreparedStatement(preparedStatement);
            throw new DbClientException(String.format("Failed to prepare statement with indexed params: %s", stmtName), e);
        }
    }

    private void closePreparedStatement(PreparedStatement preparedStatement) {
        if (preparedStatement != null) {
            try {
                preparedStatement.close();
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, String.format("Could not close PreparedStatement: %s", e.getMessage()), e);
            }
        }
    }

    private static String namedStatementErrorMessage(List<String> names, Map<String, Object> parameters) {
        // Parameters in query missing in parameters Map
        List<String> notInParams = new ArrayList<>(names.size());
        for (String name : names) {
            if (!parameters.containsKey(name)) {
                notInParams.add(name);
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Query parameters missing in Map: ");
        boolean first = true;
        for (String name : notInParams) {
            if (first) {
                first = false;
            } else {
                sb.append(", ");
            }
            sb.append(name);
        }
        return sb.toString();
    }

    // JDBC PreparedStatement parameters setting from EclipseLink
    private void setParameter(PreparedStatement statement, int index, Object parameter) throws SQLException {
        // Start with common types
        if (parameter instanceof String s) {
            // Check for stream binding of large strings.
            if (jdbcContext().parametersConfig().useStringBinding()
                    && (s.length() > jdbcContext().parametersConfig().stringBindingSize())) {
                CharArrayReader reader = new CharArrayReader(s.toCharArray());
                statement.setCharacterStream(index, reader, (s.length()));
            } else {
                if (jdbcContext().parametersConfig().useNString()) {
                    statement.setNString(index, s);
                } else {
                    statement.setString(index, s);
                }
            }
        } else if (parameter instanceof Number number) {
            if (number instanceof Integer i) {
                statement.setInt(index, i);
            } else if (number instanceof Long l) {
                statement.setLong(index, l);
            }  else if (number instanceof BigDecimal bd) {
                statement.setBigDecimal(index, bd);
            } else if (number instanceof Double d) {
                statement.setDouble(index, d);
            } else if (number instanceof Float f) {
                statement.setFloat(index, f);
            } else if (number instanceof Short s) {
                statement.setShort(index, s);
            } else if (number instanceof Byte b) {
                statement.setByte(index, b);
            } else if (number instanceof BigInteger bi) {
                // Convert to BigDecimal.
                statement.setBigDecimal(index, new BigDecimal(bi));
            } else {
                statement.setObject(index, number);
            }
        // java.sql Date/Time
        }  else if (parameter instanceof java.sql.Date d) {
            statement.setDate(index, d);
        } else if (parameter instanceof java.sql.Time t){
            statement.setTime(index, t);
        } else if (parameter instanceof java.sql.Timestamp ts) {
            statement.setTimestamp(index, ts);
        // java.time Date/Time
        }  else if (parameter instanceof java.time.LocalDate ld) {
            if (jdbcContext().parametersConfig().setObjectForJavaTime()) {
                statement.setObject(index, ld);
            } else {
                statement.setDate(index, java.sql.Date.valueOf(ld));
            }
        } else if (parameter instanceof java.time.LocalDateTime ldt) {
            if (jdbcContext().parametersConfig().setObjectForJavaTime()) {
                statement.setObject(index, ldt);
            } else {
                statement.setTimestamp(index, java.sql.Timestamp.valueOf(ldt));
            }
        } else if (parameter instanceof java.time.OffsetDateTime odt) {
            if (jdbcContext().parametersConfig().setObjectForJavaTime()) {
                statement.setObject(index, odt);
            } else {
                statement.setTimestamp(index, java.sql.Timestamp.from((odt).toInstant()));
            }
        } else if (parameter instanceof java.time.LocalTime lt) {
            if (jdbcContext().parametersConfig().setObjectForJavaTime()) {
                statement.setObject(index, lt);
            } else {
                // Fallback option for old JDBC drivers may differ
                if (jdbcContext().parametersConfig().timestampForLocalTime()) {
                    statement.setTimestamp(index,
                                           java.sql.Timestamp.valueOf(
                                                   java.time.LocalDateTime.of(java.time.LocalDate.ofEpochDay(0), lt)));
                } else {
                    statement.setTime(index, java.sql.Time.valueOf(lt));
                }
            }
        } else if (parameter instanceof java.time.OffsetTime ot) {
            if (jdbcContext().parametersConfig().setObjectForJavaTime()) {
                statement.setObject(index, ot);
            } else {
                statement.setTimestamp(index,
                                       java.sql.Timestamp.valueOf(
                                               java.time.LocalDateTime.of(java.time.LocalDate.ofEpochDay(0), ot.toLocalTime())));
            }
        } else if (parameter instanceof Boolean b) {
            statement.setBoolean(index, b);
        } else if (parameter == null) {
            // Normally null is passed as a DatabaseField so the type is included, but in some case may be passed directly.
            statement.setNull(index, Types.NULL);
        } else if (parameter instanceof byte[] b) {
            if (jdbcContext().parametersConfig().useByteArrayBinding()) {
                ByteArrayInputStream inputStream = new ByteArrayInputStream(b);
                statement.setBinaryStream(index, inputStream, b.length);
            } else {
                statement.setBytes(index, b);
            }
        // Next process types that need conversion.
        } else if (parameter instanceof Calendar c) {
            statement.setTimestamp(index, timestampFromDate(c.getTime()));
        } else if (parameter instanceof java.util.Date d) {
            statement.setTimestamp(index, timestampFromDate(d));
        } else if (parameter instanceof Character c) {
            statement.setString(index, String.valueOf(c));
        } else if (parameter instanceof char[] c) {
            statement.setString(index, new String(c));
        } else if (parameter instanceof Character[] c) {
            statement.setString(index, String.valueOf(characterArrayToCharArray(c)));
        } else if (parameter instanceof Byte[] b) {
            statement.setBytes(index, byteArrayToByteArray(b));
        } else if (parameter instanceof SQLXML s) {
            statement.setSQLXML(index, s);
        } else if (parameter instanceof UUID uuid) {
            statement.setString(index, uuid.toString());
        } else {
            statement.setObject(index, parameter);
        }
    }

    private static java.sql.Timestamp timestampFromLong(long millis) {
        java.sql.Timestamp timestamp = new java.sql.Timestamp(millis);

        // Must  account for negative millis < 1970
        // Must account for the jdk millis bug where it does not set the nanos.
        if ((millis % 1000) > 0) {
            timestamp.setNanos((int) (millis % 1000) * 1000000);
        } else if ((millis % 1000) < 0) {
            timestamp.setNanos((int) (1000000000 - (Math.abs((millis % 1000) * 1000000))));
        }
        return timestamp;
    }

    private static java.sql.Timestamp timestampFromDate(java.util.Date date) {
        return timestampFromLong(date.getTime());
    }

    private static char[] characterArrayToCharArray(Character[] source) {
        char[] chars = new char[source.length];
        for (int i = 0; i < source.length; i++) {
            chars[i] = source[i];
        }
        return chars;
    }

    private static byte[] byteArrayToByteArray(Byte[] source) {
        byte[] bytes = new byte[source.length];
        for (int i = 0; i < source.length; i++) {
            Byte value = source[i];
            if (value != null) {
                bytes[i] = value;
            }
        }
        return bytes;
    }

}
