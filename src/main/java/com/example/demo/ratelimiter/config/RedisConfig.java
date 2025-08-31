package com.example.demo.ratelimiter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 설정 클래스
 * 
 * RedisTemplate Bean을 정의하여 Redis 기반 Rate Limiter들이 
 * 의존성 주입을 받을 수 있도록 설정
 */
@Configuration
public class RedisConfig {
    
    /**
     * RedisTemplate<String, String> Bean 등록
     * 
     * Rate Limiter들이 사용할 RedisTemplate을 설정합니다.
     * Key와 Value 모두 String으로 직렬화하여 Redis에 저장합니다.
     * 
     * @param connectionFactory Spring Boot가 자동으로 생성하는 Redis 연결 팩토리
     * @return 설정된 RedisTemplate 인스턴스
     */
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        
        // Redis 연결 팩토리 설정
        template.setConnectionFactory(connectionFactory);
        
        // Key 직렬화 설정 (String으로 저장)
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        
        // Value 직렬화 설정 (String으로 저장)
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        
        // 설정 적용
        template.afterPropertiesSet();
        
        return template;
    }
}

