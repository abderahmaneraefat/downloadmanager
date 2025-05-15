package com.example.projet.model;


import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
public class DownloadTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String url;
    private String fileName;
    private String filePath;
    private long fileSize;
    private long downloadedBytes;
    private DownloadStatus status;
    private int numberOfThreads;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private double downloadSpeed;

    public enum DownloadStatus {
        QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELLED
    }
}
