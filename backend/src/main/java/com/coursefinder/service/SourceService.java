package com.coursefinder.service;

import com.coursefinder.connector.CourseConnector;
import com.coursefinder.dto.response.SourceDto;
import com.coursefinder.dto.response.SourceDto.SyncMode;
import com.coursefinder.entity.CourseSource;
import com.coursefinder.entity.SyncRun;
import com.coursefinder.repository.CourseRepository;
import com.coursefinder.repository.SyncRunRepository;
import com.coursefinder.repository.projection.SourceStatsRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SourceService {

    private final CourseRepository courseRepository;
    private final SyncRunRepository syncRunRepository;
    private final CatalogSyncService catalogSyncService;
    private final Map<CourseSource, CourseConnector> connectors;

    public SourceService(CourseRepository courseRepository,
                         SyncRunRepository syncRunRepository,
                         CatalogSyncService catalogSyncService,
                         List<CourseConnector> connectors) {
        this.courseRepository = courseRepository;
        this.syncRunRepository = syncRunRepository;
        this.catalogSyncService = catalogSyncService;
        this.connectors = connectors.stream()
                .collect(Collectors.toMap(CourseConnector::source, Function.identity()));
    }

    @Transactional(readOnly = true)
    public List<SourceDto> listSources() {
        Map<CourseSource, SourceStatsRow> stats = courseRepository.findSourceStats().stream()
                .collect(Collectors.toMap(SourceStatsRow::source, Function.identity()));

        return Arrays.stream(CourseSource.values())
                .map(source -> toDto(source, stats.get(source)))
                .toList();
    }

    private SourceDto toDto(CourseSource source, SourceStatsRow stats) {
        boolean catalogSync = catalogSyncService.supportsCatalogSync(source);
        Optional<SyncRun> lastRun = catalogSync
                ? syncRunRepository.findFirstBySourceOrderByStartedAtDesc(source)
                : Optional.empty();
        CourseConnector connector = connectors.get(source);

        return new SourceDto(
                source,
                source.getDisplayName(),
                catalogSync ? SyncMode.FULL_CATALOG : SyncMode.ON_DEMAND,
                connector != null && connector.isEnabled(),
                stats == null ? 0 : stats.courseCount(),
                stats == null ? null : stats.lastSyncedAt(),
                lastRun.map(SyncRun::getStatus).orElse(null),
                lastRun.map(SyncRun::getFinishedAt).orElse(null)
        );
    }
}
