/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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
package io.helidon.dbclient.mongodb;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Logger;

import javax.json.Json;

/**
 * Statement parameter parsers.
 */
final class StatementParsers {

    /** Local logger instance. */
    private static final Logger LOGGER = Logger.getLogger(StatementParsers.class.getName());

    static String toJson(Object value) {
        if ((value instanceof Integer)  || (value instanceof Short)  || (value instanceof Byte)){
            return Json.createValue(((Number) value).intValue()).toString();
        }
        if (value instanceof Long) {
            return Json.createValue((Long) value).toString();
        }
        if ((value instanceof Double) || (value instanceof Float)) {
            return Json.createValue(((Number) value).doubleValue()).toString();
        }
        if (value instanceof BigInteger) {
            return Json.createValue((BigInteger) value).toString();
        }
        if (value instanceof BigDecimal) {
            return Json.createValue((BigDecimal) value).toString();
        }
        if (value instanceof Boolean) {
            return value.toString();
        }
        // Check instanceof Number is more expensive than final types, it shall be at the end
        if (value instanceof Number) {
            return value.toString();
        }
        // String.valueOf handles null value
        return Json.createValue(String.valueOf(value)).toString();
    }

    private StatementParsers() {
    }

    static StatementParser indexedParser(String statement, List<Object> indexedParams) {
        return new IndexedParser(statement, indexedParams);
    }

    static StatementParser namedParser(String statement, Map<String, Object> indexedParams) {
        return new NamedParser(statement, indexedParams);
    }

    @FunctionalInterface
    interface StatementParser {
        String convert();
    }

    // Slightly modified copy-paste from JDBC statement parser
    // Replaces "$name" named parameters with value from mappings if exists
    abstract static class Parser {
        /**
         * Parser ACTION to be performed.
         */
        @FunctionalInterface
        interface Action {
            void process(Parser parser);
        }

        /**
         * States used in state machine.
         */
        enum State {
            STATEMENT, // Common statement
            STRING,    // SQL string
            PAR_BEG,   // After colon, expecting parameter name
            PARAMETER  // Processing parameter name
        }

        /**
         * Do nothing.
         *
         * @param parser parser instance
         */
        static void doNothing(Parser parser) {
        }

        /**
         * Copy character from input string to output as is.
         *
         * @param parser parser instance
         */
        static void copyChar(Parser parser) {
            parser.sb.append(parser.c);
        }

        /**
         * SQL statement to be parsed.
         */
        private final String statement;
        /**
         * Target SQL statement builder.
         */
        private final StringBuilder sb;
        /**
         * Temporary string storage.
         */
        private final StringBuilder nap;

        /**
         * Character being currently processed.
         */
        private char c;

        private Parser(String statement) {
            this.sb = new StringBuilder(statement.length());
            this.nap = new StringBuilder(32);
            this.statement = statement;
            this.c = '\0';
        }

        String statement() {
            return statement;
        }

        StringBuilder sb() {
            return sb;
        }

        StringBuilder nap() {
            return nap;
        }

        char c() {
            return c;
        }

        void c(char newC) {
            c = newC;
        }
    }

    /**
     * Mapping parser state machine.
     */
    static final class NamedParser implements StatementParser {

        /**
         * First character of named parameter identifier.
         */
        private static final char PAR_BEG = '$';

        /**
         * Parser ACTION interface.
         */
        private interface Action extends Consumer<NamedParser> {}

        /**
         * States used in state machine.
         */
        enum State {
            STATEMENT, // Common statement
            STRING,    // SQL string
            PAR_BEG,   // After colon, expecting parameter name
            PARAMETER  // Processing parameter name
        }

        /**
         * Character classes used in state machine.
         */
        private enum CharClass {
            LETTER, // Letter
            NUMBER, // Number
            QUOTE,  // Double quote, begins/ends string in JSON
            COLON,  // Colon, not a parameter when part of the sequence
            DOLLAR, // Dollar sign, begins named parameter
            OTHER;  // Other character

            // This is far from optimal code, but direct translation table would be too large for Java char
            private static CharClass charClass(char c) {
                switch (c) {
                    case '"': return QUOTE;
                    case PAR_BEG: return DOLLAR;
                    case ':': return COLON;
                    default:
                        return Character.isLetter(c)
                                ? LETTER
                                : (Character.isDigit(c) ? NUMBER : OTHER);
                }
            }

        }

        /**
         * States TRANSITION table.
         */
        private static final State[][] TRANSITION = {
            // Transitions from STATEMENT state
            {
                State.STATEMENT, // LETTER: regular part of the statement, keep processing it
                State.STATEMENT, // NUMBER: regular part of the statement, keep processing it
                State.STRING,    // QUOTE: beginning of JSON string processing, switch to STRING state
                State.STATEMENT, // COLON: regular part of the statement, keep processing it
                State.PAR_BEG,   // DOLLAR: possible beginning of named parameter, switch to PAR_BEG state
                State.STATEMENT  // OTHER: regular part of the statement, keep processing it
            },
            // Transitions from STRING state
            {
                State.STRING,    // LETTER: regular part of the JSON string, keep processing it
                State.STRING,    // NUMBER: regular part of the JSON string, keep processing it
                State.STATEMENT, // QUOTE: end of JSON string processing, go back to STATEMENT state
                State.STRING,    // COLON: regular part of the JSON string, keep processing it
                State.STRING,    // DOLLAR: regular part of the JSON string, keep processing it
                State.STRING     // OTHER: regular part of the JSON string, keep processing it
            },
            // Transitions from PAR_BEG state
            {
                State.PARAMETER, // LETTER: first character of named parameter, switch to PARAMETER state
                State.STATEMENT, // NUMBER: can't be first character of named parameter, go back to STATEMENT state
                State.STRING,    // QUOTE: not a named parameter but beginning of JSON string processing,
                                 //        switch to STRING state
                State.STATEMENT, // COLON: can't be first character of named parameter, go back to STATEMENT state
                State.PAR_BEG,   // DOLLAR: not a named parameter but possible beginning of another named parameter,
                                 //         retry named parameter processing
                State.STATEMENT  // OTHER: can't be first character of named parameter, go back to STATEMENT state
            },
            // Transitions from PARAMETER state
            {
                State.PARAMETER, // LETTER: next character of named parameter, keep processing it
                State.PARAMETER, // NUMBER: next character of named parameter, keep processing it
                State.STATEMENT, // QUOTE: end of named parameter and beginning of JSON string processing,
                                 //        switch to STRING state
                State.STATEMENT, // COLON: not a named parameter, colon is part of the name, go back to STATEMENT state
                State.PAR_BEG,   // DOLLAR: end of named parameter and possible beginning of another named parameter
                                 //         switch to PAR_BEG state
                State.STATEMENT  // OTHER: can't be next character of named parameter, go back to STATEMENT state
            }
        };


        /**
         * States TRANSITION ACTION table.
         */
        private static final Action[][] ACTION = {
            // Actions performed on transitions from STATEMENT state
            {
                NamedParser::copyCurrChar, // LETTER: copy regular statement character to output
                NamedParser::copyCurrChar, // NUMBER: copy regular statement character to output
                NamedParser::copyCurrChar, // QUOTE: copy regular statement character to output
                NamedParser::copyCurrChar, // COLON: copy regular statement character to output
                NamedParser::storeCharPos, // DOLLAR: store current character position
                NamedParser::copyCurrChar  // OTHER: copy regular statement character to output
            },
            // Actions performed on transitions from STRING state
            {
                NamedParser::copyCurrChar, // LETTER: copy regular statement character to output
                NamedParser::copyCurrChar, // NUMBER: copy regular statement character to output
                NamedParser::copyCurrChar, // QUOTE: copy regular statement character to output
                NamedParser::copyCurrChar, // COLON: copy regular statement character to output
                NamedParser::copyCurrChar, // DOLLAR: copy regular statement character to output
                NamedParser::copyCurrChar  // OTHER: copy regular statement character to output
            },
            // Actions performed on transitions from PAR_BEG state
            {
                NamedParser::doNothing,       // LETTER: do nothing, parameter name was not finished yet
                NamedParser::copyStoredChars, // NUMBER: copy characters from stored position up to current character to output
                NamedParser::copyStoredChars, // QUOTE: copy characters from stored position up to current character to output
                NamedParser::copyStoredChars, // COLON: copy characters from stored position up to current character to output
                NamedParser::copyStoredCharsStoreCharPos, // DOLLAR: copy characters from stored position
                                                          //         up to current character to output
                NamedParser::copyStoredChars  // OTHER: copy characters from stored position up to current character to output
            },
            // Actions performed on transitions from PARAMETER state
            {
                NamedParser::doNothing,               // LETTER: do nothing, parameter name was not finished yet
                NamedParser::doNothing,               // NUMBER: do nothing, parameter name was not finished yet
                NamedParser::finishParamCopyCurrChar, // QUOTE: finish parameter processing
                NamedParser::copyStoredChars,         // COLON: copy characters from stored position
                                                      //        up to current character to output
                NamedParser::finishParamStoreCharPos, // DOLLAR: finish parameter processing
                NamedParser::finishParamCopyCurrChar  // OTHER: finish parameter processing
            }
        };

        /**
         * Do nothing.
         *
         * @param parser parser instance
         */
        static void doNothing(NamedParser parser) {
        }

        /**
         * Copy current character from input string to output as is.
         *
         * @param parser parser instance
         */
        static void copyCurrChar(NamedParser parser) {
            parser.sb.append(parser.statement.charAt(parser.curPos));
        }

        /**
         * Copy characters from stored position up to current character from input string to output as is.
         *
         * @param parser parser instance
         */
        static void copyStoredChars(NamedParser parser) {
            parser.sb.append(parser.statement, parser.paramBegPos, parser.curPos + 1);
        }

        /**
         * Store current character position into parser instance.
         *
         * @param parser parser instance
         */
        private static void storeCharPos(NamedParser parser) {
            parser.paramBegPos = parser.curPos;
        }

        /**
         * Copy characters from stored position up to previous character from input string to output as is.
         * Store current character position into parser instance.
         *
         * @param parser parser instance
         */
        static void copyStoredCharsStoreCharPos(NamedParser parser) {
            parser.sb.append(parser.statement, parser.paramBegPos, parser.curPos);
            parser.paramBegPos = parser.curPos;
        }

        /**
         * Finish parameter processing and copy current character from input string to output.
         * Parameter name is replaced by mapped value if mapping for given parameter exists.
         * Otherwise parameter name is left in statement as is without replacing it.
         *
         * @param parser  parser instance
         */
        private static void finishParamCopyCurrChar(NamedParser parser) {
            String parName = parser.statement.substring(parser.paramBegPos + 1, parser.curPos);
            if (parser.mappings.containsKey(parName)) {
                parser.sb.append(toJson(parser.mappings.get(parName)));
                parser.sb.append(parser.statement.charAt(parser.curPos));
            } else {
                parser.sb.append(parser.statement, parser.paramBegPos, parser.curPos + 1);
            }
        }

        /**
         * Finish parameter processing and store current character position into parser instance.
         * Parameter name is replaced by mapped value if mapping for given parameter exists.
         * Otherwise parameter name is left in statement as is without replacing it.
         *
         * @param parser  parser instance
         */
        private static void finishParamStoreCharPos(NamedParser parser) {
            String parName = parser.statement.substring(parser.paramBegPos + 1, parser.curPos);
            if (parser.mappings.containsKey(parName)) {
                parser.sb.append(toJson(parser.mappings.get(parName)));
            } else {
                parser.sb.append(parser.statement, parser.paramBegPos, parser.curPos);
            }
            parser.paramBegPos = parser.curPos;
        }

        /** Parameter name to value mapping. */
        private final Map<String, ? extends Object> mappings;
        /** SQL statement to be parsed. */
        private final String statement;
        /** Target SQL statement builder. */
        private final StringBuilder sb;
        /** Current position in the parsed String. */
        private int curPos;
        /** Parameter beginning position (index of '$' character). */
        private int paramBegPos;

        /**
         * Character class of character being currently processed.
         */
        private CharClass cl;

        NamedParser(String statement, Map<String, ? extends Object> mappings) {
            this.statement = statement;
            this.sb = new StringBuilder(statement.length());
            this.mappings = mappings;
            this.cl = null;
        }

        @Override
        public String convert() {
            State state = State.STATEMENT;  // Initial state: common statement processing
            final int len = statement.length();
            for (curPos = 0; curPos < len; curPos++) {
                cl = CharClass.charClass(statement.charAt(curPos));
                ACTION[state.ordinal()][cl.ordinal()].accept(this);
                state = TRANSITION[state.ordinal()][cl.ordinal()];
            }
            switch (state) {
                case PAR_BEG:
                    sb.append(statement, paramBegPos, len);
                    break;
                case PARAMETER:
                    String parName = statement.substring(paramBegPos + 1, len);
                    if (mappings.containsKey(parName)) {
                        sb.append(toJson(mappings.get(parName)));
                    } else {
                        sb.append(statement, paramBegPos, len);
                    }
                    break;
                default:
            }
            LOGGER.fine(() -> String.format("Named Statement %s", sb.toString()));
            return sb.toString();
        }

    }

    static final class IndexedParser extends Parser implements StatementParser {

        /**
         * First character of named parameter identifier.
         */
        private static final char PAR_LT = '?';

        /**
         * Character classes used in state machine.
         */
        private enum CharClass {
            LT,  // Letter
            NUM, // Number
            DQ,  // Double quote, begins/ends string in JSON
            QST, // Question mark, parameter
            OTH; // Other character

            // This is far from optimal code, but direct translation table would be too large for Java char
            private static CharClass charClass(char c) {
                // DO NOT replace with a single line combined ternary expression!
                if (Character.isLetter(c)) {
                    return LT;
                }

                if (Character.isDigit(c)) {
                    return NUM;
                }

                if (c == '"') {
                    return DQ;
                }

                if (c == PAR_LT) {
                    return QST;
                }

                return OTH;
            }

        }

        /**
         * States TRANSITION table.
         */
        private static final State[][] TRANSITION = {
            // Transition from STATEMENT
            {
                State.STATEMENT,
                State.STATEMENT,
                State.STRING,
                State.STATEMENT,
                State.STATEMENT
            },
            // Transition from STRING
            {
                State.STRING,
                State.STRING,
                State.STATEMENT,
                State.STRING,
                State.STRING
            }
        };

        private static final Action COPY_ACTION = Parser::copyChar;
        private static final Action VAAP_ACTION = IndexedParser::nextParam;
        /**
         * States TRANSITION ACTION table.
         */
        private static final Action[][] ACTION = {
                // LETTER         NUMBER          DQ           QST          OTHER
                {COPY_ACTION, COPY_ACTION, COPY_ACTION, VAAP_ACTION, COPY_ACTION}, // STATEMENT
                {COPY_ACTION, COPY_ACTION, COPY_ACTION, COPY_ACTION, COPY_ACTION}  // STRING
        };

        /**
         * Append next parameter from parameters list.
         *
         * @param parser parser instance
         */
        private static void nextParam(Parser parser) {
            if (((IndexedParser) parser).parIt.hasNext()) {
                parser.sb.append(toJson(((IndexedParser) parser).parIt.next()));
            } else {
                parser.sb.append(parser.c);
            }
        }

        private final List<Object> parameters;
        private final ListIterator<Object> parIt;
        /**
         * Character class of character being currently processed.
         */
        private CharClass cl;

        private IndexedParser(String statement, List<Object> parameters) {
            super(statement);
            this.parameters = parameters;
            this.parIt = parameters.listIterator();
            this.cl = null;
        }

        @Override
        public String convert() {
            State state = State.STATEMENT;  // Initial state: common statement processing
            int len = statement().length();
            for (int i = 0; i < len; i++) {
                c(statement().charAt(i));
                cl = CharClass.charClass(c());
                ACTION[state.ordinal()][cl.ordinal()].process(this);
                state = TRANSITION[state.ordinal()][cl.ordinal()];
            }
            LOGGER.fine(() -> String.format("Indexed Statement %s", sb().toString()));
            return sb().toString();
        }

    }
}
