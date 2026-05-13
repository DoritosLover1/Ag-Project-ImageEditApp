package uiframe;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
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

    // Mesaj verisi sınıfı
    private static class ChatMessage {
        final String sender;
        final String message;
        final String time;
        final boolean isSelf;
        final boolean isSystem;

        ChatMessage(String sender, String message, String time, boolean isSelf, boolean isSystem) {
            this.sender = sender;
            this.message = message;
            this.time = time;
            this.isSelf = isSelf;
            this.isSystem = isSystem;
        }
    }

    private final JTextPane chatArea;
    private final JTextField inputField;
    private final JButton sendButton;
    private final JLabel titleLabel;
    private final JPanel inputPanel;
    private String localUsername;
    private final Consumer<String> onSend;
    private final java.util.List<ChatMessage> messageHistory = new java.util.ArrayList<>();

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

        Color panelBg = theme != null ? theme.chatPanelBg : SIDEBAR_BG;
        Color panelBorder = theme != null ? theme.inputBorder : SIDEBAR_BORDER;

        setBackground(panelBg);
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, panelBorder));
        setPreferredSize(new Dimension(230, 260));
        setMinimumSize(new Dimension(230, 100));

        // --- Başlık ---
        titleLabel = new JLabel("Mesajlaşma");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        Color titleFg = theme != null ? theme.titleText : SIDEBAR_HEADER;
        titleLabel.setForeground(titleFg);
        titleLabel.setOpaque(true);
        Color titleBg = theme != null ? theme.sidebarBg : SIDEBAR_TITLE_BG;
        titleLabel.setBackground(titleBg);
        titleLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, panelBorder),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        add(titleLabel, BorderLayout.NORTH);

        // --- Sohbet Alanı ---
        chatArea = new JTextPane();
        chatArea.setEditable(false);
        Color areaBg = theme != null ? theme.chatAreaBg : SIDEBAR_PANEL;
        chatArea.setBackground(areaBg);
        chatArea.setBorder(new EmptyBorder(4, 6, 4, 6));

        JScrollPane scrollPane = new JScrollPane(chatArea);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(areaBg);
        scrollPane.getVerticalScrollBar().setBackground(panelBg);
        scrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(6, 0));
        add(scrollPane, BorderLayout.CENTER);

        // --- Giriş Alanı ---
        inputPanel = new JPanel(new BorderLayout(4, 0));
        inputPanel.setBackground(panelBg);
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

        // Panel arka planları
        Color panelBg = theme.chatPanelBg;
        Color areaBg = theme.chatAreaBg;
        Color borderColor = theme.inputBorder;

        setBackground(panelBg);
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, borderColor));
        inputPanel.setBackground(panelBg);
        chatArea.setBackground(areaBg);

        JScrollPane scrollPane = (JScrollPane) chatArea.getParent().getParent();
        scrollPane.getViewport().setBackground(areaBg);
        scrollPane.getVerticalScrollBar().setBackground(panelBg);

        // Başlık
        titleLabel.setForeground(theme.titleText);
        titleLabel.setBackground(theme.sidebarBg);
        titleLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, borderColor),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));

        applyInputStyle();
        applyButtonTheme(sendButton);
        sendButton.repaint();
        inputPanel.repaint();

        // Mesajları yeniden yükle (tema renkleriyle)
        reloadMessages();
        repaint();
    }

    public void setUsername(String username) {
        this.localUsername = username != null ? username : "Sen";
    }

    public void clearMessages() {
        SwingUtilities.invokeLater(() -> {
            messageHistory.clear();
            chatArea.setText("");
            appendSystemMessage("Odaya bağlandınız. Sohbet temizlendi.");
        });
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
        Color hintColor = theme != null ? theme.hintText : SIDEBAR_HEADER;
        inputField.setForeground(hintColor);
        inputField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (inputField.getText().equals(placeholder)) {
                    inputField.setText("");
                    Color text = theme != null ? theme.chatMsgText : SIDEBAR_TEXT;
                    inputField.setForeground(text);
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (inputField.getText().isEmpty()) {
                    inputField.setText(placeholder);
                    Color hintColor = theme != null ? theme.hintText : SIDEBAR_HEADER;
                    inputField.setForeground(hintColor);
                }
            }
        });
    }

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty() || text.equals("Mesaj yaz..."))
            return;
        inputField.setText("");
        Color textColor = theme != null ? theme.chatMsgText : SIDEBAR_TEXT;
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

    public void receiveHistoryMessage(String sender, String message, long timestamp) {
        SwingUtilities.invokeLater(() -> {
            String time = new SimpleDateFormat("HH:mm").format(new Date(timestamp));
            appendHistoryMessage(sender, message, time, sender.equals(localUsername));
        });
    }

    private void appendMessage(String sender, String message, boolean isSelf) {
        String time = new SimpleDateFormat("HH:mm").format(new Date());

        // Mesajı geçmişe kaydet
        messageHistory.add(new ChatMessage(sender, message, time, isSelf, false));

        // Mesajı görüntüle
        displayMessage(sender, message, time, isSelf, false);
    }

    private void appendHistoryMessage(String sender, String message, String time, boolean isSelf) {
        // Geçmiş mesajları da messageHistory'ye ekliyoruz ki tema değişince kaybolmasınlar
        messageHistory.add(new ChatMessage(sender, message, time, isSelf, false));
        displayMessage(sender, message, time, isSelf, false);
    }

    private void appendSystemMessage(String text) {
        // Sistem mesajını geçmişe kaydet
        messageHistory.add(new ChatMessage("", text, "", false, true));

        // Sistem mesajını görüntüle
        displayMessage("", text, "", false, true);
    }

    private void displayMessage(String sender, String message, String time, boolean isSelf, boolean isSystem) {
        StyledDocument doc = chatArea.getStyledDocument();
        try {
            if (isSystem) {
                Style s = chatArea.addStyle("sys", null);
                Color sysColor = theme != null ? theme.hintText : SIDEBAR_HEADER;
                StyleConstants.setForeground(s, sysColor);
                StyleConstants.setItalic(s, true);
                StyleConstants.setFontSize(s, 10);
                doc.insertString(doc.getLength(), "  " + message + "\n", s);
            } else {
                Color msgSelfColor = theme != null ? theme.chatMsgSelf : MSG_SELF;
                Color msgOtherColor = theme != null ? theme.chatMsgOther : MSG_OTHER;
                Color msgTimeColor = theme != null ? theme.chatMsgTime : MSG_TIME;
                Color msgTextColor = theme != null ? theme.chatMsgText : SIDEBAR_TEXT;

                Style nameStyle = chatArea.addStyle("n" + System.nanoTime(), null);
                StyleConstants.setForeground(nameStyle, isSelf ? msgSelfColor : msgOtherColor);
                StyleConstants.setBold(nameStyle, true);
                StyleConstants.setFontSize(nameStyle, 11);
                doc.insertString(doc.getLength(), sender, nameStyle);

                Style timeStyle = chatArea.addStyle("t" + System.nanoTime(), null);
                StyleConstants.setForeground(timeStyle, msgTimeColor);
                StyleConstants.setFontSize(timeStyle, 10);
                doc.insertString(doc.getLength(), "  " + time + "\n", timeStyle);

                Style msgStyle = chatArea.addStyle("m" + System.nanoTime(), null);
                StyleConstants.setForeground(msgStyle, msgTextColor);
                StyleConstants.setFontSize(msgStyle, 11);
                doc.insertString(doc.getLength(), "  " + message + "\n\n", msgStyle);
            }

            chatArea.setCaretPosition(doc.getLength());
        } catch (BadLocationException ignored) {
        }
    }

    // Tema değişince mesajları yeniden yükle
    private void reloadMessages() {
        // Chat alanını temizle
        chatArea.setText("");

        // Tüm mesajları yeniden görüntüle
        for (ChatMessage msg : messageHistory) {
            displayMessage(msg.sender, msg.message, msg.time, msg.isSelf, msg.isSystem);
        }
    }
}