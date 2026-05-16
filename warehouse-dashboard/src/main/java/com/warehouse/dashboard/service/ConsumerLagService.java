package com.warehouse.dashboard.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsumerLagService {

    private final AdminClient adminClient;

    @Value("${warehouse.kafka.consumer-group}")
    private String consumerGroup;

    @Value("${warehouse.kafka.topics.errors}")
    private String errorsTopic;

    private final AtomicLong cachedLag        = new AtomicLong(0);
    private final AtomicLong lastFetchMs       = new AtomicLong(0);
    private final AtomicReference<Boolean> kafkaUpRef = new AtomicReference<>(true);

    private static final long CACHE_TTL_MS = 5_000;

    public long getTotalLag() {
        long now = System.currentTimeMillis();
        if (now - lastFetchMs.get() < CACHE_TTL_MS) {
            return cachedLag.get();
        }
        try {
            long lag = fetchLag();
            cachedLag.set(lag);
            kafkaUpRef.set(true);
        } catch (Exception e) {
            log.warn("Could not fetch consumer lag: {}", e.getMessage());
            kafkaUpRef.set(false);
        } finally {
            lastFetchMs.set(now);
        }
        return cachedLag.get();
    }

    public boolean isKafkaUp() {
        getTotalLag(); // triggers a fetch if stale
        return kafkaUpRef.get();
    }

    private long fetchLag() throws Exception {
        ListConsumerGroupOffsetsResult groupOffsets = adminClient.listConsumerGroupOffsets(consumerGroup);
        Map<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata> committed =
                groupOffsets.partitionsToOffsetAndMetadata().get(5, TimeUnit.SECONDS);

        Map<TopicPartition, OffsetSpec> latestSpecs = committed.keySet().stream()
                .filter(tp -> tp.topic().equals(errorsTopic))
                .collect(Collectors.toMap(tp -> tp, tp -> OffsetSpec.latest()));

        if (latestSpecs.isEmpty()) return 0L;

        Map<TopicPartition, org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo> endOffsets =
                adminClient.listOffsets(latestSpecs).all().get(5, TimeUnit.SECONDS);

        long totalLag = 0;
        for (Map.Entry<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata> entry : committed.entrySet()) {
            TopicPartition tp = entry.getKey();
            if (!tp.topic().equals(errorsTopic)) continue;
            long committedOffset = entry.getValue().offset();
            long endOffset = endOffsets.getOrDefault(tp,
                    new org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo(committedOffset, -1L, null))
                    .offset();
            totalLag += Math.max(0, endOffset - committedOffset);
        }
        return totalLag;
    }
}
