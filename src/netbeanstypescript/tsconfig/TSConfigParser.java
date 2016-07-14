/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2016 Everlaw. All rights reserved.
 *
 * Oracle and Java are registered trademarks of Oracle and/or its affiliates.
 * Other names may be trademarks of their respective owners.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * http://www.netbeans.org/cddl-gplv2.html
 * or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
 * specific language governing permissions and limitations under the
 * License.  When distributing the software, include this License Header
 * Notice in each file and include the License file at
 * nbbuild/licenses/CDDL-GPL-2-CP.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * If you wish your version of this file to be governed by only the CDDL
 * or only the GPL Version 2, indicate your decision by adding
 * "[Contributor] elects to include this software in this distribution
 * under the [CDDL or GPL Version 2] license." If you do not indicate a
 * single choice of license, a recipient has the option to distribute
 * your version of this file under either the CDDL, the GPL Version 2 or
 * to extend the choice of license to its licensees as provided above.
 * However, if you add GPL Version 2 code and therefore, elected the GPL
 * Version 2 license, then the option applies only if the new code is
 * made subject to such option by the copyright holder.
 */
package netbeanstypescript.tsconfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.event.ChangeListener;
import netbeanstypescript.TSService;
import netbeanstypescript.api.lexer.JsTokenId;
import netbeanstypescript.tsconfig.TSConfigCodeCompletion.TSConfigElementHandle;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.netbeans.api.lexer.TokenSequence;
import org.netbeans.modules.csl.api.Error;
import org.netbeans.modules.csl.api.Severity;
import org.netbeans.modules.csl.spi.DefaultError;
import org.netbeans.modules.csl.spi.ParserResult;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.api.Task;
import org.netbeans.modules.parsing.spi.ParseException;
import org.netbeans.modules.parsing.spi.Parser;
import org.netbeans.modules.parsing.spi.SourceModificationEvent;
import org.openide.filesystems.FileObject;

/**

 * @author jeffrey
 */
public class TSConfigParser extends Parser {

    static class ConfigNode {
        int keyOffset;
        int startOffset;
        int endOffset;
        boolean missing;
        Object value;
        List<ConfigNode> elements;
        HashMap<String, ConfigNode> properties;

        Map<String, TSConfigElementHandle> validMap;
        Object expectedType;
    }

    static class Result extends ParserResult {
        final FileObject fileObj;
        List<Error> errors = new ArrayList<>();
        ConfigNode root;

        Result(Snapshot snapshot) {
            super(snapshot);
            fileObj = snapshot.getSource().getFileObject();
        }

        void addError(String error, ConfigNode node) {
            addError(error, node.startOffset, node.endOffset, Severity.ERROR);
        }

        void addError(String error, int start, int end) {
            addError(error, start, end, Severity.ERROR);
        }

        void addError(String error, int start, int end, Severity sev) {
            errors.add(new DefaultError(null, error, null, fileObj, start, end, false, sev));
        }

        @Override
        public List<? extends Error> getDiagnostics() { return errors; }
        @Override
        protected void invalidate() {}
    }

    Result result;

    @Override
    public void parse(Snapshot snapshot, Task task, SourceModificationEvent sme) throws ParseException {
        final TokenSequence<JsTokenId> ts =
                snapshot.getTokenHierarchy().tokenSequence(JsTokenId.javascriptLanguage());
        final Result res = new Result(snapshot);

        res.root = (new Object() {
            private JsTokenId advance() {
                while (ts.moveNext()) {
                    JsTokenId id = ts.token().id();
                    String category = id.primaryCategory();
                    if (! ("comment".equals(category) || "whitespace".equals(category))) {
                        return id;
                    }
                }
                return null;
            }
            private void error(String msg) {
                res.addError(msg, ts.offset(), ts.offset() + ts.token().length());
            }
            private boolean nextListItem(boolean previous, JsTokenId endToken, String type) {
                if (! previous) {
                    JsTokenId id = advance();
                    if (id == null || id == JsTokenId.BRACKET_RIGHT_BRACKET || id == JsTokenId.BRACKET_RIGHT_CURLY) {
                        if (id != endToken) {
                            if (id != null) ts.movePrevious();
                            error("Unterminated " + type + ".");
                        }
                        return false;
                    }
                    return true;
                } else {
                    JsTokenId id = advance();
                    if (id == JsTokenId.OPERATOR_COMMA) {
                        id = advance();
                        if (id == null) {
                            error("Unterminated " + type + ".");
                            return false;
                        }
                        return true;
                    } else if (id == null || id == JsTokenId.BRACKET_RIGHT_BRACKET || id == JsTokenId.BRACKET_RIGHT_CURLY) {
                        if (id != endToken) {
                            if (id != null) ts.movePrevious();
                            error("Unterminated " + type + ".");
                        }
                        return false;
                    } else {
                        error("Missing comma.");
                        return true;
                    }
                }
            }
            private ConfigNode value() {
                ConfigNode node = new ConfigNode();
                node.startOffset = ts.offset();
                switch (ts.token().id()) {
                    case KEYWORD_NULL:  break;
                    case KEYWORD_TRUE:  node.value = Boolean.TRUE; break;
                    case KEYWORD_FALSE: node.value = Boolean.FALSE; break;
                    case STRING: case NUMBER:
                        // TODO: negative numbers
                        try {
                            node.value = JSONValue.parseWithException(ts.token().text().toString());
                        } catch (org.json.simple.parser.ParseException e) {
                            error("Invalid JSON literal: " + e);
                            return null;
                        }
                        break;
                    case BRACKET_LEFT_BRACKET:
                        node.elements = new ArrayList<>();
                        for (boolean in = false;
                                (in = nextListItem(in, JsTokenId.BRACKET_RIGHT_BRACKET, "array")); ) {
                            ConfigNode element = value();
                            if (element != null) {
                                node.elements.add(element);
                            }
                        }
                        break;
                    case BRACKET_LEFT_CURLY:
                        node.properties = new LinkedHashMap<>();
                        for (boolean in = false;
                                (in = nextListItem(in, JsTokenId.BRACKET_RIGHT_CURLY, "object")); ) {
                            ConfigNode keyNode = value();
                            if (keyNode == null) continue;
                            String key = null;
                            if (keyNode.value instanceof String) {
                                key = (String) keyNode.value;
                            } else {
                                res.addError("JSON object key must be a string.", keyNode);
                            }
                            int colonOffset = keyNode.endOffset;
                            ConfigNode valueNode;
                            if (advance() == JsTokenId.OPERATOR_COLON) {
                                colonOffset = ts.offset() + 1;
                                advance();
                            } else {
                                res.addError("JSON object must consist of '\"key\": value' pairs.", keyNode);
                            }
                            valueNode = value();
                            if (valueNode == null) {
                                valueNode = new ConfigNode();
                                valueNode.missing = true;
                                valueNode.startOffset = colonOffset;
                                valueNode.endOffset = ts.offset() + ts.token().length();
                            }
                            valueNode.keyOffset = keyNode.startOffset;
                            if (key != null) {
                                ConfigNode oldValue = node.properties.put(key, valueNode);
                                if (oldValue != null) {
                                    res.addError("Duplicate key '" + key + "' will be ignored.",
                                            oldValue.keyOffset, oldValue.endOffset, Severity.WARNING);
                                }
                            }
                        }
                        break;
                    case OPERATOR_COMMA:
                    case BRACKET_RIGHT_BRACKET:
                    case BRACKET_RIGHT_CURLY:
                        error("Missing value.");
                        ts.movePrevious();
                        return null;
                    default:
                        error("Unexpected token type " + ts.token().id());
                        return null;
                }
                node.endOffset = ts.offset() + ts.token().length();
                return node;
            }
            ConfigNode root() {
                if (advance() == null) {
                    return null;
                }
                ConfigNode root = value();
                if (advance() != null) {
                    error("Extra text at end of JSON.");
                }
                return root;
            }
        }).root();

        ROOT: if (res.root != null) {
            if (res.root.properties == null) {
                res.addError("tsconfig.json value should be an object", res.root);
                break ROOT;
            }

            Map<String, TSConfigElementHandle> rootCompletions = res.root.validMap = new HashMap<>();

            rootCompletions.put("compileOnSave", new TSConfigElementHandle(
                    "compileOnSave", "boolean", "If true, any TypeScript files modified during the IDE session are automatically transpiled to JS."));
            ConfigNode compileOnSave = res.root.properties.get("compileOnSave");
            if (compileOnSave != null && ! (compileOnSave.value instanceof Boolean)) {
                res.addError("'compileOnSave' value must be a boolean.", compileOnSave);
            }

            ConfigNode files = res.root.properties.get("files");
            rootCompletions.put("files", new TSConfigElementHandle(
                    "files", "list", "Array of files to include in the project."));
            if (files != null) {
                if (files.elements == null) {
                    res.addError("'files' value must be an array of strings.", files);
                } else {
                    for (ConfigNode file: files.elements) {
                        if (! (file.value instanceof String)) {
                            res.addError("'files' element should be a string.", file);
                        }
                    }
                }
            }

            ConfigNode exclude = res.root.properties.get("exclude");
            rootCompletions.put("exclude", new TSConfigElementHandle(
                    "exclude", "list", "Array of files and directories not to include in the project."));
            if (exclude != null) {
                if (files != null) {
                    res.addError("'exclude' is ignored when 'files' is used.",
                            exclude.keyOffset, exclude.endOffset, Severity.WARNING);
                } else if (exclude.elements == null) {
                    res.addError("'exclude' value must be an array of strings.", exclude);
                } else {
                    for (ConfigNode file: exclude.elements) {
                        if (! (file.value instanceof String)) {
                            res.addError("'exclude' element should be a string.", file);
                        }
                    }
                }
            }

            rootCompletions.put("compilerOptions", new TSConfigElementHandle(
                    "compilerOptions", "object", null));
            ConfigNode compilerOptions = res.root.properties.get("compilerOptions");
            OPTIONS: if (compilerOptions != null) {
                if (compilerOptions.properties == null) {
                    res.addError("'compilerOptions' value should be an object.", compilerOptions);
                    break OPTIONS;
                }
                JSONArray validArray = (JSONArray) TSService.call("getCompilerOptions", res.fileObj);
                if (validArray == null) {
                    res.addError("Error communicating with Node.js process", compilerOptions);
                    break OPTIONS;
                }
                HashMap<String, TSConfigElementHandle> validMap = new HashMap<>();
                for (Object obj: validArray) {
                    TSConfigElementHandle eh = new TSConfigElementHandle((JSONObject) obj);
                    validMap.put(eh.getName(), eh);
                }
                compilerOptions.validMap = validMap;
                for (Map.Entry<String, ConfigNode> entry: compilerOptions.properties.entrySet()) {
                    String key = entry.getKey();
                    ConfigNode value = entry.getValue();
                    TSConfigElementHandle optionInfo = validMap.get(key);
                    if (optionInfo == null) {
                        res.addError("Unknown compiler option '" + key + "'.",
                                value.keyOffset, value.endOffset);
                        continue;
                    }
                    checkType(res, value, optionInfo);
                    if (key.equals("out")) {
                        res.addError("'out' option is deprecated. Use 'outFile' instead.",
                                value.keyOffset, value.endOffset, Severity.WARNING);
                    }
                    if (optionInfo.commandLineOnly) {
                        res.addError("Option '" + key + "' is only meaningful when used from the command line.",
                                value.keyOffset, value.endOffset, Severity.WARNING);
                    }
                }
            }
        }

        this.result = res;
    }

    private void checkType(Result res, ConfigNode value, TSConfigElementHandle optionInfo) {
        String key = optionInfo.name;
        Object type = optionInfo.type;
        value.expectedType = type;
        if (value.missing) return;
        if (type instanceof String) {
            boolean valid = false;
            switch ((String) type) {
                case "boolean": valid = value.value instanceof Boolean; break;
                case "number": valid = value.value instanceof Number; break;
                case "string": valid = value.value instanceof String; break;
            }
            if (! valid) {
                res.addError("Compiler option '" + key + "' requires a value of type " + type + ".", value);
            }
        } else if (type instanceof JSONObject) {
            if (! (value.value instanceof String &&
                   ((Map) type).containsKey(((String) value.value).toLowerCase()))) {
                res.addError(optionInfo.errorMessage, value);
            }
        }
    }

    @Override
    public Parser.Result getResult(Task task) throws ParseException {
        return result;
    }
    @Override
    public void addChangeListener(ChangeListener cl) {}
    @Override
    public void removeChangeListener(ChangeListener cl) {}
}
