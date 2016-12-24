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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.swing.ImageIcon;
import org.json.simple.JSONObject;
import org.netbeans.modules.csl.api.*;
import org.netbeans.modules.csl.spi.ParserResult;
import org.openide.filesystems.FileObject;

/**
 *
 * @author jeffrey
 */
public class TSStructureScanner implements StructureScanner {

    static class TSStructureItem extends TSElementHandle implements StructureItem {
        String typeExtends;
        String type;
        Object overrides;

        FileObject fileObj;
        TSStructureItem parent;
        int numOfName;
        List<TSStructureItem> children;

        TSStructureItem(JSONObject item) {
            super(new OffsetRange(((Number) item.get("start")).intValue(),
                                  ((Number) item.get("end")).intValue()),
                    item);
            typeExtends = (String) item.get("extends");
            type = (String) item.get("type");
            overrides = item.get("overrides");
        }

        @Override
        public String getSortText() { return name; }
        @Override
        public String getHtml(HtmlFormatter hf) {
            if (modifiers.contains(Modifier.DEPRECATED)) {
                hf.deprecated(true);
                hf.appendText(name);
                hf.deprecated(false);
            } else {
                hf.appendText(name);
            }
            if (typeExtends != null) {
                hf.appendText(" :: ");
                hf.type(true);
                hf.appendText(typeExtends);
                hf.type(false);
            }
            if (type != null) {
                hf.appendText(" : ");
                hf.type(true);
                hf.appendText(type);
                hf.type(false);
            }
            return hf.getText();
        }
        @Override
        public ElementHandle getElementHandle() { return this; }
        @Override
        public FileObject getFileObject() { return fileObj; }
        @Override
        public String getMimeType() { return "text/typescript"; }
        @Override
        public boolean isLeaf() { return children.isEmpty(); }
        @Override
        public List<? extends StructureItem> getNestedItems() { return children; }
        @Override
        public long getPosition() { return textSpan.getStart(); }
        @Override
        public long getEndPosition() { return textSpan.getEnd(); }
        @Override
        public ImageIcon getCustomIcon() { return getIcon(); }

        @Override
        public boolean equals(Object obj) {
            if (! (obj instanceof TSStructureItem)) return false;
            TSStructureItem left = this;
            TSStructureItem right = (TSStructureItem) obj;
            while (left != right) {
                if (left == null || right == null) return false;
                if (left.kind != right.kind) return false;
                if (! left.name.equals(right.name)) return false;
                if (left.numOfName != right.numOfName) return false;
                left = left.parent;
                right = right.parent;
            }
            return true;
        }
        @Override
        public int hashCode() {
            return Objects.hash(kind, name, numOfName);
        }
    }

    private List<TSStructureItem> convertStructureItems(FileObject fileObj, TSStructureItem parent, Object arr) {
        if (arr == null) {
            return Collections.emptyList();
        }
        List<TSStructureItem> items = new ArrayList<>();
        Map<String, Integer> nameCounts = new HashMap<>();
        for (JSONObject elem: (List<JSONObject>) arr) {
            TSStructureItem item = new TSStructureItem(elem);
            Integer numOfName = nameCounts.get(item.name);
            if (numOfName == null) numOfName = 0;
            nameCounts.put(item.name, numOfName + 1);

            item.fileObj = fileObj;
            item.parent = parent;
            item.numOfName = numOfName;
            item.children = convertStructureItems(fileObj, item, elem.get("children"));
            items.add(item);
        }
        return items;
    }

    @Override
    public List<? extends StructureItem> scan(ParserResult pr) {
        FileObject fo = pr.getSnapshot().getSource().getFileObject();
        return convertStructureItems(fo, null, TSService.call("getStructureItems", fo));
    }

    @Override
    public Map<String, List<OffsetRange>> folds(ParserResult pr) {
        Object arr = TSService.call("getFolds", pr.getSnapshot().getSource().getFileObject());
        if (arr == null) {
            return Collections.emptyMap();
        }
        List<OffsetRange> ranges = new ArrayList<>();
        for (JSONObject span: (List<JSONObject>) arr) {
            ranges.add(new OffsetRange(
                ((Number) span.get("start")).intValue(),
                ((Number) span.get("end")).intValue()));
        }
        return Collections.singletonMap("codeblocks", ranges);
    }

    @Override
    public Configuration getConfiguration() {
        return new Configuration(true, true);
    }    
}
