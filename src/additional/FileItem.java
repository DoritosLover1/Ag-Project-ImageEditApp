package additional;

import java.util.List;

public class FileItem {

    String name;
    public String owner;
    List<String> allowedUsers;

    public FileItem(String name, String owner, List<String> allowedUsers) {
        this.name = name;
        this.owner = owner;
        this.allowedUsers = allowedUsers;
    }

    public boolean canView(String user) {
        return owner.equals(user) || allowedUsers.contains(user);
    }

    public String toString() {
        return name + " | owner: " + owner;
    }
    
	public String getName() {
        return name;
    }
}