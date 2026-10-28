package pe.edu.monitoreo;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PasswordsTest {
    @Test void saltedHash(){String a=Passwords.hash("UnaClave12345"),b=Passwords.hash("UnaClave12345");assertNotEquals(a,b);assertTrue(Passwords.verify("UnaClave12345",a));assertFalse(Passwords.verify("incorrecta",a));assertFalse(Passwords.verify("algo","hash-invalido"));}
    @Test void weakPassword(){assertThrows(IllegalArgumentException.class,()->Passwords.hash("123"));}
}
