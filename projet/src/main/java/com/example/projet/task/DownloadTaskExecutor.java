package com.example.projet.task;

import com.example.projet.model.DownloadTask;
import com.example.projet.model.DownloadTask.DownloadStatus;
import com.example.projet.repository.DownloadTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.net.ssl.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Component
@RequiredArgsConstructor
@Slf4j
public class DownloadTaskExecutor {
    private final DownloadTaskRepository repository;
    private final ConcurrentMap<Long, AtomicBoolean> cancellationFlags = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, AtomicBoolean> pauseFlags = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, List<Future<?>>> futuresMap = new ConcurrentHashMap<>();
    private final SSLContext sslContext = createTrustAllSSLContext();

    private SSLContext createTrustAllSSLContext() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[] {
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() { return null; }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                    }
            };

            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, trustAllCerts, new java.security.SecureRandom());
            return context;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException("Failed to initialize SSL context", e);
        }
    }

    public void executeDownload(DownloadTask task) {
        Long taskId = task.getId();
        cancellationFlags.putIfAbsent(taskId, new AtomicBoolean(false));
        pauseFlags.putIfAbsent(taskId, new AtomicBoolean(false));

        try {
            updateTaskStatus(taskId, DownloadStatus.DOWNLOADING);

            URL url = new URL(task.getUrl());
            long fileSize = task.getFileSize();
            int numThreads = task.getNumberOfThreads();
            String filePath = task.getFilePath();

            Path tempDir = Paths.get(filePath).getParent().resolve("temp");
            if (!Files.exists(tempDir)) {
                Files.createDirectories(tempDir);
            }

            long chunkSize = fileSize / numThreads;
            List<DownloadRange> ranges = new ArrayList<>();
            for (int i = 0; i < numThreads; i++) {
                long start = i * chunkSize;
                long end = (i == numThreads - 1) ? fileSize - 1 : start + chunkSize - 1;
                ranges.add(new DownloadRange(start, end));
            }

            ExecutorService executor = Executors.newFixedThreadPool(numThreads, new DownloadThreadFactory(taskId));
            List<Future<?>> futures = new ArrayList<>();
            futuresMap.put(taskId, futures);

            AtomicLong totalDownloaded = new AtomicLong(0);
            for (int i = 0; i < ranges.size(); i++) {
                DownloadRange range = ranges.get(i);
                String partFilePath = tempDir.resolve(task.getFileName() + ".part" + i).toString();

                futures.add(executor.submit(() -> {
                    FileLock lock = null;
                    try (RandomAccessFile partFile = new RandomAccessFile(partFilePath, "rw");
                         FileChannel channel = partFile.getChannel()) {

                        lock = channel.tryLock();
                        if (lock == null) {
                            throw new IOException("Could not acquire lock on file: " + partFilePath);
                        }

                        downloadChunk(url, partFile, range, taskId, totalDownloaded);
                    } catch (Exception e) {
                        log.error("Error downloading chunk for task {}: {}", taskId, e.getMessage());
                        updateTaskStatus(taskId, DownloadStatus.FAILED);
                    } finally {
                        if (lock != null && lock.isValid()) {
                            try {
                                lock.release();
                            } catch (IOException e) {
                                log.warn("Error releasing file lock: {}", e.getMessage());
                            }
                        }
                    }
                }));
            }

            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Download interrupted for task {}", taskId);
                    updateTaskStatus(taskId, DownloadStatus.PAUSED);
                    return;
                } catch (ExecutionException e) {
                    log.error("Error in download thread: {}", e.getCause().getMessage());
                    updateTaskStatus(taskId, DownloadStatus.FAILED);
                    return;
                }

                if (cancellationFlags.get(taskId).get()) {
                    updateTaskStatus(taskId, DownloadStatus.CANCELLED);
                    cleanupTempFiles(tempDir, task.getFileName(), ranges.size());
                    return;
                }

                if (pauseFlags.get(taskId).get()) {
                    updateTaskStatus(taskId, DownloadStatus.PAUSED);
                    return;
                }
            }

            mergeParts(tempDir, filePath, task.getFileName(), ranges.size());
            cleanupTempFiles(tempDir, task.getFileName(), ranges.size());
            updateTaskStatus(taskId, DownloadStatus.COMPLETED);
            updateTaskCompletedAt(taskId, LocalDateTime.now());

        } catch (Exception e) {
            log.error("Error executing download for task {}: {}", taskId, e.getMessage());
            updateTaskStatus(taskId, DownloadStatus.FAILED);
        } finally {
            cancellationFlags.remove(taskId);
            pauseFlags.remove(taskId);
            futuresMap.remove(taskId);
        }
    }

    private void downloadChunk(URL url, RandomAccessFile partFile, DownloadRange range,
                               Long taskId, AtomicLong totalDownloaded) throws IOException {
        HttpURLConnection connection = createConnection(url, range);

        int maxRetries = 3;
        int retryCount = 0;
        boolean success = false;

        while (retryCount < maxRetries && !success && !isCancelledOrPaused(taskId)) {
            try (InputStream inputStream = connection.getInputStream()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                long downloaded = 0;
                long lastUpdateTime = System.currentTimeMillis();
                long lastDownloaded = 0;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    if (isCancelledOrPaused(taskId)) {
                        break;
                    }

                    partFile.write(buffer, 0, bytesRead);
                    downloaded += bytesRead;
                    totalDownloaded.addAndGet(bytesRead);

                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastUpdateTime > 500) {
                        double speed = (totalDownloaded.get() - lastDownloaded) / ((currentTime - lastUpdateTime) / 1000.0);
                        updateTaskProgress(taskId, totalDownloaded.get(), speed);
                        lastDownloaded = totalDownloaded.get();
                        lastUpdateTime = currentTime;
                    }
                }
                success = true;
            } catch (IOException e) {
                retryCount++;
                if (retryCount >= maxRetries) {
                    throw e;
                }
                sleepBeforeRetry(retryCount);
                connection = createConnection(url, range);
            } finally {
                connection.disconnect();
            }
        }
    }

    private HttpURLConnection createConnection(URL url, DownloadRange range) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setRequestProperty("Range", "bytes=" + range.start + "-" + range.end);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        connection.setRequestProperty("Accept", "*/*");
        connection.setRequestProperty("Connection", "keep-alive");
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(30000);
        connection.setInstanceFollowRedirects(true);

        if (connection instanceof HttpsURLConnection) {
            HttpsURLConnection httpsConn = (HttpsURLConnection) connection;
            httpsConn.setSSLSocketFactory(sslContext.getSocketFactory());
            httpsConn.setHostnameVerifier((hostname, session) -> true);
        }

        return connection;
    }

    private void mergeParts(Path tempDir, String outputFilePath, String fileName, int numParts) throws IOException {
        try (FileOutputStream outputStream = new FileOutputStream(outputFilePath, true);
             FileChannel outputChannel = outputStream.getChannel()) {

            for (int i = 0; i < numParts; i++) {
                Path partPath = tempDir.resolve(fileName + ".part" + i);
                try (FileInputStream fis = new FileInputStream(partPath.toFile());
                     FileChannel inputChannel = fis.getChannel()) {

                    FileLock lock = inputChannel.tryLock(0, Long.MAX_VALUE, true);
                    if (lock != null) {
                        inputChannel.transferTo(0, inputChannel.size(), outputChannel);
                        lock.release();
                    }
                }
            }
        }
    }

    private void cleanupTempFiles(Path tempDir, String fileName, int numParts) {
        for (int i = 0; i < numParts; i++) {
            try {
                Path partPath = tempDir.resolve(fileName + ".part" + i);
                Files.deleteIfExists(partPath);
            } catch (IOException e) {
                log.warn("Could not delete temporary file part {}", i, e);
            }
        }
    }

    private boolean isCancelledOrPaused(Long taskId) {
        return cancellationFlags.getOrDefault(taskId, new AtomicBoolean(false)).get() ||
                pauseFlags.getOrDefault(taskId, new AtomicBoolean(false)).get();
    }

    private void sleepBeforeRetry(int retryCount) {
        try {
            Thread.sleep(2000 * retryCount);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    public void pauseDownload(Long taskId) {
        pauseFlags.getOrDefault(taskId, new AtomicBoolean(false)).set(true);
        futuresMap.getOrDefault(taskId, new ArrayList<>()).forEach(future -> future.cancel(true));
        updateTaskStatus(taskId, DownloadStatus.PAUSED);
    }

    public void cancelDownload(Long taskId) {
        cancellationFlags.getOrDefault(taskId, new AtomicBoolean(false)).set(true);
        futuresMap.getOrDefault(taskId, new ArrayList<>()).forEach(future -> future.cancel(true));
        updateTaskStatus(taskId, DownloadStatus.CANCELLED);
    }

    private void updateTaskStatus(Long taskId, DownloadStatus status) {
        repository.findById(taskId).ifPresent(task -> {
            task.setStatus(status);
            repository.save(task);
        });
    }

    private void updateTaskProgress(Long taskId, long downloadedBytes, double downloadSpeed) {
        repository.findById(taskId).ifPresent(task -> {
            task.setDownloadedBytes(downloadedBytes);
            task.setDownloadSpeed(downloadSpeed);
            repository.save(task);
        });
    }

    private void updateTaskCompletedAt(Long taskId, LocalDateTime completedAt) {
        repository.findById(taskId).ifPresent(task -> {
            task.setCompletedAt(completedAt);
            repository.save(task);
        });
    }

    private static class DownloadRange {
        final long start;
        final long end;

        DownloadRange(long start, long end) {
            this.start = start;
            this.end = end;
        }
    }

    private static class DownloadThreadFactory implements ThreadFactory {
        private final Long taskId;
        private int threadCount = 0;

        public DownloadThreadFactory(Long taskId) {
            this.taskId = taskId;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setName("DownloadThread-" + taskId + "-" + (++threadCount));
            thread.setPriority(Thread.MIN_PRIORITY);
            thread.setUncaughtExceptionHandler((t, e) -> {
                log.error("Uncaught exception in download thread {}: {}", t.getName(), e.getMessage());
            });
            return thread;
        }
    }
}