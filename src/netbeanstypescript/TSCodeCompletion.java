/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2015 Everlaw. All rights reserved.
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
package netbeanstypescript;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import netbeanstypescript.api.lexer.JsTokenId;
import netbeanstypescript.api.lexer.LexUtilities;
import netbeanstypescript.options.OptionsUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.netbeans.api.lexer.TokenId;
import org.netbeans.api.lexer.TokenSequence;
import org.netbeans.modules.csl.api.*;
import org.netbeans.modules.csl.spi.DefaultCompletionResult;
import org.netbeans.modules.csl.spi.ParserResult;
import org.openide.filesystems.FileObject;

/**
 *
 * @author jeffrey
 */
public class TSCodeCompletion implements CodeCompletionHandler {

    public static class TSCompletionProposal extends TSNameKindModifiers implements CompletionProposal {
        FileObject fileObj;
        int caretOffset;
        int anchorOffset;

        String type;

        TSCompletionProposal(FileObject fileObj, int caretOffset, int anchorOffset, JSONObject m) {
            super(m);
            this.fileObj = fileObj;
            this.caretOffset = caretOffset;
            this.anchorOffset = anchorOffset;
            type = (String) m.get("type"); // may be null
        }
        
        @Override
        public int getAnchorOffset() { return anchorOffset; }
        @Override
        public ElementHandle getElement() {
            Object info = TSService.call("getCompletionEntryDetails", fileObj, caretOffset, name);
            return info == null ? null : new TSElementHandle(OffsetRange.NONE, (JSONObject) info);
        }
        @Override
        public String getInsertPrefix() { return name; }
        @Override
        public String getSortText() { return null; }
        @Override
        public String getLhsHtml(HtmlFormatter hf) {
            if (modifiers.contains(Modifier.DEPRECATED)) {
                hf.deprecated(true);
                hf.appendText(name);
                hf.deprecated(false);
            } else {
                hf.appendText(name);
            }
            return hf.getText();
        }
        @Override
        public String getRhsHtml(HtmlFormatter hf) {
            hf.setMaxLength(OptionsUtils.forLanguage(JsTokenId.javascriptLanguage()).getCodeCompletionItemSignatureWidth());
            if (type == null) {
                return null;
            }
            hf.type(true);
            hf.appendText(type);
            hf.type(false);
            return hf.getText();
        }
        @Override
        public boolean isSmart() { return false; }
        @Override
        public int getSortPrioOverride() { return 0; }
        @Override
        public String getCustomInsertTemplate() {
            String suffix = "";
            switch (getKind()) {
                case METHOD: suffix = "(${cursor})"; break;
                case PACKAGE: suffix = "/"; break;
            }
            return getInsertPrefix() + suffix;
        }
    }

    @Override
    public CodeCompletionResult complete(CodeCompletionContext ccc) {
        FileObject fileObj = ccc.getParserResult().getSnapshot().getSource().getFileObject();
        int caretOffset = ccc.getCaretOffset();
        String prefix = ccc.getPrefix();
        JSONObject info = (JSONObject) TSService.call("getCompletions", fileObj, caretOffset,
                prefix, ccc.isPrefixMatch(), ccc.isCaseSensitive());
        if (info == null) {
            return CodeCompletionResult.NONE;
        }

        List<CompletionProposal> lst = new ArrayList<>();
        for (Object ent: (JSONArray) info.get("entries")) {
            lst.add(new TSCodeCompletion.TSCompletionProposal(
                    fileObj,
                    caretOffset,
                    caretOffset - prefix.length(),
                    (JSONObject) ent));
        }
        return new DefaultCompletionResult(lst, false);
    }

    @Override
    public String document(ParserResult pr, ElementHandle eh) {
        TSElementHandle teh = (TSElementHandle) eh;
        return teh.displayParts + (teh.documentation.isEmpty() ? "" : "<p>") + teh.documentation;
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

    // CHARS_NO_AUTO_COMPLETE and getAutoQuery from javascript2.editor JsCodeCompletion
    private static final String CHARS_NO_AUTO_COMPLETE = ";,/+-\\:={}[]()"; //NOI18N

    @Override
    public QueryType getAutoQuery(JTextComponent component, String typedText) {
        if (typedText.length() == 0) {
            return QueryType.NONE;
        }

        int offset = component.getCaretPosition();
        TokenSequence<? extends JsTokenId> ts = LexUtilities.getJsTokenSequence(component.getDocument(), offset);
        if (ts != null) {
            int diff = ts.move(offset);
            TokenId currentTokenId = null;
            if (diff == 0 && ts.movePrevious() || ts.moveNext()) {
                currentTokenId = ts.token().id();
            }

            char lastChar = typedText.charAt(typedText.length() - 1);
            if (currentTokenId == JsTokenId.BLOCK_COMMENT || currentTokenId == JsTokenId.DOC_COMMENT
                    || currentTokenId == JsTokenId.LINE_COMMENT) {
                if (lastChar == '@') { //NOI18N
                    return QueryType.COMPLETION;
                }
            } else if (currentTokenId == JsTokenId.STRING && lastChar == '/') {
                return QueryType.COMPLETION;
            } else {
                switch (lastChar) {
                    case '.': //NOI18N
                        if (OptionsUtils.forLanguage(JsTokenId.javascriptLanguage()).autoCompletionAfterDot()) {
                            return QueryType.COMPLETION;
                        }
                        break;
                    default:
                        if (OptionsUtils.forLanguage(JsTokenId.javascriptLanguage()).autoCompletionFull()) {
                            if (!Character.isWhitespace(lastChar) && CHARS_NO_AUTO_COMPLETE.indexOf(lastChar) == -1) {
                                return QueryType.COMPLETION;
                            }
                        }
                        return QueryType.NONE;
                }
            }
        }
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
