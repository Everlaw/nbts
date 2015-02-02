package netbeanstypescript;

import java.awt.event.ActionEvent;
import java.util.Collection;
import javax.swing.event.ChangeListener;
import javax.swing.text.JTextComponent;
import org.netbeans.editor.BaseAction;
import org.netbeans.lib.editor.util.StringEscapeUtils;
import org.netbeans.modules.csl.api.ElementHandle;
import org.netbeans.modules.csl.spi.GsfUtilities;
import org.netbeans.modules.refactoring.api.AbstractRefactoring;
import org.netbeans.modules.refactoring.api.Problem;
import org.netbeans.modules.refactoring.spi.RefactoringElementImplementation;
import org.netbeans.modules.refactoring.spi.RefactoringElementsBag;
import org.netbeans.modules.refactoring.spi.RefactoringPlugin;
import org.netbeans.modules.refactoring.spi.RefactoringPluginFactory;
import org.netbeans.modules.refactoring.spi.SimpleRefactoringElementImplementation;
import org.netbeans.modules.refactoring.spi.ui.CustomRefactoringPanel;
import org.netbeans.modules.refactoring.spi.ui.RefactoringUI;
import org.netbeans.modules.refactoring.spi.ui.UI;
import org.openide.filesystems.FileObject;
import org.openide.text.PositionBounds;
import org.openide.util.HelpCtx;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author jeffrey
 */
public class TSFindUsagesAction extends BaseAction {

    TSFindUsagesAction() { super("Find Usages"); }

    @Override
    public void actionPerformed(ActionEvent event, JTextComponent target) {
        final TSWhereUsedQuery query = new TSWhereUsedQuery(target);

        final ElementHandle decl = TSService.INSTANCE.findDeclaration(query.fileObj, query.caretPosition).getElement();
        final String name = decl != null ? decl.getName() : "(unknown symbol)";

        UI.openRefactoringUI(new RefactoringUI() {
            @Override public String getName() { return "Usages of " + name; }
            @Override public String getDescription() {
                return "Usages of <b>" + StringEscapeUtils.escapeHtml(name) + "</b>";
            }
            @Override public boolean isQuery() { return true; }
            @Override public CustomRefactoringPanel getPanel(ChangeListener cl) { return null; }
            @Override public Problem setParameters() { return null; }
            @Override public Problem checkParameters() { return null; }
            @Override public boolean hasParameters() { return false; }
            @Override public AbstractRefactoring getRefactoring() { return query; }
            @Override public HelpCtx getHelpCtx() { return null; }
        });
    }

    static class TSWhereUsedQuery extends AbstractRefactoring {
        // This method of identifying which symbol to query for isn't ideal: if the file that the
        // "Find Usages" was initiated from changes, then refreshing the results can end up looking
        // for the wrong symbol. Fixing this would probably be more difficult than it's worth.
        FileObject fileObj;
        int caretPosition;
        
        TSWhereUsedQuery(JTextComponent target) {
            super(Lookup.EMPTY);
            this.fileObj = GsfUtilities.findFileObject(target);
            this.caretPosition = target.getCaretPosition();
        }

        class Plugin implements RefactoringPlugin {
            @Override public Problem preCheck() { return null; }
            @Override public Problem checkParameters() { return null; }
            @Override public Problem fastCheckParameters() { return null; }
            @Override public void cancelRequest() {}
            @Override public Problem prepare(RefactoringElementsBag refactoringElements) {
                Collection<RefactoringElementImplementation> uses =
                        TSService.INSTANCE.getReferencesAtPosition(fileObj, caretPosition);
                if (uses == null) {
                    // Doesn't work without dialog?
                    //return new Problem(true, "Could not find symbol at " + fileObj + " offset " + caretPosition);
                    refactoringElements.add(TSWhereUsedQuery.this, new SimpleRefactoringElementImplementation() {
                        @Override public String getText() { return toString(); }
                        @Override public String getDisplayText() {
                            return "Could not resolve symbol at offset " + caretPosition;
                        }
                        @Override public void performChange() {}
                        @Override public Lookup getLookup() { return Lookup.EMPTY; }
                        @Override public FileObject getParentFile() { return fileObj; }
                        @Override public PositionBounds getPosition() { return null; }
                    });
                    return null;
                }
                refactoringElements.addAll(TSWhereUsedQuery.this, uses);
                return null;
            }
        };
    }

    @ServiceProvider(service = RefactoringPluginFactory.class)
    public static class TSRefactoringPluginFactory implements RefactoringPluginFactory {
        @Override
        public RefactoringPlugin createInstance(AbstractRefactoring refactoring) {
            if (refactoring instanceof TSWhereUsedQuery) {
                return ((TSWhereUsedQuery) refactoring).new Plugin();
            }
            return null;
        }
    }
}
