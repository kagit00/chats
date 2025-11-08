package com.flairbit.chats.config;

import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.InputStreamReader;
import java.security.interfaces.RSAPublicKey;

@Configuration
public class FlairBitPublicKeyConfig {

    @Bean
    public RSAPublicKey flairbitPublicKey(@Value("${flairbit.auth.public-key}") Resource resource) throws Exception {
        try (PEMParser pemParser = new PEMParser(new InputStreamReader(resource.getInputStream()))) {
            Object obj = pemParser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
            return (RSAPublicKey) converter.getPublicKey((SubjectPublicKeyInfo) obj);
        }
    }
}
