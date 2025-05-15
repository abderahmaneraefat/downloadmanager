package com.example.projet.dto;



import com.example.projet.model.DownloadTask.DownloadStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DownloadProgressDTO {
    private Long id;
    private String fileName;
    private String url;
    private long fileSize;
    private long downloadedBytes;
    private double progress;
    private double downloadSpeed;
    private DownloadStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
