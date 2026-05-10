package uiframe;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.function.Consumer;

public class ChatPanel extends JPanel {

    private final JTextPane chatArea;
    private final JTextField inputField;
    private final JButton sendButton;
    private String localUsername;
    private final Consumer<String> onSend;

    public ChatPanel(String localUsername, Consumer<String> onSend) {
        this.localUsername = localUsername != null ? localUsername : "Sen";
        this.onSend = onSend;

        // Sidebar ile birebir aynı renk paleti
        Color bgDark    = new Color(24, 24, 36);
        Color bgPanel   = new Color(20, 20, 35);
        Color inputBg   = new Color(30, 30, 48);
        Color borderCol = new Color(60, 60, 90);
        Color textColor = new Color(180, 220, 255);
        Color headerCol = new Color(160, 180, 220);
        Color btnColor  = new Color(100, 150, 200);
        Color btnHover  = new Color(120, 170, 220);

        setLayout(new BorderLayout(0, 0));
        setBackground(bgDark);
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, borderCol));
        setPreferredSize(new Dimension(230, 260));

        // Başlık — sectionTitle ile aynı stil
        JLabel titleLabel = new JLabel("Mesajlaşma");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        titleLabel.setForeground(headerCol);
        titleLabel.setOpaque(true);
        titleLabel.setBackground(new Color(30, 30, 50));
        titleLabel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, borderCol),
            BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
        add(titleLabel, BorderLayout.NORTH);

        // Sohbet alanı
        chatArea = new JTextPane();
        chatArea.setEditable(false);
        chatArea.setBackground(bgPanel);
        chatArea.setBorder(new EmptyBorder(4, 6, 4, 6));

        JScrollPane scrollPane = new JScrollPane(chatArea);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(bgPanel);
        scrollPane.getVerticalScrollBar().setBackground(bgDark);
        scrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(6, 0));
        add(scrollPane, BorderLayout.CENTER);

        // Giriş alanı
        JPanel inputPanel = new JPanel(new BorderLayout(4, 0));
        inputPanel.setBackground(bgDark);
        inputPanel.setBorder(new EmptyBorder(5, 6, 6, 6));

        inputField = new JTextField();
        inputField.setBackground(inputBg);
        inputField.setForeground(textColor);
        inputField.setCaretColor(textColor);
        inputField.setFont(new Font("SansSerif", Font.PLAIN, 11));
        inputField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(borderCol),
            new EmptyBorder(4, 7, 4, 7)
        ));
        setPlaceholder(inputField, "Mesaj yaz...", textColor, headerCol);

        // Gönder butonu — uygulamanın CustomButton stilinde
        sendButton = new JButton("Gönder") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? btnHover : btnColor);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                g2.setColor(new Color(80, 120, 170));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("SansSerif", Font.BOLD, 11));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(),
                    (getWidth() - fm.stringWidth(getText())) / 2,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
        sendButton.setPreferredSize(new Dimension(58, 28));
        sendButton.setFocusPainted(false);
        sendButton.setBorderPainted(false);
        sendButton.setContentAreaFilled(false);
        sendButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        add(inputPanel, BorderLayout.SOUTH);

        sendButton.addActionListener(e -> sendMessage());
        inputField.addActionListener(e -> sendMessage());

        appendSystemMessage("Sohbete katıldınız 👋");
    }

    public void setUsername(String username) {
        this.localUsername = username != null ? username : "Sen";
    }

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty() || text.equals("Mesaj yaz...")) return;
        inputField.setText("");
        if (onSend != null) onSend.accept(text);
        appendMessage(localUsername, text, true);
    }

    public void receiveMessage(String sender, String message) {
        SwingUtilities.invokeLater(() -> {
            if (sender.equals(localUsername)) return;
            appendMessage(sender, message, false);
        });
    }

    private void appendMessage(String sender, String message, boolean isSelf) {
        StyledDocument doc = chatArea.getStyledDocument();
        try {
            String time = new SimpleDateFormat("HH:mm").format(new Date());

            Style nameStyle = chatArea.addStyle("n" + System.nanoTime(), null);
            StyleConstants.setForeground(nameStyle, isSelf
                ? new Color(130, 180, 255)   // mavi — kendi mesajı
                : new Color(130, 220, 160));  // yeşil — karşı taraf
            StyleConstants.setBold(nameStyle, true);
            StyleConstants.setFontSize(nameStyle, 11);
            doc.insertString(doc.getLength(), sender, nameStyle);

            Style timeStyle = chatArea.addStyle("t" + System.nanoTime(), null);
            StyleConstants.setForeground(timeStyle, new Color(100, 110, 150));
            StyleConstants.setFontSize(timeStyle, 10);
            doc.insertString(doc.getLength(), "  " + time + "\n", timeStyle);

            Style msgStyle = chatArea.addStyle("m" + System.nanoTime(), null);
            StyleConstants.setForeground(msgStyle, new Color(180, 220, 255));
            StyleConstants.setFontSize(msgStyle, 11);
            doc.insertString(doc.getLength(), "  " + message + "\n\n", msgStyle);

            chatArea.setCaretPosition(doc.getLength());
        } catch (BadLocationException ignored) {}
    }

    private void appendSystemMessage(String text) {
        SwingUtilities.invokeLater(() -> {
            StyledDocument doc = chatArea.getStyledDocument();
            try {
                Style s = chatArea.addStyle("sys", null);
                StyleConstants.setForeground(s, new Color(160, 180, 220));
                StyleConstants.setItalic(s, true);
                StyleConstants.setFontSize(s, 10);
                doc.insertString(doc.getLength(), "  " + text + "\n", s);
            } catch (BadLocationException ignored) {}
        });
    }

    private void setPlaceholder(JTextField field, String placeholder, Color textColor, Color phColor) {
        field.setText(placeholder);
        field.setForeground(phColor);
        field.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                if (field.getText().equals(placeholder)) {
                    field.setText("");
                    field.setForeground(textColor);
                }
            }
            @Override public void focusLost(FocusEvent e) {
                if (field.getText().isEmpty()) {
                    field.setText(placeholder);
                    field.setForeground(phColor);
                }
            }
        });
    }
}