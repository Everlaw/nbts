/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2011 Oracle and/or its affiliates. All rights reserved.
 *
 * Oracle and Java are registered trademarks of Oracle and/or its affiliates.
 * Other names may be trademarks of their respective owners.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development and
 * Distribution License("CDDL") (collectively, the "License"). You may not use
 * this file except in compliance with the License. You can obtain a copy of
 * the License at http://www.netbeans.org/cddl-gplv2.html or
 * nbbuild/licenses/CDDL-GPL-2-CP. See the License for the specific language
 * governing permissions and limitations under the License. When distributing
 * the software, include this License Header Notice in each file and include
 * the License file at nbbuild/licenses/CDDL-GPL-2-CP. Oracle designates this
 * particular file as subject to the "Classpath" exception as provided by
 * Oracle in the GPL Version 2 section of the License file that accompanied
 * this code. If applicable, add the following below the License Header, with
 * the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyrighted [year] [name of copyright owner]"
 *
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license." If you do not indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to its
 * licensees as provided above. However, if you add GPL Version 2 code and
 * therefore, elected the GPL Version 2 license, then the option applies only
 * if the new code is made subject to such option by the copyright holder.
 *
 * Contributor(s):
 *
 * Portions Copyrighted 2011 Sun Microsystems, Inc.
 * Portions Copyrighted 2015 Everlaw
 */
package netbeanstypescript.api.lexer;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import netbeanstypescript.lexer.JsDocumentationTokenId;
import org.netbeans.api.lexer.InputAttributes;
import org.netbeans.api.lexer.Language;
import org.netbeans.api.lexer.LanguagePath;
import org.netbeans.api.lexer.Token;
import org.netbeans.api.lexer.TokenId;
import org.netbeans.spi.lexer.LanguageEmbedding;
import org.netbeans.spi.lexer.LanguageHierarchy;
import org.netbeans.spi.lexer.Lexer;
import org.netbeans.spi.lexer.LexerRestartInfo;

/**
 *
 * @author Petr Pisl
 * @author Petr Hejl
 */
public enum JsTokenId implements TokenId {

    ERROR(null, "error") { // NOI18N
        @Override
        public boolean isError() {
            return true;
        }
    },

    UNKNOWN(null, "unknown") { // NOI18N
        @Override
        public boolean isError() {
            return true;
        }
    },

    NUMBER(null, "constant"), // NOI18N

    IDENTIFIER(null, "identifier"), // NOI18N

    WHITESPACE(null, "whitespace"), // NOI18N
    EOL(null, "whitespace"), // NOI18N

    LINE_COMMENT(null, "comment"), // NOI18N
    BLOCK_COMMENT(null, "comment"), // NOI18N
    DOC_COMMENT(null, "comment"), // NOI18N

    STRING_BEGIN(null, "string"), // NOI18N
    STRING(null, "string"), // NOI18N
    STRING_END(null, "string"), // NOI18N

    REGEXP_BEGIN(null, "mod-regexp"), // NOI18N
    REGEXP(null, "mod-regexp"), // NOI18N
    REGEXP_END(null, "mod-regexp"), // NOI18N

    BRACKET_LEFT_PAREN("(", "separator"), // NOI18N
    BRACKET_RIGHT_PAREN(")", "separator"), // NOI18N
    BRACKET_LEFT_CURLY("{", "separator"), // NOI18N
    BRACKET_RIGHT_CURLY("}", "separator"), // NOI18N
    BRACKET_LEFT_BRACKET("[", "separator"), // NOI18N
    BRACKET_RIGHT_BRACKET("]", "separator"), // NOI18N

    OPERATOR_SEMICOLON(";", "separator"), // NOI18N
    OPERATOR_COMMA(",", "separator"), // NOI18N
    OPERATOR_DOT(".", "separator"), // NOI18N
    OPERATOR_DOT_DOT_DOT("...", "operator"), // NOI18N
    OPERATOR_ASSIGNMENT("=", "operator"), // NOI18N
    OPERATOR_GREATER(">", "operator"), // NOI18N
    OPERATOR_LOWER("<", "operator"), // NOI18N
    OPERATOR_NOT("!", "operator"), // NOI18N
    OPERATOR_BITWISE_NOT("~", "operator"), // NOI18N
    OPERATOR_TERNARY("?", "operator"), // NOI18N
    OPERATOR_COLON(":", "separator"), // NOI18N
    OPERATOR_EQUALS("==", "operator"), // NOI18N
    OPERATOR_EQUALS_EXACTLY("===", "operator"), // NOI18N
    OPERATOR_LOWER_EQUALS("<=", "operator"), // NOI18N
    OPERATOR_GREATER_EQUALS(">=", "operator"), // NOI18N
    OPERATOR_NOT_EQUALS("!=", "operator"), // NOI18N
    OPERATOR_NOT_EQUALS_EXACTLY("!==", "operator"), // NOI18N
    OPERATOR_AND("&&", "operator"), // NOI18N
    OPERATOR_OR("||", "operator"), // NOI18N
    OPERATOR_INCREMENT("++", "operator"), // NOI18N
    OPERATOR_DECREMENT("--", "operator"), // NOI18N
    OPERATOR_PLUS("+", "operator"), // NOI18N
    OPERATOR_MINUS("-", "operator"), // NOI18N
    OPERATOR_MULTIPLICATION("*", "operator"), // NOI18N
    OPERATOR_DIVISION("/", "operator"), // NOI18N
    OPERATOR_BITWISE_AND("&", "operator"), // NOI18N
    OPERATOR_BITWISE_OR("|", "operator"), // NOI18N
    OPERATOR_BITWISE_XOR("^", "operator"), // NOI18N
    OPERATOR_MODULUS("%", "operator"), // NOI18N
    OPERATOR_LEFT_SHIFT_ARITHMETIC("<<", "operator"), // NOI18N
    OPERATOR_RIGHT_SHIFT_ARITHMETIC(">>", "operator"), // NOI18N
    OPERATOR_RIGHT_SHIFT(">>>", "operator"), // NOI18N
    OPERATOR_PLUS_ASSIGNMENT("+=", "operator"), // NOI18N
    OPERATOR_MINUS_ASSIGNMENT("-=", "operator"), // NOI18N
    OPERATOR_MULTIPLICATION_ASSIGNMENT("*=", "operator"), // NOI18N
    OPERATOR_DIVISION_ASSIGNMENT("/=", "operator"), // NOI18N
    OPERATOR_BITWISE_AND_ASSIGNMENT("&=", "operator"), // NOI18N
    OPERATOR_BITWISE_OR_ASSIGNMENT("|=", "operator"), // NOI18N
    OPERATOR_BITWISE_XOR_ASSIGNMENT("^=", "operator"), // NOI18N
    OPERATOR_MODULUS_ASSIGNMENT("%=", "operator"), // NOI18N
    OPERATOR_LEFT_SHIFT_ARITHMETIC_ASSIGNMENT("<<=", "operator"), // NOI18N
    OPERATOR_RIGHT_SHIFT_ARITHMETIC_ASSIGNMENT(">>=", "operator"), // NOI18N
    OPERATOR_RIGHT_SHIFT_ASSIGNMENT(">>>=", "operator"), // NOI18N
    OPERATOR_AT("@", "operator"), // NOI18N
    OPERATOR_LESS_THAN_SLASH("</", "operator"), // NOI18N

    KEYWORD_BREAK("break", "keyword"), // NOI18N
    KEYWORD_CASE("case", "keyword"), // NOI18N
    KEYWORD_CATCH("catch", "keyword"), // NOI18N
    KEYWORD_CONTINUE("continue", "keyword"), // NOI18N
    KEYWORD_DEBUGGER("debugger", "keyword"), // NOI18N
    KEYWORD_DEFAULT("default", "keyword"), // NOI18N
    KEYWORD_DELETE("delete", "keyword"), // NOI18N
    KEYWORD_DO("do", "keyword"), // NOI18N
    KEYWORD_ELSE("else", "keyword"), // NOI18N
    KEYWORD_FINALLY("finally", "keyword"), // NOI18N
    KEYWORD_FOR("for", "keyword"), // NOI18N
    KEYWORD_FUNCTION("function", "keyword"), // NOI18N
    KEYWORD_IF("if", "keyword"), // NOI18N
    KEYWORD_IN("in", "keyword"), // NOI18N
    KEYWORD_INSTANCEOF("instanceof", "keyword"), // NOI18N
    KEYWORD_NEW("new", "keyword"), // NOI18N
    KEYWORD_RETURN("return", "keyword"), // NOI18N
    KEYWORD_SWITCH("switch", "keyword"), // NOI18N
    KEYWORD_THIS("this", "keyword"), // NOI18N
    KEYWORD_THROW("throw", "keyword"), // NOI18N
    KEYWORD_TRY("try", "keyword"), // NOI18N
    KEYWORD_TYPEOF("typeof", "keyword"), // NOI18N
    KEYWORD_VAR("var", "keyword"), // NOI18N
    KEYWORD_VOID("void", "keyword"), // NOI18N
    KEYWORD_WHILE("while", "keyword"), // NOI18N
    KEYWORD_WITH("with", "keyword"), // NOI18N

    RESERVED_CLASS("class", "keyword"), // NOI18N
    RESERVED_CONST("const", "keyword"), // NOI18N
    RESERVED_ENUM("enum", "keyword"), // NOI18N
    RESERVED_EXPORT("export", "keyword"), // NOI18N
    RESERVED_EXTENDS("extends", "keyword"), // NOI18N
    RESERVED_IMPORT("import", "keyword"), // NOI18N
    RESERVED_SUPER("super", "keyword"), // NOI18N

    RESERVED_IMPLEMENTS("implements", "keyword"), // NOI18N
    RESERVED_INTERFACE("interface", "keyword"), // NOI18N
    RESERVED_LET("let", "keyword"), // NOI18N
    RESERVED_PACKAGE("package", "reserved"), // NOI18N
    RESERVED_PRIVATE("private", "keyword"), // NOI18N
    RESERVED_PROTECTED("protected", "keyword"), // NOI18N
    RESERVED_PUBLIC("public", "keyword"), // NOI18N
    RESERVED_STATIC("static", "keyword"), // NOI18N
    RESERVED_YIELD("yield", "keyword"), // NOI18N

    // non-highlighted keywords: constructor, get, require, set
    TYPESCRIPT_ABSTRACT("abstract", "keyword"), // NOI18N
    TYPESCRIPT_ANY("any", "keyword"), // NOI18N
    TYPESCRIPT_AS("as", "keyword"), // NOI18N
    TYPESCRIPT_ASYNC("async", "keyword"), // NOI18N
    TYPESCRIPT_AWAIT("await", "keyword"), // NOI18N
    TYPESCRIPT_BOOLEAN("boolean", "keyword"), // NOI18N
    TYPESCRIPT_DECLARE("declare", "keyword"), // NOI18N
    TYPESCRIPT_FROM("from", "keyword"), // NOI18N
    TYPESCRIPT_IS("is", "keyword"), // NOI18N
    TYPESCRIPT_MODULE("module", "keyword"), // NOI18N
    TYPESCRIPT_NAMESPACE("namespace", "keyword"), // NOI18N
    TYPESCRIPT_NUMBER("number", "keyword"), // NOI18N
    TYPESCRIPT_OF("of", "keyword"), // NOI18N
    TYPESCRIPT_STRING("string", "keyword"), // NOI18N
    TYPESCRIPT_SYMBOL("symbol", "keyword"), // NOI18N
    TYPESCRIPT_TYPE("type", "keyword"), // NOI18N

    KEYWORD_TRUE("true", "keyword"), // NOI18N
    KEYWORD_FALSE("false", "keyword"), // NOI18N
    KEYWORD_NULL("null", "keyword"); // NOI18N

    //public static final String JAVASCRIPT_MIME_TYPE = "text/javascript"; // NOI18N
    public static final String JAVASCRIPT_MIME_TYPE = "text/typescript"; // NOI18N

    //public static final String JSON_MIME_TYPE = "text/x-json"; // NOI18N

    private final String fixedText;

    private final String primaryCategory;

    JsTokenId(String fixedText, String primaryCategory) {
        this.fixedText = fixedText;
        this.primaryCategory = primaryCategory;
    }

    public String fixedText() {
        return fixedText;
    }

    @Override
    public String primaryCategory() {
        return primaryCategory;
    }

    public boolean isKeyword() {
        return "keyword".equals(primaryCategory); //NOI18N
    }

    public boolean isError() {
        return false;
    }

    private static final Language<JsTokenId> JAVASCRIPT_LANGUAGE =
            new LanguageHierarchy<JsTokenId>() {
                @Override
                protected String mimeType() {
                    return JsTokenId.JAVASCRIPT_MIME_TYPE;
                }

                @Override
                protected Collection<JsTokenId> createTokenIds() {
                    return EnumSet.allOf(JsTokenId.class);
                }

                @Override
                protected Map<String, Collection<JsTokenId>> createTokenCategories() {
                    Map<String, Collection<JsTokenId>> cats =
                            new HashMap<String, Collection<JsTokenId>>();
                    return cats;
                }

                @Override
                protected Lexer<JsTokenId> createLexer(LexerRestartInfo<JsTokenId> info) {
                    //return JsLexer.create(info);
                    return new TSLexer(info);
                }

                @Override
                protected LanguageEmbedding<?> embedding(Token<JsTokenId> token,
                        LanguagePath languagePath, InputAttributes inputAttributes) {
                    JsTokenId id = token.id();

                    if (id == DOC_COMMENT || id == BLOCK_COMMENT) {
                        return LanguageEmbedding.create(JsDocumentationTokenId.language(), 0, 0);
                    }

                    return null; // No embedding
                }
            }.language();

    /*private static final Language<JsTokenId> JSON_LANGUAGE =
            new LanguageHierarchy<JsTokenId>() {
                @Override
                protected String mimeType() {
                    return JsTokenId.JSON_MIME_TYPE;
                }

                @Override
                protected Collection<JsTokenId> createTokenIds() {
                    return EnumSet.allOf(JsTokenId.class);
                }

                @Override
                protected Map<String, Collection<JsTokenId>> createTokenCategories() {
                    Map<String, Collection<JsTokenId>> cats =
                            new HashMap<String, Collection<JsTokenId>>();
                    return cats;
                }

                @Override
                protected Lexer<JsTokenId> createLexer(LexerRestartInfo<JsTokenId> info) {
                    return JsonLexer.create(info);
                }
            }.language();*/

    public static Language<JsTokenId> javascriptLanguage() {
        return JAVASCRIPT_LANGUAGE;
    }

    /*public static Language<JsTokenId> jsonLanguage() {
        return JSON_LANGUAGE;
    }*/
}
