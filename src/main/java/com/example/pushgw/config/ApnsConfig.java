package com.example.pushgw.config;

import com.eatthepath.pushy.apns.ApnsClient;
import com.eatthepath.pushy.apns.ApnsClientBuilder;
import com.eatthepath.pushy.apns.auth.ApnsSigningKey;
import io.netty.channel.nio.NioEventLoopGroup;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

@Configuration
@Getter
@Slf4j
public class ApnsConfig {

    @Value("${apns.key.id}")
    private String keyId;
    @Value("${apns.team.id}")
    private String teamId;
    @Value("${apns.bundle.id1}")
    private String bundleId1;
    @Value("${apns.bundle.id2")
    private String bundleId2;
    @Value("${apns.p8.path1}")
    private String p8Path1;
    @Value("${apns.p8.path2}")
    private String p8Path2;
    private final String homepath = System.getProperty("exe.home");

    @Bean(name = "apnsClient1")
    public ApnsClient apnsClient1() {
        try {
            File p8 = new File(homepath + p8Path1);
            ApnsSigningKey signingKey = ApnsSigningKey.loadFromPkcs8File(p8, teamId, keyId);

            log.info("Apns client1 initialized");

            return new ApnsClientBuilder()
                    .setApnsServer(ApnsClientBuilder.PRODUCTION_APNS_HOST)
                    .setEventLoopGroup(new NioEventLoopGroup())
                    .setSigningKey(signingKey)
                    .build();

        } catch ( IOException | NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Apns client initialization failed");
            throw new RuntimeException("Failed to initialize ApnsClient1", e);
        }
    }

    @Bean(name = "apnsClient2")
    public ApnsClient apnsClient2() {
        try {
            File p8 = new File(homepath + p8Path2);
            ApnsSigningKey signingKey = ApnsSigningKey.loadFromPkcs8File(p8, teamId, keyId);

            log.info("Apns client2 initialized");

            return new ApnsClientBuilder()
                    .setApnsServer(ApnsClientBuilder.PRODUCTION_APNS_HOST)
                    .setSigningKey(signingKey)
                    .build();

        } catch ( IOException | NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Apns client initialization failed");
            throw new RuntimeException("Failed to initialize ApnsClient2", e);
        }

    }


}
