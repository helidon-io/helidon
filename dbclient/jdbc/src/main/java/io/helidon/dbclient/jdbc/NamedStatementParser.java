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
package io.helidon.dbclient.jdbc;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Mapping parser state machine.
 * <p>Finds all named parameters in the query. Returns {@link List} of named parameters in the same order as listed
 * in the query (may contain duplicate items).
 * <p>Translates query to indexed notation with named parameters replaced with '?' characters.
 * <p>Before replacement:
 * {@code SELECT * FROM table WHERE name = :name AND type = :type}
 * <p>After replacement:
 * {@code SELECT * FROM table WHERE name = ? AND type = ?}
 * <p>Expected list of parameters:
 * {@code "name", "type"}
 */
class NamedStatementParser {

    @FunctionalInterface
    private interface Action extends Consumer<NamedStatementParser> {}

    /**
     * Character classes used in state machine.
     */
    private enum CharClass {
        IDENTIFIER_START,   // Any character for which the method Character.isJavaIdentifierStart returns true
        // or '_'
        IDENTIFIER_PART,    // Any character for which the method Character.isJavaIdentifierPart returns true
        LF,                 // Line feed / new line (\n), terminates line alone or in CR LF sequence
        CR,                 // Carriage return (\r), terminates line in CR LF sequence
        APOSTROPHE,         // Single quote ('), begins string in SQL
        STAR,               // Star (*), part of multiline comment beginning "/*" and ending "*/" sequence
        DASH,               // Dash (-), part of single line comment beginning sequence "--"
        SLASH,              // Slash (/), part of multiline comment beginning "/*" and ending "*/" sequence
        COLON,              // Colon (:), begins named parameter
        OTHER;              // Other characters

        /**
         * Returns character class corresponding to provided character.
         *
         * @param c character to determine its character class
         * @return character class corresponding to provided character
         */
        private static CharClass charClass(char c) {
            switch (c) {
                case '\r': return CR;
                case '\n': return LF;
                case '\'': return APOSTROPHE;
                case '*': return STAR;
                case '-': return DASH;
                case '/': return SLASH;
                case ':': return COLON;
                case '_': return IDENTIFIER_START;
                default:
                    return Character.isJavaIdentifierStart(c)
                            ? IDENTIFIER_START
                            : (Character.isJavaIdentifierPart(c) ? IDENTIFIER_PART : OTHER);
            }
        }

    }

    /**
     * States used in state machine.
     */
    private enum State {
        STATEMENT,            // Common statement processing
        STRING,               // SQL string processing after 1st APOSTROPHE was recieved
        COLON,                // Symbolic name processing after opening COLON (colon) was recieved
        PARAMETER,            // Symbolic name processing after 1st LETTER or later LETTER
                              // or NUMBER of parameter name was recieved
        MULTILN_COMMENT_BG,   // Multiline comment processing after opening slash was recieved from the "/*" sequence
        MULTILN_COMMENT_END,  // Multiline comment processing after closing star was recieved from the "*/" sequence
        MULTILN_COMMENT,      // Multiline comment processing of the comment itself
        SINGLELN_COMMENT_BG,  // Single line comment processing after opening dash was recieved from the "--" sequence
        SINGLELN_COMMENT_END, // Single line comment processing after closing CR was recieved from the CR LF sequence
        SINGLELN_COMMENT;     // Single line comment processing of the comment itself

        /** States transition table. */
        private static final State[][] TRANSITION = {
                // Transitions from STATEMENT state
                {
                        STATEMENT,           // IDENTIFIER_START: regular part of the statement, keep processing it
                        STATEMENT,           // IDENTIFIER_PART: regular part of the statement, keep processing it
                        STATEMENT,           // LF: regular part of the statement, keep processing it
                        STATEMENT,           // CR: regular part of the statement, keep processing it
                        STRING,              // APOSTROPHE: beginning of SQL string processing, switch to STRING state
                        STATEMENT,           // STAR: regular part of the statement, keep processing it
                        SINGLELN_COMMENT_BG, // DASH: possible starting sequence of single line comment,
                                             //       switch to SINGLELN_COMMENT_BG state
                        MULTILN_COMMENT_BG,  // SLASH: possible starting sequence of multi line comment,
                                             //        switch to MULTILN_COMMENT_BG state
                        COLON,               // COLON: possible beginning of named parameter, switch to COLON state
                        STATEMENT            // OTHER: regular part of the statement, keep processing it
                },
                // Transitions from STRING state
                {
                        STRING,              // IDENTIFIER_START: regular part of the SQL string, keep processing it
                        STRING,              // IDENTIFIER_PART: regular part of the SQL string, keep processing it
                        STRING,              // LF: regular part of the SQL string, keep processing it
                        STRING,              // CR: regular part of the SQL string, keep processing it
                        STATEMENT,           // APOSTROPHE: end of SQL string processing, go back to STATEMENT state
                        STRING,              // STAR: regular part of the SQL string, keep processing it
                        STRING,              // DASH: regular part of the SQL string, keep processing it
                        STRING,              // SLASH: regular part of the SQL string, keep processing it
                        STRING,              // COLON: regular part of the SQL string, keep processing it
                        STRING               // OTHER: regular part of the SQL string, keep processing it
                },
                // Transitions from COLON state
                {
                        PARAMETER,           // IDENTIFIER_START: first character of named parameter,
                                             //                   switch to PARAMETER state
                        STATEMENT,           // IDENTIFIER_PART: can't be first character of named parameter,
                                             //                  go back to STATEMENT state
                        STATEMENT,           // LF: can't be first character of named parameter, go back to STATEMENT state
                        STATEMENT,           // CR: can't be first character of named parameter, go back to STATEMENT state
                        STRING,              // APOSTROPHE: not a named parameter but beginning of SQL string processing,
                                             //             switch to STRING state
                        STATEMENT,           // STAR: can't be first character of named parameter, go back to STATEMENT state
                        SINGLELN_COMMENT_BG, // DASH: not a named parameter but possible starting sequence of single line comment,
                                             //       switch to SINGLELN_COMMENT_BG state
                        MULTILN_COMMENT_BG,  // SLASH: not a named parameter but possible starting sequence of multi line comment,
                                             //        switch to MULTILN_COMMENT_BG state
                        COLON,               // COLON: not a named parameter but possible beginning of another named parameter,
                                             //        retry named parameter processing
                        STATEMENT            // OTHER: can't be first character of named parameter, go back to STATEMENT state
                },
                // Transitions from PARAMETER state
                {
                        PARAMETER,           // IDENTIFIER_START: next character of named parameter, keep processing it
                        PARAMETER,           // IDENTIFIER_PART: next character of named parameter, keep processing it
                        STATEMENT,           // LF: can't be next character of named parameter, go back to STATEMENT state
                        STATEMENT,           // CR: can't be next character of named parameter, go back to STATEMENT state
                        STRING,              // APOSTROPHE: end of named parameter and beginning of SQL string processing,
                                             //             switch to STRING state
                        STATEMENT,           // STAR: can't be next character of named parameter, go back to STATEMENT state
                        SINGLELN_COMMENT_BG, // DASH: end of named parameter and possible starting sequence of single
                                             //       line comment, switch to SINGLELN_COMMENT_BG state
                        MULTILN_COMMENT_BG,  // SLASH: end of named parameter and possible starting sequence of multi
                                             //       line comment, switch to MULTILN_COMMENT_BG state
                        COLON,               // COLON: end of named parameter and possible beginning of another named parameter,
                                             //        switch to COLON state to restart named parameter processing
                        STATEMENT            // OTHER: can't be next character of named parameter, go back to STATEMENT state
                },
                // Transitions from MULTILN_COMMENT_BG state
                {
                        STATEMENT,           // IDENTIFIER_START: not starting sequence of multi line comment,
                                             //                   go back to STATEMENT state
                        STATEMENT,           // IDENTIFIER_PART: not starting sequence of multi line comment,
                                             //                  go back to STATEMENT state
                        STATEMENT,           // LF: not starting sequence of multi line comment, go back to STATEMENT state
                        STATEMENT,           // CR: not starting sequence of multi line comment, go back to STATEMENT state
                        STRING,              // APOSTROPHE: not starting sequence of multi line comment but beginning of SQL
                                             //             string processing, switch to STRING state
                        MULTILN_COMMENT,     // STAR: end of starting sequence of multi line comment,
                                             //       switch to MULTILN_COMMENT state
                        SINGLELN_COMMENT_BG, // DASH: not starting sequence of multi line comment but possible starting sequence
                                             //       of single line comment, switch to SINGLELN_COMMENT_BG state
                        MULTILN_COMMENT_BG,  // SLASH: not starting sequence of multi line comment but possible starting sequence
                                             //       of next multi line comment, retry multi line comment processing
                        COLON,               // COLON: not starting sequence of multi line comment but possible beginning
                                             //        of named parameter, switch to COLON state
                        STATEMENT            // OTHER: not starting sequence of multi line comment, go back to STATEMENT state
                },
                // Transitions from MULTILN_COMMENT_END state
                {
                        MULTILN_COMMENT,     // IDENTIFIER_START: not ending sequence of multi line comment,
                                             //                   go back to MULTILN_COMMENT state
                        MULTILN_COMMENT,     // IDENTIFIER_PART: not ending sequence of multi line comment,
                                             //                  go back to MULTILN_COMMENT state
                        MULTILN_COMMENT,     // LF: not ending sequence of multi line comment, go back to MULTILN_COMMENT state
                        MULTILN_COMMENT,     // CR: not ending sequence of multi line comment, go back to MULTILN_COMMENT state
                        MULTILN_COMMENT,     // APOSTROPHE: not ending sequence of multi line comment,
                                             //             go back to MULTILN_COMMENT state
                        MULTILN_COMMENT_END, // STAR: not ending sequence of multi line comment but possible ending sequence
                                             //       of next multi line comment, retry end of multi line comment processing
                        MULTILN_COMMENT,     // DASH: not ending sequence of multi line comment, go back to MULTILN_COMMENT state
                        STATEMENT,           // SLASH: end of ending sequence of multi line comment,
                                             //        switch to STATEMENT state
                        MULTILN_COMMENT,     // COLON: not ending sequence of multi line comment, go back to MULTILN_COMMENT state
                        MULTILN_COMMENT      // OTHER: not ending sequence of multi line comment, go back to MULTILN_COMMENT state
                },
                // Transitions from MULTILN_COMMENT state
                {
                        MULTILN_COMMENT,     // IDENTIFIER_START: regular multi line comment, keep processing it
                        MULTILN_COMMENT,     // IDENTIFIER_PART: regular multi line comment, keep processing it
                        MULTILN_COMMENT,     // LF: regular multi line comment, keep processing it
                        MULTILN_COMMENT,     // CR: regular multi line comment, keep processing it
                        MULTILN_COMMENT,     // APOSTROPHE: regular multi line comment, keep processing it
                        MULTILN_COMMENT_END, // STAR: possible ending sequence of multi line comment,
                                             //       switch to MULTILN_COMMENT_END state
                        MULTILN_COMMENT,     // DASH: regular multi line comment, keep processing it
                        MULTILN_COMMENT,     // SLASH: regular multi line comment, keep processing it
                        MULTILN_COMMENT,     // COLON: regular multi line comment, keep processing it
                        MULTILN_COMMENT      // OTHER: regular multi line comment, keep processing it
                },
                // Transitions from SINGLELN_COMMENT_BG state
                {
                        STATEMENT,           // IDENTIFIER_START: not starting sequence of single line comment,
                                             //                   go back to STATEMENT state
                        STATEMENT,           // IDENTIFIER_PART: not starting sequence of single line comment,
                                             //                  go back to STATEMENT state
                        STATEMENT,           // LF: not starting sequence of single line comment, go back to STATEMENT state
                        STATEMENT,           // CR: not starting sequence of single line comment, go back to STATEMENT state
                        STRING,              // APOSTROPHE: not starting sequence of single line comment but beginning of SQL
                                             //             string processing, switch to STRING state
                        STATEMENT,           // STAR: not starting sequence of single line comment, go back to STATEMENT state
                        SINGLELN_COMMENT,    // DASH: end of starting sequence of single line comment,
                                             //       switch to SINGLELN_COMMENT state
                        MULTILN_COMMENT_BG,  // SLASH: not starting sequence of single line comment
                                             //       but possible starting sequence
                                             //       of next multi line comment, switch to MULTILN_COMMENT_BG state
                        COLON,               // COLON: not starting sequence of single line comment but possible beginning
                                             //        of named parameter, switch to COLON state
                        STATEMENT            // OTHER: not starting sequence of single line comment, go back to STATEMENT state
                },
                // Transitions from SINGLELN_COMMENT_END state
                {
                        SINGLELN_COMMENT,     // IDENTIFIER_START: not ending sequence of single line comment,
                                              //                   go back to SINGLELN_COMMENT state
                        SINGLELN_COMMENT,     // IDENTIFIER_PART: not ending sequence of single line comment,
                                              //                  go back to SINGLELN_COMMENT state
                        STATEMENT,            // LF: end of single line comment, switch to STATEMENT state
                        SINGLELN_COMMENT_END, // CR: not ending sequence of single line comment but possible ending sequence
                                              //     of next single line comment, retry end of single line comment processing
                        SINGLELN_COMMENT,     // APOSTROPHE: not ending sequence of single line comment,
                                              //             go back to SINGLELN_COMMENT state
                        SINGLELN_COMMENT,     // STAR: not ending sequence of single line comment,
                                              //       go back to SINGLELN_COMMENT state
                        SINGLELN_COMMENT,     // DASH: not ending sequence of single line comment,
                                              //       go back to SINGLELN_COMMENT state
                        SINGLELN_COMMENT,     // SLASH: not ending sequence of single line comment,
                                              //       go back to SINGLELN_COMMENT state
                        SINGLELN_COMMENT,     // COLON: not ending sequence of single line comment,
                                              //       go back to SINGLELN_COMMENT state
                        SINGLELN_COMMENT      // OTHER: not ending sequence of single line comment,
                                              //       go back to SINGLELN_COMMENT state
                },
                // Transitions from SINGLELN_COMMENT state
                {
                        SINGLELN_COMMENT,     // IDENTIFIER_START: regular single line comment, keep processing it
                        SINGLELN_COMMENT,     // IDENTIFIER_PART: regular single line comment, keep processing it
                        STATEMENT,            // LF: end of single line comment, switch to STATEMENT state
                        SINGLELN_COMMENT_END, // CR: possible beginning of ending sequence of multi line comment,
                                              //     switch to SINGLELN_COMMENT_END state
                        SINGLELN_COMMENT,     // APOSTROPHE: regular single line comment, keep processing it
                        SINGLELN_COMMENT,     // STAR: regular single line comment, keep processing it
                        SINGLELN_COMMENT,     // DASH: regular single line comment, keep processing it
                        SINGLELN_COMMENT,     // SLASH: regular single line comment, keep processing it
                        SINGLELN_COMMENT,     // COLON: regular single line comment, keep processing it
                        SINGLELN_COMMENT      // OTHER: regular single line comment, keep processing it
                }
        };
    }

    /**
     * State automaton action table.
     */
    private static final Action[][] ACTION = {
            // Actions performed on transitions from STATEMENT state
            {
                    NamedStatementParser::copyChar,  // IDENTIFIER_START: copy regular statement character to output
                    NamedStatementParser::copyChar,  // IDENTIFIER_PART: copy regular statement character to output
                    NamedStatementParser::copyChar,  // LF: copy regular statement character to output
                    NamedStatementParser::copyChar,  // CR: copy regular statement character to output
                    NamedStatementParser::copyChar,  // APOSTROPHE: copy SQL string character to output
                    NamedStatementParser::copyChar,  // STAR: copy regular statement character to output
                    NamedStatementParser::copyChar,  // DASH: copy character to output, no matter whether it's comment or not
                    NamedStatementParser::copyChar,  // SLASH: copy character to output, no matter whether it's comment or not
                    NamedStatementParser::doNothing, // COLON: delay character copying until it's obvious
                                                     //        whether this is parameter or not
                    NamedStatementParser::copyChar   // OTHER: copy regular statement character to output
            },
            // Actions performed on transitions from STRING state
            {
                    NamedStatementParser::copyChar, // IDENTIFIER_START: copy SQL string character to output
                    NamedStatementParser::copyChar, // IDENTIFIER_PART: copy SQL string character to output
                    NamedStatementParser::copyChar, // LF: copy SQL string character to output
                    NamedStatementParser::copyChar, // CR: copy SQL string character to output
                    NamedStatementParser::copyChar, // APOSTROPHE: copy SQL string character to output
                    NamedStatementParser::copyChar, // STAR: copy SQL string character to output
                    NamedStatementParser::copyChar, // DASH: copy SQL string character to output
                    NamedStatementParser::copyChar, // SLASH: copy SQL string character to output
                    NamedStatementParser::copyChar, // COLON: copy SQL string character to output
                    NamedStatementParser::copyChar  // OTHER: copy SQL string character to output
            },
            // Actions performed on transitions from COLON state
            {
                    NamedStatementParser::setFirstParamChar,   // IDENTIFIER_START: set first parameter character
                    NamedStatementParser::addColonAndCopyChar, // IDENTIFIER_PART: not a parameter, add delayed colon and copy
                                                               //                  current statement character to output
                    NamedStatementParser::addColonAndCopyChar, // LF: not a parameter, add delayed colon and copy current
                                                               //     statement character to output
                    NamedStatementParser::addColonAndCopyChar, // CR: not a parameter, add delayed colon and copy current
                                                               //     statement character to output
                    NamedStatementParser::addColonAndCopyChar, // APOSTROPHE: not a parameter, add delayed colon and copy
                                                               //             current SQL string character to output
                    NamedStatementParser::addColonAndCopyChar, // STAR: not a parameter, add delayed colon and copy current
                                                               //       statement character to output
                    NamedStatementParser::addColonAndCopyChar, // DASH: not a parameter, add delayed colon and copy current
                                                               //       statement character to output, no matter whether
                                                               //       it's comment or not
                    NamedStatementParser::addColonAndCopyChar, // SLASH: not a parameter, add delayed colon and copy current
                                                               //        statement character to output, no matter whether
                                                               //        it's comment or not
                    NamedStatementParser::addColon,            // COLON: not a parameter, add delayed colon and delay current
                                                               //        colon copying until it's obvious whether
                                                               //        this is parameter or not
                    NamedStatementParser::addColonAndCopyChar  // OTHER: not a parameter, add delayed colon and copy current
                                                               //        statement character to output
            },
            // Actions performed on transitions from PARAMETER state
            {
                    NamedStatementParser::setNextParamChar,       // IDENTIFIER_START: set next parameter character
                    NamedStatementParser::setNextParamChar,       // IDENTIFIER_PART: set next parameter character
                    NamedStatementParser::finishParamAndCopyChar, // LF: finish parameter processing and copy current character
                                                                  //     as part of regular statement
                    NamedStatementParser::finishParamAndCopyChar, // CR: finish parameter processing and copy current character
                                                                  //     as part of regular statement
                    NamedStatementParser::finishParamAndCopyChar, // APOSTROPHE: finish parameter processing and copy current
                                                                  //             character as part of regular statement
                    NamedStatementParser::finishParamAndCopyChar, // STAR: finish parameter processing and copy current
                                                                  //       character as part of regular statement
                    NamedStatementParser::finishParamAndCopyChar, // DASH: finish parameter processing and copy current
                                                                  //       character as part of regular statement
                    NamedStatementParser::finishParamAndCopyChar, // SLASH: finish parameter processing and copy current
                                                                  //        character as part of regular statement
                    NamedStatementParser::finishParam,            // COLON: finish parameter processing and delay character
                                                                  //        copying until it's obvious whether this is next
                                                                  //        parameter or not
                    NamedStatementParser::finishParamAndCopyChar  // OTHER: finish parameter processing and copy current
                                                                  //        character as part of regular statement
            },
            // Actions performed on transitions from MULTILN_COMMENT_BG state
            {
                    NamedStatementParser::copyChar,  // IDENTIFIER_START: copy regular statement character to output
                    NamedStatementParser::copyChar,  // IDENTIFIER_PART: copy regular statement character to output
                    NamedStatementParser::copyChar,  // LF: copy regular statement character to output
                    NamedStatementParser::copyChar,  // CR: copy regular statement character to output
                    NamedStatementParser::copyChar,  // APOSTROPHE: copy SQL string character to output
                    NamedStatementParser::copyChar,  // STAR: copy multi line comment character to output
                    NamedStatementParser::copyChar,  // DASH: copy character to output, no matter whether it's comment or not
                    NamedStatementParser::copyChar,  // SLASH: copy character to output, no matter whether it's comment or not
                    NamedStatementParser::doNothing, // COLON: delay character copying until it's obvious whether
                                                     //        this is parameter or not
                    NamedStatementParser::copyChar   // OTHER: copy regular statement character to output
            },
            // Actions performed on transitions from MULTILN_COMMENT_END state
            {
                    NamedStatementParser::copyChar, // IDENTIFIER_START: copy multi line comment character to output
                    NamedStatementParser::copyChar, // IDENTIFIER_PART: copy multi line comment character to output
                    NamedStatementParser::copyChar, // LF: copy multi line comment character to output
                    NamedStatementParser::copyChar, // CR: copy multi line comment character to output
                    NamedStatementParser::copyChar, // APOSTROPHE: copy multi line comment character to output
                    NamedStatementParser::copyChar, // STAR: copy multi line comment character to output
                    NamedStatementParser::copyChar, // DASH: copy multi line comment character to output
                    NamedStatementParser::copyChar, // SLASH: copy multi line comment character to output
                    NamedStatementParser::copyChar, // COLON: copy multi line comment character to output
                    NamedStatementParser::copyChar  // OTHER: copy multi line comment character to output
            },
            // Actions performed on transitions from MULTILN_COMMENT state
            {
                    NamedStatementParser::copyChar, // IDENTIFIER_START: copy multi line comment character to output
                    NamedStatementParser::copyChar, // IDENTIFIER_PART: copy multi line comment character to output
                    NamedStatementParser::copyChar, // LF: copy multi line comment character to output
                    NamedStatementParser::copyChar, // CR: copy multi line comment character to output
                    NamedStatementParser::copyChar, // APOSTROPHE: copy multi line comment character to output
                    NamedStatementParser::copyChar, // STAR: copy multi line comment character to output
                    NamedStatementParser::copyChar, // DASH: copy multi line comment character to output
                    NamedStatementParser::copyChar, // SLASH: copy multi line comment character to output
                    NamedStatementParser::copyChar, // COLON: copy multi line comment character to output
                    NamedStatementParser::copyChar  // OTHER: copy multi line comment character to output
            },
            // Actions performed on transitions from SINGLELN_COMMENT_BG state
            {
                    NamedStatementParser::copyChar,  // IDENTIFIER_START: copy regular statement character to output
                    NamedStatementParser::copyChar,  // IDENTIFIER_PART: copy regular statement character to output
                    NamedStatementParser::copyChar,  // LF: copy regular statement character to output
                    NamedStatementParser::copyChar,  // CR: copy regular statement character to output
                    NamedStatementParser::copyChar,  // APOSTROPHE: copy SQL string character to output
                    NamedStatementParser::copyChar,  // STAR: copy regular statement character to output
                    NamedStatementParser::copyChar,  // DASH: copy single line comment character to output
                    NamedStatementParser::copyChar,  // SLASH: copy character to output, no matter whether it's comment or not
                    NamedStatementParser::doNothing, // COLON: delay character copying until it's obvious whether
                                                     //        this is parameter or not
                    NamedStatementParser::copyChar   // OTHER: copy regular statement character to output
            },
            // Actions performed on transitions from SINGLELN_COMMENT_END state
            {
                    NamedStatementParser::copyChar,  // IDENTIFIER_START: copy single line comment character to output
                    NamedStatementParser::copyChar,  // IDENTIFIER_PART: copy single line comment character to output
                    NamedStatementParser::copyChar,  // LF: copy single line comment character to output
                    NamedStatementParser::copyChar,  // CR: copy single line comment character to output
                    NamedStatementParser::copyChar,  // APOSTROPHE: copy single line comment character to output
                    NamedStatementParser::copyChar,  // STAR: copy single line comment character to output
                    NamedStatementParser::copyChar,  // DASH: copy single line comment character to output
                    NamedStatementParser::copyChar,  // SLASH: copy single line comment character to output
                    NamedStatementParser::copyChar,  // COLON: copy single line comment character to output
                    NamedStatementParser::copyChar   // OTHER: copy single line comment character to output
            },
            // Actions performed on transitions from SINGLELN_COMMENT state
            {
                    NamedStatementParser::copyChar,  // IDENTIFIER_START: copy single line comment character to output
                    NamedStatementParser::copyChar,  // IDENTIFIER_PART: copy single line comment character to output
                    NamedStatementParser::copyChar,  // LF: copy single line comment character to output
                    NamedStatementParser::copyChar,  // CR: copy single line comment character to output
                    NamedStatementParser::copyChar,  // APOSTROPHE: copy single line comment character to output
                    NamedStatementParser::copyChar,  // STAR: copy single line comment character to output
                    NamedStatementParser::copyChar,  // DASH: copy single line comment character to output
                    NamedStatementParser::copyChar,  // SLASH: copy single line comment character to output
                    NamedStatementParser::copyChar,  // COLON: copy single line comment character to output
                    NamedStatementParser::copyChar   // OTHER: copy single line comment character to output
            }
    };

    /**
     * Do nothing.
     *
     * @param parser parser instance
     */
    private static void doNothing(NamedStatementParser parser) {
    }

    /**
     * Copy character from input string to output as is.
     *
     * @param parser parser instance
     */
    private static void copyChar(NamedStatementParser parser) {
        parser.sb.append(parser.c);
    }

    /**
     * Add previous colon character to output.
     *
     * @param parser parser instance
     */
    private static void addColon(NamedStatementParser parser) {
        parser.sb.append(':');
    }

    /**
     * Copy previous colon and current input string character to output.
     *
     * @param parser parser instance
     */
    private static void addColonAndCopyChar(NamedStatementParser parser) {
        parser.sb.append(':');
        parser.sb.append(parser.c);
    }

    /**
     * Store 1st named parameter letter.
     *
     * @param parser parser instance
     */
    private static void setFirstParamChar(NamedStatementParser parser) {
        parser.nap.setLength(0);
        parser.nap.append(parser.c);
    }

    /**
     * Store next named parameter letter or number.
     *
     * @param parser parser instance
     */
    private static void setNextParamChar(NamedStatementParser parser) {
        parser.nap.append(parser.c);
    }

    /**
     * Finish stored named parameter and copy current character from input string to output as is.
     *
     * @param parser parser instance
     */
    private static void finishParamAndCopyChar(NamedStatementParser parser) {
        String parName = parser.nap.toString();
        parser.names.add(parName);
        parser.sb.append('?');
        parser.sb.append(parser.c);
    }

    /**
     * Finish stored named parameter without copying current character from input string to output as is.
     *
     * @param parser parser instance
     */
    private static void finishParam(NamedStatementParser parser) {
        String parName = parser.nap.toString();
        parser.names.add(parName);
        parser.sb.append('?');
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
     * Ordered list of parameter names.
     */
    private final List<String> names;

    /**
     * Character being currently processed.
     */
    private char c;

    /**
     * Character class of character being currently processed.
     */
    private CharClass cl;

    NamedStatementParser(String statement) {
        this.sb = new StringBuilder(statement.length());
        this.nap = new StringBuilder(32);
        this.names = new LinkedList<>();
        this.statement = statement;
        this.c = '\0';
        this.cl = null;
    }

    // Translates query to indexed notation with named parameters replaced with '?' characters.
    // This method does the actual parsing of the query and also produces List of named parameters to be returned later.
    String convert() {
        State state = State.STATEMENT;  // Initial state: common statement processing
        int len = statement.length();
        for (int i = 0; i < len; i++) {
            c = statement.charAt(i);
            cl = CharClass.charClass(c);
            ACTION[state.ordinal()][cl.ordinal()].accept(this);
            state = State.TRANSITION[state.ordinal()][cl.ordinal()];
        }
        // Process end of statement
        if (state == State.PARAMETER) {
            String parName = nap.toString();
            names.add(parName);
            sb.append('?');
        }
        return sb.toString();
    }

    // Returns List of named parameters in the same order as listed in the query (may contain duplicate items).
    List<String> namesOrder() {
        return names;
    }

}
