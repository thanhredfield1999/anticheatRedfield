package vn.heomc.anticheat.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class BoundedEvidenceBuffer {
    private final int capacity;
    private final Deque<EvidenceRecord> records = new ArrayDeque<>();
    public BoundedEvidenceBuffer(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
    }
    public synchronized void add(EvidenceRecord record) {
        if (records.size() == capacity) records.removeFirst();
        records.addLast(record);
    }
    public synchronized List<EvidenceRecord> snapshot() { return List.copyOf(new ArrayList<>(records)); }
    public synchronized int size() { return records.size(); }
}
