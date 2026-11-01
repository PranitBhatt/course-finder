package com.coursefinder.service;

import com.coursefinder.config.properties.YouTubeProperties;
import com.coursefinder.connector.ConnectorCourse;
import com.coursefinder.connector.ConnectorException;
import com.coursefinder.connector.QuotaExceededException;
import com.coursefinder.connector.TopicSearchConnector;
import com.coursefinder.entity.CourseSource;
import com.coursefinder.entity.TopicFetch;
import com.coursefinder.repository.CourseRepository;
import com.coursefinder.repository.TopicFetchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TopicFetchServiceTest {

    private static final Duration TTL = Duration.ofDays(7);

    @Mock
    private TopicSearchConnector youTube;
    @Mock
    private TopicFetchRepository topicFetchRepository;
    @Mock
    private CourseRepository courseRepository;

    private TopicFetchService service;

    @BeforeEach
    void setUp() {
        lenient().when(youTube.source()).thenReturn(CourseSource.YOUTUBE);
        lenient().when(youTube.isEnabled()).thenReturn(true);
        YouTubeProperties properties = new YouTubeProperties(
                "key", "https://youtube.test", 25, TTL, 9000, Duration.ofSeconds(1), Duration.ofSeconds(1));
        service = new TopicFetchService(List.of(youTube), topicFetchRepository, courseRepository, properties);
    }

    private static TopicFetch fetchedAgo(Duration age) {
        TopicFetch fetch = new TopicFetch(CourseSource.YOUTUBE, "machine learning");
        ReflectionTestUtils.setField(fetch, "fetchedAt", Instant.now().minus(age));
        return fetch;
    }

    private static ConnectorCourse course(String id) {
        return new ConnectorCourse(CourseSource.YOUTUBE, id, "Title " + id, null,
                "https://www.youtube.com/playlist?list=" + id, null, null, null, null, null, true, null);
    }

    @Test
    void normalizesTopics() {
        assertThat(TopicFetchService.normalizeTopic("  Machine   LEARNING ")).isEqualTo("machine learning");
    }

    @Test
    void freshCacheSkipsTheApi() {
        when(topicFetchRepository.findBySourceAndTopic(CourseSource.YOUTUBE, "machine learning"))
                .thenReturn(Optional.of(fetchedAgo(Duration.ofDays(1))));

        service.ensureFresh("Machine Learning", null);

        verify(youTube, never()).searchByTopic(anyString());
        verify(courseRepository, never()).upsertAll(anyList(), any());
    }

    @Test
    void staleCacheFetchesUpsertsAndRecordsTheFetch() {
        when(topicFetchRepository.findBySourceAndTopic(CourseSource.YOUTUBE, "machine learning"))
                .thenReturn(Optional.of(fetchedAgo(Duration.ofDays(8))));
        List<ConnectorCourse> courses = List.of(course("PL1"), course("PL2"));
        when(youTube.searchByTopic("machine learning")).thenReturn(courses);

        service.ensureFresh("machine learning", null);

        verify(courseRepository).upsertAll(any(), any());
        ArgumentCaptor<TopicFetch> saved = ArgumentCaptor.forClass(TopicFetch.class);
        verify(topicFetchRepository).save(saved.capture());
        assertThat(saved.getValue().getResultCount()).isEqualTo(2);
        assertThat(saved.getValue().isFresh(TTL)).isTrue();
    }

    @Test
    void unknownTopicIsFetched() {
        when(topicFetchRepository.findBySourceAndTopic(any(), anyString())).thenReturn(Optional.empty());
        when(youTube.searchByTopic("rust")).thenReturn(List.of(course("PL9")));

        service.ensureFresh("Rust", null);

        verify(youTube).searchByTopic("rust");
        verify(topicFetchRepository).save(any(TopicFetch.class));
    }

    @Test
    void quotaExceededIsSwallowedAndNothingIsRecorded() {
        when(topicFetchRepository.findBySourceAndTopic(any(), anyString())).thenReturn(Optional.empty());
        when(youTube.searchByTopic(anyString()))
                .thenThrow(new QuotaExceededException(CourseSource.YOUTUBE, "quota"));

        service.ensureFresh("java", null);

        verify(topicFetchRepository, never()).save(any());
    }

    @Test
    void connectorFailureStartsCooldown() {
        when(topicFetchRepository.findBySourceAndTopic(any(), anyString())).thenReturn(Optional.empty());
        when(youTube.searchByTopic(anyString()))
                .thenThrow(new ConnectorException(CourseSource.YOUTUBE, "boom"));

        service.ensureFresh("java", null);
        service.ensureFresh("kotlin", null);

        verify(youTube, times(1)).searchByTopic(anyString());
    }

    @Test
    void disabledConnectorIsIgnored() {
        when(youTube.isEnabled()).thenReturn(false);

        service.ensureFresh("java", null);

        verify(youTube, never()).searchByTopic(anyString());
    }

    @Test
    void sourceFilterSkipsOtherSources() {
        service.ensureFresh("java", CourseSource.COURSERA);

        verify(youTube, never()).searchByTopic(anyString());
        verify(topicFetchRepository, never()).findBySourceAndTopic(any(), anyString());
    }
}
