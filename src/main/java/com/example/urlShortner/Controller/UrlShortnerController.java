package com.example.urlShortner.Controller;

import com.example.urlShortner.DTO.UrlShortenDTO;
import com.example.urlShortner.Service.UrlShortnerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Optional;

@RestController
public class UrlShortnerController {

    @Autowired
    private UrlShortnerService urlShortnerService;

    @PostMapping("/shorten")
    public String shortenUrl(@RequestBody UrlShortenDTO urlShortenDTO) {
        String originalUrl = urlShortenDTO.getOriginalUrl();
        Long workerId = urlShortenDTO.getWorkerId();
        Long datacenterId = urlShortenDTO.getDatacenterId();
        return urlShortnerService.generateShortUrl(originalUrl, workerId, datacenterId);

    }

    @GetMapping("/{shortUrlCode}") // Renamed path variable
    public ResponseEntity<Void> redirectToOriginalUrl(@PathVariable String shortUrlCode) { // Renamed method and parameter
        Optional<String> originalUrlOptional = urlShortnerService.getOriginalUrl(shortUrlCode); // Call updated service method

        if (originalUrlOptional.isPresent()) {
            String originalUrl = originalUrlOptional.get();
            // Perform a 301 Moved Permanently redirect
            return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                    .location(URI.create(originalUrl))
                    .build();
        } else {
            // If short code not found, return 404 Not Found
            return ResponseEntity.notFound().build();
        }
    }
}
