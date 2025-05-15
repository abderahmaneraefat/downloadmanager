package com.example.projet.service;

import com.example.projet.dto.DownloadProgressDTO;
import com.example.projet.dto.DownloadRequestDTO;
import com.example.projet.exception.DownloadException;
import com.example.projet.model.DownloadTask;
import com.example.projet.model.DownloadTask.DownloadStatus;
import com.example.projet.repository.DownloadTaskRepository;
import com.example.projet.task.DownloadTaskExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.net.ssl.SSLContext;
import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DownloadServiceImpl implements DownloadService {

    private final DownloadTaskRepository repository;
    private final DownloadTaskExecutor taskExecutor;

    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final SSLContext sslContext;
    @Value("${file.storage.location}")
    private String storageLocation;
    private HttpURLConnection createConnection(URL url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        if (connection instanceof HttpsURLConnection) {
            ((HttpsURLConnection) connection).setSSLSocketFactory(sslContext.getSocketFactory());
            ((HttpsURLConnection) connection).setHostnameVerifier((hostname, session) -> true);
        }

        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(30000);
        connection.setInstanceFollowRedirects(true);

        return connection;
    }

    @Override
    @Transactional
    public DownloadTask startDownload(DownloadRequestDTO request) throws DownloadException {
        try {
            // Validate URL and get file info
            URL url = new URL(request.getUrl());
            HttpURLConnection connection = createConnection(url);            connection.setRequestMethod("HEAD");
            connection.setInstanceFollowRedirects(true); // Autorise les redirections

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new DownloadException("Invalid URL or resource not available. Response code: " + responseCode);
            }

            long fileSize = connection.getContentLengthLong();
            if (fileSize <= 0) {
                throw new DownloadException("Could not determine file size or file is empty");
            }

            // Determine file name and prepare storage
            String fileName = determineFileName(request.getFileName(), request.getUrl());
            Path downloadPath = prepareDownloadDirectory(fileName);

            // Create and save download task
            DownloadTask task = createDownloadTask(request, url, fileSize, fileName, downloadPath);
            DownloadTask savedTask = repository.save(task);

            // Start the download in the background
            executorService.submit(() -> {
                try {
                    taskExecutor.executeDownload(savedTask);
                } catch (Exception e) {
                    log.error("Download execution failed for task {}", savedTask.getId(), e);
                    updateTaskStatus(savedTask.getId(), DownloadStatus.FAILED);
                }
            });

            return savedTask;
        } catch (IOException e) {
            throw new DownloadException("Failed to initiate download: " + e.getMessage(), e);
        }
    }


    private void configureConnection(HttpURLConnection connection) {
        connection.setConnectTimeout(30000); // Timeout de connexion
        connection.setReadTimeout(30000); // Timeout de lecture
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");

        // Gérer les connexions HTTPS
        if (connection instanceof HttpsURLConnection) {
            ((HttpsURLConnection) connection).setHostnameVerifier((hostname, session) -> true);
        }
    }


    private String determineFileName(String customName, String urlString) {
        if (customName != null && !customName.trim().isEmpty()) {
            return customName;
        }
        try {
            URL url = new URL(urlString);
            String path = url.getPath();
            return path.substring(path.lastIndexOf('/') + 1);
        } catch (Exception e) {
            return "download_" + System.currentTimeMillis(); // Si échec, générer un nom par défaut
        }
    }

    private Path prepareDownloadDirectory(String fileName) throws IOException {
        Path downloadDir = Paths.get(storageLocation);
        if (!Files.exists(downloadDir)) {
            Files.createDirectories(downloadDir);
        }
        return downloadDir.resolve(fileName);
    }

    private DownloadTask createDownloadTask(DownloadRequestDTO request, URL url, long fileSize,
                                            String fileName, Path downloadPath) {
        DownloadTask task = new DownloadTask();
        task.setUrl(url.toString());
        task.setFileName(fileName);
        task.setFilePath(downloadPath.toString());
        task.setFileSize(fileSize);
        task.setDownloadedBytes(0);
        task.setStatus(DownloadStatus.QUEUED);
        task.setNumberOfThreads(Math.max(1, Math.min(request.getNumberOfThreads(), 16))); // Limite des threads à 16 max
        task.setCreatedAt(LocalDateTime.now());
        return task;
    }

    @Override
    @Transactional
    public void pauseDownload(Long taskId) throws DownloadException {
        DownloadTask task = getTaskById(taskId);
        if (task.getStatus() != DownloadStatus.DOWNLOADING) {
            throw new DownloadException("Download is not in progress");
        }
        task.setStatus(DownloadStatus.PAUSED);
        repository.save(task);
        taskExecutor.pauseDownload(taskId);
        log.info("Download paused for task {}", taskId);
    }

    @Override
    @Transactional
    public void resumeDownload(Long taskId) throws DownloadException {
        DownloadTask task = getTaskById(taskId);
        if (task.getStatus() != DownloadStatus.PAUSED) {
            throw new DownloadException("Download is not paused");
        }
        task.setStatus(DownloadStatus.QUEUED);
        repository.save(task);
        executorService.submit(() -> taskExecutor.executeDownload(task));
        log.info("Download resumed for task {}", taskId);
    }

    @Override
    @Transactional
    public void cancelDownload(Long taskId) throws DownloadException {
        DownloadTask task = getTaskById(taskId);
        if (task.getStatus() == DownloadStatus.COMPLETED || task.getStatus() == DownloadStatus.FAILED) {
            throw new DownloadException("Cannot cancel completed or failed download");
        }
        task.setStatus(DownloadStatus.CANCELLED);
        repository.save(task);
        taskExecutor.cancelDownload(taskId);
        log.info("Download cancelled for task {}", taskId);
    }

    @Override
    @Transactional(readOnly = true)
    public DownloadProgressDTO getDownloadProgress(Long taskId) throws DownloadException {
        return repository.findById(taskId)
                .map(this::convertToProgressDTO)
                .orElseThrow(() -> new DownloadException("Download task not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DownloadProgressDTO> getAllDownloads() {
        return repository.findAll().stream()
                .map(this::convertToProgressDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteDownload(Long taskId) throws DownloadException {
        DownloadTask task = getTaskById(taskId);
        try {
            // Delete file if exists
            if (task.getFilePath() != null) {
                Files.deleteIfExists(Paths.get(task.getFilePath()));
                // Clean up temp files if any
                Path tempDir = Paths.get(task.getFilePath()).getParent().resolve("temp");
                if (Files.exists(tempDir)) {
                    Files.walk(tempDir)
                            .filter(p -> p.getFileName().toString().startsWith(task.getFileName()))
                            .forEach(p -> {
                                try {
                                    Files.deleteIfExists(p);
                                } catch (IOException e) {
                                    log.warn("Could not delete temp file {}", p, e);
                                }
                            });
                }
            }
            repository.deleteById(taskId);
            log.info("Download deleted for task {}", taskId);
        } catch (IOException e) {
            throw new DownloadException("Failed to delete download files: " + e.getMessage(), e);
        }
    }

    private DownloadTask getTaskById(Long taskId) throws DownloadException {
        return repository.findById(taskId)
                .orElseThrow(() -> new DownloadException("Download task not found"));
    }

    private DownloadProgressDTO convertToProgressDTO(DownloadTask task) {
        DownloadProgressDTO dto = new DownloadProgressDTO();
        dto.setId(task.getId());
        dto.setFileName(task.getFileName());
        dto.setUrl(task.getUrl());
        dto.setFileSize(task.getFileSize());
        dto.setDownloadedBytes(task.getDownloadedBytes());
        dto.setProgress(calculateProgress(task));
        dto.setDownloadSpeed(task.getDownloadSpeed());
        dto.setStatus(task.getStatus());
        dto.setCreatedAt(task.getCreatedAt());
        dto.setCompletedAt(task.getCompletedAt());
        return dto;
    }

    private double calculateProgress(DownloadTask task) {
        return task.getFileSize() > 0 ?
                (double) task.getDownloadedBytes() / task.getFileSize() * 100 : 0;
    }

    private void updateTaskStatus(Long taskId, DownloadStatus status) {
        repository.findById(taskId).ifPresent(task -> {
            task.setStatus(status);
            if (status == DownloadStatus.COMPLETED || status == DownloadStatus.FAILED) {
                task.setCompletedAt(LocalDateTime.now());
            }
            repository.save(task);
        });
    }
}
