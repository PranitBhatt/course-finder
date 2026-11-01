package com.coursefinder.controller;

import com.coursefinder.dto.response.SourceDto;
import com.coursefinder.service.SourceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/sources")
public class SourceController {

    private final SourceService sourceService;

    public SourceController(SourceService sourceService) {
        this.sourceService = sourceService;
    }

    /** {@code GET /api/sources} - every platform with course count and last sync info. */
    @GetMapping
    public List<SourceDto> listSources() {
        return sourceService.listSources();
    }
}
