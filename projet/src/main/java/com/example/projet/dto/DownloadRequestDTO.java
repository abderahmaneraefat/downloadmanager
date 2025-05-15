package com.example.projet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DownloadRequestDTO {
    @NotBlank(message = "URL is required")
    private String url;

    @NotBlank(message = "File name is required")
    private String fileName;

    @Positive(message = "Number of threads must be positive")
    private int numberOfThreads = 4;
}