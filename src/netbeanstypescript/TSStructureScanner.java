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

/**
 *
 * @author jeffrey
 */
public class TSStructureScanner implements StructureScanner {

    private static class TSStructureItem extends TSNameKindModifiers implements StructureItem {
        String typeExtends;
        String type;
        int start;
        int end;

        TSStructureItem parent;
        int numOfName;
        List<TSStructureItem> children;

        TSStructureItem(JSONObject item) {
            super(item);
            typeExtends = (String) item.get("extends");
            type = (String) item.get("type");
            start = ((Number) item.get("start")).intValue();
            end = ((Number) item.get("end")).intValue();
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
        public ElementHandle getElementHandle() { return null; }
        @Override
        public boolean isLeaf() { return children.isEmpty(); }
        @Override
        public List<? extends StructureItem> getNestedItems() { return children; }
        @Override
        public long getPosition() { return start; }
        @Override
        public long getEndPosition() { return end; }
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

    private List<TSStructureItem> convertStructureItems(TSStructureItem parent, Object arr) {
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

            item.parent = parent;
            item.numOfName = numOfName;
            item.children = convertStructureItems(item, elem.get("children"));
            items.add(item);
        }
        return items;
    }

    @Override
    public List<? extends StructureItem> scan(ParserResult pr) {
        return convertStructureItems(null, TSService.INSTANCE.getStructureItems(
                pr.getSnapshot().getSource().getFileObject()));
    }

    @Override
    public Map<String, List<OffsetRange>> folds(ParserResult pr) {
        return Collections.singletonMap("codeblocks", TSService.INSTANCE.getFolds(
                pr.getSnapshot().getSource().getFileObject()));
    }

    @Override
    public Configuration getConfiguration() {
        return new Configuration(true, true);
    }    
}
