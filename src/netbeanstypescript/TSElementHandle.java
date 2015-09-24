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
import org.json.simple.JSONObject;
import org.netbeans.modules.csl.api.ElementHandle;
import org.netbeans.modules.csl.api.OffsetRange;
import org.netbeans.modules.csl.spi.ParserResult;
import org.openide.filesystems.FileObject;

/**
 *
 * @author jeffrey
 */
public class TSElementHandle extends TSNameKindModifiers implements ElementHandle {

    OffsetRange textSpan;
    String displayParts;
    String documentation;

    static String symbolDisplayToHTML(Object displayParts) {
        if (displayParts == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (JSONObject part: (List<JSONObject>) displayParts) {
            String text = (String) part.get("text");
            String kind = (String) part.get("kind");
            if (kind.endsWith("Name")) {
                sb.append("<b>");
            }
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                switch (c) {
                    case '<': sb.append("&lt;"); break;
                    case '>': sb.append("&gt;"); break;
                    case '&': sb.append("&amp;"); break;
                    case '\n': sb.append("<br>"); break;
                    case ' ':
                        sb.append(sb.length() == 0 || sb.charAt(sb.length() - 1) == ' ' ? "&nbsp;" : " ");
                        break;
                    default: sb.append(c); break;
                }
            }
            if (kind.endsWith("Name")) {
                sb.append("</b>");
            }
        }
        sb.append("</pre>");
        return sb.toString();
    }

    static String docDisplayToHTML(Object displayParts) {
        if (displayParts == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (JSONObject part: (List<JSONObject>) displayParts) {
            String text = (String) part.get("text");
            String kind = (String) part.get("kind");
            sb.append(text);
        }
        return sb.toString();
    }

    // info may be either CompletionEntryDetails or QuickInfo
    TSElementHandle(OffsetRange textSpan, JSONObject info) {
        super(info);
        this.textSpan = textSpan;
        displayParts = symbolDisplayToHTML(info.get("displayParts"));
        documentation = docDisplayToHTML(info.get("documentation"));
    }

    @Override
    public FileObject getFileObject() { return null; }
    @Override
    public String getMimeType() { return null; }
    @Override
    public String getIn() { return null; }
    @Override
    public boolean signatureEquals(ElementHandle eh) { return false; }
    @Override
    public OffsetRange getOffsetRange(ParserResult pr) { return textSpan; }
}
