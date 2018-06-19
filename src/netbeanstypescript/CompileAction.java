/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2017 Everlaw. All rights reserved.
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

import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import org.json.simple.JSONObject;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.netbeans.modules.parsing.api.ParserManager;
import org.netbeans.modules.parsing.api.ResultIterator;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.parsing.api.UserTask;
import org.netbeans.modules.parsing.spi.ParseException;
import org.openide.awt.StatusDisplayer;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;
import org.openide.util.RequestProcessor;
import org.openide.util.Utilities;

/**
 *
 * @author jeffrey
 */
public class CompileAction extends AbstractAction {

    public CompileAction() {
        super("Compile File");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        final Collection<? extends FileObject> fileObjects =
                Utilities.actionsGlobalContext().lookupAll(FileObject.class);
        class CompileTask extends UserTask implements Runnable {
            @Override
            public void run() {
                ProgressHandle progress = ProgressHandleFactory.createHandle("TypeScript compile");
                progress.start();
                try {
                    List<Source> sources = new ArrayList<>(fileObjects.size());
                    for (FileObject fileObj: fileObjects) {
                        sources.add(Source.create(fileObj));
                    }
                    ParserManager.parse(sources, this);
                } catch (ParseException e) {
                    if (e.getCause() instanceof TSService.TSException) {
                        ((TSService.TSException) e.getCause()).notifyLater();
                    } else {
                        Exceptions.printStackTrace(e);
                    }
                } finally {
                    progress.finish();
                }
            }
            @Override
            public void run(ResultIterator ri) throws Exception {
                FileObject fileObj = ri.getParserResult().getSnapshot().getSource().getFileObject();
                writeEmitOutput(fileObj, TSService.callEx("getEmitOutput", fileObj));
            }
        }
        RequestProcessor.getDefault().post(new CompileTask());
    }

    public static void writeEmitOutput(FileObject src, Object res) throws TSService.TSException {
        if (res == null) {
            return;
        }
        for (JSONObject file: (List<JSONObject>) ((JSONObject) res).get("outputFiles")) {
            String name = (String) file.get("name");
            boolean writeBOM = Boolean.TRUE.equals(file.get("writeByteOrderMark"));
            String text = (String) file.get("text");
            TSService.log.log(Level.FINE, "Writing {0}", name);
            // Using the FileObject API instead of direct FS access ensures that the changes
            // show up in the IDE quickly.
            try (Writer w = new OutputStreamWriter(FileUtil.createData(new File(name)).getOutputStream(),
                    StandardCharsets.UTF_8)) {
                if (writeBOM) w.write('\uFEFF');
                w.write(text);
            } catch (IOException e) {
                throw new TSService.TSException("Could not write file " + name + "\n" + e);
            }
        }
        StatusDisplayer.getDefault().setStatusText(src.getNameExt() + " compiled.");
    }
}
