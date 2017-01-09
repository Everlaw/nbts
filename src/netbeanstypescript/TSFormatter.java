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

import java.util.List;
import java.util.prefs.Preferences;
import javax.swing.text.BadLocationException;
import org.json.simple.JSONObject;
import org.netbeans.editor.BaseDocument;
import org.netbeans.modules.csl.api.Formatter;
import org.netbeans.modules.csl.api.OffsetRange;
import org.netbeans.modules.csl.spi.GsfUtilities;
import org.netbeans.modules.csl.spi.ParserResult;
import org.netbeans.modules.editor.indent.api.IndentUtils;
import org.netbeans.modules.editor.indent.spi.CodeStylePreferences;
import org.netbeans.modules.editor.indent.spi.Context;
import org.openide.util.Exceptions;

/**
 *
 * @author jeffrey
 */
public class TSFormatter implements Formatter {

    @Override
    public void reformat(Context context, ParserResult pr) {
        final BaseDocument doc = (BaseDocument) context.document();
        JSONObject settings = new JSONObject();
        settings.put("indentSize", IndentUtils.indentLevelSize(doc));
        settings.put("tabSize", IndentUtils.tabSize(doc));
        settings.put("newLineCharacter", "\n");
        settings.put("convertTabsToSpaces", IndentUtils.isExpandTabs(doc));
        settings.put("indentStyle", 2);
        // TODO: The JS editor's settings don't correspond well with ts.FormatCodeSettings.
        // Should probably create a separate text/typescript style preferences dialog, so
        // it's clear to the user what can and can't be changed.
        Preferences jsPrefs = CodeStylePreferences.get(doc, "text/javascript").getPreferences();
        settings.put("insertSpaceAfterCommaDelimiter",
                jsPrefs.getBoolean("spaceAfterComma", true));
        settings.put("insertSpaceAfterSemicolonInForStatements",
                jsPrefs.getBoolean("spaceAfterSemi", true));
        settings.put("insertSpaceBeforeAndAfterBinaryOperators",
                jsPrefs.getBoolean("spaceAroundBinaryOps", true));
        settings.put("insertSpaceAfterKeywordsInControlFlowStatements",
                jsPrefs.getBoolean("spaceBeforeIfParen", true));
        settings.put("insertSpaceAfterFunctionKeywordForAnonymousFunctions",
                jsPrefs.getBoolean("spaceBeforeAnonMethodDeclParen", true));
        settings.put("insertSpaceAfterOpeningAndBeforeClosingNonemptyParenthesis",
                jsPrefs.getBoolean("spaceWithinParens", false));
        settings.put("insertSpaceAfterOpeningAndBeforeClosingNonemptyBrackets",
                jsPrefs.getBoolean("spaceWithinArrayBrackets", false));
        settings.put("insertSpaceAfterOpeningAndBeforeClosingNonemptyBraces",
                jsPrefs.getBoolean("spaceWithinBraces", false));
        settings.put("insertSpaceAfterOpeningAndBeforeClosingTemplateStringBraces",
                jsPrefs.getBoolean("spaceWithinBraces", false));
        settings.put("insertSpaceAfterOpeningAndBeforeClosingJsxExpressionBraces",
                jsPrefs.getBoolean("spaceWithinBraces", false));
        settings.put("insertSpaceAfterTypeAssertion",
                // JS doesn't have typecasts...
                CodeStylePreferences.get(doc, "text/x-java").getPreferences()
                        .getBoolean("spaceAfterTypeCast", true));
        settings.put("placeOpenBraceOnNewLineForFunctions",
                jsPrefs.get("functionDeclBracePlacement", "").startsWith("NEW"));
        settings.put("placeOpenBraceOnNewLineForControlBlocks",
                jsPrefs.get("ifBracePlacement", "").startsWith("NEW"));
        final Object edits = TSService.call("getFormattingEdits",
                GsfUtilities.findFileObject(doc), context.startOffset(), context.endOffset(),
                settings);
        if (edits == null) {
            return;
        }
        doc.runAtomic(new Runnable() {
            @Override
            public void run() {
                try {
                    applyEdits(doc, edits);
                } catch (BadLocationException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        });
    }

    public static OffsetRange applyEdits(BaseDocument doc, Object edits) throws BadLocationException {
        int sizeChange = 0;
        int editsStart = -1, editsEnd = -1;
        for (JSONObject edit: (List<JSONObject>) edits) {
            JSONObject span = (JSONObject) edit.get("span");
            int start = ((Number) span.get("start")).intValue() + sizeChange;
            int length = ((Number) span.get("length")).intValue();
            String newText = (String) edit.get("newText");
            doc.replace(start, length, newText, null);
            sizeChange += newText.length() - length;
            if (editsStart == -1) {
                editsStart = start;
            }
            editsEnd = start + newText.length();
        }
        return editsStart >= 0 ? new OffsetRange(editsStart, editsEnd) : null;
    }

    @Override
    public void reindent(Context context) {} // JsTypedBreakInterceptor handles indentation

    @Override
    public boolean needsParserResult() {
        return true; // just making sure services has an up to date copy of the source
    }

    @Override
    public int indentSize() { return 4; }

    @Override
    public int hangingIndentSize() { return 8; }
}
