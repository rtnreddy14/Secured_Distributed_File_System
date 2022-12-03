import java.io.*;
import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.Array;
import java.util.*;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;

@SuppressWarnings("unchecked")
public class P2PMasterImpl extends UnicastRemoteObject implements P2PMaster {

    public HashMap<String, List<User>> fileUsers;
    public Set<User> allUsers; // username -> userPublicKey

    protected P2PMasterImpl() throws IOException {
        super();
        allUsers = new HashSet<>();
        fileUsers = new HashMap<>();

        // Set up config file for storing authorized users
        File allUsersDB = new File("configurations/allUsers");
        allUsersDB.getParentFile().mkdirs();
        if (allUsersDB.createNewFile())
            System.out.println("Created configuration file " + allUsersDB.getPath());
        if (allUsersDB.length() != 0) {
            try {
                FileInputStream fis = new FileInputStream(allUsersDB.getPath());
                ObjectInputStream ois = new ObjectInputStream(fis);
                allUsers = (HashSet<User>) ois.readObject();
                fis.close();
                ois.close();
            } catch (IOException e) {
                System.out.println("An error occurred.");
                e.printStackTrace();
                return;
            } catch (ClassNotFoundException e) {
                System.out.println("Clasrs not found.");
                e.printStackTrace();
                return;
            }
        } else {
            allUsers = new HashSet<>();
        }
        System.out.println("Loaded configuration file " + allUsersDB.getPath());
    }

    @Override
    public List<User> getPeerInfo(String filePath) throws RemoteException {
        if (fileUsers.containsKey(filePath)) {
            return fileUsers.get(filePath);
        }
        return null;
    }

    @Override
    public String registerUser(String userName, String userIP, String userPort, PublicKey userPublicKey)
            throws RemoteException {
        String ans = null;
        User newUser = new User(userName, userIP, userPort, userPublicKey, null);
        if (allUsers.contains(newUser)) {
            System.out.println("User '" + newUser.name + "' already exists");
            return ans;
        }
        newUser.setEKey(getSecureRandomKey("AES", 256));
        allUsers.add(newUser);
        updateAllUsers();
        ans = encryptWithPublicKey(newUser.getEKey(), newUser.getPKey());
        System.out.println(newUser.getEKey());
        return ans;
    }

    @Override
    public User getRandomPeer() throws RemoteException {
        System.out.println("Users we have:");
        System.out.println(allUsers);
        User[] userArray = allUsers.toArray(new User[allUsers.size()]);
        // generate a random number
        Random random = new Random();
        // this will generate a random number between 0 and
        // HashSet.size - 1
        int randomNumber = random.nextInt(allUsers.size());
        return userArray[randomNumber];
    }

    @Override
    public void updateHashTable(String filePath, User user) {
        List<User> users;
        if (fileUsers.containsKey(filePath)) {
            users = fileUsers.get(filePath);
            users.add(user);
        } else {
            users = new ArrayList<>();
            users.add(user);
        }
        fileUsers.put(filePath, users);
    }

    private void updateAllUsers() {
        try {
            FileOutputStream myFileOutStream = new FileOutputStream("configurations/allUsers");
            ObjectOutputStream myObjectOutStream = new ObjectOutputStream(myFileOutStream);
            myObjectOutStream.writeObject(allUsers);
            myObjectOutStream.close();
            myFileOutStream.close();
        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }

    private static String getSecureRandomKey(String cipher, int keySize) {
        byte[] secureRandomKeyBytes = new byte[keySize / 8];
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(secureRandomKeyBytes);
        return new String(Base64.getEncoder().encode(new SecretKeySpec(secureRandomKeyBytes, cipher).getEncoded()));
    }

    private static String encryptWithPublicKey(String plain, PublicKey pkey) {
        try {
            Cipher encryptCipher = Cipher.getInstance("RSA");
            encryptCipher.init(Cipher.ENCRYPT_MODE, pkey);
            byte[] secretMessageBytes = plain.getBytes(StandardCharsets.UTF_8);
            byte[] encryptedMessageBytes = encryptCipher.doFinal(secretMessageBytes);
            return Base64.getEncoder().encodeToString(encryptedMessageBytes);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    public static SecretKeySpec convertKey(final String myKey) {
        MessageDigest sha = null;
        byte[] key;
        try {
            key = myKey.getBytes(StandardCharsets.UTF_8);
            sha = MessageDigest.getInstance("SHA-1");
            key = sha.digest(key);
            key = Arrays.copyOf(key, 16);
            return new SecretKeySpec(key, "AES");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }

    // File encryption
    public static String encryption(final String strToEncode, final String key) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding"); // (or) AES/GCM/NoPadding
            cipher.init(Cipher.ENCRYPT_MODE, convertKey(key));
            return Base64.getEncoder()
                    .encodeToString(cipher.doFinal(strToEncode.getBytes("UTF-8")));
        } catch (Exception e) {
            System.out.println("Something went wrong in encryption: " + e.toString());
        }
        return null;
    }

    // File decryption
    public static String decryption(final String strToDecode, final String key) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, convertKey(key));
            return new String(cipher.doFinal(Base64.getDecoder().decode(strToDecode)));
        } catch (Exception e) {
            System.out.println("Something went wrong in decryption : " + e.toString());
        }
        return null;
    }
}
