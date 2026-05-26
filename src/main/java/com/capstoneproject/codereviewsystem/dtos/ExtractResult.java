package com.capstoneproject.codereviewsystem.dtos;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public  class ExtractResult {
    private String storagePath;
    private List<String> extractedFiles;
    private int totalFiles;
    private int skippedFiles;
    private String originalFileName;
    private long fileSizeBytes;
}
