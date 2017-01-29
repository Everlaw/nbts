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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import javax.swing.text.BadLocationException;
import org.json.simple.JSONObject;
import org.netbeans.modules.csl.api.*;
import org.netbeans.modules.csl.api.Error;
import org.netbeans.modules.editor.indent.api.Reformat;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;

/**
 *
 * @author jeffrey
 */
public class TSHintsProvider implements HintsProvider {

    @Override
    public void computeHints(HintsManager manager, RuleContext context, List<Hint> hints) {}

    @Override
    public void computeSuggestions(HintsManager manager, final RuleContext context, List<Hint> suggestions, int caretOffset) {
        // Group the possibly-fixable errors by span and dedupe, as getCodeFixesAtPosition requires
        LinkedHashMap<OffsetRange, LinkedHashSet<Integer>> errsBySpan = new LinkedHashMap<>();
        for (Error err: context.parserResult.getDiagnostics()) {
            int errStart = err.getStartPosition(), errEnd = err.getEndPosition();
            if (err.getKey() != null && caretOffset >= errStart && caretOffset <= errEnd) {
                OffsetRange span = new OffsetRange(errStart, errEnd);
                LinkedHashSet<Integer> errCodes = errsBySpan.get(span);
                if (errCodes == null) {
                    errsBySpan.put(span, errCodes = new LinkedHashSet<>());
                }
                errCodes.add(Integer.parseInt(err.getKey()));
            }
        }
        for (OffsetRange span: errsBySpan.keySet()) {
            final FileObject fileObj = context.parserResult.getSnapshot().getSource().getFileObject();
            Object fixes = TSService.call("getCodeFixesAtPosition", fileObj, span.getStart(), span.getEnd(),
                    errsBySpan.get(span)); // amazingly, LinkedHashSet<Integer>'s toString is valid JSON
            if (fixes == null) {
                continue;
            }
            List<HintFix> hintFixes = new ArrayList<>();
            final Reformat formatter = Reformat.get(context.doc);
            for (final JSONObject fix: (List<JSONObject>) fixes) {
                hintFixes.add(new HintFix() {
                    @Override
                    public String getDescription() {
                        return (String) fix.get("description");
                    }
                    @Override
                    public void implement() {
                        for (JSONObject change: (List<JSONObject>) fix.get("changes")) {
                            Object fileName = change.get("fileName");
                            if (! fileName.equals(fileObj.getPath())) {
                                String error = "Unimplemented: code fix involves changes to a different file " + fileName;
                                DialogDisplayer.getDefault().notify(
                                        new NotifyDescriptor.Message(error, NotifyDescriptor.ERROR_MESSAGE));
                                return;
                            }
                        }
                        formatter.lock();
                        try {
                            context.doc.runAtomic(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        for (JSONObject change: (List<JSONObject>) fix.get("changes")) {
                                            OffsetRange changed = TSFormatter.applyEdits(context.doc,
                                                    change.get("textChanges"));
                                            // Code fixes are badly formatted, so reformat the affected range
                                            // https://github.com/Microsoft/TypeScript/issues/12249
                                            if (changed != null) {
                                                formatter.reformat(changed.getStart(), changed.getEnd());
                                            }
                                        }
                                    } catch (BadLocationException ex) {
                                        Exceptions.printStackTrace(ex);
                                    }
                                }
                            });
                        } finally {
                            formatter.unlock();
                        }
                    }
                    @Override
                    public boolean isSafe() { return false; }
                    @Override
                    public boolean isInteractive() { return false; }
                });
            }
            if (! hintFixes.isEmpty()) {
                Rule rule = new Rule() {
                    @Override public boolean appliesTo(RuleContext rc) { return true; }
                    @Override public String getDisplayName() { return "TS code fix"; }
                    @Override public boolean showInTasklist() { return false; }
                    @Override public HintSeverity getDefaultSeverity() { return HintSeverity.ERROR; }
                };
                suggestions.add(new Hint(rule,
                        hintFixes.size() + (hintFixes.size() == 1 ? " code fix" : " code fixes") + " available",
                        fileObj, span, hintFixes, 0));
            }
        }
    }

    @Override
    public void computeSelectionHints(HintsManager manager, RuleContext context, List<Hint> suggestions, int start, int end) {}

    private static final Rule tsLintRule = new Rule() {
        @Override
        public boolean appliesTo(RuleContext rc) { return false; }
        @Override
        public String getDisplayName() { return "rule - display name"; }
        @Override
        public boolean showInTasklist() { return false; }
        @Override
        public HintSeverity getDefaultSeverity() { return HintSeverity.WARNING; }
    };

    @Override
    public void computeErrors(HintsManager manager, final RuleContext context, List<Hint> hints, List<Error> unhandled) {
        for (final Error error: context.parserResult.getDiagnostics()) {
            if (error.getParameters() != null) {
                HintFix hf = new HintFix() {
                    @Override
                    public String getDescription() {
                        return "tslint fix: " + (String) error.getParameters()[0];
                    }
                    @Override
                    public void implement() throws Exception {
                        context.doc.runAtomic(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    TSFormatter.applyEdits(context.doc, error.getParameters()[1]);
                                } catch (BadLocationException ex) {
                                    Exceptions.printStackTrace(ex);
                                }
                            }
                        });
                    }
                    @Override
                    public boolean isSafe() { return false; }
                    @Override
                    public boolean isInteractive() { return false; }
                };
                hints.add(new Hint(tsLintRule,
                        error.getDisplayName(),
                        error.getFile(),
                        new OffsetRange(error.getStartPosition(), error.getEndPosition()),
                        Arrays.asList(hf),
                        0));
            } else {
                // There may be relevant code fixes, but we can't query code fixes without fully computing
                // their diffs, which is potentially too expensive to do for all errors in the file.
                // So we provide code fixes as "suggestions" instead.
                unhandled.add(error);
            }
        }
    }

    @Override
    public void cancel() {}

    @Override
    public List<Rule> getBuiltinRules() { return null; }

    @Override
    public RuleContext createRuleContext() { return new RuleContext(); }
}
