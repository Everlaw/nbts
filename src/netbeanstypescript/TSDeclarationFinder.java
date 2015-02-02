package netbeanstypescript;

import javax.swing.text.Document;
import netbeanstypescript.api.lexer.JsTokenId;
import org.json.simple.JSONObject;
import org.netbeans.api.lexer.Language;
import org.netbeans.modules.csl.api.DeclarationFinder;
import org.netbeans.modules.csl.api.OffsetRange;
import org.netbeans.modules.csl.spi.GsfUtilities;
import org.netbeans.modules.csl.spi.ParserResult;

/**
 *
 * @author jeffrey
 */
public class TSDeclarationFinder implements DeclarationFinder {

    final Language<JsTokenId> language = JsTokenId.javascriptLanguage();

    @Override
    public DeclarationLocation findDeclaration(ParserResult info, int caretOffset) {
        return TSService.INSTANCE.findDeclaration(
                info.getSnapshot().getSource().getFileObject(), caretOffset);
    }

    @Override
    public OffsetRange getReferenceSpan(final Document doc, int caretOffset) {
        JSONObject quickInfo = TSService.INSTANCE.getQuickInfo(
                GsfUtilities.findFileObject(doc), caretOffset);
        if (quickInfo == null) {
            return OffsetRange.NONE;
        }
        return new OffsetRange(
                ((Number) quickInfo.get("start")).intValue(),
                ((Number) quickInfo.get("end")).intValue());
    }
}
