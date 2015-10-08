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

import java.util.Map;
import java.util.Set;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.json.simple.JSONObject;
import org.netbeans.modules.csl.api.*;
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
            return TSService.INSTANCE.getCompletionEntryDetails(fileObj, caretOffset, name);
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
            return getInsertPrefix() + (getKind() == ElementKind.METHOD ? "(${cursor})" : "");
        }
    }

    @Override
    public CodeCompletionResult complete(CodeCompletionContext ccc) {
        return TSService.INSTANCE.getCompletions(
                ccc.getParserResult().getSnapshot().getSource().getFileObject(),
                ccc.getCaretOffset(),
                ccc.getPrefix(), ccc.isPrefixMatch(), ccc.isCaseSensitive());
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

    @Override
    public QueryType getAutoQuery(JTextComponent component, String typedText) {
        return typedText.endsWith(".") ? QueryType.COMPLETION : QueryType.NONE;
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
