package sirs.server.domain;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "user")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "username", unique = true, nullable = false)
    private String username;

    @OneToMany(mappedBy = "user", fetch = FetchType.EAGER, cascade = CascadeType.REMOVE)
    private List<Invite> pendingInvites = new ArrayList<Invite>();

    @ManyToMany(fetch = FetchType.LAZY, cascade = CascadeType.REMOVE)
    @JoinColumn(name = "file_ids", nullable = false)
    private List<File> files = new ArrayList<File>();

    @Column(name = "certificate", nullable = false, columnDefinition = "BLOB")
    private byte[] certificate;

    public User() {
    }

    public User(String username, byte[] certificate) {
        this.username = username;
        this.certificate = certificate;
    }

    public int getId() {
        return id;
    }

    public List<Invite> getPendingInvites() {
        return pendingInvites;
    }

    public void setPendingInvites(List<Invite> pendingInvites) {
        this.pendingInvites = pendingInvites;
    }

    public List<File> getFiles() {
        return files;
    }

    public void setFiles(List<File> files) {
        this.files = files;
    }

    public byte[] getCertificate() {
        return certificate;
    }

    public void setCertificate(byte[] certificate) {
        this.certificate = certificate;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void addInvite(Invite invite) {
        this.pendingInvites.add(invite);
    }

    public void addFile(File file) {
        this.files.add(file);
    }

    public File getFileByName(String fileName) {
        for (File file : files) {
            if (file.getName().equals(fileName)) {
                return file;
            }
        }
        return null;
    }

    public Invite getInviteByFileName(String fileName) {
        for (Invite invite : pendingInvites) {
            if (invite.getFile().getName().equals(fileName)) {
                return invite;
            }
        }
        return null;
    }

    public void removeInvite(Invite invite) {
        this.pendingInvites.remove(invite);
    }

    public void removeInvite(int inviteId) {
        for (int i = 0; i < this.pendingInvites.size(); i++) {
            if (this.pendingInvites.get(i).getId() == inviteId) {
                this.pendingInvites.remove(i);
                return;
            }
        }
    }

    public void removeFile(File file) {
        this.files.remove(file);
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", pendingInvites=" + pendingInvites +
                ", files=" + files +
                ", certificate=" + Arrays.toString(certificate) +
                '}';
    }
}
