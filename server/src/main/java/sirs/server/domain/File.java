package sirs.server.domain;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

//TODO: there may be some cases where we get dangling files
@Entity
@Table(name = "file")
public class File {

    @Id
    private int id;

    @Column(name = "version", nullable = false)
    private int version;

    @ManyToOne
    @JoinColumn(name = "owner", nullable = false)
    private User owner;

    @ManyToOne
    @JoinColumn(name = "last_modifier", nullable = false)
    private User lastModifier;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "path", nullable = false, unique = true)
    private String path;

    @Column(name = "signature", nullable = false, columnDefinition = "BLOB")
    private byte[] signature;

    @ManyToMany(fetch = FetchType.EAGER, mappedBy = "files", cascade = CascadeType.REMOVE)
    private List<User> collaborators = new ArrayList<>();

    @OneToMany(mappedBy = "file", cascade = CascadeType.REMOVE)
    private List<Invite> pendingInvites = new ArrayList<Invite>();

    public File() {
    }

    public File(int version, User lastModifier, String name, String path) {
        this.version = version;
        this.lastModifier = lastModifier;
        this.name = name;
        this.path = path;
    }

    public int getId() {
        return id;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public void incrementVersion() {
        this.version++;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public List<User> getCollaborators() {
        return collaborators;
    }

    public void setCollaborators(List<User> collaborators) {
        this.collaborators = collaborators;
    }

    public void clearCollaborators() {
        this.collaborators.clear();
    }

    public User getLastModifier() {
        return lastModifier;
    }

    public void setLastModifier(User lastModifier) {
        this.lastModifier = lastModifier;
    }

    public void addCollaborator(User user) {
        this.collaborators.add(user);
    }

    public void removeCollaborator(int id) {
        this.collaborators = this.collaborators.stream().filter(user -> user.getId() != id).collect(Collectors.toList());
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void removeCollaborator(User user) {
        this.collaborators.remove(user);
    }

    public void removeInvite(Invite invite) {
        this.pendingInvites.remove(invite);
    }

    public void setSignature(byte[] signature) {
        this.signature = signature;
    }

    public byte[] getSignature() {
        return signature;
    }

    @Override
    public String toString() {
        return "File{" +
                "id=" + id +
                ", version=" + version +
                ", path='" + path + '\'' +
                ", shares=" + collaborators +
                '}';
    }
}
