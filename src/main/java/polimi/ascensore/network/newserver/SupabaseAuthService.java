package polimi.ascensore.network.newserver;

// application.properties
// supabase.jwt.secret=la-tua-stringa-super-segreta-copiata-dalla-dashboard

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.math.BigInteger;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.util.Base64;

@Service
public class SupabaseAuthService {

    @Value("${supabase.jwks-url}")
    private String jwksUrl;

    private PublicKey publicKey;

    // Questo metodo viene eseguito una volta all'avvio del server
    @PostConstruct
    public void init() {
        try {
            this.publicKey = fetchPublicKeyFromSupabase();
            System.out.println("✅ Chiave Pubblica ES256 caricata correttamente da Supabase");
        } catch (Exception e) {
            System.err.println("❌ Errore critico nel caricamento della chiave: " + e.getMessage());
        }
    }

    private PublicKey fetchPublicKeyFromSupabase() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(new URL(jwksUrl));
        // Prendiamo la prima chiave dell'array "keys"
        JsonNode keyNode = root.get("keys").get(0);

        String xBase64 = keyNode.get("x").asText();
        String yBase64 = keyNode.get("y").asText();

        // Convertiamo Base64URL in coordinate BigInteger
        byte[] xBytes = Base64.getUrlDecoder().decode(xBase64);
        byte[] yBytes = Base64.getUrlDecoder().decode(yBase64);
        BigInteger x = new BigInteger(1, xBytes);
        BigInteger y = new BigInteger(1, yBytes);

        // Definiamo i parametri della curva P-256 (usata da ES256)
        AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
        params.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec ecParameters = params.getParameterSpec(ECParameterSpec.class);

        // Creiamo la chiave pubblica
        ECPoint ecPoint = new ECPoint(x, y);
        ECPublicKeySpec keySpec = new ECPublicKeySpec(ecPoint, ecParameters);
        KeyFactory kf = KeyFactory.getInstance("EC");
        return kf.generatePublic(keySpec);
    }

    public String validateAndGetUserId(String token) {
        try {
            if (token == null) return null;

            // Pulizia aggressiva: rimuove virgolette, spazi e caratteri invisibili
            String cleanToken = token.replace("\"", "").trim();

            if (cleanToken.startsWith("Bearer ")) {
                cleanToken = cleanToken.substring(7).trim();
            }

            // DEBUG LOG: Vediamo esattamente cosa stiamo cercando di parsare
            System.out.println("Validating token: " + cleanToken.substring(0, 10) + "..." +
                    " (Length: " + cleanToken.length() + ")");

            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(this.publicKey)
                    .build()
                    .parseClaimsJws(cleanToken) // <--- Usa la versione pulita!
                    .getBody();

            return claims.getSubject();
        } catch (Exception e) {
            System.err.println("Errore validazione token: " + e.getMessage());
            return null;
        }
    }
}