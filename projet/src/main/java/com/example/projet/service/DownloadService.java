package com.example.projet.service;

import com.example.projet.dto.DownloadProgressDTO;
import com.example.projet.dto.DownloadRequestDTO;
import com.example.projet.exception.DownloadException;
import com.example.projet.model.DownloadTask;

import java.util.List;

public interface DownloadService {
    DownloadTask startDownload(DownloadRequestDTO request) throws DownloadException;
    void pauseDownload(Long taskId) throws DownloadException;
    void resumeDownload(Long taskId) throws DownloadException;
    void cancelDownload(Long taskId) throws DownloadException;
    DownloadProgressDTO getDownloadProgress(Long taskId) throws DownloadException;
    List<DownloadProgressDTO> getAllDownloads();
    void deleteDownload(Long taskId) throws DownloadException;
}