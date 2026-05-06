package customelements;
import uiframe.MainFrame.AppTheme;
import java.awt.*;
import javax.swing.*;
public class CustomButton extends JButton {
    public CustomButton(String text, AppTheme theme) {
        super(text);
        setBackground(theme.buttonColor);
        setForeground(theme.titleText);
        setFont(new Font("Arial", Font.BOLD, 14));
        setFocusPainted(false);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(theme.buttonColor.darker()),
                BorderFactory.createEmptyBorder(10, 20, 10, 20)));
        setCursor(new Cursor(Cursor.HAND_CURSOR));
    }
    public static void addInputFieldAsForm(JPanel panel, int row,
            GridBagConstraints gbc, String labelText,
            JTextField textField, AppTheme theme) {
        JLabel label = new JLabel(labelText);
        label.setForeground(theme.titleText);
        label.setFont(new Font("Arial", Font.PLAIN, 14));
        textField.setPreferredSize(new Dimension(150, 30));
        textField.setFont(new Font("Arial", Font.PLAIN, 14));
        textField.setBackground(theme.inputBg);
        textField.setForeground(theme.titleText);
        textField.setCaretColor(theme.titleText);
        textField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(theme.inputBorder),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)));
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 1; gbc.weightx = 0.3;
        panel.add(label, gbc);
        gbc.gridx = 1; gbc.weightx = 0.7;
        panel.add(textField, gbc);
    }
	public static JButton smallButtonGenerate(String text, AppTheme theme) {
		JButton button = new JButton(text);
		button.setBackground(theme.buttonColor);
		button.setForeground(theme.titleText);
		button.setFont(new Font("Arial", Font.BOLD, 12));
		button.setFocusPainted(false);
		button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(theme.buttonColor.darker()),
				BorderFactory.createEmptyBorder(5, 10, 5, 10)));
		button.setCursor(new Cursor(Cursor.HAND_CURSOR));
		return button;
	}
}
