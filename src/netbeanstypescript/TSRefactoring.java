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

import java.util.Collection;
import javax.swing.event.ChangeListener;
import javax.swing.text.Document;
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
import org.netbeans.modules.refactoring.spi.ui.ActionsImplementationProvider;
import org.netbeans.modules.refactoring.spi.ui.CustomRefactoringPanel;
import org.netbeans.modules.refactoring.spi.ui.RefactoringUI;
import org.netbeans.modules.refactoring.spi.ui.UI;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.text.PositionBounds;
import org.openide.util.HelpCtx;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author jeffrey
 */
@ServiceProvider(service=ActionsImplementationProvider.class)
public class TSRefactoring extends ActionsImplementationProvider {

    @Override
    public boolean canFindUsages(Lookup lookup) {
        EditorCookie cookie = lookup.lookup(EditorCookie.class);
        if (cookie != null) {
            Document doc = cookie.getDocument();
            if (doc != null) {
                FileObject fo = GsfUtilities.findFileObject(doc);
                return fo != null && "text/typescript".equals(fo.getMIMEType());
            }
        }
        return false;
    }

    @Override
    public void doFindUsages(Lookup lookup) {
        final TSWhereUsedQuery query = new TSWhereUsedQuery(lookup.lookup(EditorCookie.class));

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

        TSWhereUsedQuery(EditorCookie ec) {
            super(Lookup.EMPTY);
            this.fileObj = GsfUtilities.findFileObject(ec.getDocument());
            this.caretPosition = ec.getOpenedPanes()[0].getCaretPosition();
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
