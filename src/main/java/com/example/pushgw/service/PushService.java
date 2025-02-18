package com.example.pushgw.service;

import com.eatthepath.pushy.apns.ApnsClient;
import com.eatthepath.pushy.apns.PushNotificationResponse;
import com.eatthepath.pushy.apns.util.SimpleApnsPushNotification;
import com.example.pushgw.config.ApnsConfig;
import com.example.pushgw.model.PushReqeust;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;

@Slf4j
@Service
public class PushService {

    @Qualifier("apnsClient1")
    private final ApnsClient apnsClient1;
    @Qualifier("apnsClient2")
    private final ApnsClient apnsClient2;
    private ApnsConfig apnsConfig;

    public PushService(ApnsClient apnsClient1,
                       ApnsClient apnsClient2, ApnsConfig apnsConfig) {
        this.apnsClient1 = apnsClient1;
        this.apnsClient2 = apnsClient2;
        this.apnsConfig = apnsConfig;
    }

    public ResponseEntity<String> sendPushNotification(PushReqeust request) {

        try {
            String applicationId = request.getApplicationId();
            String bundleId;
            ApnsClient apnsClient;
            if (applicationId.equals("1")) {
                apnsClient = apnsClient1;
                bundleId = apnsConfig.getBundleId1();
            } else if (applicationId.equals("2")) {
                apnsClient = apnsClient2;
                bundleId = apnsConfig.getBundleId2();
            } else {
                log.error("Invalid applicationId");
                return ResponseEntity.badRequest().body("Invalid applicationId");
            }

            SimpleApnsPushNotification notification = new SimpleApnsPushNotification(request.getDeviceToken(), bundleId, request.getPayload().toString());

            try {
                PushNotificationResponse<SimpleApnsPushNotification> response = apnsClient.sendNotification(notification).get();

                if (response.isAccepted()) {
                    log.info("Push notification Response: {}", response);
                    return ResponseEntity.ok().body("Push notification sent successfully");
                } else {
                    String rejectionReason = response.getRejectionReason().orElse("Unknown rejection reason");
                    log.error("Push notification rejected statusCode: {}, reason: {}", response.getStatusCode(), response.getRejectionReason());
                    return ResponseEntity.status(response.getStatusCode()).body(rejectionReason);
                }
            } catch (ExecutionException e) {
                log.error("Push notification request failed: {}", e.getCause().getMessage());
                return ResponseEntity.internalServerError().body("Push notification request failed");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Push notificatino was interruped: {}", e.getCause().getMessage());
                return ResponseEntity.internalServerError().body("Push notification was interruped");
            }
        } catch (RuntimeException e) {
            log.error("Unexpected error occurred: {}", e.getCause().getMessage());
            return ResponseEntity.internalServerError().body("Unexpected error occurred");
        }

    }
}
