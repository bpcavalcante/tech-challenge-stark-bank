package stark_bank.challenge.util;

import com.starkbank.Key;

public class KeyGenerator {
    public static void main(String[] args) throws Exception {
        Key key = Key.create("keys/");

        System.out.println("=== CHAVE PRIVADA (guarde com segurança) ===");
        System.out.println(key.privatePem);
        System.out.println("=== CHAVE PÚBLICA (cole no Stark Bank) ===");
        System.out.println(key.publicPem);
        System.out.println("Arquivos salvos em: keys/private-key.pem e keys/public-key.pem");
    }
}
