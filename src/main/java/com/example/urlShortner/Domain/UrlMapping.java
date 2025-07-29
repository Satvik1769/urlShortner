package com.example.urlShortner.Domain;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "url_mapping")
@Data
public class UrlMapping {
    @Id
    private Long id;
    @Column(nullable = false)
    private String originalUrl;
    private String shortUrl;
    private LocalDateTime createdAt;
}
