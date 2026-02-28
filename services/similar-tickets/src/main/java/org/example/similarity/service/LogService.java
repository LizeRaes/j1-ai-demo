package org.example.similarity.service;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class LogService {

	public record LogEntry(String message, String type, long timestamp) { }

	private final Queue<LogEntry> logs = new ConcurrentLinkedQueue<>();
	private static final int MAX_LOGS = 1000;

	public void addLog(String message, String type) {
		logs.offer(new LogEntry(message, type, System.currentTimeMillis()));
		while (logs.size() > MAX_LOGS) {
			logs.poll();
		}
	}

	public List<LogEntry> getLogs() {
		return new ArrayList<>(logs);
	}

}
