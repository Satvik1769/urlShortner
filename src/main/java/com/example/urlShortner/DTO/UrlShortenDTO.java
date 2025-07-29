package com.example.urlShortner.DTO;

import lombok.Data;

@Data
public class UrlShortenDTO {
    private String originalUrl;
    private Long workerId;
    private Long datacenterId;
}
