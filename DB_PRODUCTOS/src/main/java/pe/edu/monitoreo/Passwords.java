package pe.edu.monitoreo;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.*;
import java.util.*;

public final class Passwords {
    private static final int ITERATIONS=210_000;
    private Passwords() {}
    public static String hash(String password) {
        if(password.length()<10 || password.length()>256) throw new IllegalArgumentException("La contraseña requiere entre 10 y 256 caracteres.");
        byte[] salt=new byte[16]; new SecureRandom().nextBytes(salt);
        return ITERATIONS+":"+Base64.getEncoder().encodeToString(salt)+":"+Base64.getEncoder().encodeToString(derive(password,salt,ITERATIONS));
    }
    public static boolean verify(String password,String hash) {
        try {
            if(password.length()>256) return false;
            var parts=hash.split(":"); int iterations=Integer.parseInt(parts[0]);
            if(parts.length!=3 || iterations<100_000 || iterations>1_000_000) return false;
            return MessageDigest.isEqual(Base64.getDecoder().decode(parts[2]),derive(password,Base64.getDecoder().decode(parts[1]),iterations));
        } catch(RuntimeException e) { return false; }
    }
    private static byte[] derive(String password,byte[] salt,int iterations) {
        var spec=new PBEKeySpec(password.toCharArray(),salt,iterations,256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        catch(GeneralSecurityException e) { throw new IllegalStateException(e); }
        finally { spec.clearPassword(); }
    }
}
