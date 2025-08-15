package com.example.demo.logging.logtrace;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class LogTraceAspect {
    private final LogTrace trace;

    @Around("execution(* com.example.demo.domain..*(..))")
    public Object doTrace(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().toShortString();
        String args = Arrays.toString(joinPoint.getArgs()); // 파라미터 값 로깅
        TraceStatus status = null;

        try {
            status = trace.begin(methodName + " args=" + args); // 여기에 포함
            Object result = joinPoint.proceed();
            trace.end(status);
            return result;
        } catch (Exception e) {
            trace.exception(status, e);
            throw e;
        }
    }
}