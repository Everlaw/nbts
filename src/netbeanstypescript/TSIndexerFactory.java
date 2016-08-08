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

import java.io.IOException;
import java.io.Writer;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Level;
import java.util.prefs.Preferences;
import org.json.simple.JSONObject;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.parsing.spi.indexing.Context;
import org.netbeans.modules.parsing.spi.indexing.CustomIndexer;
import org.netbeans.modules.parsing.spi.indexing.CustomIndexerFactory;
import org.netbeans.modules.parsing.spi.indexing.Indexable;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 * This "indexer" doesn't really index anything, it's just a way to read all the TS files in a
 * project and get notified when they're changed or deleted.
 * @author jeffrey
 */
public class TSIndexerFactory extends CustomIndexerFactory {

    @Override
    public boolean scanStarted(Context context) {
        return false;
    }

    @Override
    public CustomIndexer createIndexer() {
        return new CustomIndexer() {

            @Override
            protected void index(Iterable<? extends Indexable> files, Context context) {
                for (Indexable indxbl: files) {
                    FileObject fo = context.getRoot().getFileObject(indxbl.getRelativePath());
                    if (fo == null) continue;
                    if ("text/typescript".equals(FileUtil.getMIMEType(fo))) {
                        TSService.addFile(Source.create(fo).createSnapshot(), indxbl, context);
                        if (! context.isAllFilesIndexing() && ! context.checkForEditorModifications()) {
                            compileIfEnabled(context.getRoot(), fo);
                        }
                    } else if (fo.getNameExt().equals("tsconfig.json")) {
                        TSService.addFile(Source.create(fo).createSnapshot(), indxbl, context);
                    }
                }
            }
        };
    }

    @Override
    public void scanFinished(Context context) {
        TSService.updateErrors(context.getRootURI());
    }

    @Override
    public boolean supportsEmbeddedIndexers() {
        return false;
    }

    @Override
    public void filesDeleted(Iterable<? extends Indexable> deleted, Context context) {
        for (Indexable i: deleted) {
            TSService.removeFile(i, context);
        }
    }

    @Override
    public void filesDirty(Iterable<? extends Indexable> dirty, Context context) {
    }

    @Override
    public void rootsRemoved(Iterable<? extends URL> removedRoots) {
        for (URL url: removedRoots) {
            TSService.removeProgram(url);
        }
    }

    @Override
    public String getIndexerName() {
        return "typescript";
    }

    @Override
    public int getIndexVersion() {
        return 0;
    }

    private void compileIfEnabled(FileObject root, FileObject fileObject) {
        boolean guiSetting = false;
        Project project = FileOwnerQuery.getOwner(root);
        if (project != null) {
            Preferences prefs = ProjectUtils.getPreferences(project, TSProjectCustomizer.class, true);
            guiSetting = "true".equals(prefs.get("compileOnSave", null));
        }
        TSService.log.log(Level.FINE, "Compiling {0}", fileObject.getPath());
        JSONObject res = (JSONObject) TSService.call("getEmitOutput", fileObject, guiSetting);
        if (res == null) {
            return;
        }
        Path rootPath = Paths.get(root.getPath());
        for (JSONObject file: (List<JSONObject>) res.get("outputFiles")) {
            String name = (String) file.get("name");
            boolean writeBOM = Boolean.TRUE.equals(file.get("writeByteOrderMark"));
            String text = (String) file.get("text");
            Path p = rootPath.resolve(name);
            TSService.log.log(Level.FINE, "Writing {0}", p);
            try {
                Files.createDirectories(p.getParent());
                try (Writer w = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
                    if (writeBOM) w.write('\uFEFF');
                    w.write(text);
                }
            } catch (IOException e) {
                String error = "Compile on save: could not write file " + name + "\n" + e;
                DialogDisplayer.getDefault().notify(
                        new NotifyDescriptor.Message(error, NotifyDescriptor.ERROR_MESSAGE));
                break;
            }
        }
    }
}
