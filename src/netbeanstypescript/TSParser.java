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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.event.ChangeListener;
import org.json.simple.JSONObject;
import org.netbeans.modules.csl.api.Error;
import org.netbeans.modules.csl.api.Severity;
import org.netbeans.modules.csl.spi.DefaultError;
import org.netbeans.modules.csl.spi.ParserResult;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.api.Task;
import org.netbeans.modules.parsing.spi.ParseException;
import org.netbeans.modules.parsing.spi.Parser;
import org.netbeans.modules.parsing.spi.SourceModificationEvent;
import org.openide.filesystems.FileObject;

/**
 *
 * @author jeffrey
 */
public class TSParser extends Parser {

    private Result result;

    @Override
    public void parse(Snapshot snapshot, Task task, SourceModificationEvent event) throws ParseException {
        TSService.updateFile(snapshot);
        result = new ParserResult(snapshot) {
            @Override
            public List<? extends Error> getDiagnostics() {
                return diagnostics(getSnapshot().getSource().getFileObject());
            }

            @Override
            protected void invalidate() {}
        };
    }

    @Override
    public Result getResult(Task task) throws ParseException {
        return result;
    }

    private List<DefaultError> diagnostics(FileObject fo) {
        List<String> metaErrors;
        List<JSONObject> normalErrors = Collections.emptyList();
        try {
            JSONObject diags = (JSONObject) TSService.callEx("getDiagnostics", fo);
            metaErrors = (List<String>) diags.get("metaErrors");
            normalErrors = (List<JSONObject>) diags.get("errs");
        } catch (TSService.TSException e) {
            metaErrors = Arrays.asList(e.getMessage());
        }

        List<DefaultError> errors = new ArrayList<>();
        for (String metaError: metaErrors) {
            errors.add(new DefaultError(null, metaError, null, fo, 0, 1, true, Severity.ERROR));
        }
        for (JSONObject err: normalErrors) {
            int start = ((Number) err.get("start")).intValue();
            int length = ((Number) err.get("length")).intValue();
            String messageText = (String) err.get("messageText");
            int category = ((Number) err.get("category")).intValue();
            int code = ((Number) err.get("code")).intValue();
            errors.add(new DefaultError(Integer.toString(code), messageText, null,
                    fo, start, start + length, false,
                    category == 0 ? Severity.WARNING : Severity.ERROR));
        }
        return errors;
    }

    @Override
    public void addChangeListener(ChangeListener cl) {
    }

    @Override
    public void removeChangeListener(ChangeListener cl) {
    }
}
