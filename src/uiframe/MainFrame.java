package uiframe;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import models.*;
import network.*;
import customelements.CustomButton;

public class MainFrame {
    JFrame frame;
    private String username;
    private String myRoomCode;
    private Color cursorColor;
    private CardLayout cardLayout;
    private String currentScreen = "LOGIN";
    private JPanel mainPanel;
    private JTextField usernameField;
    private JTextField serverIPField;
    private JTextField joinRoomCodeField;
    private JLabel lobbyUsernameLabel;
    private JLabel roomCodeLabel;
    private DrawingCanvas canvas;
    private ClientNetworkManager networkManager;
    private DefaultListModel<String> memberModel;
    private DefaultListModel<String> itemListModel;
    private static final Dimension LOGIN_SIZE = new Dimension(300, 280);
    private static final Dimension LOBBY_SIZE = new Dimension(400, 320);
    private static final Dimension CANVAS_MIN_SIZE = new Dimension(1000, 600);

    public static class AppTheme {
        public Color background = new Color(45, 52, 80);
        public Color inputBg = new Color(30, 30, 50);
        public Color inputBorder = new Color(100, 100, 150);
        public Color buttonColor = new Color(100, 150, 200);
        public Color subText = new Color(200, 200, 255);
        public Color titleText = Color.WHITE;
        public Color hintText = Color.LIGHT_GRAY;
    }

    public AppTheme theme = new AppTheme();
    private static final String SETTINGS_FILE = "theme.properties";

    public MainFrame() {
        loadTheme();
        init();
    }

    private void init() {
        frame = new JFrame("DEHSETÜL VAHŞET PAİNT");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.cursorColor = new Color(
                (int) (Math.random() * 256),
                (int) (Math.random() * 256),
                (int) (Math.random() * 256));
        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);
        mainPanel.add(loginPanel(), "LOGIN");
        mainPanel.add(lobbyPanel(), "LOBBY");
        mainPanel.add(canvasPanel(), "CANVAS");
        frame.getContentPane().add(mainPanel);
        goLogin();
        frame.setVisible(true);
    }

    private void goLogin() {
        currentScreen = "LOGIN";
        cardLayout.show(mainPanel, "LOGIN");
        resizeFrame(LOGIN_SIZE, false);
    }

    private void goLobby() {
        currentScreen = "LOBBY";
        cardLayout.show(mainPanel, "LOBBY");
        resizeFrame(LOBBY_SIZE, false);
    }

    private void goCanvas() {
        currentScreen = "CANVAS";
        roomCodeLabel.setText("Oda Kodu: " + myRoomCode);
        canvas.setUsername(username);
        canvas.setCursorColor(cursorColor);
        cardLayout.show(mainPanel, "CANVAS");
        frame.setMinimumSize(CANVAS_MIN_SIZE);
        resizeFrame(null, true);
    }

    private void resizeFrame(Dimension size, boolean maximize) {
        if (maximize) {
            frame.setResizable(true);
            if (frame.getExtendedState() != JFrame.MAXIMIZED_BOTH) {
                frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
            }
        } else {
            frame.setMinimumSize(size);
            frame.setExtendedState(JFrame.NORMAL);
            frame.setResizable(false);
            mainPanel.setPreferredSize(size);
            frame.pack();
            frame.setLocationRelativeTo(null);
        }
    }

    private JToggleButton makeToolButton(String label, String tooltip) {
        JToggleButton btn = new JToggleButton(label) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean sel = isSelected();
                g2.setColor(sel ? new Color(160, 200, 255) : theme.buttonColor);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                g2.setColor(sel ? Color.WHITE : new Color(80, 120, 170));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
                g2.setColor(sel ? new Color(20, 20, 60) : Color.WHITE);
                g2.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 15));
                FontMetrics fm = g2.getFontMetrics();
                int tx = (getWidth() - fm.stringWidth(getText())) / 2;
                int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), tx, ty);
                g2.dispose();
            }
        };
        btn.setToolTipText(tooltip);
        btn.setPreferredSize(new Dimension(30, 28));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        return btn;
    }

    private JPanel loginPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(theme.background);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JLabel title = new JLabel("PaiCollab", SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 32));
        title.setForeground(theme.titleText);
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        panel.add(title, gbc);
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
            this.username = usernameField.getText();
            String serverIP = serverIPField.getText();
            try {
                this.networkManager = new ClientNetworkManager(serverIP, 12345, username, canvas);
                networkManager.setOnRoomJoined(code -> {
                    this.myRoomCode = code;
                    goCanvas();
                });
                networkManager.setOnUserListUpdated(users -> {
                    if (memberModel != null) {
                        memberModel.clear();
                        for (String u : users) {
                            if (!u.equals(username)) {
                                memberModel.addElement(u);
                            } else {
                                memberModel.addElement(u + " (Siz)");
                            }
                        }
                    }
                });
                networkManager.setOnError(msg -> {
                    JOptionPane.showMessageDialog(frame, "Sunucu Hatası: " + msg);
                });
                setupNetworkHooks();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame, "Bağlantı hatası: " + ex.getMessage());
                return;
            }
            lobbyUsernameLabel.setText("Merhaba, Ressam(!) " + this.username);
            goLobby();
        });
        usernameField.addActionListener(e -> loginBtn.doClick());
        return panel;
    }

    private JPanel lobbyPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 16));
        panel.setBackground(theme.background);
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));
        lobbyUsernameLabel = new JLabel("Merhaba, Ressam(!) " + this.username, SwingConstants.CENTER);
        lobbyUsernameLabel.setFont(new Font("Arial", Font.BOLD, 20));
        lobbyUsernameLabel.setForeground(theme.titleText);
        panel.add(lobbyUsernameLabel, BorderLayout.NORTH);
        JPanel center = new JPanel(new GridLayout(4, 1, 0, 8));
        center.setOpaque(false);
        JButton createRoomBtn = new CustomButton("Oda Oluştur", theme);
        createRoomBtn.addActionListener(e -> {
            if (networkManager != null) {
                networkManager.createRoom();
            }
        });
        center.add(createRoomBtn);
        JPanel joinRow = new JPanel(new BorderLayout(8, 0));
        joinRow.setOpaque(false);
        joinRoomCodeField = new JTextField();
        applyInputFieldStyle(joinRoomCodeField);
        JButton joinRoomBtn = new CustomButton("Katıl", theme);
        joinRow.add(joinRoomCodeField, BorderLayout.CENTER);
        joinRow.add(joinRoomBtn, BorderLayout.EAST);
        joinRoomBtn.addActionListener(e -> {
            String code = joinRoomCodeField.getText().trim();
            if (code.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Lütfen bir oda kodu girin!");
                return;
            }
            if (networkManager != null) {
                networkManager.joinRoom(code);
            }
        });
        joinRoomCodeField.addActionListener(e -> joinRoomBtn.doClick());
        center.add(joinRow);
        JButton settingsBtn = new CustomButton("Ayarlar", theme);
        settingsBtn.addActionListener(e -> openSettingsDialog());
        center.add(settingsBtn);
        JLabel hint = new JLabel("Oda kodunu bilmiyorsanız yeni bir oda oluşturun.", SwingConstants.CENTER);
        hint.setFont(new Font("Arial", Font.ITALIC, 11));
        hint.setForeground(theme.hintText);
        center.add(hint);
        panel.add(center, BorderLayout.CENTER);
        return panel;
    }

    private JPanel canvasPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(theme.background);
        canvas = new DrawingCanvas();
        canvas.setBackground(Color.WHITE);
        canvas.setPreferredSize(new Dimension(1200, 800));
        canvas.setOnItemsChanged(() -> {
            if (itemListModel != null) {
                itemListModel.clear();
                java.util.List<String> descs = canvas.getItemDescriptions();
                if (descs != null)
                    descs.forEach(itemListModel::addElement);
            }
        });
        JScrollPane canvasScroll = new JScrollPane(canvas);
        canvasScroll.setBorder(null);
        canvasScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        canvasScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        panel.add(createTopBarPanel(), BorderLayout.NORTH);
        panel.add(canvasScroll, BorderLayout.CENTER);
        panel.add(buildSidebar(), BorderLayout.EAST);
        return panel;
    }

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(new Color(24, 24, 36));
        sidebar.setPreferredSize(new Dimension(230, 0));
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(60, 60, 80)));
        memberModel = new DefaultListModel<>();
        JList<String> memberList = new JList<>(memberModel);
        memberList.setBackground(new Color(30, 30, 48));
        memberList.setForeground(new Color(180, 220, 255));
        memberList.setFont(new Font("SansSerif", Font.PLAIN, 12));
        memberList.setBorder(new EmptyBorder(4, 8, 4, 8));
        JScrollPane memberScroll = new JScrollPane(memberList);
        memberScroll.setBorder(null);
        memberScroll.setPreferredSize(new Dimension(230, 110));
        JPanel membersBox = new JPanel(new BorderLayout());
        membersBox.setOpaque(false);
        membersBox.add(sectionTitle("Uyeler"), BorderLayout.NORTH);
        membersBox.add(memberScroll, BorderLayout.CENTER);
        itemListModel = new DefaultListModel<>();
        JList<String> itemList = new JList<>(itemListModel);
        itemList.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_DELETE) {
                    int index = itemList.getSelectedIndex();
                    if (index != -1) {
                        java.util.List<models.CanvasItem> snap = canvas.getItemsSnapshot();
                        if (index < snap.size()) {
                            String id = snap.get(index).getIdOfImage();
                            if (networkManager != null) {
                                networkManager.sendRaw(network.NetworkProtocol.buildDelete(username, id));
                            }
                            canvas.removeItemById(id);
                        }
                    }
                }
            }
        });
        itemList.setBackground(new Color(20, 20, 35));
        itemList.setForeground(new Color(200, 220, 200));
        itemList.setFont(new Font("SansSerif", Font.PLAIN, 11));
        itemList.setBorder(new EmptyBorder(4, 8, 4, 8));
        itemList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2)
                    canvas.requestFocusInWindow();
            }
        });
        JScrollPane itemScroll = new JScrollPane(itemList);
        itemScroll.setBorder(null);
        JPanel itemsBox = new JPanel(new BorderLayout());
        itemsBox.setOpaque(false);
        itemsBox.add(sectionTitle("Oge Listesi"), BorderLayout.NORTH);
        itemsBox.add(itemScroll, BorderLayout.CENTER);
        JLabel hint = new JLabel(
                "<html><center>Sec araciyla tikla,<br>Delete ile sil</center></html>",
                SwingConstants.CENTER);
        hint.setForeground(new Color(120, 120, 160));
        hint.setFont(new Font("SansSerif", Font.ITALIC, 10));
        hint.setBorder(new EmptyBorder(6, 4, 6, 4));
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(membersBox, BorderLayout.NORTH);
        top.add(hint, BorderLayout.SOUTH);
        sidebar.add(top, BorderLayout.NORTH);
        sidebar.add(itemsBox, BorderLayout.CENTER);
        return sidebar;
    }

    private JLabel sectionTitle(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("SansSerif", Font.BOLD, 12));
        lbl.setForeground(new Color(160, 180, 220));
        lbl.setOpaque(true);
        lbl.setBackground(new Color(30, 30, 50));
        lbl.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(60, 60, 90)),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        return lbl;
    }

    private JPanel createTopBarPanel() {
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(theme.background);
        topBar.setBorder(new EmptyBorder(6, 10, 6, 10));
        topBar.setPreferredSize(new Dimension(0, 50));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.setOpaque(false);
        roomCodeLabel = new JLabel("Oda Kodu: " + myRoomCode);
        roomCodeLabel.setFont(new Font("Arial", Font.BOLD, 12));
        roomCodeLabel.setForeground(theme.titleText);
        JButton copyBtn = CustomButton.smallButtonGenerate("Kopyala", theme);
        copyBtn.addActionListener(e -> {
            if (myRoomCode != null) {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(myRoomCode), null);
                JOptionPane.showMessageDialog(frame, "Oda kodu panoya kopyalandı!");
            }
        });
        left.add(roomCodeLabel);
        left.add(copyBtn);
        topBar.add(left, BorderLayout.WEST);
        JPanel center = new JPanel(new FlowLayout(FlowLayout.CENTER, 3, 0));
        center.setOpaque(false);
        String[][] tools = {
                { "\uD83C\uDFAF", "Seç & Kes", "SELECT" },
                { "\u270E", "Kalem", "FREEHAND" },
                { "\uD83D\uDFE6", "Dikdörtgen", "RECTANGLE" },
                { "\u2B55", "Elips", "CIRCLE" },
                { "\u2796", "Çizgi", "LINE" },
                { "\uD83D\uDD3A", "Üçgen", "TRIANGLE" },
                { "\uD83E\uDDFC", "Silgi", "ERASER" }
        };
        ButtonGroup toolGroup = new ButtonGroup();
        for (String[] t : tools) {
            JToggleButton btn = makeToolButton(t[0], t[1]);
            DrawingCanvas.Tool toolEnum = DrawingCanvas.Tool.valueOf(t[2]);
            btn.addActionListener(e -> {
                canvas.setCurrentTool(toolEnum);
            });
            toolGroup.add(btn);
            if (t[2].equals("FREEHAND"))
                btn.setSelected(true);
            center.add(btn);
        }
        JLabel colorLabel = new JLabel("Renk:");
        colorLabel.setFont(new Font("Arial", Font.PLAIN, 11));
        colorLabel.setForeground(theme.titleText);
        center.add(colorLabel);
        JButton colorBtn = CustomButton.smallButtonGenerate("■", theme);
        colorBtn.setPreferredSize(new Dimension(26, 28));
        colorBtn.setBackground(theme.buttonColor);
        colorBtn.addActionListener(e -> {
            Color c = JColorChooser.showDialog(frame, "Renk Seç", canvas.getCurrentColor());
            if (c != null) {
                canvas.setCurrentColor(c);
                colorBtn.setBackground(c);
            }
        });
        center.add(colorBtn);
        JLabel strokeLbl = new JLabel("Kalınlık:");
        strokeLbl.setFont(new Font("Arial", Font.PLAIN, 11));
        strokeLbl.setForeground(theme.titleText);
        center.add(strokeLbl);
        JSpinner strokeSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 20, 1));
        strokeSpinner.setPreferredSize(new Dimension(42, 28));
        strokeSpinner.getEditor().getComponent(0).setBackground(theme.buttonColor);
        ((JSpinner.DefaultEditor) strokeSpinner.getEditor()).getTextField().setForeground(theme.titleText);
        strokeSpinner.addChangeListener(e -> {
            canvas.setStrokeWidth((int) strokeSpinner.getValue());
        });
        center.add(strokeSpinner);
        JToggleButton fillBtn = new JToggleButton("Dolu") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isSelected() ? new Color(160, 200, 255) : theme.buttonColor);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                g2.setColor(new Color(80, 120, 170));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
                g2.setColor(isSelected() ? new Color(20, 20, 60) : Color.WHITE);
                g2.setFont(new Font("Arial", Font.BOLD, 11));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(),
                        (getWidth() - fm.stringWidth(getText())) / 2,
                        (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
        fillBtn.setPreferredSize(new Dimension(46, 28));
        fillBtn.setFocusPainted(false);
        fillBtn.setBorderPainted(false);
        fillBtn.setContentAreaFilled(false);
        fillBtn.addActionListener(e -> canvas.setFilled(fillBtn.isSelected()));
        center.add(fillBtn);
        JButton pasteBtn = CustomButton.smallButtonGenerate("Yapistir", theme);
        pasteBtn.setToolTipText("Resim yapistir (Ctrl+V)");
        pasteBtn.addActionListener(e -> {
            canvas.pasteFromClipboard();
        });
        center.add(pasteBtn);
        topBar.add(center, BorderLayout.CENTER);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.setOpaque(false);
        JButton clearBtn = CustomButton.smallButtonGenerate("Temizle", theme);
        clearBtn.addActionListener(e -> {
            if (networkManager != null) {
                networkManager.sendRaw(NetworkProtocol.buildClear(username));
            }
            canvas.clearCanvas();
        });
        JButton settingsBtn = CustomButton.smallButtonGenerate("Ayarlar", theme);
        settingsBtn.addActionListener(e -> openSettingsDialog());
        JButton leaveBtn = CustomButton.smallButtonGenerate("Ayrıl", theme);
        leaveBtn.addActionListener(e -> {
            if (networkManager != null) {
                networkManager.leaveRoom();
            }
            if (memberModel != null)
                memberModel.clear();
            if (itemListModel != null)
                itemListModel.clear();
            canvas.clearCanvas();
            goLobby();
        });
        right.add(settingsBtn);
        right.add(clearBtn);
        right.add(leaveBtn);
        topBar.add(right, BorderLayout.EAST);
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
        temp.background = theme.background;
        temp.inputBg = theme.inputBg;
        temp.inputBorder = theme.inputBorder;
        temp.buttonColor = theme.buttonColor;
        temp.subText = theme.subText;
        temp.titleText = theme.titleText;
        temp.hintText = theme.hintText;
        String[] labels = {
                "Arka Plan", "Girdi Arka Planı", "Girdi Kenarlık",
                "Buton Rengi", "Alt Metin", "Başlık Metni", "İpucu Metni"
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
            gbc.gridx = 0;
            gbc.gridy = i;
            gbc.weightx = 0.6;
            dialog.add(lbl, gbc);
            gbc.gridx = 1;
            gbc.weightx = 0.4;
            dialog.add(colorBtns[i], gbc);
        }
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnRow.setOpaque(false);
        JButton applyBtn = new CustomButton("Uygula", theme);
        JButton cancelBtn = new CustomButton("İptal", theme);
        applyBtn.addActionListener(e -> {
            theme.background = temp.background;
            theme.inputBg = temp.inputBg;
            theme.inputBorder = temp.inputBorder;
            theme.buttonColor = temp.buttonColor;
            theme.subText = temp.subText;
            theme.titleText = temp.titleText;
            theme.hintText = temp.hintText;
            saveTheme();
            rebuildPanels();
            dialog.dispose();
        });
        cancelBtn.addActionListener(e -> dialog.dispose());
        btnRow.add(cancelBtn);
        btnRow.add(applyBtn);
        gbc.gridx = 0;
        gbc.gridy = labels.length;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(16, 12, 12, 12);
        dialog.add(btnRow, gbc);
        dialog.pack();
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    private void rebuildPanels() {
        java.util.List<CanvasItem> snap = (canvas != null && currentScreen.equals("CANVAS")) ? canvas.getItemsSnapshot()
                : null;

        mainPanel.removeAll();
        mainPanel.add(loginPanel(), "LOGIN");
        mainPanel.add(lobbyPanel(), "LOBBY");
        mainPanel.add(canvasPanel(), "CANVAS");

        if (username != null)
            canvas.setUsername(username);
        canvas.setCursorColor(cursorColor);

        if (snap != null) {
            canvas.loadCanvasState(snap);
            setupNetworkHooks();
        }

        cardLayout.show(mainPanel, currentScreen);
        frame.revalidate();
        frame.repaint();
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

    private void setupNetworkHooks() {
        if (networkManager == null)
            return;
        canvas.setOnShapeDrawn(shape -> networkManager.sendShape(shape));
        canvas.setOnCursorMoved(cp -> networkManager.sendCursor(cp));
        canvas.setOnImagePasted(pi -> {
            String msg = NetworkProtocol.buildImage(username,
                    pi.getXOfImage(), pi.getYOfImage(), pi.getWidthOfImage(), pi.getHeightOfImage(),
                    pi.getImageData(), pi.getIdOfImage());
            networkManager.sendRaw(msg);
        });
        canvas.setOnItemsCut(ids -> {
            for (String id : ids) {
                networkManager.sendRaw(NetworkProtocol.buildDelete(username, id));
            }
        });
    }

    private void setThemeColorByIndex(AppTheme t, int idx, Color c) {
        switch (idx) {
            case 0 -> t.background = c;
            case 1 -> t.inputBg = c;
            case 2 -> t.inputBorder = c;
            case 3 -> t.buttonColor = c;
            case 4 -> t.subText = c;
            case 5 -> t.titleText = c;
            case 6 -> t.hintText = c;
        }
    }

    private void saveTheme() {
        java.util.Properties props = new java.util.Properties();
        props.setProperty("background", colorToHex(theme.background));
        props.setProperty("inputBg", colorToHex(theme.inputBg));
        props.setProperty("inputBorder", colorToHex(theme.inputBorder));
        props.setProperty("buttonColor", colorToHex(theme.buttonColor));
        props.setProperty("subText", colorToHex(theme.subText));
        props.setProperty("titleText", colorToHex(theme.titleText));
        props.setProperty("hintText", colorToHex(theme.hintText));

        try (java.io.FileOutputStream out = new java.io.FileOutputStream(SETTINGS_FILE)) {
            props.store(out, "Theme Settings");
        } catch (java.io.IOException e) {
            System.err.println("Tema kaydedilemedi: " + e.getMessage());
        }
    }

    private void loadTheme() {
        java.util.Properties props = new java.util.Properties();
        try (java.io.FileInputStream in = new java.io.FileInputStream(SETTINGS_FILE)) {
            props.load(in);
            theme.background = hexToColor(props.getProperty("background"));
            theme.inputBg = hexToColor(props.getProperty("inputBg"));
            theme.inputBorder = hexToColor(props.getProperty("inputBorder"));
            theme.buttonColor = hexToColor(props.getProperty("buttonColor"));
            theme.subText = hexToColor(props.getProperty("subText"));
            theme.titleText = hexToColor(props.getProperty("titleText"));
            theme.hintText = hexToColor(props.getProperty("hintText"));
        } catch (java.io.IOException e) {
            // Dosya yoksa veya okunamazsa varsayılanlarla devam et
        }
    }

    private static String colorToHex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static Color hexToColor(String hex) {
        if (hex == null)
            return Color.BLACK;
        try {
            return Color.decode(hex);
        } catch (Exception e) {
            return Color.BLACK;
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        SwingUtilities.invokeLater(MainFrame::new);
    }
}