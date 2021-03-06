package sirs.client;

import com.google.protobuf.ByteString;
import io.grpc.StatusRuntimeException;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.SSLException;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.security.KeyPair;
import java.security.Key;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.security.KeyPairGenerator;
import java.security.KeyFactory;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.List;
import org.json.simple.*;
import org.json.simple.parser.*;
import java.util.Scanner;

import pt.ulisboa.tecnico.sdis.zk.ZKNamingException;
import sirs.client.SecurityLogic;
import org.apache.commons.lang3.tuple.Pair;
import org.bouncycastle.operator.OperatorCreationException;

public class ClientLogic {

    ClientFrontend frontend;
    String username = "";
    char[] password;
    JSONObject cache;

    public ClientLogic(String zkHost, String zkPort) throws IOException, ParseException, ZKNamingException {
        this.frontend = new ClientFrontend(zkHost, zkPort);

        // Load file cache
        try {
            FileInputStream fis = new FileInputStream(".fileCache.json");
            this.cache = (JSONObject) new JSONParser().parse(new FileReader(".fileCache.json"));
            fis.close();
        } catch (FileNotFoundException e) {
            this.cache = new JSONObject();
        }
    }

    public void register(String username) throws OperatorCreationException, GeneralSecurityException, IOException {

        // Username validation
        for (char chr : username.toCharArray()) {
            char c = Character.toUpperCase(chr);
            if ((c < 'A' || c > 'Z') && (chr < '0' || chr > '9')) {
                System.out.println("Unable to register: Invalid username");
                return;
            }
        }

        Pair<PrivateKey, Certificate> pair = SecurityLogic.generateCertificate();


        try {
            ByteString certificate = ByteString.copyFrom(pair.getRight().getEncoded());
            frontend.register(username, certificate);
            System.out.println("User registered successfully");
            System.out.print("Set password:");
            System.out.flush();
            char[] newPassword = System.console().readPassword();
            SecurityLogic.createKeystore(username, newPassword, pair.getLeft(), pair.getRight());
        } catch (StatusRuntimeException e) {
            System.out.println("Unable to register: " + e.getMessage());
        }
    }

    public void login(String username) {
        try {
            this.username = username;
            System.out.print("Insert password:");
            System.out.flush();
            this.password = System.console().readPassword();
            PrivateKey privateKey = (PrivateKey)  SecurityLogic.getKey(username, this.password, "auth");
            List<String> invites = this.frontend.login(username, privateKey);
            System.out.println("User logged in successfully");
            
            for (String invite : invites) {
                String filename = invite.substring(0, invite.lastIndexOf('.'));
                if (SecurityLogic.getKey(this.username, this.password, filename) == null)
                    System.out.println("You have been invited to edit " + filename);
                else {
                    System.out.println("A user has been removed from " + filename + "'s share");
                    System.out.println("Getting new key and file");
                    this.accept(filename);
                    this.download(filename);
                }
            }
        } catch (StatusRuntimeException | IOException | GeneralSecurityException e) {
            System.out.println("Unable to login: " + e.getMessage());
        }
    }

    // Unlocks local file without having to contact the server
    public void unlock(String fileName) throws GeneralSecurityException, IOException {
        String encryptedFile = "./files/" + fileName + ".aes";
        Key fileKey;
        JSONObject file = (JSONObject) this.cache.get(fileName);
        String lastModifier;

        if (this.username.equals("")) {
            System.out.println("User not logged in");
            return;
        }

        if (file == null) {
            System.out.println("Unable to unlock: File not in cache");
            return;
        } else {
            lastModifier = (String) file.get("lastModifier");
        }

        try {
            fileKey = SecurityLogic.getKey(this.username, this.password, fileName);
        } catch (GeneralSecurityException | IOException e) {
            System.out.println("Unable to unlock: " + e.getMessage());
            return;
        }

        if (fileKey == null) {
            System.out.println("Unable to unlock: You do not have the key for this file");
            return;
        }

        byte[] iv = SecurityLogic.usernameToIV(lastModifier);
        try {
            SecurityLogic.transform(encryptedFile, fileName, fileKey, Cipher.DECRYPT_MODE, iv);
        } catch (NoSuchFileException e) {
            System.out.println("Unable to unlock: " + e.getMessage());
            return;
        }
        System.out.println("File unlocked");
    }

    public void upload(String fileName) throws GeneralSecurityException, IOException {
        Key fileKey;
        JSONObject file = (JSONObject) this.cache.get(fileName);
        String owner;

        if (this.username.equals("")) {
            System.out.println("User not logged in");
            return;
        }

        try {
            // Get file owner and key
            if (file == null) {
                owner = this.username;
                fileKey = SecurityLogic.generateAESKey(this.username, this.password, fileName);
            } else {
                owner = (String) file.get("owner");
                fileKey = SecurityLogic.getKey(this.username, this.password, fileName);
            }
        } catch (GeneralSecurityException | IOException e) {
            System.out.println("Unable to upload: " + e.getMessage());
            return;
        }

        java.io.File directory = new java.io.File("./files/");
        if (!directory.exists()) {
            directory.mkdir();
        }

        // Encrypt the file
        String encryptedFile = "./files/" + fileName + ".aes";
        byte[] iv = SecurityLogic.usernameToIV(this.username);
        try {
            SecurityLogic.transform(fileName, encryptedFile, fileKey, Cipher.ENCRYPT_MODE, iv);
        } catch (NoSuchFileException e) {
            System.out.println("Unable to upload: " + e.getMessage());
            return;
        }

        // Sign the file
        PrivateKey signKey = (PrivateKey) SecurityLogic.getKey(this.username, this.password, "auth");
        byte[] signature = SecurityLogic.sign(encryptedFile, signKey);

        int version;
        try {
            version = this.frontend.upload(fileName + ".aes", signature, owner);
        } catch(Exception e) {
            System.out.println("Unable to upload: " + e.getMessage());
            return;
        }

        // Update cache
        if (file == null) {
            file = new JSONObject();
            file.put("owner", this.username);
            file.put("version", 1);
            file.put("lastModifier", this.username);
        } else {
            file.put("version", version);
            file.put("lastModifier", this.username);
        }
        this.cache.put(fileName, file);

        System.out.println("File uploaded");
    }

    public void download(String fileName) throws GeneralSecurityException, IOException {

        if (this.username.equals("")) {
            System.out.println("User not logged in");
            return;
        }

        java.io.File directory = new java.io.File("./files/");
        if (!directory.exists()) {
            directory.mkdir();
        }

        Key fileKey = SecurityLogic.getKey(this.username, this.password, fileName);
        String encryptedFile = fileName + ".aes";
        JSONObject file;
        try {
            file = this.frontend.download(encryptedFile);
        } catch (Exception e) {
            System.out.println("Unable to download: " + e.getMessage());
            return;
        }
        if (file != null) {
            String lastModifier = (String) file.get("lastModifier");
            System.out.println("Signature verified");
            byte[] iv = SecurityLogic.usernameToIV(lastModifier);
            SecurityLogic.transform("./files/" + encryptedFile, fileName, fileKey, Cipher.DECRYPT_MODE, iv);

            // Update cache
            this.cache.put(fileName, file);
        } else
            System.out.println("File was tampered. Try again later or unlock local version");

    }

    // Removes user from shar
    public void remove(String username, String fileName) throws GeneralSecurityException, IOException {

        if (this.username.equals("")) {
            System.out.println("User not logged in");
            return;
        }

        List<Pair<String, ByteString>> users;
        try {
            users = this.frontend.remove(fileName + ".aes", username);
        } catch (Exception e) {
            System.out.println("Unable to remove: " + e.getMessage());
            return;
        }
        
        Key fileKey;
        try {
            fileKey = SecurityLogic.generateAESKey(this.username, this.password, fileName);
        } catch (GeneralSecurityException | IOException e) {
            System.out.println("Unable to remove: " + e.getMessage());
            return;
        }

        // Reupload file encrypted with new key
        this.upload(fileName);

        // Resend invites to other collaborators
        for (Pair<String, ByteString> user : users) {
            String invited = user.getLeft();
            ByteString cert = user.getRight();

            byte[] certBytes = new byte[cert.size()];
            cert.copyTo(certBytes, 0);
            X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(certBytes));

            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, certificate);
            byte[] keyBytes = cipher.doFinal(fileKey.getEncoded());

            String encryptedFile = fileName + ".aes";
            try {
                this.frontend.invite(invited, encryptedFile, keyBytes);
            } catch (Exception e) {
                System.out.println("Unable to resend invites: " + e.getMessage());
                return;
            }
        }
        System.out.println("User removed");
    }

    // Invites user to edit file
    public void invite(String username, String fileName) throws GeneralSecurityException, IOException { 
           
        if (this.username.equals("")) {
            System.out.println("User not logged in");
            return;
        }

        byte[] certBytes;
        try {
            certBytes = this.frontend.share(username);
        } catch (Exception e) {
            System.out.println("Unable to get user's certificate: " + e.getMessage());
            return;
        }
        X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(certBytes));

        byte[] fileKey;
        try {
            fileKey = SecurityLogic.getKey(this.username, this.password, fileName).getEncoded();
        } catch (GeneralSecurityException | IOException e) {
            System.out.println("Unable to send invite: " + e.getMessage());
            return;
        }

        if (fileKey == null) {
            System.out.println("Unable to invite: File key not found");
            return;
        }

        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, certificate);
        byte[] keyBytes = cipher.doFinal(fileKey);

        String encryptedFile = fileName + ".aes";
        try {
            this.frontend.invite(username, encryptedFile, keyBytes);
        } catch (Exception e) {
            System.out.println("Unable to send invite: " + e.getMessage());
            return;
        }
        System.out.println("Invite sent");
    }

    // Accepts invite to edit file
    public void accept(String fileName) throws GeneralSecurityException, FileNotFoundException, IOException {

        if (this.username.equals("")) {
            System.out.println("User not logged in");
            return;
        }

        byte[] encryptedKeyBytes;
        try {
            encryptedKeyBytes = this.frontend.accept(fileName + ".aes");
        } catch (Exception e) {
            System.out.println("Unable to accept invite: " + e.getMessage());
            return;
        }
        PrivateKey privKey = (PrivateKey) SecurityLogic.getKey(this.username, this.password, "auth");
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, privKey);
        byte[] keyBytes = cipher.doFinal(encryptedKeyBytes);
        Key key = new SecretKeySpec(keyBytes, 0, 16, "AES");

        try {
            SecurityLogic.saveKey(this.username, this.password, fileName, key);
        } catch (GeneralSecurityException | IOException e) {
            System.out.println("Unable to accept invite: " + e.getMessage());
        }
        System.out.println("Invite accepted");
    }

    public void close() throws IOException  {
        FileOutputStream fos = new FileOutputStream(".fileCache.json");
        fos.write(this.cache.toString().getBytes());
        fos.close();

        this.frontend.close();
    }
}
