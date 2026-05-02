package uiframe;

import javax.swing.*;
import javax.swing.border.*;
import customelements.CustomButton;
import java.awt.*;
import java.awt.datatransfer.StringSelection;

public class MainFrame {
    JFrame frame;

    private String username;
    private String myRoomCode;

    private CardLayout cardLayout;
    private JPanel mainPanel;

    private JTextField usernameField;
    private JTextField serverIPField;
    private JTextField joinRoomCodeField;
    private JLabel lobbyUsernameLabel;
    
    private JLabel roomCodeLabel;

    public static class AppTheme {
        public Color background  = new Color(45, 52, 80);
        public Color inputBg     = new Color(30, 30, 50);
        public Color inputBorder = new Color(100, 100, 150);
        public Color buttonColor = new Color(100, 150, 200);
        public Color subText     = new Color(200, 200, 255);
        public Color titleText   = Color.WHITE;
        public Color hintText    = Color.LIGHT_GRAY;
    }

    public AppTheme theme = new AppTheme();

    public MainFrame() {
        init();
    }

    private void init() {
        frame = new JFrame("DEHSETÜL VAHŞET PAİNT");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);

        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);
        mainPanel.add(loginPanel(), "LOGIN");
        mainPanel.add(lobbyPanel(), "LOBBY");
        mainPanel.add(canvasPanel(), "CANVAS");

        frame.getContentPane().add(mainPanel);
        cardLayout.show(mainPanel, "LOGIN");

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private JPanel loginPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(theme.background);
        panel.setPreferredSize(new Dimension(300, 300));
        panel.setMinimumSize(new Dimension(300, 300));
        panel.setMaximumSize(new Dimension(300, 300));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel applicationTitleLb = new JLabel("PaiCollab", SwingConstants.CENTER);
        applicationTitleLb.setFont(new Font("Arial", Font.BOLD, 32));
        applicationTitleLb.setForeground(theme.titleText);

        gbc.gridy = 0;
        gbc.gridwidth = 2;
        panel.add(applicationTitleLb, gbc);

        CustomButton.addInputFieldAsForm(panel, 2, gbc, "Localhost:",
                serverIPField = new JTextField("localhost"), theme);
        CustomButton.addInputFieldAsForm(panel, 3, gbc, "Username:",
                usernameField = new JTextField(), theme);

        JButton loginBtn = new CustomButton("Login", theme);
        loginBtn.setForeground(theme.titleText);

        gbc.gridy = 4;
        gbc.gridwidth = 2;
        panel.add(loginBtn, gbc);

        loginBtn.addActionListener(e -> {
            // BAĞLANTI FONKSİYONU GELECEK NORMALDE
            this.username = usernameField.getText();
            lobbyUsernameLabel.setText("Merhaba, Ressam(!) " + this.username);
            cardLayout.show(mainPanel, "LOBBY");
        });

        usernameField.addActionListener(e -> loginBtn.doClick());

        return panel;
    }

    private JPanel lobbyPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 20));
        panel.setBackground(theme.background);
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));
        panel.setPreferredSize(new Dimension(450, 400));
        panel.setMinimumSize(new Dimension(450, 400));
        panel.setMaximumSize(new Dimension(450, 400));

        lobbyUsernameLabel = new JLabel(
                "Merhaba, Ressam(!) " + this.username, SwingConstants.CENTER);
        lobbyUsernameLabel.setFont(new Font("Arial", Font.BOLD, 24));
        lobbyUsernameLabel.setForeground(theme.titleText);
        panel.add(lobbyUsernameLabel, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridLayout(4, 2, 0, 10));
        center.setOpaque(false);

        JButton createRoomBtn = new CustomButton("Oda Oluştur", theme);
        createRoomBtn.addActionListener(e -> {
            // ODA OLUŞTURMA FONKSİYONU GELECEK NORMALDE
            myRoomCode = "1245";
            JOptionPane.showMessageDialog(frame,
                    "Oda oluşturuldu! Oda Kodu: " + myRoomCode);
        });
        center.add(createRoomBtn);

        JPanel joinRow = new JPanel(new BorderLayout(10, 0));
        joinRow.setOpaque(false);
        joinRoomCodeField = new JTextField();
        applyInputFieldStyle(joinRoomCodeField);
        JButton joinRoomBtn = new CustomButton("Odaya Katıl", theme);
        joinRoomBtn.setPreferredSize(new Dimension(150, 30));
        joinRow.add(joinRoomCodeField, BorderLayout.CENTER);
        joinRow.add(joinRoomBtn, BorderLayout.EAST);
        joinRoomBtn.addActionListener(e -> {
            String code = joinRoomCodeField.getText().trim();
            if (code.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Lütfen bir oda kodu girin!");
                return;
            }
            JOptionPane.showMessageDialog(frame, "Odaya katılındı! Oda Kodu: " + code);
        });
        joinRoomCodeField.addActionListener(e -> joinRoomBtn.doClick());
        center.add(joinRow);

        JButton settingsBtn = new CustomButton("◉_◉ Ayarlar", theme);
        settingsBtn.addActionListener(e -> openSettingsDialog());
        center.add(settingsBtn);

        JLabel hint = new JLabel(
                "Oda kodunu bilmiyorsanız, yeni bir oda oluşturabilirsiniz.",
                SwingConstants.CENTER);
        hint.setFont(new Font("Arial", Font.ITALIC, 12));
        hint.setForeground(theme.hintText);
        center.add(hint);

        panel.add(center, BorderLayout.CENTER);
        return panel;
    }

    private JPanel canvasPanel() {
    	JPanel panel = new JPanel(new BorderLayout());
    	panel.setBackground(theme.background);
    	
    	panel.add(createTopBarPanel(), BorderLayout.NORTH);
    	
        return new JPanel(); // GELECEK NORMALDE BİR ÇİZİM PANELİ OLACAK
    }
    
    private JPanel createTopBarPanel() {
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(theme.background);
        topBar.setBorder(new EmptyBorder(10, 15, 10, 15));

        // SOL KISIM
        JPanel leftSideOfPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftSideOfPanel.setOpaque(false);
        roomCodeLabel = new JLabel("Oda Kodu: " + myRoomCode);
        roomCodeLabel.setFont(new Font("Arial", Font.BOLD, 14));
        roomCodeLabel.setForeground(theme.titleText);

        JButton copyRoomCodeBtn = CustomButton.smallButtonGenerate("Kopyala", theme);
        copyRoomCodeBtn.addActionListener(e -> {
            if (myRoomCode != null) {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(myRoomCode), null);
                JOptionPane.showMessageDialog(frame, "Oda kodu panoya kopyalandı!");
            } else {
                JOptionPane.showMessageDialog(frame, "Kopyalanacak oda kodu bulunamadı!");
            }
        });
        leftSideOfPanel.add(roomCodeLabel);
        leftSideOfPanel.add(copyRoomCodeBtn);
        topBar.add(leftSideOfPanel, BorderLayout.WEST);

        // ORTA KISIM — araç butonları
        JPanel centerTools = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
        centerTools.setOpaque(false);

        String[][] tools = {
            {"<", "Seç & Kes",   "SELECT"},
            {"🖋️", "Kalem",       "FREEHAND"},
            {"■", "Dikdörtgen",  "RECTANGLE"},
            {"O", "Elips",       "CIRCLE"},
            {"/", "Çizgi",       "LINE"},
        };

        ButtonGroup toolGroup = new ButtonGroup();
        for (String[] t : tools) {
            JToggleButton btn = new JToggleButton(t[0]);
            btn.setToolTipText(t[1]);
            btn.setFont(new Font("Arial", Font.BOLD, 13));
            btn.setPreferredSize(new Dimension(36, 28));
            btn.setBackground(theme.buttonColor);
            btn.setForeground(theme.titleText);
            btn.setFocusPainted(false);
            btn.setBorderPainted(true);
            DrawingCanvas.Tool toolEnum = DrawingCanvas.Tool.valueOf(t[2]);
            btn.addActionListener(e -> canvas.setCurrentTool(toolEnum));
            toolGroup.add(btn);
            if (t[2].equals("FREEHAND")) btn.setSelected(true);
            centerTools.add(btn);
        }

        // Renk seçici — tema renkli etiket + düğme
        JLabel colorLabel = new JLabel("Renk:");
        colorLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        colorLabel.setForeground(theme.titleText);
        centerTools.add(colorLabel);

        JButton colorBtn = CustomButton.smallButtonGenerate("■", theme);
        colorBtn.setBackground(theme.buttonColor);
        colorBtn.setToolTipText("Renk seç");
        colorBtn.addActionListener(e -> {
            Color c = JColorChooser.showDialog(frame, "Renk Seç", canvas.getCurrentColor());
            if (c != null) { canvas.setCurrentColor(c); colorBtn.setBackground(c); }
        });
        centerTools.add(colorBtn);

        // Kalınlık
        JLabel strokeLbl = new JLabel("Kalınlık:");
        strokeLbl.setFont(new Font("Arial", Font.PLAIN, 12));
        strokeLbl.setForeground(theme.titleText);
        centerTools.add(strokeLbl);

        JSpinner strokeSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 20, 1));
        strokeSpinner.setPreferredSize(new Dimension(50, 28));
        strokeSpinner.getEditor().getComponent(0).setBackground(theme.buttonBackground);
        ((JSpinner.DefaultEditor) strokeSpinner.getEditor()).getTextField().setForeground(theme.buttonText);
        strokeSpinner.addChangeListener(e -> canvas.setStrokeWidth((int) strokeSpinner.getValue()));
        centerTools.add(strokeSpinner);

        // Dolu toggle — smallButtonGenerate ile
        JToggleButton fillBtn = new JToggleButton("Dolu");
        fillBtn.setFont(new Font("Arial", Font.BOLD, 12));
        fillBtn.setPreferredSize(new Dimension(52, 28));
        fillBtn.setBackground(theme.buttonColor);
        fillBtn.setForeground(theme.titleText);
        fillBtn.setFocusPainted(false);
        fillBtn.addActionListener(e -> canvas.setFilled(fillBtn.isSelected()));
        centerTools.add(fillBtn);

        // Yapıştır
        JButton pasteBtn = CustomButton.smallButtonGenerate("Yapıştır", theme);
        pasteBtn.setToolTipText("Resim yapıştır (Ctrl+V)");
        pasteBtn.addActionListener(e -> canvas.pasteFromClipboard());
        centerTools.add(pasteBtn);

        topBar.add(centerTools, BorderLayout.CENTER);

        // SAĞ KISIM
        JPanel rightSideOfPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightSideOfPanel.setOpaque(false);

        JButton clearCanvasBtn = CustomButton.smallButtonGenerate("Çizimleri Temizle", theme);
        clearCanvasBtn.addActionListener(e-> {
            JOptionPane.showMessageDialog(frame, "Çizimler temizlendi!");
        });

        JButton leaveRoomBtn = CustomButton.smallButtonGenerate("Odadan Ayrıl", theme);
        leaveRoomBtn.addActionListener(e -> {
            JOptionPane.showMessageDialog(frame, "Odadan ayrıldınız!");
            cardLayout.show(mainPanel, "LOBBY");
        });

        rightSideOfPanel.add(clearCanvasBtn);
        rightSideOfPanel.add(leaveRoomBtn);
        topBar.add(rightSideOfPanel, BorderLayout.EAST);

        return topBar;
    }
    
    private void applyInputFieldStyle(JTextField field) {
        field.setFont(new Font("Arial", Font.PLAIN, 14));
        field.setBackground(theme.inputBg);
        field.setForeground(theme.titleText);
        field.setCaretColor(theme.titleText);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(theme.inputBorder),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)));
    }

    private void openSettingsDialog() {
        JDialog dialog = new JDialog(frame, "Ayarlar", true);
        dialog.setLayout(new GridBagLayout());
        dialog.getContentPane().setBackground(theme.background);
        dialog.setResizable(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 12, 8, 12);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        AppTheme temp = new AppTheme();
        temp.background  = theme.background;
        temp.inputBg     = theme.inputBg;
        temp.inputBorder = theme.inputBorder;
        temp.buttonColor = theme.buttonColor;
        temp.subText     = theme.subText;
        temp.titleText   = theme.titleText;
        temp.hintText    = theme.hintText;

        String[] labels = {
            "Arka Plan",
            "Girdi Arka Planı",
            "Girdi Kenarlık",
            "Buton Rengi",
            "Alt Metin",
            "Başlık Metni",
            "İpucu Metni"
        };

        JButton[] colorBtns = new JButton[labels.length];

        for (int i = 0; i < labels.length; i++) {
            final int idx = i;

            JLabel lbl = new JLabel(labels[i]);
            lbl.setForeground(theme.titleText);
            lbl.setFont(new Font("Arial", Font.PLAIN, 13));

            colorBtns[i] = new JButton();
            colorBtns[i].setPreferredSize(new Dimension(60, 26));
            colorBtns[i].setBackground(getThemeColorByIndex(temp, i));
            colorBtns[i].setBorder(BorderFactory.createLineBorder(theme.inputBorder));
            colorBtns[i].setFocusPainted(false);
            colorBtns[i].setCursor(new Cursor(Cursor.HAND_CURSOR));
            colorBtns[i].addActionListener(e -> {
                Color secim = JColorChooser.showDialog(
                        dialog, labels[idx] + " rengini seçin",
                        getThemeColorByIndex(temp, idx));
                if (secim != null) {
                    setThemeColorByIndex(temp, idx, secim);
                    colorBtns[idx].setBackground(secim);
                }
            });

            gbc.gridx = 0; gbc.gridy = i; gbc.weightx = 0.6;
            dialog.add(lbl, gbc);
            gbc.gridx = 1; gbc.weightx = 0.4;
            dialog.add(colorBtns[i], gbc);
        }

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnRow.setOpaque(false);

        JButton applyBtn  = new CustomButton("Uygula",  theme);
        JButton cancelBtn = new CustomButton("İptal",   theme);

        applyBtn.addActionListener(e -> {
            theme.background  = temp.background;
            theme.inputBg     = temp.inputBg;
            theme.inputBorder = temp.inputBorder;
            theme.buttonColor = temp.buttonColor;
            theme.subText     = temp.subText;
            theme.titleText   = temp.titleText;
            theme.hintText    = temp.hintText;

            rebuildPanels();
            dialog.dispose();
        });

        cancelBtn.addActionListener(e -> dialog.dispose());

        btnRow.add(cancelBtn);
        btnRow.add(applyBtn);

        gbc.gridx = 0; gbc.gridy = labels.length;
        gbc.gridwidth = 2; gbc.insets = new Insets(16, 12, 12, 12);
        dialog.add(btnRow, gbc);

        dialog.pack();
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    private void rebuildPanels() {
        mainPanel.removeAll();
        mainPanel.add(loginPanel(), "LOGIN");
        mainPanel.add(lobbyPanel(), "LOBBY");
        mainPanel.add(canvasPanel(), "CANVAS");
        cardLayout.show(mainPanel, "LOBBY");
        mainPanel.revalidate();
        mainPanel.repaint();
    }

    private Color getThemeColorByIndex(AppTheme t, int idx) {
        return switch (idx) {
            case 0 -> t.background;
            case 1 -> t.inputBg;
            case 2 -> t.inputBorder;
            case 3 -> t.buttonColor;
            case 4 -> t.subText;
            case 5 -> t.titleText;
            case 6 -> t.hintText;
            default -> Color.GRAY;
        };
    }

    private void setThemeColorByIndex(AppTheme t, int idx, Color c) {
        switch (idx) {
            case 0 -> t.background  = c;
            case 1 -> t.inputBg     = c;
            case 2 -> t.inputBorder = c;
            case 3 -> t.buttonColor = c;
            case 4 -> t.subText     = c;
            case 5 -> t.titleText   = c;
            case 6 -> t.hintText    = c;
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {}
        SwingUtilities.invokeLater(MainFrame::new);
    }
}