package com.example;

import com.fazecast.jSerialComm.SerialPort;

import javax.swing.*;
import java.awt.*;


public class FormAgent extends JFrame {

    private final JTextField urlField   = new JTextField("ws://localhost:8081/agent-ws", 24);
    private final JTextField tokenField = new JTextField(24);
    private final JComboBox<String> portBox = new JComboBox<>();
    private final JTextField baudField  = new JTextField("9600", 6);
    private final JTextArea magazine = new JTextArea(12, 40);
    private final JButton start = new JButton("Пуск");
    private final JButton stop  = new JButton("Стоп");

    private Connector connector;

    public FormAgent() {
        super("Агент COM-порта");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        // порты системы + пункт «Имитатор»
        for (SerialPort p : SerialPort.getCommPorts()) {
            portBox.addItem(p.getSystemPortName());
        }
        portBox.addItem("Имитатор COM (эхо)");

        magazine.setLineWrap(true);
        magazine.setWrapStyleWord(true);
        magazine.setEditable(false);

        JPanel top = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 4, 4, 4);
        g.anchor = GridBagConstraints.WEST;

        g.gridx = 0; g.gridy = 0; top.add(new JLabel("Адрес сервера:"), g);
        g.gridx = 1; g.gridy = 0; top.add(urlField, g);
        g.gridx = 0; g.gridy = 1; top.add(new JLabel("Токен:"), g);
        g.gridx = 1; g.gridy = 1; top.add(tokenField, g);
        g.gridx = 0; g.gridy = 2; top.add(new JLabel("Порт:"), g);
        g.gridx = 1; g.gridy = 2; top.add(portBox, g);
        g.gridx = 0; g.gridy = 3; top.add(new JLabel("Скорость, бод:"), g);
        g.gridx = 1; g.gridy = 3; top.add(baudField, g);

        stop.setEnabled(false);
        start.addActionListener(e -> onStart());
        stop.addActionListener(e -> onStop());

        JButton exit = new JButton("Выход");
        exit.addActionListener(e -> System.exit(0));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttons.add(start);
        buttons.add(stop);
        buttons.add(exit);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(magazine), BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);

        setSize(560, 480);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void onStart() {
        String token = tokenField.getText().trim();
        if (token.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Введите токен, полученный в веб-приложении.",
                    "Нет токена", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int baud;
        try {
            baud = Integer.parseInt(baudField.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Скорость должна быть числом.",
                    "Ошибка", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Object sel = portBox.getSelectedItem();
        String portName = (sel != null && sel.toString().startsWith("Имитатор")) ? null : sel.toString();

        connector = new Connector(urlField.getText().trim(), token, portName, baud, this::log);
        connector.start();

        start.setEnabled(false);
        stop.setEnabled(true);
        log("--- Запуск агента ---");
    }

    private void onStop() {
        if (connector != null) connector.stop();
        start.setEnabled(true);
        stop.setEnabled(false);
    }

    // Журнал: всегда обновляем GUI из потока EDT
    private void log(String line) {
        SwingUtilities.invokeLater(() -> {
            magazine.append(line + System.lineSeparator());
            magazine.setCaretPosition(magazine.getDocument().getLength());
        });
    }
}
