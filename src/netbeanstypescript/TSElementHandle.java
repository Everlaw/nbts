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
