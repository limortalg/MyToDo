package limor.tal.mytodo;

import java.util.Date;

public class User {
    public String uid;
    public String email;
    public String displayName;
    public String photoUrl;
    public Date createdAt;
    public Date lastLogin;
    public boolean isSignedIn;

    public User() {
        // Default constructor required for Firestore
    }

    public User(String uid, String email, String displayName, String photoUrl) {
        this.uid = uid;
        this.email = email;
        this.displayName = displayName;
        this.photoUrl = photoUrl;
        this.createdAt = new Date();
        this.lastLogin = new Date();
        this.isSignedIn = true;
    }

    // Getters and setters
    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getLastLogin() { return lastLogin; }
    public void setLastLogin(Date lastLogin) { this.lastLogin = lastLogin; }

    public boolean isSignedIn() { return isSignedIn; }
    public void setSignedIn(boolean signedIn) { isSignedIn = signedIn; }
}
