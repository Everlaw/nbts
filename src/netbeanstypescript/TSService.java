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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.netbeans.modules.csl.api.Severity;
import org.netbeans.modules.csl.spi.DefaultError;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.parsing.spi.indexing.Context;
import org.netbeans.modules.parsing.spi.indexing.ErrorsCache;
import org.netbeans.modules.parsing.spi.indexing.ErrorsCache.Convertor;
import org.netbeans.modules.parsing.spi.indexing.Indexable;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.URLMapper;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.RequestProcessor;

/**
 * 
 * @author jeffrey
 */
public class TSService {

    static final Logger log = Logger.getLogger(TSService.class.getName());
    static final RequestProcessor RP = new RequestProcessor("TSService", 1, true);

    private static class ExceptionFromJS extends Exception {
        ExceptionFromJS(String msg) { super(msg); }
    }

    static void stringToJS(StringBuilder sb, CharSequence s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x20) {
                sb.append("\\u");
                for (int j = 12; j >= 0; j -= 4) {
                    sb.append("0123456789ABCDEF".charAt((c >> j) & 0x0F));
                }
            } else {
                if (c == '\\' || c == '"') {
                    sb.append('\\');
                }
                sb.append(c);
            }
        }
        sb.append('"');
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

    private static NodeJSProcess nodejs = null;
    private static final Map<URL, ProgramData> programs = new HashMap<>();
    private static final Map<String, FileData> allFiles = new HashMap<>();

    private static class NodeJSProcess {
        OutputStream stdin;
        BufferedReader stdout;
        String error;
        Set<Integer> supportedCodeFixes = new HashSet<>();

        NodeJSProcess() throws Exception {
            log.info("Starting nodejs");
            File file = InstalledFileLocator.getDefault().locate("nbts-services.js", "netbeanstypescript", false);
            // Node installs to /usr/local/bin on OS X, but OS X doesn't put /usr/local/bin in the
            // PATH of applications started from the GUI
            for (String command: new String[] { "nodejs", "node", "/usr/local/bin/node" }) {
                try {
                    Process process = new ProcessBuilder()
                        .command(command, "--harmony", file.toString())
                        .start();
                    stdin = process.getOutputStream();
                    stdout = new BufferedReader(new InputStreamReader(process.getInputStream(),
                            StandardCharsets.UTF_8));
                    process.getErrorStream().close();
                    error = null;
                    break;
                } catch (Exception e) {
                    error = "Error creating Node.js process. Make sure the \"nodejs\" or \"node\" executable is installed and on your PATH."
                            + "\n\nClose all TypeScript projects and reopen to retry."
                            + "\n\n" + e;
                }
            }

            Object codeFixes = eval("ts.getSupportedCodeFixes()\n");
            if (codeFixes != null) {
                for (String code: (List<String>) codeFixes) {
                    supportedCodeFixes.add(Integer.valueOf(code));
                }
            }

            StringBuilder initLibs = new StringBuilder();
            for (Map.Entry<String, FileObject> lib: builtinLibs.entrySet()) {
                initLibs.append("void(builtinLibs[");
                stringToJS(initLibs, lib.getKey());
                initLibs.append("]=");
                stringToJS(initLibs, Source.create(lib.getValue()).createSnapshot().getText());
                initLibs.append(");");
            }
            eval(initLibs.append('\n').toString());
        }

        final Object eval(String code) throws ParseException, ExceptionFromJS {
            if (error != null) {
                return null;
            }
            log.log(Level.FINER, "OUT[{0}]: {1}", new Object[] {
                code.length(), code.length() > 120 ? code.substring(0, 120) + "...\n" : code});
            long t1 = System.currentTimeMillis();
            String s;
            try {
                stdin.write(code.getBytes(StandardCharsets.UTF_8));
                stdin.flush();
                while ((s = stdout.readLine()) != null && s.charAt(0) == 'L') {
                    log.fine((String) JSONValue.parseWithException(s.substring(1)));
                }
            } catch (Exception e) {
                error = "Error communicating with Node.js process."
                        + "\n\nClose all TypeScript projects and reopen to retry."
                        + "\n\n" + e;
                return null;
            }
            log.log(Level.FINER, "IN[{0},{1}]: {2}\n", new Object[] {
                s.length(), System.currentTimeMillis() - t1,
                s.length() > 120 ? s.substring(0, 120) + "..." : s});
            if (s.charAt(0) == 'X') {
                throw new ExceptionFromJS((String) JSONValue.parseWithException(s.substring(1)));
            } else if (s.equals("undefined")) {
                return null; // JSON parser doesn't like undefined
            } else {
                return JSONValue.parseWithException(s);
            }
        }

        void close() throws IOException {
            if (stdin != null) stdin.close();
            if (stdout != null) stdout.close();
        }
    }

    private static class ProgramData {
        final FileObject root;
        final Map<String, FileData> byRelativePath = new HashMap<>();
        final List<FileObject> needCompileOnSave = new ArrayList<>();
        boolean needErrorsUpdate;
        Object currentErrorsUpdate;

        ProgramData(FileObject root) throws Exception {
            this.root = root;
        }

        Object call(String method, Object... args) {
            StringBuilder sb = new StringBuilder(method).append('(');
            for (Object arg: args) {
                if (sb.charAt(sb.length() - 1) != '(') sb.append(',');
                if (arg instanceof CharSequence) {
                    stringToJS(sb, (CharSequence) arg);
                } else {
                    sb.append(String.valueOf(arg));
                }
            }
            sb.append(")\n");
            try {
                return nodejs.eval(sb.toString());
            } catch (Exception e) {
                log.log(Level.INFO, "Exception in nodejs.eval", e);
                return null;
            }
        }

        final void addFile(FileData fd, Snapshot s, boolean modified) {
            call("updateFile", fd.path, s.getText(), modified);
            byRelativePath.put(fd.indexable.getRelativePath(), fd);
            needErrorsUpdate = true;
        }

        String removeFile(Indexable indexable) throws Exception {
            FileData fd = byRelativePath.remove(indexable.getRelativePath());
            if (fd != null) {
                needErrorsUpdate = true;
                call("deleteFile", fd.path);
                return fd.path;
            }
            return null;
        }

        void removeAll() {
            for (FileData fd: byRelativePath.values()) {
                call("deleteFile", fd.path);
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

    static void addFile(Snapshot snapshot, Indexable indxbl, Context cntxt) {
        lock.lock();
        try {
            URL rootURL = cntxt.getRootURI();

            ProgramData program = programs.get(rootURL);
            if (program == null) {
                if (nodejs == null) {
                    nodejs = new NodeJSProcess();
                }
                program = new ProgramData(cntxt.getRoot());
            }
            programs.put(rootURL, program);

            FileData fi = new FileData();
            fi.program = program;
            fi.fileObject = snapshot.getSource().getFileObject();
            fi.indexable = indxbl;
            fi.path = fi.fileObject.getPath();
            allFiles.put(fi.path, fi);

            program.addFile(fi, snapshot, cntxt.checkForEditorModifications());
            if (! cntxt.isAllFilesIndexing() && ! cntxt.checkForEditorModifications()) {
                program.needCompileOnSave.add(fi.fileObject);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }

    static void removeFile(Indexable indxbl, Context cntxt) {
        lock.lock();
        try {
            ProgramData program = programs.get(cntxt.getRootURI());
            if (program != null) {
                try {
                    String path = program.removeFile(indxbl);
                    allFiles.remove(path);
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
                            JSONObject errors = (JSONObject) program.call("fileCall", "getDiagnostics", fi.path);
                            if (errors != null) {
                                ErrorsCache.setErrors(rootURI, fi.indexable,
                                        (List<JSONObject>) errors.get("errs"), errorConvertor);
                            }
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
                try {
                    nodejs.close();
                } catch (IOException e) {}
                nodejs = null;
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
                fd.program.call("updateFile", fd.path, snapshot.getText(), true);
            }
        } finally {
            lock.unlock();
        }
    }

    static List<DefaultError> getDiagnostics(Snapshot snapshot) {
        FileObject fo = snapshot.getSource().getFileObject();
        if (fo == null) {
            return Arrays.asList(new DefaultError(null, "FileObject is null",
                    null, fo, 0, 1, true, Severity.ERROR));
        }
        lock.lock();
        try {
            FileData fd = allFiles.get(fo.getPath());
            if (fd == null) {
                return Arrays.asList(new DefaultError(null,
                    "Unknown source root for file " + fo.getPath(),
                    null, fo, 0, 1, true, Severity.ERROR));
            }

            JSONObject diags = (JSONObject) fd.program.call("fileCall", "getDiagnostics", fd.path);
            if (diags == null) {
                return Arrays.asList(new DefaultError(null,
                    nodejs.error != null ? nodejs.error : "Error in getDiagnostics",
                    null, fo, 0, 1, true, Severity.ERROR));
            }

            List<DefaultError> errors = new ArrayList<>();
            for (String metaError: (List<String>) diags.get("metaErrors")) {
                errors.add(new DefaultError(null, metaError, null, fo, 0, 1, true, Severity.ERROR));
            }
            for (JSONObject err: (List<JSONObject>) diags.get("errs")) {
                int start = ((Number) err.get("start")).intValue();
                int length = ((Number) err.get("length")).intValue();
                String messageText = (String) err.get("messageText");
                int category = ((Number) err.get("category")).intValue();
                int code = ((Number) err.get("code")).intValue();
                boolean fix = nodejs.supportedCodeFixes.contains(code);
                errors.add(new DefaultError(fix ? Integer.toString(code) : null, messageText, null,
                        fo, start, start + length, false,
                        category == 0 ? Severity.WARNING : Severity.ERROR));
            }
            return errors;
        } finally {
            lock.unlock();
        }
    }

    public static Object call(String method, FileObject fileObj, Object... args) {
        if (fileObj == null) {
            return null;
        }
        lock.lock();
        try {
            FileData fd = allFiles.get(fileObj.getPath());
            if (fd == null) {
                return null;
            }
            Object[] filenameAndArgs = new Object[args.length + 2];
            filenameAndArgs[0] = method;
            filenameAndArgs[1] = fd.path;
            System.arraycopy(args, 0, filenameAndArgs, 2, args.length);
            return fd.program.call("fileCall", filenameAndArgs);
        } finally {
            lock.unlock();
        }
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
