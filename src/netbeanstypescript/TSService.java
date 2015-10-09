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
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.text.Position.Bias;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;
import org.netbeans.lib.editor.util.StringEscapeUtils;
import org.netbeans.modules.csl.api.*;
import org.netbeans.modules.csl.api.DeclarationFinder.DeclarationLocation;
import org.netbeans.modules.csl.spi.DefaultCompletionResult;
import org.netbeans.modules.csl.spi.DefaultError;
import org.netbeans.modules.csl.spi.GsfUtilities;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.parsing.spi.indexing.Context;
import org.netbeans.modules.parsing.spi.indexing.ErrorsCache;
import org.netbeans.modules.parsing.spi.indexing.ErrorsCache.Convertor;
import org.netbeans.modules.parsing.spi.indexing.Indexable;
import org.netbeans.modules.refactoring.spi.RefactoringElementImplementation;
import org.netbeans.modules.refactoring.spi.SimpleRefactoringElementImplementation;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.URLMapper;
import org.openide.modules.InstalledFileLocator;
import org.openide.text.CloneableEditorSupport;
import org.openide.text.PositionBounds;
import org.openide.text.PositionRef;
import org.openide.util.Lookup;

/**
 * 
 * @author jeffrey
 */
public class TSService {

    static final TSService INSTANCE = new TSService();

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

    static class NodeJSProcess {
        OutputStream stdin;
        BufferedReader stdout;
        String error;
        static final String builtinLibPrefix = "(builtin) ";
        Map<String, FileObject> builtinLibs = new HashMap<>();

        NodeJSProcess() throws Exception {
            System.out.println("TSService: starting nodejs");
            File file = InstalledFileLocator.getDefault().locate("nbts-services.js", "netbeanstypescript", false);
            for (String command: new String[] { "nodejs", "node" }) {
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

            System.out.println("TSService: nodejs loaded");
        }

        final Object eval(String code) throws ParseException, ExceptionFromJS {
            if (error != null) {
                return null;
            }
            //System.out.print("OUT[" + code.length() + "]: " + (code.length() > 120 ? code.substring(0, 120) + "...\n" : code));
            //long t1 = System.currentTimeMillis();
            String s;
            try {
                stdin.write(code.getBytes());
                stdin.flush();
                while ((s = stdout.readLine()) != null && s.charAt(0) == 'L') {
                    System.out.println(JSONValue.parseWithException(s.substring(1)));
                }
            } catch (Exception e) {
                error = "Error communicating with Node.js process."
                        + "\n\nClose all TypeScript projects and reopen to retry."
                        + "\n\n" + e;
                return null;
            }
            //System.out.println("IN[" + s.length() + "," + (System.currentTimeMillis() - t1) + "]: "
            //        + (s.length() > 120 ? s.substring(0, 120) + "..." : s));
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

    NodeJSProcess nodejs = null;

    int nextProgId = 0;

    class ProgramData {
        final String progVar = "p" + nextProgId++;
        final Map<String, FileObject> files = new HashMap<>();
        final Map<String, Indexable> indexables = new HashMap<>();
        boolean needErrorsUpdate;

        ProgramData() throws Exception {
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
                e.printStackTrace();
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

    final Map<URL, ProgramData> programs = new HashMap<>();

    static class FileData {
        ProgramData program;
        String relPath;
    }
    final Map<FileObject, FileData> allFiles = new HashMap<>();

    synchronized void addFile(Snapshot snapshot, Indexable indxbl, Context cntxt) {
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

    synchronized void removeFile(Indexable indxbl, Context cntxt) {
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

    synchronized void scanFinished(Context context) {
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
                System.out.println(fileName + ": No indexable!");
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

    synchronized void removeProgram(URL rootURL) {
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
            System.out.println("TSService: no programs left; shutting down nodejs");
            try {
                nodejs.close();
            } catch (IOException e) {}
            nodejs = null;
        }
    }

    synchronized CodeCompletionResult getCompletions(FileObject fileObj, int caretOffset,
            String prefix, boolean isPrefixMatch, boolean caseSensitive) {
        FileData fd = allFiles.get(fileObj);
        if (fd == null) {
            return DefaultCompletionResult.NONE;
        }

        JSONObject info = (JSONObject) fd.program.call("getCompletions", fd.relPath, caretOffset,
                prefix, isPrefixMatch, caseSensitive);
        if (info == null) {
            return CodeCompletionResult.NONE;
        }

        List<CompletionProposal> lst = new ArrayList<>();
        for (Object ent: (JSONArray) info.get("entries")) {
            lst.add(new TSCodeCompletion.TSCompletionProposal(
                    fileObj,
                    caretOffset,
                    caretOffset - prefix.length(),
                    (JSONObject) ent));
        }
        return new DefaultCompletionResult(lst, false);
    }

    synchronized ElementHandle getCompletionEntryDetails(FileObject fileObj, int caretOffset, String name) {
        FileData fd = allFiles.get(fileObj);
        if (fd == null) {
            return null;
        }
        JSONObject info = (JSONObject) fd.program.call("getCompletionEntryDetails", fd.relPath, caretOffset, name);
        return info == null ? null : new TSElementHandle(OffsetRange.NONE, info);
    }

    synchronized void updateFile(Snapshot snapshot) {
        FileData fd = allFiles.get(snapshot.getSource().getFileObject());
        if (fd != null) {
            fd.program.setFileSnapshot(fd.relPath, null, snapshot, true);
        }
    }

    synchronized List<DefaultError> getDiagnostics(Snapshot snapshot) {
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
                    fo, start, start + length, false,
                    category == 0 ? Severity.WARNING : Severity.ERROR));
        }
        return errors;
    }

    synchronized JSONObject getQuickInfo(FileObject fileObj, int caretOffset) {
        FileData fd = allFiles.get(fileObj);
        if (fd == null) {
            return null;
        }
        return (JSONObject) fd.program.call("getQuickInfoAtPosition", fd.relPath, caretOffset);
    }

    synchronized DeclarationLocation findDeclaration(FileObject fileObj, int caretOffset) {
        FileData fd = allFiles.get(fileObj);
        if (fd == null) {
            return DeclarationLocation.NONE;
        }

        JSONObject quickInfo = (JSONObject) fd.program.call("getQuickInfoAtPosition", fd.relPath, caretOffset);
        if (quickInfo == null) {
            return DeclarationLocation.NONE;
        }
        TSElementHandle eh = new TSElementHandle(new OffsetRange(
                ((Number) quickInfo.get("start")).intValue(),
                ((Number) quickInfo.get("end")).intValue()), quickInfo);

        DeclarationLocation allLocs = new DeclarationLocation(fileObj, caretOffset, eh);

        JSONArray defs = (JSONArray) fd.program.call("getDefsAtPosition", fd.relPath, caretOffset);
        if (defs == null) {
            return allLocs;
        }
        for (final JSONObject def: (List<JSONObject>) defs) {
            final String destFileName = (String) def.get("fileName");

            FileObject destFileObj = fd.program.getFile(destFileName);
            int destOffset = ((Number) def.get("start")).intValue();
            final DeclarationLocation declLoc = new DeclarationLocation(destFileObj, destOffset, eh);
            if (defs.size() == 1) {
                return declLoc; // can't use AlternativeLocations when there's only one
            }

            final TSElementHandle handle = new TSElementHandle(OffsetRange.NONE, def);
            allLocs.addAlternative(new DeclarationFinder.AlternativeLocation() {
                @Override
                public ElementHandle getElement() { return handle; }
                @Override
                public String getDisplayHtml(HtmlFormatter hf) {
                    hf.appendText((String) def.get("kind"));
                    hf.appendText(" ");
                    String containerName = (String) def.get("containerName");
                    if (! containerName.isEmpty()) {
                        hf.appendText(containerName);
                        hf.appendText(".");
                    }
                    hf.appendText((String) def.get("name"));
                    hf.appendText(" @ ");
                    hf.appendText(destFileName);
                    hf.appendText(":");
                    hf.appendText(def.get("line").toString());
                    return hf.getText() + " &nbsp;&nbsp;&nbsp;"; // Last word gets cut off...
                }
                @Override
                public DeclarationLocation getLocation() { return declLoc; }
                @Override
                public int compareTo(DeclarationFinder.AlternativeLocation o) { return 0; }
            });
        }
        return allLocs;
    }

    synchronized Map<OffsetRange, ColoringAttributes> findOccurrences(FileObject fileObj, int caretOffset) {
        FileData fd = allFiles.get(fileObj);
        if (fd == null) {
            return Collections.emptyMap();
        }
        JSONArray occurrences = (JSONArray) fd.program.call("getOccurrencesAtPosition", fd.relPath, caretOffset);
        if (occurrences == null) {
            return Collections.emptyMap();
        }
        Map<OffsetRange, ColoringAttributes> ranges = new HashMap<>();
        for (Object o: occurrences) {
            JSONObject occ = (JSONObject) o;
            int start = ((Number) occ.get("start")).intValue();
            int end = ((Number) occ.get("end")).intValue();
            ranges.put(new OffsetRange(start, end), ColoringAttributes.MARK_OCCURRENCES);
        }
        return ranges;
    }

    synchronized Object getSemanticHighlights(FileObject fileObj) {
        FileData fd = allFiles.get(fileObj);
        if (fd == null) {
            return null;
        }
        return fd.program.call("getNetbeansSemanticHighlights", fd.relPath);
    }

    synchronized Object getStructureItems(FileObject fileObj) {
        FileData fd = allFiles.get(fileObj);
        if (fd == null) {
            return null;
        }
        return fd.program.call("getStructureItems", fd.relPath);
    }

    synchronized List<OffsetRange> getFolds(FileObject fileObj) {
        FileData fd = allFiles.get(fileObj);
        if (fd == null) {
            return Collections.emptyList();
        }
        JSONArray arr = (JSONArray) fd.program.call("getFolds", fd.relPath);
        if (arr == null) {
            return Collections.emptyList();
        }
        List<OffsetRange> ranges = new ArrayList<>();
        for (JSONObject span: (List<JSONObject>) arr) {
            ranges.add(new OffsetRange(
                ((Number) span.get("start")).intValue(),
                ((Number) span.get("end")).intValue()));
        }
        return ranges;
    }

    synchronized List<RefactoringElementImplementation> getReferencesAtPosition(FileObject fileObj, int position) {
        FileData fd = allFiles.get(fileObj);
        if (fd == null) {
            return null;
        }
        JSONArray arr = (JSONArray) fd.program.call("getReferencesAtPosition", fd.relPath, position);
        if (arr == null) {
            return null;
        }
        List<RefactoringElementImplementation> uses = new ArrayList<>();
        for (JSONObject use: (List<JSONObject>) arr) {
            String fileName = (String) use.get("fileName");
            final int start = ((Number) use.get("start")).intValue();
            final int end = ((Number) use.get("end")).intValue();

            final int lineStart = ((Number) use.get("lineStart")).intValue();
            final String lineText = (String) use.get("lineText");

            final FileObject useFileObj = fd.program.getFile(fileName);
            if (useFileObj == null) {
                continue;
            }

            CloneableEditorSupport ces = GsfUtilities.findCloneableEditorSupport(useFileObj);
            PositionRef ref1 = ces.createPositionRef(start, Bias.Forward);
            PositionRef ref2 = ces.createPositionRef(end, Bias.Forward);
            final PositionBounds bounds = new PositionBounds(ref1, ref2);

            uses.add(new SimpleRefactoringElementImplementation() {
                @Override
                public String getText() { return toString(); }
                @Override
                public String getDisplayText() {
                    StringBuilder sb = new StringBuilder();
                    sb.append(StringEscapeUtils.escapeHtml(lineText.substring(0, start - lineStart)));
                    sb.append("<b>");
                    sb.append(StringEscapeUtils.escapeHtml(lineText.substring(start - lineStart, end - lineStart)));
                    sb.append("</b>");
                    sb.append(StringEscapeUtils.escapeHtml(lineText.substring(end - lineStart)));
                    return sb.toString();
                }
                @Override
                public void performChange() {}
                @Override
                public Lookup getLookup() { return Lookup.EMPTY; }
                @Override
                public FileObject getParentFile() { return useFileObj; }
                @Override
                public PositionBounds getPosition() { return bounds; }
            });
        }
        return uses;
    }

    synchronized List<JSONObject> getFormattingEdits(FileObject fileObj, int start, int end,
            int indent, int tabSize, boolean expandTabs) {
        FileData fd = allFiles.get(fileObj);
        if (fd == null) {
            return Collections.emptyList();
        }
        Object res = fd.program.call("getFormattingEdits", fd.relPath, start, end,
                indent, tabSize, expandTabs);
        return res != null ? (List<JSONObject>) res : Collections.<JSONObject>emptyList();
    }
}
