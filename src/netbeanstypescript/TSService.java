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

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.simple.JSONObject;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.parsing.spi.indexing.Context;
import org.netbeans.modules.parsing.spi.indexing.ErrorsCache;
import org.netbeans.modules.parsing.spi.indexing.ErrorsCache.Convertor;
import org.netbeans.modules.parsing.spi.indexing.Indexable;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.URLMapper;
import org.openide.util.Pair;
import org.openide.util.RequestProcessor;

/**
 * 
 * @author jeffrey
 */
public class TSService {

    static final Logger log = Logger.getLogger(TSService.class.getName());
    static final RequestProcessor RP = new RequestProcessor("TSService", 1, true);

    public static class TSException extends Exception {
        public TSException(String msg) { super(msg); }

        public void notifyLater() {
            DialogDisplayer.getDefault().notifyLater(
                    new NotifyDescriptor.Message(getMessage(), NotifyDescriptor.ERROR_MESSAGE));
        }
    }

    static final String builtinLibPrefix = "(builtin)/";
    static final Map<String, FileObject> builtinLibs = new HashMap<>();
    static {
        URL libDirURL = TSService.class.getClassLoader().getResource("netbeanstypescript/lib");
        for (FileObject lib: URLMapper.findFileObject(libDirURL).getChildren()) {
            builtinLibs.put(builtinLibPrefix + lib.getNameExt(), lib);
        }
    }

    // All access to the TSService state below should be done with this lock acquired. This lock
    // has a fair ordering policy so error checking won't starve other user actions.
    private static final Lock lock = new ReentrantLock(true);

    private static TSServiceProcess currentProcess = null;
    private static final Map<URL, ProgramData> programs = new HashMap<>();
    private static final Map<String, FileData> allFiles = new HashMap<>();

    static TSServiceProcess createNodeProcess() {
        TSServiceProcess nodejs = new TSServiceProcess();
        for (Map.Entry<String, FileObject> lib: builtinLibs.entrySet()) {
            nodejs.call("initLib", lib.getKey(),
                    Source.create(lib.getValue()).createSnapshot().getText());
        }
        return nodejs;
    }

    private static class ProgramData {
        final TSServiceProcess process;
        final FileObject root;
        final Map<String, FileData> byRelativePath = new HashMap<>();
        final List<FileObject> needCompileOnSave = new ArrayList<>();
        boolean needErrorsUpdate;
        Object currentErrorsUpdate;

        ProgramData(FileObject root) {
            if (currentProcess == null || ! currentProcess.isValid()) {
                if (currentProcess != null) currentProcess.close();
                currentProcess = createNodeProcess();
            }
            this.process = currentProcess;
            this.root = root;
        }

        final void addFile(FileData fd, Snapshot s, boolean modified) {
            process.call("updateFile", fd.path, s.getText(), modified);
            byRelativePath.put(fd.indexable.getRelativePath(), fd);
            needErrorsUpdate = true;
        }

        String removeFile(Indexable indexable) {
            FileData fd = byRelativePath.remove(indexable.getRelativePath());
            if (fd != null) {
                needErrorsUpdate = true;
                process.call("deleteFile", fd.path);
                return fd.path;
            }
            return null;
        }

        void removeAll() {
            for (FileData fd: byRelativePath.values()) {
                process.call("deleteFile", fd.path);
            }
            byRelativePath.clear();
        }
    }

    private static class FileData {
        ProgramData program;
        FileObject fileObject;
        Indexable indexable;
        String path;
    }

    static void addFiles(List<Pair<Indexable, Snapshot>> files, Context cntxt) {
        lock.lock();
        try {
            URL rootURL = cntxt.getRootURI();

            ProgramData program = programs.get(rootURL);
            if (program == null) {
                program = new ProgramData(cntxt.getRoot());
            }
            programs.put(rootURL, program);

            for (Pair<Indexable, Snapshot> item: files) {
                FileData fi = new FileData();
                fi.program = program;
                fi.fileObject = item.second().getSource().getFileObject();
                fi.indexable = item.first();
                fi.path = fi.fileObject.getPath();
                allFiles.put(fi.path, fi);

                program.addFile(fi, item.second(), cntxt.checkForEditorModifications());
                if (! cntxt.isAllFilesIndexing() && ! cntxt.checkForEditorModifications()) {
                    program.needCompileOnSave.add(fi.fileObject);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    static void removeFiles(Iterable<? extends Indexable> indxbls, Context cntxt) {
        lock.lock();
        try {
            ProgramData program = programs.get(cntxt.getRootURI());
            if (program != null) {
                try {
                    for (Indexable indxbl: indxbls) {
                        String path = program.removeFile(indxbl);
                        allFiles.remove(path);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    static final Convertor<JSONObject> errorConvertor = new Convertor<JSONObject>() {
        @Override
        public ErrorsCache.ErrorKind getKind(JSONObject err) {
            int category = ((Number) err.get("category")).intValue();
            return category == 0 ? ErrorsCache.ErrorKind.WARNING
                                 : ErrorsCache.ErrorKind.ERROR;
        }
        @Override
        public int getLineNumber(JSONObject err) {
            return ((Number) err.get("line")).intValue();
        }
        @Override
        public String getMessage(JSONObject err) {
            return (String) err.get("messageText");
        }
    };

    static void preIndex(URL rootURI) {
        lock.lock();
        try {
            ProgramData program = programs.get(rootURI);
            // Stop errors update task so it doesn't starve indexing. We'll restart it in postIndex.
            if (program != null) {
                program.currentErrorsUpdate = null;
            }
        } finally {
            lock.unlock();
        }
    }

    static void postIndex(final URL rootURI) {
        final ProgramData program;
        final Object currentUpdate;
        final String[] files;
        final FileObject[] compileOnSave;
        lock.lock();
        try {
            program = programs.get(rootURI);
            if (program == null || ! program.needErrorsUpdate) {
                return;
            }
            program.needErrorsUpdate = false;
            program.currentErrorsUpdate = currentUpdate = new Object();
            files = program.byRelativePath.keySet().toArray(new String[0]);
            compileOnSave = program.needCompileOnSave.toArray(new FileObject[0]);
            program.needCompileOnSave.clear();
        } finally {
            lock.unlock();
        }
        new Runnable() {
            RequestProcessor.Task task = RP.create(this);
            ProgressHandle progress = ProgressHandleFactory.createHandle("TypeScript error checking", task);
            @Override
            public void run() {
                TSIndexerFactory.compileIfEnabled(program.root, compileOnSave);
                progress.start(files.length);
                try {
                    long t1 = System.currentTimeMillis();
                    for (int i = 0; i < files.length; i++) {
                        String fileName = files[i];
                        progress.progress(fileName, i);
                        if (fileName.endsWith(".json")) {
                            continue;
                        }
                        lock.lockInterruptibly();
                        try {
                            if (program.currentErrorsUpdate != currentUpdate) {
                                return; // this task has been superseded
                            }
                            FileData fi = program.byRelativePath.get(fileName);
                            if (fi == null) {
                                continue;
                            }
                            JSONObject errors = (JSONObject) program.process.query("getDiagnostics", fi.path);
                            ErrorsCache.setErrors(rootURI, fi.indexable, (List<JSONObject>) errors.get("errs"), errorConvertor);
                        } catch (TSException e) {
                            // leave ErrorsCache unchanged
                        } finally {
                            lock.unlock();
                        }
                    }
                    log.log(Level.FINE, "updateErrors for {0} completed in {1}ms",
                            new Object[] { rootURI, System.currentTimeMillis() - t1 });
                } catch (InterruptedException e) {
                    log.log(Level.INFO, "updateErrors for {0} cancelled by user", rootURI);
                } finally {
                    progress.finish();
                }
            }
        }.task.schedule(0);
    }

    static void removeProgram(URL rootURL) {
        lock.lock();
        try {
            ProgramData program = programs.remove(rootURL);
            if (program == null) {
                return;
            }
            program.currentErrorsUpdate = null; // stop any updateErrors task

            Iterator<FileData> iter = allFiles.values().iterator();
            while (iter.hasNext()) {
                FileData fd = iter.next();
                if (fd.program == program) {
                    iter.remove();
                }
            }

            program.removeAll();

            if (programs.isEmpty()) {
                log.info("No programs left; shutting down nodejs");
                currentProcess.close();
                currentProcess = null;
            }
        } finally {
            lock.unlock();
        }
    }

    static void updateFile(Snapshot snapshot) {
        FileObject fo = snapshot.getSource().getFileObject();
        if (fo == null) {
            return;
        }
        lock.lock();
        try {
            FileData fd = allFiles.get(fo.getPath());
            if (fd != null) {
                fd.program.process.call("updateFile", fd.path, snapshot.getText(), true);
            }
        } finally {
            lock.unlock();
        }
    }

    public static Object callEx(String method, FileObject fileObj, Object... args)
            throws TSException {
        if (fileObj == null) {
            throw new TSException("FileObject is null");
        }
        lock.lock();
        try {
            FileData fd = allFiles.get(fileObj.getPath());
            if (fd == null) {
                throw new TSException("Unknown source root for file " + fileObj.getPath());
            }
            Object[] filenameAndArgs = new Object[args.length + 2];
            filenameAndArgs[0] = method;
            filenameAndArgs[1] = fd.path;
            System.arraycopy(args, 0, filenameAndArgs, 2, args.length);
            return fd.program.process.query(filenameAndArgs);
        } finally {
            lock.unlock();
        }
    }

    public static Object call(String method, FileObject fileObj, Object... args) {
        try {
            return callEx(method, fileObj, args);
        } catch (TSException e) { return null; }
    }

    static FileObject findIndexedFileObject(String path) {
        lock.lock();
        try {
            FileData fd = allFiles.get(path);
            return fd != null ? fd.fileObject : null;
        } finally {
            lock.unlock();
        }
    }

    static FileObject findAnyFileObject(String path) {
        return path.startsWith(builtinLibPrefix)
                ? builtinLibs.get(path)
                : FileUtil.toFileObject(new File(path));
    }
}
