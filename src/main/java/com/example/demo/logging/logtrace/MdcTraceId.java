package com.example.demo.logging.logtrace;

import org.slf4j.MDC;

public class MdcTraceId {

    private static final String TRACE_ID = "traceId";
    private static final String TRACE_LEVEL = "traceLevel";

    public static void sync() {
        TraceId current = get();
        if (current == null) {
            set(new TraceId());
        } else {
            set(current.createNextId());
        }
    }

    public static void release() {
        TraceId current = get();
        if (current == null || current.isFirstLevel()) {
            MDC.clear();
        } else {
            set(current.createPreviousId());
        }
    }

    public static void set(TraceId traceId) {
        MDC.put(TRACE_ID, traceId.getId());
        MDC.put(TRACE_LEVEL, String.valueOf(traceId.getLevel()));
    }

    public static TraceId get() {
        String id = MDC.get(TRACE_ID);
        String levelStr = MDC.get(TRACE_LEVEL);
        if (id == null || levelStr == null) return null;
        return new TraceId(id, Integer.parseInt(levelStr));
    }
}