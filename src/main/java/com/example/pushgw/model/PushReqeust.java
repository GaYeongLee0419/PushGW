package com.example.pushgw.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;

@Getter
public class PushReqeust {
    private String deviceToken;
    private String applicationId;
    private JsonNode payload;
}
