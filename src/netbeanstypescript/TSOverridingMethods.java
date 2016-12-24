package netbeanstypescript;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import netbeanstypescript.TSStructureScanner.TSStructureItem;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.netbeans.modules.csl.api.*;
import org.netbeans.modules.csl.api.DeclarationFinder.AlternativeLocation;
import org.netbeans.modules.csl.api.DeclarationFinder.DeclarationLocation;
import org.netbeans.modules.csl.spi.ParserResult;
import org.openide.filesystems.FileObject;

/**
 *
 * @author jeffrey
 */
public class TSOverridingMethods implements OverridingMethods {

    @Override
    public Collection<? extends AlternativeLocation> overrides(ParserResult pr, ElementHandle eh) {
        //System.out.println("checking overrides: " + eh);
        if (eh instanceof TSStructureItem) {
            final TSStructureItem si = (TSStructureItem) eh;
            if (si.overrides != null) {
                ArrayList<AlternativeLocation> list = new ArrayList<>();
                for (Object override: (JSONArray) si.overrides) {
                    JSONObject override0 = (JSONObject) override;
                    final String fileName = (String) override0.get("fileName");
                    final int start = ((Number) override0.get("start")).intValue();
                    final String name = ((String) override0.get("name"));
                    final boolean wasAbstract = (Boolean) override0.get("wasAbstract");
                    list.add(new AlternativeLocation() {
                        @Override
                        public ElementHandle getElement() {
                            return new ElementHandle() {
                                @Override
                                public FileObject getFileObject() { return null; }
                                @Override
                                public String getMimeType() { return null; }
                                @Override
                                public String getName() { return si.name; }
                                @Override
                                public String getIn() { return null; }
                                @Override
                                public ElementKind getKind() { return si.kind; }
                                @Override
                                public Set<Modifier> getModifiers() {
                                    return wasAbstract
                                            ? Collections.singleton(Modifier.ABSTRACT)
                                            : Collections.<Modifier>emptySet();
                                }
                                @Override
                                public boolean signatureEquals(ElementHandle eh) { return false; }
                                @Override
                                public OffsetRange getOffsetRange(ParserResult pr) { return OffsetRange.NONE; }
                            };
                        }
                        @Override
                        public String getDisplayHtml(HtmlFormatter hf) {
                            return name + " in " + fileName;
                        }
                        @Override
                        public DeclarationLocation getLocation() {
                            FileObject destFileObj = TSService.findAnyFileObject(fileName);
                            if (destFileObj == null) {
                                return DeclarationLocation.NONE;
                            }
                            return new DeclarationLocation(destFileObj, start);
                        }
                        @Override
                        public int compareTo(AlternativeLocation o) { return 0; }
                    });
                }
                return list;
            }
        }
        return null;
    }

    @Override
    public boolean isOverriddenBySupported(ParserResult pr, ElementHandle eh) {
        return false;
    }

    @Override
    public Collection<AlternativeLocation> overriddenBy(ParserResult pr, ElementHandle eh) {
        throw new UnsupportedOperationException();
    }
}
