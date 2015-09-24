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

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import javax.swing.ImageIcon;
import org.json.simple.JSONObject;
import org.netbeans.modules.csl.api.ElementKind;
import org.netbeans.modules.csl.api.Modifier;
import org.openide.util.ImageUtilities;

/**
 *
 * @author jeffrey
 */
public class TSNameKindModifiers {
    static final ImageIcon enumIcon = new ImageIcon(ImageUtilities.loadImage(
            "org/netbeans/modules/csl/source/resources/icons/enum.png"));
    static final ImageIcon interfaceIcon = new ImageIcon(ImageUtilities.loadImage(
            "org/netbeans/modules/csl/source/resources/icons/interface.png"));

    String name;
    ElementKind kind = ElementKind.OTHER;
    ImageIcon icon = null;
    Set<Modifier> modifiers = Collections.emptySet();

    TSNameKindModifiers(JSONObject obj) {
        name = (String) obj.get("name");

        // See ScriptElementKind in services/services.ts
        switch ((String) obj.get("kind")) {
            case "keyword": kind = ElementKind.KEYWORD; break;
            case "script": break;
            case "module": kind = ElementKind.MODULE; break;
            case "class": kind = ElementKind.CLASS; break;
            case "interface": case "type": kind = ElementKind.INTERFACE; icon = interfaceIcon; break;
            case "enum": kind = ElementKind.CLASS; icon = enumIcon; break;
            case "var": kind = ElementKind.VARIABLE; break;
            case "local var": kind = ElementKind.VARIABLE; break;
            case "function": kind = ElementKind.METHOD; break;
            case "local function": kind = ElementKind.METHOD; break;
            case "method": kind = ElementKind.METHOD; break;
            case "getter": kind = ElementKind.FIELD; break;
            case "setter": kind = ElementKind.FIELD; break;
            case "property": kind = ElementKind.FIELD; break;
            case "constructor": kind = ElementKind.CONSTRUCTOR; break;
            case "call": break;
            case "index": break;
            case "construct": break;
            case "parameter": kind = ElementKind.PARAMETER; break;
            case "type parameter": break;
            case "primitive type": break;
            case "label": break;
            case "alias": break;
            case "const": kind = ElementKind.CONSTANT; break;
            case "let": kind = ElementKind.VARIABLE; break;
            default: System.out.println("Unknown symbol kind [" + obj.get("kind") + "]");
        }

        // See ScriptElementKindModifier in services/services.ts
        String kindModifiers = (String) obj.get("kindModifiers");
        if (kindModifiers != null && ! kindModifiers.isEmpty()) {
            modifiers = EnumSet.noneOf(Modifier.class);
            for (String modifier: kindModifiers.split(",")) {
                switch (modifier) {
                    case "public": modifiers.add(Modifier.PUBLIC); break;
                    case "private": modifiers.add(Modifier.PRIVATE); break;
                    case "protected": modifiers.add(Modifier.PROTECTED); break;
                    case "export": break;
                    case "declare": break;
                    case "static": modifiers.add(Modifier.STATIC); break;
                    case "abstract": modifiers.add(Modifier.ABSTRACT); break;
                    case "deprecated": modifiers.add(Modifier.DEPRECATED); break;
                    default: System.out.println("Unknown modifier [" + modifier + "]");
                }
            }
        }
    }

    public String getName() { return name; }
    public ElementKind getKind() { return kind; }
    public ImageIcon getIcon() { return icon; }
    public Set<Modifier> getModifiers() { return modifiers; }
}
