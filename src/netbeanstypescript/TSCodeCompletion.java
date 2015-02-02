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
                ccc.getPrefix());
    }

    @Override
    public String document(ParserResult pr, ElementHandle eh) {
        TSElementHandle teh = (TSElementHandle) eh;
        return teh.displayParts + (teh.documentation.isEmpty() ? "" : "<p>") + teh.documentation;
    }

    @Override
    public ElementHandle resolveLink(String string, ElementHandle eh) {
        System.out.println("@@@ resolveLink");
        return null;
    }

    @Override
    public String getPrefix(ParserResult info, int caretOffset, boolean upToOffset) {
        System.out.println("@@@ getPrefix(info=" + info + ",caret=" + caretOffset + ",upTo=" + upToOffset + ")");
        
        CharSequence seq = info.getSnapshot().getText();
        int i = caretOffset;
        while (i > 0 && Character.isJavaIdentifierPart(seq.charAt(i - 1))) {
            i--;
        }

        String ret = seq.subSequence(i, caretOffset).toString();
        System.out.println("returning " + ret);
        return ret;
    }

    @Override
    public QueryType getAutoQuery(JTextComponent component, String typedText) {
        //System.out.println("@@@ getAutoQuery " + typedText);
        return typedText.endsWith(".") ? QueryType.COMPLETION : QueryType.NONE;
    }

    @Override
    public String resolveTemplateVariable(String string, ParserResult pr, int i, String string1, Map map) {
        System.out.println("@@@ resolveTemplateVariable");
        return null;
    }

    @Override
    public Set<String> getApplicableTemplates(Document dcmnt, int i, int i1) {
        System.out.println("@@@ getApplicableTemplates");
        return null;
    }

    @Override
    public ParameterInfo parameters(ParserResult pr, int i, CompletionProposal cp) {
        System.out.println("@@@ parameters");
        return ParameterInfo.NONE;
    }
    
}
