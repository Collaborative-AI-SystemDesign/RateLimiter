package com.example.demo.logging.config;

import com.example.demo.logging.logtrace.LogTrace;
import com.example.demo.logging.logtrace.MdcLogTrace;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LogTraceConfig {

    @Bean
    public LogTrace logTrace() {
        return new MdcLogTrace();
    }
}
