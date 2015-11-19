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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;
import org.netbeans.modules.csl.api.Severity;
import org.netbeans.modules.csl.spi.DefaultError;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.parsing.spi.indexing.Context;
import org.netbeans.modules.parsing.spi.indexing.ErrorsCache;
import org.netbeans.modules.parsing.spi.indexing.ErrorsCache.Convertor;
import org.netbeans.modules.parsing.spi.indexing.Indexable;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.URLMapper;
import org.openide.modules.InstalledFileLocator;

/**
 * 
 * @author jeffrey
 */
public class TSService {

    static final Logger log = Logger.getLogger(TSService.class.getName());

    private static class ExceptionFromJS extends Exception {
        ExceptionFromJS(String msg) { super(msg); }
    }

    static void stringToJS(StringBuilder sb, CharSequence s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x20 || c > 0x7E) {
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

    private static class NodeJSProcess {
        OutputStream stdin;
        BufferedReader stdout;
        String error;
        static final String builtinLibPrefix = "(builtin) ";
        Map<String, FileObject> builtinLibs = new HashMap<>();
        int nextProgId = 0;

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
                    stdout = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    process.getErrorStream().close();
                    error = null;
                    break;
                } catch (Exception e) {
                    error = "Error creating Node.js process. Make sure the \"nodejs\" or \"node\" executable is installed and on your PATH."
                            + "\n\nClose all TypeScript projects and reopen to retry."
                            + "\n\n" + e;
                }
            }

            StringBuilder initLibs = new StringBuilder();
            for (String lib: new String[] { "lib.d.ts", "lib.es6.d.ts" }) {
                initLibs.append("void(builtinLibs[");
                stringToJS(initLibs, builtinLibPrefix + lib);
                initLibs.append("]=");
                URL libURL = TSService.class.getClassLoader().getResource("netbeanstypescript/resources/" + lib);
                FileObject libObj = URLMapper.findFileObject(libURL);
                stringToJS(initLibs, Source.create(libObj).createSnapshot().getText());
                initLibs.append(");");
                builtinLibs.put(builtinLibPrefix + lib, libObj);
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
                stdin.write(code.getBytes());
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

    private static NodeJSProcess nodejs = null;

    private static class ProgramData {
        final String progVar;
        final Map<String, FileObject> files = new HashMap<>();
        final Map<String, Indexable> indexables = new HashMap<>();
        boolean needErrorsUpdate;

        ProgramData() throws Exception {
            progVar = "p" + nodejs.nextProgId++;
            nodejs.eval(progVar + " = new Program()\n");
        }

        Object call(String method, Object... args) {
            StringBuilder sb = new StringBuilder(progVar).append('.').append(method).append('(');
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

        final void setFileSnapshot(String relPath, Indexable indexable, Snapshot s, boolean modified) {
            needErrorsUpdate = true;
            call("updateFile", relPath, s.getText(), modified);
            files.put(relPath, s.getSource().getFileObject());
            if (indexable != null) {
                indexables.put(relPath, indexable);
            }
        }

        FileObject getFile(String relPath) {
            return (relPath.startsWith(NodeJSProcess.builtinLibPrefix) ? nodejs.builtinLibs : files).get(relPath);
        }

        FileObject removeFile(String relPath) throws Exception {
            FileObject fileObj = files.remove(relPath);
            if (fileObj != null) {
                needErrorsUpdate = true;
                call("deleteFile", relPath);
            }
            indexables.remove(relPath);
            return fileObj;
        }

        void dispose() throws Exception {
            nodejs.eval("delete " + progVar + "\n");
        }
    }

    private static final Map<URL, ProgramData> programs = new HashMap<>();

    private static class FileData {
        ProgramData program;
        String relPath;
    }
    private static final Map<FileObject, FileData> allFiles = new HashMap<>();

    static synchronized void addFile(Snapshot snapshot, Indexable indxbl, Context cntxt) {
        try {
            URL rootURL = cntxt.getRootURI();

            ProgramData program = programs.get(rootURL);
            if (program == null) {
                if (nodejs == null) {
                    nodejs = new NodeJSProcess();
                }
                program = new ProgramData();
            }
            programs.put(rootURL, program);

            FileData fi = new FileData();
            fi.program = program;
            fi.relPath = indxbl.getRelativePath();
            allFiles.put(snapshot.getSource().getFileObject(), fi);

            program.setFileSnapshot(fi.relPath, indxbl, snapshot, cntxt.checkForEditorModifications());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static synchronized void removeFile(Indexable indxbl, Context cntxt) {
        ProgramData program = programs.get(cntxt.getRootURI());
        if (program != null) {
            try {
                FileObject fileObj = program.removeFile(indxbl.getRelativePath());
                allFiles.remove(fileObj);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    static synchronized void scanFinished(Context context) {
        ProgramData program = programs.get(context.getRootURI());
        if (program == null || ! program.needErrorsUpdate) {
            return;
        }
        program.needErrorsUpdate = false;
        // TODO: this is slow and locks out other TSService usage for too long in big projects
        JSONObject diags = (JSONObject) program.call("getAllDiagnostics");
        if (diags == null) {
            return;
        }
        for (String fileName: (Set<String>) diags.keySet()) {
            JSONArray errors = (JSONArray) diags.get(fileName);
            Indexable i = program.indexables.get(fileName);
            if (i == null) {
                log.log(Level.WARNING, "{0}: No indexable!", fileName);
                continue;
            }
            ErrorsCache.setErrors(context.getRootURI(), i, errors, new Convertor<JSONObject>() {
                @Override
                public ErrorsCache.ErrorKind getKind(JSONObject err) {
                    int category = ((Number) err.get("category")).intValue();
                    if (category == 0) {
                        return ErrorsCache.ErrorKind.WARNING;
                    } else {
                        return ErrorsCache.ErrorKind.ERROR;
                    }
                }
                @Override
                public int getLineNumber(JSONObject err) {
                    return ((Number) err.get("line")).intValue();
                }
                @Override
                public String getMessage(JSONObject err) {
                    return (String) err.get("messageText");
                }
            });
        }
    }

    static synchronized void removeProgram(URL rootURL) {
        ProgramData program = programs.remove(rootURL);
        if (program == null) {
            return;
        }

        Iterator<FileData> iter = allFiles.values().iterator();
        while (iter.hasNext()) {
            FileData fd = iter.next();
            if (fd.program == program) {
                iter.remove();
            }
        }

        try {
            program.dispose();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (programs.isEmpty()) {
            log.info("No programs left; shutting down nodejs");
            try {
                nodejs.close();
            } catch (IOException e) {}
            nodejs = null;
        }
    }

    static synchronized void updateFile(Snapshot snapshot) {
        FileData fd = allFiles.get(snapshot.getSource().getFileObject());
        if (fd != null) {
            fd.program.setFileSnapshot(fd.relPath, null, snapshot, true);
        }
    }

    static synchronized List<DefaultError> getDiagnostics(Snapshot snapshot) {
        FileObject fo = snapshot.getSource().getFileObject();
        FileData fd = allFiles.get(fo);
        if (fd == null) {
            return Arrays.asList(new DefaultError(null, 
                "Unknown source root for file " + fo.getPath(),
                null, fo, 0, 1, true, Severity.ERROR));
        }

        JSONArray diags = (JSONArray) fd.program.call("getDiagnostics", fd.relPath);
        if (diags == null) {
            return Arrays.asList(new DefaultError(null,
                nodejs.error != null ? nodejs.error : "Error in getDiagnostics",
                null, fo, 0, 1, true, Severity.ERROR));
        }

        List<DefaultError> errors = new ArrayList<>();
        for (JSONObject err: (List<JSONObject>) diags) {
            int start = ((Number) err.get("start")).intValue();
            int length = ((Number) err.get("length")).intValue();
            String messageText = (String) err.get("messageText");
            int category = ((Number) err.get("category")).intValue();
            //int code = ((Number) err.get("code")).intValue();
            errors.add(new DefaultError(null, messageText, null,
                    fo, start, start + length, category < 0,
                    category == 0 ? Severity.WARNING : Severity.ERROR));
        }
        return errors;
    }

    static synchronized Object call(String method, FileObject fileObj, Object... args) {
        FileData fd = allFiles.get(fileObj);
        if (fd == null) {
            return null;
        }
        Object[] filenameAndArgs = new Object[args.length + 1];
        filenameAndArgs[0] = fd.relPath;
        System.arraycopy(args, 0, filenameAndArgs, 1, args.length);
        Object ret = fd.program.call(method, filenameAndArgs);
        // Translate file names back to file objects
        if (ret instanceof JSONArray) {
            for (Object item: (JSONArray) ret) {
                if (item instanceof JSONObject) {
                    JSONObject obj = (JSONObject) item;
                    Object fileName = obj.get("fileName");
                    if (fileName instanceof String) {
                        obj.put("fileObject", fd.program.getFile((String) fileName));
                    }
                }
            }
        }
        return ret;
    }
}
