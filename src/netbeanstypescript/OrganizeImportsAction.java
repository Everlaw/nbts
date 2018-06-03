package netbeanstypescript;

import java.awt.event.ActionEvent;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.text.JTextComponent;
import org.json.simple.JSONObject;
import org.netbeans.api.editor.EditorActionNames;
import org.netbeans.api.editor.EditorActionRegistration;
import org.netbeans.api.progress.ProgressUtils;
import org.netbeans.editor.BaseAction;
import org.netbeans.editor.BaseDocument;
import org.netbeans.editor.Utilities;
import org.netbeans.modules.parsing.api.ParserManager;
import org.netbeans.modules.parsing.api.ResultIterator;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.parsing.api.UserTask;
import org.netbeans.modules.parsing.spi.ParseException;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;

/**
 *
 * @author jeffrey
 */
@EditorActionRegistration(name=EditorActionNames.organizeImports, mimeType="text/typescript")
public class OrganizeImportsAction extends BaseAction {

    @Override
    public void actionPerformed(ActionEvent e, JTextComponent target) {
        final BaseDocument doc = Utilities.getDocument(target);
        if (doc == null) return;
        final AtomicBoolean cancel = new AtomicBoolean();
        class OrganizeImportsTask extends UserTask implements Runnable {
            @Override
            public void run() {
                try {
                    ParserManager.parse(Collections.singleton(Source.create(doc)), this);
                } catch (ParseException e) {
                    Exceptions.printStackTrace(e);
                }
            }
            @Override
            public void run(ResultIterator ri) throws ParseException {
                FileObject fileObj = ri.getParserResult().getSnapshot().getSource().getFileObject();
                try {
                    Object changes = TSService.callEx("organizeImports", fileObj,
                           TSFormatter.getFormattingSettings(doc));
                    if (changes == null || cancel.get()) return;
                    TSHintsProvider.doFixes(doc, fileObj, (List<JSONObject>) changes);
                } catch (TSService.TSException e) {
                    e.notifyLater();
                }
            }
        }
        ProgressUtils.runOffEventDispatchThread(new OrganizeImportsTask(),
                "Organize Imports", cancel, false);
    }
}
