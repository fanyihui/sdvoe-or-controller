package com.or.sdvoe.service;

import com.or.sdvoe.domain.VideoSink;
import com.or.sdvoe.domain.VideoSource;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

public class Inventory {
    private final Map<String, VideoSource> sources = new HashMap<>();
    private final Map<String, VideoSink> sinks = new HashMap<>();

    public Inventory putSource(VideoSource source) {
        sources.put(source.getId(), source);
        return this;
    }

    public Inventory putSink(VideoSink sink) {
        sinks.put(sink.getId(), sink);
        return this;
    }

    public VideoSource getSource(String sourceId) {
        VideoSource source = sources.get(sourceId);
        if (source == null) {
            throw new NoSuchElementException("source not found: " + sourceId);
        }
        return source;
    }

    public VideoSink getSink(String sinkId) {
        VideoSink sink = sinks.get(sinkId);
        if (sink == null) {
            throw new NoSuchElementException("sink not found: " + sinkId);
        }
        return sink;
    }
}
