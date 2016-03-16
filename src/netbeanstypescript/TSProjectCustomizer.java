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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.prefs.Preferences;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.spi.project.ui.support.ProjectCustomizer;
import org.openide.awt.Mnemonics;
import org.openide.util.Lookup;

/**
 *
 * @author jeffrey
 */
public class TSProjectCustomizer implements ProjectCustomizer.CompositeCategoryProvider {

    // See javascript2.editor/src/org/netbeans/modules/javascript2/editor/api/package-info.java
    @ProjectCustomizer.CompositeCategoryProvider.Registrations({
        @ProjectCustomizer.CompositeCategoryProvider.Registration(
                projectType = "org.netbeans.modules.web.clientproject", // HTML5_CLIENT_PROJECT
                category = "jsframeworks"
        ),
        @ProjectCustomizer.CompositeCategoryProvider.Registration(
                projectType = "org-netbeans-modules-php-project", // PHP_PROJECT
                category = "jsframeworks"
        ),
        @ProjectCustomizer.CompositeCategoryProvider.Registration(
                projectType = "org-netbeans-modules-maven", // MAVEN_PROJECT
                category = "jsframeworks"
        ),
        // Non-Maven web application projects don't have the jsframeworks category
        @ProjectCustomizer.CompositeCategoryProvider.Registration(
                projectType = "org-netbeans-modules-web-project",
                position = 360
        )
    })
    public static TSProjectCustomizer createCustomizer() {
        return new TSProjectCustomizer();
    }

    @Override
    public ProjectCustomizer.Category createCategory(Lookup context) {
        return ProjectCustomizer.Category.create("typescript", "TypeScript", null);
    }

    @Override
    public JComponent createComponent(ProjectCustomizer.Category category, Lookup context) {
        Project project = context.lookup(Project.class);
        final Preferences prefs = ProjectUtils.getPreferences(project, TSProjectCustomizer.class, true);

        JPanel panel = new JPanel();
        panel.add(new JLabel("<html>This setting is deprecated. Use <b>\"compileOnSave\": true</b> in tsconfig.json instead.</html>"));
        final JCheckBox compileOnSave = new JCheckBox((String) null,
                "true".equals(prefs.get("compileOnSave", null)));
        Mnemonics.setLocalizedText(compileOnSave, "&Compile on Save");
        panel.add(compileOnSave);

        category.setStoreListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (compileOnSave.isSelected()) {
                    prefs.put("compileOnSave", "true");
                } else {
                    prefs.remove("compileOnSave");
                }
            }
        });

        return panel;
    }
}
