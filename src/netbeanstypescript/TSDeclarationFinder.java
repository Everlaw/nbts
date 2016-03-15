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
import javax.swing.text.Document;
import netbeanstypescript.api.lexer.JsTokenId;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.netbeans.api.lexer.Language;
import org.netbeans.modules.csl.api.DeclarationFinder;
import org.netbeans.modules.csl.api.ElementHandle;
import org.netbeans.modules.csl.api.HtmlFormatter;
import org.netbeans.modules.csl.api.OffsetRange;
import org.netbeans.modules.csl.spi.GsfUtilities;
import org.netbeans.modules.csl.spi.ParserResult;
import org.openide.filesystems.FileObject;

/**
 *
 * @author jeffrey
 */
public class TSDeclarationFinder implements DeclarationFinder {

    final Language<JsTokenId> language = JsTokenId.javascriptLanguage();

    @Override
    public DeclarationLocation findDeclaration(ParserResult info, int caretOffset) {
        FileObject fileObj = info.getSnapshot().getSource().getFileObject();
        JSONObject quickInfo = (JSONObject) TSService.call("getQuickInfoAtPosition",
                fileObj, caretOffset);
        if (quickInfo == null) {
            return DeclarationLocation.NONE;
        }
        TSElementHandle eh = new TSElementHandle(new OffsetRange(
                ((Number) quickInfo.get("start")).intValue(),
                ((Number) quickInfo.get("end")).intValue()), quickInfo);

        DeclarationLocation allLocs = new DeclarationLocation(fileObj, caretOffset, eh);

        JSONArray defs = (JSONArray) TSService.call("getDefsAtPosition", fileObj, caretOffset);
        if (defs == null) {
            return allLocs;
        }
        for (final JSONObject def: (List<JSONObject>) defs) {
            final String destFileName = (String) def.get("fileName");
            FileObject destFileObj = TSService.findFileObject(destFileName);
            if (destFileObj == null) {
                return DeclarationLocation.NONE;
            }
            int destOffset = ((Number) def.get("start")).intValue();
            final DeclarationLocation declLoc = new DeclarationLocation(destFileObj, destOffset, eh);
            if (defs.size() == 1) {
                return declLoc; // can't use AlternativeLocations when there's only one
            }

            final TSElementHandle handle = new TSElementHandle(OffsetRange.NONE, def);
            allLocs.addAlternative(new DeclarationFinder.AlternativeLocation() {
                @Override
                public ElementHandle getElement() { return handle; }
                @Override
                public String getDisplayHtml(HtmlFormatter hf) {
                    hf.appendText((String) def.get("kind"));
                    hf.appendText(" ");
                    String containerName = (String) def.get("containerName");
                    if (! containerName.isEmpty()) {
                        hf.appendText(containerName);
                        hf.appendText(".");
                    }
                    hf.appendText((String) def.get("name"));
                    hf.appendText(" @ ");
                    hf.appendText(destFileName);
                    hf.appendText(":");
                    hf.appendText(def.get("line").toString());
                    return hf.getText() + " &nbsp;&nbsp;&nbsp;"; // Last word gets cut off...
                }
                @Override
                public DeclarationLocation getLocation() { return declLoc; }
                @Override
                public int compareTo(DeclarationFinder.AlternativeLocation o) { return 0; }
            });
        }
        return allLocs;
    }

    @Override
    public OffsetRange getReferenceSpan(final Document doc, int caretOffset) {
        JSONObject quickInfo = (JSONObject) TSService.call("getQuickInfoAtPosition",
                GsfUtilities.findFileObject(doc), caretOffset);
        if (quickInfo == null) {
            return OffsetRange.NONE;
        }
        return new OffsetRange(
                ((Number) quickInfo.get("start")).intValue(),
                ((Number) quickInfo.get("end")).intValue());
    }
}
