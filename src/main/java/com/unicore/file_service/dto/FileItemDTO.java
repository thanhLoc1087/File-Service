package com.unicore.file_service.dto;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FileItemDTO implements Serializable{
    private String id;
    private String name;
    private String thumbnailLink;
}
