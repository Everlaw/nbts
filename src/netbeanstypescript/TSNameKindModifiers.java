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
            case "const": break;
            case "let": break;
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
