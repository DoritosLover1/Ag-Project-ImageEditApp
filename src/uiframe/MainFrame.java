package uiframe;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

import additional.FileItem;
import panels.CanvasPane;

public class MainFrame {

    JFrame frame;

    JTextField txtUser = new JTextField();

    JButton btnLogin = new JButton("Giriş");
    JButton btnAddFriend = new JButton("Arkadaş Ekle");

    DefaultListModel<String> friendModel = new DefaultListModel<>();
    JList<String> friendList = new JList<>(friendModel);

    JTextField friendInput = new JTextField();

    DefaultListModel<FileItem> myFilesModel = new DefaultListModel<>();
    JList<FileItem> myFilesList = new JList<>(myFilesModel);

    DefaultListModel<FileItem> sharedFilesModel = new DefaultListModel<>();
    JList<FileItem> sharedFilesList = new JList<>(sharedFilesModel);

    JTabbedPane tabs = new JTabbedPane();

    java.util.List<FileItem> allFiles = new ArrayList<>();
    Map<FileItem, CanvasPane> openedTabs = new HashMap<>();

    public MainFrame() {
        init();
    }

    private void init() {

        frame = new JFrame("Paint System");
        frame.setSize(1000, 661);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().setLayout(null);

        txtUser.setBounds(10, 41, 120, 25);
        frame.getContentPane().add(txtUser);
        btnLogin.setFocusPainted(false);
        btnLogin.setRequestFocusEnabled(false);

        btnLogin.setBounds(140, 41, 80, 25);
        frame.getContentPane().add(btnLogin);

        JLabel usernameLabel = new JLabel("Kullanıcı Adı");
        usernameLabel.setBounds(41, 22, 94, 14);
        frame.getContentPane().add(usernameLabel);

        JLabel friendLabel = new JLabel("Arkadaş Listesi");
        friendLabel.setBounds(58, 77, 112, 20);
        frame.getContentPane().add(friendLabel);

        friendList.setBounds(10, 102, 180, 150);
        frame.getContentPane().add(friendList);

        friendInput.setBounds(36, 258, 120, 25);
        frame.getContentPane().add(friendInput);
        btnAddFriend.setRequestFocusEnabled(false);
        btnAddFriend.setFocusPainted(false);

        btnAddFriend.setBounds(36, 286, 120, 25);
        frame.getContentPane().add(btnAddFriend);

        btnAddFriend.addActionListener(e -> {
            friendModel.addElement(friendInput.getText());
        });

        JLabel myLabel = new JLabel("📁 Benim Paylaştıklarım");
        myLabel.setBounds(40, 318, 120, 20);
        frame.getContentPane().add(myLabel);

        myFilesList.setBounds(10, 340, 180, 100);
        frame.getContentPane().add(myFilesList);

        JLabel sharedLabel = new JLabel("📥 Benimle Paylaşılanlar");
        sharedLabel.setBounds(41, 486, 140, 20);
        frame.getContentPane().add(sharedLabel);

        sharedFilesList.setBounds(10, 511, 180, 100);
        frame.getContentPane().add(sharedFilesList);

        JButton btnAddFile = new JButton("Fotoğraf Ekle");
        btnAddFile.setFocusPainted(false);
        btnAddFile.setRequestFocusEnabled(false);
        btnAddFile.setBounds(41, 450, 120, 25);
        frame.getContentPane().add(btnAddFile);

        JButton btnAddTab = new JButton("+ Tab");
        btnAddTab.setRequestFocusEnabled(false);
        btnAddTab.setFocusPainted(false);
        btnAddTab.setBounds(810, 57, 90, 25);
        frame.getContentPane().add(btnAddTab);

        tabs.setBounds(270, 93, 630, 450);
        frame.getContentPane().add(tabs);

        btnLogin.addActionListener(e -> {
            JOptionPane.showMessageDialog(frame,
                    "Giriş yapıldı: " + txtUser.getText());
            refreshFiles();
        });

        btnAddFriend.addActionListener(e -> {
            friendModel.addElement(friendInput.getText());
        });

        btnAddFile.addActionListener(e -> {

            String user = txtUser.getText();

            ArrayList<String> allowed = new ArrayList<>();
            allowed.add(user);

            FileItem file = new FileItem(
                    "Dosya " + (allFiles.size() + 1),
                    user,
                    allowed
            );

            allFiles.add(file);
            refreshFiles();
        });

        myFilesList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                openFile(myFilesList.getSelectedValue());
            }
        });

        sharedFilesList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                openFile(sharedFilesList.getSelectedValue());
            }
        });

        btnAddTab.addActionListener(e -> {

            String user = txtUser.getText();

            FileItem file = new FileItem(
                    "Dosya " + (allFiles.size() + 1),
                    user,
                    new ArrayList<>(List.of(user))
            );

            allFiles.add(file);
            refreshFiles();
        });

        frame.setVisible(true);
    }

    private void openFile(FileItem selected) {

        String user = txtUser.getText();

        if (selected == null) return;
        if (!selected.canView(user)) return;

        if (openedTabs.containsKey(selected)) {
            tabs.setSelectedComponent(openedTabs.get(selected));
            return;
        }

        CanvasPane canvas = new CanvasPane();

        openedTabs.put(selected, canvas);
        tabs.addTab(selected.getName(), canvas);
    }

    private void refreshFiles() {

        myFilesModel.clear();
        sharedFilesModel.clear();

        String user = txtUser.getText();

        for (FileItem f : allFiles) {

            if (f.owner.equals(user)) {
                myFilesModel.addElement(f);
            }
            else if (f.canView(user)) {
                sharedFilesModel.addElement(f);
            }
        }
    }

    public static void main(String[] args) {
        new MainFrame();
    }
}