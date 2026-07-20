package com.example.AInventorySetup;

import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.PluginPanel;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.GridLayout;

public class AInventorySetupPanel extends PluginPanel {

    private final AInventorySetupPlugin plugin;
    private final ClientThread clientThread;
    private final JComboBox<String> templateCombo = new JComboBox<>();

    public AInventorySetupPanel(AInventorySetupPlugin plugin, ClientThread clientThread) {
        this.plugin = plugin;
        this.clientThread = clientThread;

        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel content = new JPanel(new GridLayout(0, 1, 0, 6));

        JButton fillButton = new JButton("Fill Inventory");
        fillButton.addActionListener(e -> onFill());

        JButton saveButton = new JButton("Save Current as Template");
        saveButton.addActionListener(e -> onSave());

        JButton deleteButton = new JButton("Delete Template");
        deleteButton.addActionListener(e -> onDelete());

        content.add(new JLabel("Template:"));
        content.add(templateCombo);
        content.add(fillButton);
        content.add(saveButton);
        content.add(deleteButton);

        add(content, BorderLayout.NORTH);
    }

    public void refresh() {
        String selected = (String) templateCombo.getSelectedItem();
        templateCombo.removeAllItems();
        for (String name : plugin.getTemplateNames()) {
            templateCombo.addItem(name);
        }
        if (selected != null) {
            templateCombo.setSelectedItem(selected);
        }
    }

    private void onFill() {
        String name = (String) templateCombo.getSelectedItem();
        if (name == null) {
            JOptionPane.showMessageDialog(this, "No template selected");
            return;
        }
        clientThread.invoke(() -> plugin.startApplyTemplate(name));
    }

    private void onSave() {
        String name = JOptionPane.showInputDialog(this, "Template name:");
        if (name == null || name.trim().isEmpty()) {
            return;
        }
        String finalName = name.trim();
        if (plugin.hasTemplate(finalName)) {
            int result = JOptionPane.showConfirmDialog(this,
                    "Overwrite existing template '" + finalName + "'?",
                    "Overwrite", JOptionPane.YES_NO_OPTION);
            if (result != JOptionPane.YES_OPTION) {
                return;
            }
        }
        clientThread.invoke(() -> plugin.captureAndSaveTemplate(finalName));
    }

    private void onDelete() {
        String name = (String) templateCombo.getSelectedItem();
        if (name == null) {
            return;
        }
        int result = JOptionPane.showConfirmDialog(this,
                "Delete template '" + name + "'?", "Delete", JOptionPane.YES_NO_OPTION);
        if (result == JOptionPane.YES_OPTION) {
            plugin.deleteTemplate(name);
            refresh();
        }
    }
}
