package sirs.server.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import sirs.server.domain.File;
import sirs.server.domain.User;
import sirs.server.repository.FileRepository;
import sirs.server.repository.UserRepository;

import java.sql.SQLException;
import java.util.List;

@Service
public class FileService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FileRepository fileRepository;

    @Retryable(value = {SQLException.class}, backoff = @Backoff(delay = 5000))
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void createFile(int ownerId, String fileName, byte[] signature) {
        String path = "./users/" + ownerId + "/" + fileName;
        User owner = userRepository.findById(ownerId).orElseThrow();
        File file = new File(1, owner, fileName, path);
        file.setOwner(owner);
        file.addCollaborator(owner);
        file.setLastModifier(owner);
        file.setSignature(signature);
        owner.addFile(file);
        fileRepository.save(file);
    }

    @Retryable(value = {SQLException.class}, backoff = @Backoff(delay = 5000))
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public File getFileByPath(String path) {
        File file = fileRepository.findByPath(path).orElse(null);
        return file;
    }

    @Retryable(value = {SQLException.class}, backoff = @Backoff(delay = 5000))
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public File getFileByUser(String name, int userId) {
        User user = userRepository.findById(userId).orElseThrow();
        return user.getFileByName(name);
    }


    @Retryable(value = {SQLException.class}, backoff = @Backoff(delay = 5000))
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public List<User> getFileCollaboratos(int fileId) {
        File file = fileRepository.findById(fileId).orElseThrow();
        return file.getCollaborators();
    }

    @Retryable(value = {SQLException.class}, backoff = @Backoff(delay = 5000))
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void clearCollaborators(int fileId) {
        File file = fileRepository.findById(fileId).orElseThrow();

        for (User user : file.getCollaborators()) {
            user.removeFile(file);
        }

        file.clearCollaborators();
    }

    @Retryable(value = {SQLException.class}, backoff = @Backoff(delay = 5000))
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void updateFile(String path, byte[] signature, int modifierId) {
        User modifier = userRepository.findById(modifierId).orElseThrow();
        File file = fileRepository.findByPath(path).orElse(null);
        file.incrementVersion();
        file.setSignature(signature);
        file.setLastModifier(modifier);
    }


    @Retryable(value = {SQLException.class}, backoff = @Backoff(delay = 5000))
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void deleteFile(int fileId) {
        fileRepository.deleteById(fileId);
    }

    @Retryable(value = {SQLException.class}, backoff = @Backoff(delay = 5000))
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void addCollaborator(int fileId, int userId) {
        File file = fileRepository.findById(fileId).orElseThrow();
        User user = userRepository.findById(userId).orElseThrow();
        file.addCollaborator(user);
        user.addFile(file);
    }
}
