package cpc.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Custom dialog with modern toggle switches, section headers, dropdowns,
 * text fields, file choosers, and collapsible groups.
 * Adapted from PipelineDialog in the IHF Analysis Pipeline plugin.
 */
public class CPCDialog {

    private static final Color BG_COLOR = new Color(245, 245, 245);
    private static final Color HEADER_COLOR = new Color(55, 71, 79);
    private static final Color LABEL_COLOR = new Color(33, 33, 33);
    private static final Color HELP_COLOR = new Color(117, 117, 117);

    private final JDialog dialog;
    private final JPanel contentPanel;
    private JPanel activeTarget;
    private boolean wasCanceled = true;
    private boolean wasBackPressed = false;

    private final List<ToggleSwitch> toggles = new ArrayList<ToggleSwitch>();
    private final List<JTextField> textFields = new ArrayList<JTextField>();
    private final List<JComboBox<String>> combos = new ArrayList<JComboBox<String>>();
    private final List<JTextField> numericFields = new ArrayList<JTextField>();

    private final JPanel leftButtonPanel;
    private final JButton backButton;
    private Runnable onOK;

    private int toggleIndex = 0;
    private int textFieldIndex = 0;
    private int comboIndex = 0;
    private int numericFieldIndex = 0;

    public CPCDialog(String title) {
        dialog = new JDialog((Frame) null, title, true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBorder(new EmptyBorder(15, 20, 10, 20));
        contentPanel.setBackground(BG_COLOR);
        activeTarget = contentPanel;

        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(BG_COLOR);
        dialog.getContentPane().setLayout(new BorderLayout());
        dialog.getContentPane().add(scrollPane, BorderLayout.CENTER);

        JPanel buttonBar = new JPanel(new BorderLayout());
        buttonBar.setBackground(BG_COLOR);

        leftButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        leftButtonPanel.setBackground(BG_COLOR);

        JPanel rightButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        rightButtonPanel.setBackground(BG_COLOR);
        backButton = new JButton("Back");
        JButton okBtn = new JButton("OK");
        JButton cancelBtn = new JButton("Cancel");
        backButton.setPreferredSize(new Dimension(80, 28));
        okBtn.setPreferredSize(new Dimension(80, 28));
        cancelBtn.setPreferredSize(new Dimension(80, 28));
        backButton.addActionListener(e -> { wasBackPressed = true; wasCanceled = true; dialog.dispose(); });
        okBtn.addActionListener(e -> {
            wasCanceled = false;
            if (onOK != null) {
                dialog.dispose();
                onOK.run();
            } else {
                dialog.dispose();
            }
        });
        cancelBtn.addActionListener(e -> { wasCanceled = true; dialog.dispose(); });
        backButton.setVisible(false);
        rightButtonPanel.add(backButton);
        rightButtonPanel.add(cancelBtn);
        rightButtonPanel.add(okBtn);

        buttonBar.add(leftButtonPanel, BorderLayout.WEST);
        buttonBar.add(rightButtonPanel, BorderLayout.EAST);
        dialog.getContentPane().add(buttonBar, BorderLayout.SOUTH);
    }

    // ── Group support ──────────────────────────────────────────────

    /**
     * Begins a collapsible group. All subsequent add* calls go into this group
     * until {@link #endGroup()} is called. The returned panel can be shown/hidden.
     */
    public JPanel beginGroup() {
        JPanel group = new JPanel();
        group.setLayout(new BoxLayout(group, BoxLayout.Y_AXIS));
        group.setAlignmentX(Component.LEFT_ALIGNMENT);
        group.setOpaque(false);
        activeTarget = group;
        return group;
    }

    /** Ends the current group and adds it to the content panel. */
    public void endGroup() {
        if (activeTarget != contentPanel) {
            contentPanel.add(activeTarget);
            activeTarget = contentPanel;
        }
    }

    /** Re-packs the dialog after showing/hiding groups. */
    public void repack() {
        dialog.pack();
        Dimension pref = dialog.getPreferredSize();
        int maxH = (int) (Toolkit.getDefaultToolkit().getScreenSize().height * 0.8);
        if (pref.height > maxH) {
            dialog.setSize(pref.width + 30, maxH);
        }
        dialog.setLocationRelativeTo(null);
    }

    // ── Content methods ────────────────────────────────────────────

    /** Adds a bold section header with separator. */
    public void addHeader(String text) {
        activeTarget.add(Box.createVerticalStrut(10));
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 13f));
        label.setForeground(HEADER_COLOR);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        activeTarget.add(label);
        activeTarget.add(Box.createVerticalStrut(4));

        JSeparator sep = new JSeparator(SwingConstants.HORIZONTAL);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep.setAlignmentX(Component.LEFT_ALIGNMENT);
        activeTarget.add(sep);
        activeTarget.add(Box.createVerticalStrut(6));
    }

    /** Adds a labeled toggle switch. */
    public ToggleSwitch addToggle(String label, boolean defaultValue) {
        ToggleSwitch toggle = new ToggleSwitch(defaultValue);
        toggles.add(toggle);

        JPanel row = createRow();
        JLabel lbl = new JLabel(label);
        lbl.setFont(lbl.getFont().deriveFont(Font.PLAIN, 12f));
        lbl.setForeground(LABEL_COLOR);
        row.add(lbl);
        row.add(Box.createHorizontalGlue());
        row.add(toggle);

        activeTarget.add(row);
        activeTarget.add(Box.createVerticalStrut(4));
        return toggle;
    }

    /** Adds small explanatory text below the previous element. */
    public JLabel addHelpText(String text) {
        JLabel help = new JLabel("<html><body style='width:280px;'>" + text + "</body></html>");
        help.setFont(help.getFont().deriveFont(Font.ITALIC, 10f));
        help.setForeground(HELP_COLOR);
        help.setAlignmentX(Component.LEFT_ALIGNMENT);
        help.setBorder(new EmptyBorder(0, 24, 2, 0));
        activeTarget.add(help);
        activeTarget.add(Box.createVerticalStrut(2));
        return help;
    }

    /** Adds a labeled text input field. */
    public JTextField addStringField(String label, String defaultValue, int columns) {
        JPanel row = createRow();
        JLabel lbl = new JLabel(label);
        lbl.setFont(lbl.getFont().deriveFont(Font.PLAIN, 12f));
        lbl.setForeground(LABEL_COLOR);
        row.add(lbl);
        row.add(Box.createHorizontalGlue());
        JTextField tf = new JTextField(defaultValue, columns);
        tf.setMaximumSize(new Dimension(columns * 12, 24));
        row.add(tf);

        textFields.add(tf);
        activeTarget.add(row);
        activeTarget.add(Box.createVerticalStrut(4));
        return tf;
    }

    /** Adds a labeled dropdown. */
    public JComboBox<String> addChoice(String label, String[] items, String defaultItem) {
        JPanel row = createRow();
        JLabel lbl = new JLabel(label);
        lbl.setFont(lbl.getFont().deriveFont(Font.PLAIN, 12f));
        lbl.setForeground(LABEL_COLOR);
        row.add(lbl);
        row.add(Box.createHorizontalGlue());
        JComboBox<String> combo = new JComboBox<String>(items);
        if (defaultItem != null) combo.setSelectedItem(defaultItem);
        combo.setMaximumSize(new Dimension(280, 24));
        row.add(combo);

        combos.add(combo);
        activeTarget.add(row);
        activeTarget.add(Box.createVerticalStrut(4));
        return combo;
    }

    /** Adds a file path field with browse button. */
    public JTextField addFileField(String label, String defaultPath) {
        return addFileField(label, defaultPath,
                new FileNameExtensionFilter("ROI Set (*.zip)", "zip"));
    }

    /** Adds a file path field with browse button and custom file filter. */
    public JTextField addFileField(String label, String defaultPath,
                                   FileNameExtensionFilter filter) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        row.setBorder(new EmptyBorder(0, 4, 0, 4));

        JLabel lbl = new JLabel(label);
        lbl.setFont(lbl.getFont().deriveFont(Font.PLAIN, 12f));
        lbl.setForeground(LABEL_COLOR);
        row.add(lbl);
        row.add(Box.createHorizontalGlue());

        JTextField tf = new JTextField(defaultPath, 18);
        tf.setMaximumSize(new Dimension(200, 24));
        row.add(tf);
        row.add(Box.createHorizontalStrut(4));

        JButton browse = new JButton("...");
        browse.setPreferredSize(new Dimension(28, 24));
        browse.setMaximumSize(new Dimension(28, 24));
        browse.setFocusPainted(false);
        browse.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            if (filter != null) fc.setFileFilter(filter);
            if (fc.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                tf.setText(fc.getSelectedFile().getAbsolutePath());
            }
        });
        row.add(browse);

        textFields.add(tf);
        activeTarget.add(row);
        activeTarget.add(Box.createVerticalStrut(4));
        return tf;
    }

    /** Adds a directory path field with browse button (folder chooser). */
    public JTextField addDirectoryField(String label, String defaultPath) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        row.setBorder(new EmptyBorder(0, 4, 0, 4));

        JLabel lbl = new JLabel(label);
        lbl.setFont(lbl.getFont().deriveFont(Font.PLAIN, 12f));
        lbl.setForeground(LABEL_COLOR);
        row.add(lbl);
        row.add(Box.createHorizontalGlue());

        JTextField tf = new JTextField(defaultPath, 18);
        tf.setMaximumSize(new Dimension(200, 24));
        row.add(tf);
        row.add(Box.createHorizontalStrut(4));

        JButton browse = new JButton("...");
        browse.setPreferredSize(new Dimension(28, 24));
        browse.setMaximumSize(new Dimension(28, 24));
        browse.setFocusPainted(false);
        browse.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (defaultPath != null && !defaultPath.isEmpty()) {
                fc.setCurrentDirectory(new File(defaultPath));
            }
            if (fc.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                tf.setText(fc.getSelectedFile().getAbsolutePath());
            }
        });
        row.add(browse);

        textFields.add(tf);
        activeTarget.add(row);
        activeTarget.add(Box.createVerticalStrut(4));
        return tf;
    }

    /** Adds a plain text message. */
    public JLabel addMessage(String text) {
        JLabel label = new JLabel("<html><body style='width:280px;'>" + text + "</body></html>");
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 11f));
        label.setForeground(LABEL_COLOR);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(0, 4, 2, 0));
        activeTarget.add(label);
        activeTarget.add(Box.createVerticalStrut(4));
        return label;
    }

    /** Adds a labeled numeric input field. */
    public JTextField addNumericField(String label, double defaultValue, int decimals) {
        JPanel row = createRow();
        JLabel lbl = new JLabel(label);
        lbl.setFont(lbl.getFont().deriveFont(Font.PLAIN, 12f));
        lbl.setForeground(LABEL_COLOR);
        row.add(lbl);
        row.add(Box.createHorizontalGlue());
        String val = decimals == 0 ? String.valueOf((int) defaultValue) : String.valueOf(defaultValue);
        JTextField tf = new JTextField(val, 8);
        tf.setMaximumSize(new Dimension(96, 24));
        row.add(tf);
        numericFields.add(tf);
        activeTarget.add(row);
        activeTarget.add(Box.createVerticalStrut(4));
        return tf;
    }

    /** Adds a button to the content area. */
    public JButton addButton(String label) {
        JPanel row = createRow();
        JButton btn = new JButton(label);
        btn.setFocusPainted(false);
        row.add(btn);
        activeTarget.add(row);
        activeTarget.add(Box.createVerticalStrut(4));
        return btn;
    }

    /** Adds vertical spacing. */
    public void addSpacer(int height) {
        activeTarget.add(Box.createVerticalStrut(height));
    }

    /** Adds a custom component. */
    public void addComponent(JComponent component) {
        if (component == null) return;
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        activeTarget.add(component);
        activeTarget.add(Box.createVerticalStrut(4));
    }

    // ── Dialog lifecycle ───────────────────────────────────────────

    /** Shows the dialog (blocking). Returns true if OK was pressed. */
    public boolean showDialog() {
        wasBackPressed = false;
        dialog.pack();
        Dimension pref = dialog.getPreferredSize();
        int maxH = (int) (Toolkit.getDefaultToolkit().getScreenSize().height * 0.8);
        if (pref.height > maxH) {
            dialog.setSize(pref.width + 30, maxH);
        }
        dialog.setLocationRelativeTo(null);
        if (dialog.isModal()) {
            dialog.setVisible(true);
            return !wasCanceled;
        }

        final CountDownLatch closed = new CountDownLatch(1);
        WindowAdapter closeListener = new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) {
                closed.countDown();
            }
        };
        dialog.addWindowListener(closeListener);
        dialog.setVisible(true);
        try {
            closed.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            wasCanceled = true;
            dialog.dispose();
        } finally {
            dialog.removeWindowListener(closeListener);
        }
        return !wasCanceled;
    }

    public boolean wasCanceled() { return wasCanceled; }

    public void enableBackButton() {
        backButton.setVisible(true);
    }

    public void setModal(boolean modal) { dialog.setModal(modal); }

    /** Sets a callback to run when OK is pressed (used with showNonBlocking). */
    public void setOnOK(Runnable callback) { this.onOK = callback; }

    /** Shows the dialog without blocking the calling thread. */
    public void showNonBlocking() {
        dialog.setModal(false);
        dialog.pack();
        Dimension pref = dialog.getPreferredSize();
        int maxH = (int) (Toolkit.getDefaultToolkit().getScreenSize().height * 0.8);
        if (pref.height > maxH) {
            dialog.setSize(pref.width + 30, maxH);
        }
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
    }

    public JButton addFooterButton(String label) {
        JButton btn = new JButton(label);
        btn.setPreferredSize(new Dimension(90, 28));
        leftButtonPanel.add(btn);
        return btn;
    }

    public boolean wasBackPressed() { return wasBackPressed; }

    // ── Sequential retrieval (matches GenericDialog pattern) ──────

    public boolean getNextBoolean() { return toggles.get(toggleIndex++).isSelected(); }
    public String getNextString() { return textFields.get(textFieldIndex++).getText(); }
    public String getNextChoice() { return (String) combos.get(comboIndex++).getSelectedItem(); }

    public double getNextNumber() {
        String text = numericFields.get(numericFieldIndex++).getText().trim();
        try { return Double.parseDouble(text); }
        catch (NumberFormatException e) { return 0; }
    }

    // ── Internal ───────────────────────────────────────────────────

    private JPanel createRow() {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        row.setBorder(new EmptyBorder(0, 4, 0, 4));
        return row;
    }
}
