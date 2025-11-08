package com.flairbit.chats.config;

import com.flairbit.chats.utils.DefaultValuesPopulator;
import com.nimbusds.jose.jwk.RSAKey;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.InputStreamReader;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;

@Configuration
public class KeyConfig {

    @Bean
    public RSAKey servicePrivateKey(@Value("${service.auth.private-key}") Resource resource) throws Exception {
        try (PEMParser pemParser = new PEMParser(new InputStreamReader(resource.getInputStream()))) {
            Object parsedObject = pemParser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter();

            PrivateKey privateKey;
            PublicKey publicKey;

            if (parsedObject instanceof PEMKeyPair pemKeyPair) {
                KeyPair keyPair = converter.getKeyPair(pemKeyPair);
                privateKey = keyPair.getPrivate();
                publicKey = keyPair.getPublic();
            } else if (parsedObject instanceof PrivateKeyInfo privateKeyInfo) {
                privateKey = converter.getPrivateKey(privateKeyInfo);
                KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                RSAPrivateCrtKey privk = (RSAPrivateCrtKey) privateKey;

                RSAPublicKeySpec publicSpec = new RSAPublicKeySpec(
                        privk.getModulus(),
                        privk.getPublicExponent()
                );
                publicKey = keyFactory.generatePublic(publicSpec);
            } else {
                throw new IllegalStateException("Unsupported PEM format: " + parsedObject.getClass());
            }

            return new RSAKey.Builder((RSAPublicKey) publicKey)
                    .privateKey((RSAPrivateKey) privateKey)
                    .keyID("df40f3f2-7d94-4361-88b8-d6f3a942036b")
                    .build();
        }
    }
}
