package com.example.projet.controller;

import com.example.projet.dto.DownloadProgressDTO;
import com.example.projet.dto.DownloadRequestDTO;
import com.example.projet.exception.DownloadException;
import com.example.projet.model.DownloadTask;
import com.example.projet.service.DownloadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/downloads")
@RequiredArgsConstructor
public class DownloadController {
    private final DownloadService downloadService;

    @PostMapping
    public ResponseEntity<DownloadTask> startDownload(@Valid @RequestBody DownloadRequestDTO request)
            throws DownloadException {
        return ResponseEntity.ok(downloadService.startDownload(request));
    }

    @GetMapping
    public ResponseEntity<List<DownloadProgressDTO>> getAllDownloads() {
        return ResponseEntity.ok(downloadService.getAllDownloads());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DownloadProgressDTO> getDownloadProgress(@PathVariable Long id) throws DownloadException {
        return ResponseEntity.ok(downloadService.getDownloadProgress(id));
    }

    @PostMapping("/{id}/pause")
    public ResponseEntity<Void> pauseDownload(@PathVariable Long id) throws DownloadException {
        downloadService.pauseDownload(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/resume")
    public ResponseEntity<Void> resumeDownload(@PathVariable Long id) throws DownloadException {
        downloadService.resumeDownload(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancelDownload(@PathVariable Long id) throws DownloadException {
        downloadService.cancelDownload(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDownload(@PathVariable Long id) throws DownloadException {
        downloadService.deleteDownload(id);
        return ResponseEntity.ok().build();
    }
}