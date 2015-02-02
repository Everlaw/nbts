package netbeanstypescript;

import netbeanstypescript.api.lexer.JsTokenId;
import org.netbeans.api.lexer.Language;
import org.netbeans.core.spi.multiview.MultiViewElement;
import org.netbeans.core.spi.multiview.text.MultiViewEditorElement;
import org.netbeans.modules.csl.api.*;
import org.netbeans.modules.csl.spi.DefaultLanguageConfig;
import org.netbeans.modules.csl.spi.LanguageRegistration;
import org.netbeans.modules.parsing.spi.Parser;
import org.openide.filesystems.MIMEResolver;
import org.openide.util.Lookup;
import org.openide.windows.TopComponent;

/**
 *
 * @author jeffrey
 */
@LanguageRegistration(mimeType="text/typescript", useMultiview=true)
public class TSLanguage extends DefaultLanguageConfig {

    @MIMEResolver.ExtensionRegistration(
            displayName = "TypeScript files",
            mimeType = "text/typescript",
            extension = {"ts"}
    )
    @MultiViewElement.Registration(
            displayName = "Source",
            iconBase = "netbeanstypescript/resources/typescript.png",
            mimeType = "text/typescript",
            persistenceType = TopComponent.PERSISTENCE_ONLY_OPENED,
            preferredID = "TS"
    )
    public static MultiViewEditorElement createEditor(Lookup context) {
        return new MultiViewEditorElement(context);
    }

    @Override
    public String getLineCommentPrefix() { return "//"; }

    @Override
    public Language getLexerLanguage() { return JsTokenId.javascriptLanguage(); }

    @Override
    public String getDisplayName() { return "TypeScript"; }

    @Override
    public Parser getParser() { return new TSParser(); }

    @Override
    public CodeCompletionHandler getCompletionHandler() { return new TSCodeCompletion(); }

    @Override
    public InstantRenamer getInstantRenamer() { return new TSInstantRenamer(); }

    @Override
    public boolean hasStructureScanner() { return true; }
    @Override
    public StructureScanner getStructureScanner() { return new TSStructureScanner(); }
    
    @Override
    public DeclarationFinder getDeclarationFinder() { return new TSDeclarationFinder(); }

    @Override
    public boolean hasOccurrencesFinder() { return true; }
    @Override
    public OccurrencesFinder getOccurrencesFinder() { return new TSOccurrencesFinder(); }

    @Override
    public SemanticAnalyzer getSemanticAnalyzer() { return new TSSemanticAnalyzer(); }
}
