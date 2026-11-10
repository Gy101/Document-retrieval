package com.doccheck.service;

import com.doccheck.mapper.CheckTaskMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CheckQueueServiceInstanceTest {

    @Test
    void configuredInstanceUsesOwnProcessingQueue() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ListOperations<String, String> listOps = mock(ListOperations.class);
        when(redis.opsForList()).thenReturn(listOps);
        when(listOps.rightPopAndLeftPush(anyString(), anyString())).thenReturn(null);

        CheckQueueService service = new CheckQueueService(redis, mock(CheckTaskMapper.class));
        ReflectionTestUtils.setField(service, "configuredInstanceId", "node/a 01");
        service.initInstance();

        assertThat(service.instanceId()).isEqualTo("node_a_01");
        service.dequeue();
        verify(listOps).rightPopAndLeftPush(CheckQueueService.QUEUE_KEY,
                CheckQueueService.PROCESSING_KEY_PREFIX + "node_a_01");
    }
}
