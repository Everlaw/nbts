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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import javax.swing.text.Document;
import netbeanstypescript.api.lexer.JsTokenId;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.netbeans.api.lexer.Token;
import org.netbeans.api.lexer.TokenHierarchy;
import org.netbeans.api.lexer.TokenSequence;
import org.netbeans.modules.csl.api.DeclarationFinder;
import org.netbeans.modules.csl.api.ElementHandle;
import org.netbeans.modules.csl.api.HtmlFormatter;
import org.netbeans.modules.csl.api.OffsetRange;
import org.netbeans.modules.csl.spi.ParserResult;
import org.netbeans.modules.parsing.api.ParserManager;
import org.netbeans.modules.parsing.api.ResultIterator;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.parsing.api.UserTask;
import org.netbeans.modules.parsing.spi.ParseException;
import org.openide.filesystems.FileObject;
import org.openide.util.RequestProcessor;

/**
 *
 * @author jeffrey
 */
public class TSDeclarationFinder implements DeclarationFinder {

    private final RequestProcessor RP = new RequestProcessor(TSDeclarationFinder.class.getName());

    @Override
    public DeclarationLocation findDeclaration(ParserResult info, int caretOffset) {
        FileObject fileObj = info.getSnapshot().getSource().getFileObject();
        JSONArray defs = (JSONArray) TSService.call("getDefsAtPosition", fileObj, caretOffset);
        if (defs == null) {
            return DeclarationLocation.NONE;
        }

        TSElementHandle eh = null;
        JSONObject quickInfo = (JSONObject) TSService.call("getQuickInfoAtPosition", fileObj, caretOffset);
        if (quickInfo != null) {
            eh = new TSElementHandle(new OffsetRange(
                    ((Number) quickInfo.get("start")).intValue(),
                    ((Number) quickInfo.get("end")).intValue()), quickInfo);
        }

        DeclarationLocation allLocs = new DeclarationLocation(fileObj, caretOffset, eh);

        for (final JSONObject def: (List<JSONObject>) defs) {
            final String destFileName = (String) def.get("fileName");
            FileObject destFileObj = TSService.findAnyFileObject(destFileName);
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

    private final Set<String> possibleRefs = new HashSet<>(Arrays.asList(
            "comment", "identifier", "keyword", "string"));

    @Override
    public OffsetRange getReferenceSpan(final Document doc, final int caretOffset) {
        final OffsetRange[] tokenRange = new OffsetRange[1];
        doc.render(new Runnable() {
            @Override public void run() {
                TokenSequence<?> ts = TokenHierarchy.get(doc)
                        .tokenSequence(JsTokenId.javascriptLanguage());
                int offsetWithinToken = ts.move(caretOffset);
                if (ts.moveNext()) {
                    Token<?> tok = ts.token();
                    if (possibleRefs.contains(tok.id().primaryCategory())) {
                        int start = caretOffset - offsetWithinToken;
                        tokenRange[0] = new OffsetRange(start, start + tok.length());
                        return;
                    }
                }
                // If we're right between two tokens, check the previous
                if (offsetWithinToken == 0 && ts.movePrevious()) {
                    Token<?> tok = ts.token();
                    if (possibleRefs.contains(tok.id().primaryCategory())) {
                        tokenRange[0] = new OffsetRange(caretOffset - tok.length(), caretOffset);
                    }
                }
            }
        });
        if (tokenRange[0] == null) {
            return OffsetRange.NONE;
        }

        // Now query the language service to see if this is actually a reference
        final AtomicBoolean isReference = new AtomicBoolean();
        class ReferenceSpanTask extends UserTask implements Runnable {
            @Override
            public void run() {
                try {
                    ParserManager.parse(Collections.singleton(Source.create(doc)), this);
                } catch (ParseException e) {
                    TSService.log.log(Level.WARNING, null, e);
                }
            }
            @Override
            public void run(ResultIterator ri) throws ParseException {
                // Calling ResultIterator#getParserResult() ensures latest snapshot pushed to server
                Object defs = TSService.call("getDefsAtPosition",
                        ri.getParserResult().getSnapshot().getSource().getFileObject(),
                        caretOffset);
                isReference.set(defs != null);
            }
        }
        // Don't block the UI thread for too long in case server is busy
        RequestProcessor.Task task = RP.post(new ReferenceSpanTask());
        try {
            task.waitFinished(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            task.cancel();
        }
        return isReference.get() ? tokenRange[0] : OffsetRange.NONE;
    }
}
