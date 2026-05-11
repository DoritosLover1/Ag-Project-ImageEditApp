package uiframe;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

public class ChatPanel extends JPanel {

    private final JTextPane chatArea;
    private final JTextField inputField;
    private final JButton sendButton;
    private final JLabel titleLabel;
    private final JPanel inputPanel;
    private String localUsername;
    private final Consumer<String> onSend;

    // Tema referansı — MainFrame.AppTheme
    private MainFrame.AppTheme theme;

    // Sidebar sabit renkleri (tema değişmez olanlar)
    private static final Color SIDEBAR_BG = new Color(24, 24, 36);
    private static final Color SIDEBAR_PANEL = new Color(20, 20, 35);
    private static final Color SIDEBAR_TITLE_BG = new Color(30, 30, 50);
    private static final Color SIDEBAR_BORDER = new Color(60, 60, 90);
    private static final Color SIDEBAR_TEXT = new Color(180, 220, 255);
    private static final Color SIDEBAR_HEADER = new Color(160, 180, 220);
    private static final Color MSG_SELF = new Color(130, 180, 255);
    private static final Color MSG_OTHER = new Color(130, 220, 160);
    private static final Color MSG_TIME = new Color(100, 110, 150);

    public ChatPanel(String localUsername, MainFrame.AppTheme theme, Consumer<String> onSend) {
        this.localUsername = localUsername != null ? localUsername : "Sen";
        this.theme = theme;
        this.onSend = onSend;

        setLayout(new BorderLayout(0, 0));
        setBackground(SIDEBAR_BG);
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, SIDEBAR_BORDER));
        setPreferredSize(new Dimension(230, 260));
        setMinimumSize(new Dimension(230, 100));

        // --- Başlık ---
        titleLabel = new JLabel("Mesajlaşma");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        titleLabel.setForeground(SIDEBAR_HEADER);
        titleLabel.setOpaque(true);
        titleLabel.setBackground(SIDEBAR_TITLE_BG);
        titleLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, SIDEBAR_BORDER),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        add(titleLabel, BorderLayout.NORTH);

        // --- Sohbet Alanı ---
        chatArea = new JTextPane();
        chatArea.setEditable(false);
        chatArea.setBackground(SIDEBAR_PANEL);
        chatArea.setBorder(new EmptyBorder(4, 6, 4, 6));

        JScrollPane scrollPane = new JScrollPane(chatArea);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(SIDEBAR_PANEL);
        scrollPane.getVerticalScrollBar().setBackground(SIDEBAR_BG);
        scrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(6, 0));
        add(scrollPane, BorderLayout.CENTER);

        // --- Giriş Alanı ---
        inputPanel = new JPanel(new BorderLayout(4, 0));
        inputPanel.setBackground(SIDEBAR_BG);
        inputPanel.setBorder(new EmptyBorder(5, 6, 6, 6));
        inputPanel.setMinimumSize(new Dimension(0, 42));
        inputPanel.setPreferredSize(new Dimension(0, 42));

        inputField = new JTextField();
        inputField.setMinimumSize(new Dimension(0, 28));
        inputField.setPreferredSize(new Dimension(0, 28));
        applyInputStyle();
        setPlaceholder();

        sendButton = makeSendButton();
        sendButton.setMinimumSize(new Dimension(58, 28));
        sendButton.setPreferredSize(new Dimension(58, 28));

        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        add(inputPanel, BorderLayout.SOUTH);

        // --- Olaylar ---
        sendButton.addActionListener(e -> sendMessage());
        inputField.addActionListener(e -> sendMessage());

        appendSystemMessage("Sohbete katıldınız 👋");
    }

    // Geriye dönük uyumluluk — theme olmadan da çalışsın
    public ChatPanel(String localUsername, Consumer<String> onSend) {
        this(localUsername, null, onSend);
    }

    // Tema değişince MainFrame'den çağrılır
    public void updateTheme(MainFrame.AppTheme newTheme) {
        this.theme = newTheme;
        applyInputStyle();
        applyButtonTheme(sendButton);
        sendButton.repaint();
        inputPanel.repaint();
        repaint();
    }

    public void setUsername(String username) {
        this.localUsername = username != null ? username : "Sen";
    }

    // --- Input stil uygula (tema renklerini kullan, sidebar sabitlerini fallback
    // yap) ---
    private void applyInputStyle() {
        Color bg = theme != null ? theme.inputBg : new Color(30, 30, 48);
        Color border = theme != null ? theme.inputBorder : SIDEBAR_BORDER;
        Color text = theme != null ? theme.titleText : SIDEBAR_TEXT;

        inputField.setBackground(bg);
        inputField.setForeground(text);
        inputField.setCaretColor(text);
        inputField.setFont(new Font("SansSerif", Font.PLAIN, 11));
        inputField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(border),
                new EmptyBorder(4, 7, 4, 7)));
    }

    private JButton makeSendButton() {
        JButton btn = new JButton("Gönder");
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(true); // true yap
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setFont(new Font("SansSerif", Font.BOLD, 11));
        btn.setForeground(Color.WHITE);
        applyButtonTheme(btn); // tema rengini uygula
        return btn;
    }

    private void applyButtonTheme(JButton btn) {
        Color base = theme != null ? theme.buttonColor : new Color(100, 150, 200);
        btn.setBackground(base);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(base.darker(), 1),
                new EmptyBorder(4, 8, 4, 8)));
        btn.setForeground(theme != null ? theme.titleText : Color.WHITE);
    }

    private void setPlaceholder() {
        String placeholder = "Mesaj yaz...";
        inputField.setText(placeholder);
        inputField.setForeground(SIDEBAR_HEADER);
        inputField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (inputField.getText().equals(placeholder)) {
                    inputField.setText("");
                    Color text = theme != null ? theme.titleText : SIDEBAR_TEXT;
                    inputField.setForeground(text);
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (inputField.getText().isEmpty()) {
                    inputField.setText(placeholder);
                    inputField.setForeground(SIDEBAR_HEADER);
                }
            }
        });
    }

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty() || text.equals("Mesaj yaz..."))
            return;
        inputField.setText("");
        Color textColor = theme != null ? theme.titleText : SIDEBAR_TEXT;
        inputField.setForeground(textColor);
        if (onSend != null)
            onSend.accept(text);
        appendMessage(localUsername, text, true);
    }

    public void receiveMessage(String sender, String message) {
        SwingUtilities.invokeLater(() -> {
            if (sender.equals(localUsername))
                return;
            appendMessage(sender, message, false);
        });
    }

    private void appendMessage(String sender, String message, boolean isSelf) {
        StyledDocument doc = chatArea.getStyledDocument();
        try {
            String time = new SimpleDateFormat("HH:mm").format(new Date());

            Style nameStyle = chatArea.addStyle("n" + System.nanoTime(), null);
            StyleConstants.setForeground(nameStyle, isSelf ? MSG_SELF : MSG_OTHER);
            StyleConstants.setBold(nameStyle, true);
            StyleConstants.setFontSize(nameStyle, 11);
            doc.insertString(doc.getLength(), sender, nameStyle);

            Style timeStyle = chatArea.addStyle("t" + System.nanoTime(), null);
            StyleConstants.setForeground(timeStyle, MSG_TIME);
            StyleConstants.setFontSize(timeStyle, 10);
            doc.insertString(doc.getLength(), "  " + time + "\n", timeStyle);

            Style msgStyle = chatArea.addStyle("m" + System.nanoTime(), null);
            StyleConstants.setForeground(msgStyle, SIDEBAR_TEXT);
            StyleConstants.setFontSize(msgStyle, 11);
            doc.insertString(doc.getLength(), "  " + message + "\n\n", msgStyle);

            chatArea.setCaretPosition(doc.getLength());
        } catch (BadLocationException ignored) {
        }
    }

    private void appendSystemMessage(String text) {
        SwingUtilities.invokeLater(() -> {
            StyledDocument doc = chatArea.getStyledDocument();
            try {
                Style s = chatArea.addStyle("sys", null);
                StyleConstants.setForeground(s, SIDEBAR_HEADER);
                StyleConstants.setItalic(s, true);
                StyleConstants.setFontSize(s, 10);
                doc.insertString(doc.getLength(), "  " + text + "\n", s);
            } catch (BadLocationException ignored) {
            }
        });
    }
}