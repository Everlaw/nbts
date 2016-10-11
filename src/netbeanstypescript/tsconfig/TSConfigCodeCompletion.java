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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import netbeanstypescript.tsconfig.TSConfigParser.ConfigNode;
import org.json.simple.JSONObject;
import org.netbeans.modules.csl.api.*;
import org.netbeans.modules.csl.spi.DefaultCompletionProposal;
import org.netbeans.modules.csl.spi.DefaultCompletionResult;
import org.netbeans.modules.csl.spi.ParserResult;
import org.openide.filesystems.FileObject;

/**
 *
 * @author jeffrey
 */
public class TSConfigCodeCompletion implements CodeCompletionHandler {

    static class TSConfigElementHandle implements ElementHandle {
        private static final Set<String> commandLineOnlySet = new HashSet<>(Arrays.asList(
                "help", "init", "locale", "project", "version"));

        String name;
        boolean commandLineOnly;
        Object type;
        boolean hidden;
        String message;
        TSConfigElementHandle element;

        TSConfigElementHandle(JSONObject obj) {
            name = (String) obj.get("name");
            commandLineOnly = commandLineOnlySet.contains(name);
            type = obj.get("type");
            hidden = Boolean.TRUE.equals(obj.get("experimental"));
            JSONObject description = (JSONObject) obj.get("description");
            if (description != null) {
                message = (String) description.get("message");
            } else {
                hidden = true;
            }
            JSONObject elem = (JSONObject) obj.get("element");
            if (elem != null) {
                element = new TSConfigElementHandle(elem);
            }
        }

        TSConfigElementHandle(String name, Object type, String message) {
            this.name = name;
            this.type = type;
            this.message = message;
        }

        @Override
        public FileObject getFileObject() { return null; }
        @Override
        public String getMimeType() { return null; }
        @Override
        public String getName() { return name; }
        @Override
        public String getIn() { return null; }
        @Override
        public ElementKind getKind() { return ElementKind.PROPERTY; }
        @Override
        public Set<Modifier> getModifiers() { return Collections.emptySet(); }
        @Override
        public boolean signatureEquals(ElementHandle eh) { return false; }
        @Override
        public OffsetRange getOffsetRange(ParserResult pr) { return OffsetRange.NONE; }
    }

    @Override
    public CodeCompletionResult complete(CodeCompletionContext ccc) {
        ConfigNode node = ((TSConfigParser.Result) ccc.getParserResult()).root;
        if (node == null) {
            return CodeCompletionResult.NONE;
        }

        final int caret = ccc.getCaretOffset();
        String prefix = ccc.getPrefix();
        NODE: while (true) {
            if (node.properties != null) {
                int keyStart = caret;
                for (ConfigNode property: node.properties.values()) {
                    if (caret >= property.keyOffset && caret <= property.endOffset) {
                        if (caret >= property.startOffset) {
                            node = property;
                            continue NODE;
                        }
                        keyStart = property.keyOffset;
                    }
                }
                if (node.validMap != null && caret > node.startOffset && caret < node.endOffset) {
                    List<CompletionProposal> proposals = new ArrayList<>();
                    for (final TSConfigElementHandle element: node.validMap.values()) {
                        if (element.commandLineOnly || element.hidden || ! element.name.startsWith(prefix)) {
                            continue;
                        }
                        DefaultCompletionProposal prop = new DefaultCompletionProposal() {
                            @Override
                            public String getName() { return element.name; }
                            @Override
                            public ElementHandle getElement() { return element; }
                            @Override
                            public String getRhsHtml(HtmlFormatter hf) {
                                hf.type(true);
                                hf.appendText(element.type instanceof String ? (String) element.type : "enum");
                                hf.type(false);
                                return hf.getText();
                            }
                            @Override
                            public String getCustomInsertTemplate() {
                                String value = "";
                                if (element.type instanceof String) {
                                    switch ((String) element.type) {
                                        case "boolean": value = "true"; break;
                                        case "string": value = "\"${cursor}\""; break;
                                        case "object": value = "{${cursor}}"; break;
                                        case "list": value = "[${cursor}]"; break;
                                    }
                                }
                                return '"' + getInsertPrefix() + "\": " + value;
                            }
                        };
                        prop.setAnchorOffset(keyStart);
                        prop.setKind(element.getKind());
                        proposals.add(prop);
                    }
                    return new DefaultCompletionResult(proposals, false);
                }
            }
            if (node.expectedType instanceof JSONObject) {
                List<CompletionProposal> proposals = new ArrayList<>();
                for (final Object validValue: ((Map) node.expectedType).keySet()) {
                    DefaultCompletionProposal prop = new DefaultCompletionProposal() {
                        @Override
                        public String getName() { return "\"" + validValue + '"'; }
                        @Override
                        public ElementHandle getElement() { return null; }
                    };
                    prop.setAnchorOffset(node.missing ? caret : node.startOffset);
                    prop.setKind(ElementKind.PARAMETER);
                    proposals.add(prop);
                }
                return new DefaultCompletionResult(proposals, false);
            }
            return CodeCompletionResult.NONE;
        }
    }

    @Override
    public String document(ParserResult pr, ElementHandle eh) {
        TSConfigElementHandle elem = (TSConfigElementHandle) eh;
        return elem.message;
    }

    @Override
    public ElementHandle resolveLink(String string, ElementHandle eh) {
        return null;
    }

    @Override
    public String getPrefix(ParserResult info, int caretOffset, boolean upToOffset) {
        CharSequence seq = info.getSnapshot().getText();
        int i = caretOffset, j = i;
        while (i > 0 && Character.isJavaIdentifierPart(seq.charAt(i - 1))) {
            i--;
        }
        while (! upToOffset && j < seq.length() && Character.isJavaIdentifierPart(seq.charAt(j))) {
            j++;
        }
        return seq.subSequence(i, j).toString();
    }

    @Override
    public QueryType getAutoQuery(JTextComponent jtc, String string) {
        return QueryType.NONE;
    }

    @Override
    public String resolveTemplateVariable(String string, ParserResult pr, int i, String string1, Map map) {
        return null;
    }

    @Override
    public Set<String> getApplicableTemplates(Document dcmnt, int i, int i1) {
        return null;
    }

    @Override
    public ParameterInfo parameters(ParserResult pr, int i, CompletionProposal cp) {
        return ParameterInfo.NONE;
    }
}
