package com.example.pushgw.controller;

import com.example.pushgw.model.PushReqeust;
import com.example.pushgw.service.PushService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/push")
public class PushController {

    private final PushService pushService;

    public PushController(PushService pushService) {
        this.pushService = pushService;
    }

    @PostMapping("/send")
    public ResponseEntity<String> sendPush(@RequestBody PushReqeust reqeust) {
        return pushService.sendPushNotification(reqeust);
    }
}
