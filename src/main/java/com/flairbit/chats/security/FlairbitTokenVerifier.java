package com.flairbit.chats.security;

import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Component;

import java.security.interfaces.RSAPublicKey;
import java.util.Date;

@Component
public class FlairbitTokenVerifier {

    private final RSAPublicKey flairbitPublicKey;

    public FlairbitTokenVerifier(RSAPublicKey flairbitPublicKey) {
        this.flairbitPublicKey = flairbitPublicKey;
    }

    public JWTClaimsSet verifyAndExtract(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            JWSVerifier verifier = new RSASSAVerifier(flairbitPublicKey);

            if (!signedJWT.verify(verifier)) {
                throw new SecurityException("Invalid Flairbit token signature");
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            Date now = new Date();
            if (claims.getExpirationTime() == null || now.after(claims.getExpirationTime())) {
                throw new SecurityException("Token expired");
            }

            return claims;
        } catch (Exception e) {
            throw new SecurityException("Token verification failed: " + e.getMessage());
        }
    }
}
