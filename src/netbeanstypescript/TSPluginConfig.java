package netbeanstypescript;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Arrays;
import java.util.Locale;
import java.util.prefs.Preferences;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import org.netbeans.editor.BaseAction;
import org.netbeans.editor.BaseDocument;
import org.netbeans.spi.lexer.MutableTextInput;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotificationLineSupport;
import org.openide.awt.Mnemonics;
import org.openide.util.NbPreferences;

/**
 *
 * @author jeffrey
 */
public class TSPluginConfig extends BaseAction {

    private static final Preferences PREFS = NbPreferences.forModule(TSPluginConfig.class);
    public static volatile int configGen = 1;

    public TSPluginConfig() {
        super("TypeScript Setup...");
    }

    public static String getLibDir() { return PREFS.get("libDir", ""); }
    public static String getLocale() { return PREFS.get("locale", ""); }

    @Override
    public void actionPerformed(ActionEvent evt, JTextComponent target) {
        if (! new Dialog().show()) return;
        // Reparse current document
        final BaseDocument doc = (BaseDocument) target.getDocument();
        doc.runAtomic(new Runnable() {
            @Override public void run() {
                MutableTextInput mti = (MutableTextInput) doc.getProperty(MutableTextInput.class);
                if (mti != null) {
                    mti.tokenHierarchyControl().rebuild();
                }
            }
        });
    }

    private static class Dialog implements ActionListener, DocumentListener {
        JComponent panel = new Box(BoxLayout.PAGE_AXIS);
        DialogDescriptor dd = new DialogDescriptor(panel, "TypeScript Setup");
        NotificationLineSupport nls = dd.createNotificationLineSupport();
        JTextField dirField = new JTextField();
        JComboBox<TSLocale> locale = new JComboBox<>();

        boolean show() {
            panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

            JComponent dirRow = new JPanel(new BorderLayout(5, 5));
            JLabel dirLabel = new JLabel();
            dirLabel.setPreferredSize(new Dimension(100, 1));
            Mnemonics.setLocalizedText(dirLabel, "Lib &directory:");
            dirRow.add(dirLabel, BorderLayout.LINE_START);
            dirLabel.setLabelFor(dirField);
            dirField.getDocument().addDocumentListener(this);
            dirField.setText(getLibDir());
            dirRow.add(dirField, BorderLayout.CENTER);
            JButton browse = new JButton();
            Mnemonics.setLocalizedText(browse, "&Browse...");
            browse.addActionListener(this);
            dirRow.add(browse, BorderLayout.LINE_END);
            dirRow.setPreferredSize(new Dimension(600, dirField.getPreferredSize().height));
            panel.add(dirRow);
            panel.add(Box.createVerticalStrut(5));

            JComponent localeRow = new JPanel(new BorderLayout(5, 5));
            JLabel localeLabel = new JLabel();
            localeLabel.setPreferredSize(new Dimension(100, 1));
            Mnemonics.setLocalizedText(localeLabel, "&Locale:");
            localeRow.add(localeLabel, BorderLayout.LINE_START);
            localeLabel.setLabelFor(locale);
            localeRow.add(locale, BorderLayout.CENTER);
            localeRow.setPreferredSize(new Dimension(600, locale.getPreferredSize().height));
            panel.add(localeRow);

            if (DialogDisplayer.getDefault().notify(dd) != DialogDescriptor.OK_OPTION) {
                return false;
            }
            PREFS.put("libDir", dirField.getText());
            if (locale.isEnabled()) {
                PREFS.put("locale", ((TSLocale) locale.getSelectedItem()).id);
            }
            configGen++;
            return true;
        }

        @Override public void actionPerformed(ActionEvent e) {
            JFileChooser chooser = new JFileChooser(dirField.getText());
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(dirField) == JFileChooser.APPROVE_OPTION) {
                dirField.setText(chooser.getSelectedFile().toString());
            }
        }

        @Override public void insertUpdate(DocumentEvent e) { update(); }
        @Override public void removeUpdate(DocumentEvent e) { update(); }
        @Override public void changedUpdate(DocumentEvent e) { update(); }

        void update() {
            String dir = dirField.getText();
            if (! new File(dir + "/typescript.js").exists()) {
                locale.setEnabled(false);
                nls.setErrorMessage("Directory must contain typescript.js.");
                return;
            }
            File libDir = new File(dir);
            DefaultComboBoxModel<TSLocale> model = new DefaultComboBoxModel<>();
            TSLocale english = new TSLocale();
            english.id = "";
            english.display = "(default): " + Locale.ENGLISH.getDisplayName();
            model.addElement(english);
            String[] names = libDir.list();
            if (names != null) {
                Arrays.sort(names);
                String currentLocale = getLocale();
                for (String id: names) {
                    if (! new File(libDir, id + "/diagnosticMessages.generated.json").exists()) continue;
                    TSLocale other = new TSLocale();
                    other.id = id;
                    String[] parts = (id + "---x").split("-");
                    Locale javaLocale = new Locale(parts[0], parts[1], parts[2]);
                    other.display = id + ": " + javaLocale.getDisplayName();
                    model.addElement(other);
                    if (id.equals(currentLocale)) model.setSelectedItem(other);
                }
            }
            locale.setModel(model);
            locale.setEnabled(true);
            nls.clearMessages();
        }

        static class TSLocale {
            String id, display;
            @Override public String toString() { return display; }
        }
    }
}
