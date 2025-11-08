package com.flairbit.chats.client;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.Date;

@Component
public class ServiceAuthClient {

    private final RSAKey privateKey;
    @Value("${service-name}") private String serviceName;

    public ServiceAuthClient(RSAKey privateKey) {
        this.privateKey =  privateKey;
    }

    public String createToken(String subject) {
        long now = System.currentTimeMillis() / 1000L;

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(serviceName)
                .subject(subject)
                .issueTime(new Date(now * 1000))
                .expirationTime(new Date((now + 120) * 1000))
                .build();

        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(privateKey.getKeyID())
                        .build(),
                claims
        );

        try {
            jwt.sign(new RSASSASigner(privateKey.toPrivateKey()));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException("Failed to sign JWS", e);
        }
    }
}
