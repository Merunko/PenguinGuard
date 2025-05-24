package me.merunko.PenguinGuard.cache;

import com.google.auth.oauth2.GoogleCredentials;
import me.google.drive.DriveService;
import me.merunko.PenguinGuard.Entry.Entry;
import me.merunko.PenguinGuard.Entry.EntryReader;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class EntryCache {
    private static volatile EntryCache instance;
    private final Map<String, Entry> entryMap; // ID to Entry mapping for fast lookup
    private final Map<String, List<Entry>> categoryMap; // Category to Entries mapping
    private volatile long lastRefreshTime;
    private static final long CACHE_TIMEOUT_MS = 300_000; // 5 minutes
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private EntryCache() {
        this.entryMap = new ConcurrentHashMap<>();
        this.categoryMap = new ConcurrentHashMap<>();
        this.lastRefreshTime = 0;
    }

    public static EntryCache getInstance() {
        if (instance == null) {
            synchronized (EntryCache.class) {
                if (instance == null) {
                    instance = new EntryCache();
                }
            }
        }
        return instance;
    }

    public void initializeCache(GoogleCredentials credentials) throws IOException, DriveService.DriveOperationException {
        Objects.requireNonNull(credentials, "GoogleCredentials cannot be null");
        refreshCache(credentials);
    }

    public void refreshCache(GoogleCredentials credentials) throws IOException, DriveService.DriveOperationException {
        Objects.requireNonNull(credentials, "GoogleCredentials cannot be null");

        lock.writeLock().lock();
        try {
            credentials.refreshIfExpired();
            List<Entry> entries = new EntryReader(credentials).readAllEntries();

            // Clear existing data
            entryMap.clear();
            categoryMap.clear();

            // Populate new data
            for (Entry entry : entries) {
                entryMap.put(entry.id(), entry);
                categoryMap.computeIfAbsent(entry.category(), k -> new ArrayList<>()).add(entry);
            }

            lastRefreshTime = System.currentTimeMillis();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean isCacheStale() {
        lock.readLock().lock();
        try {
            return System.currentTimeMillis() - lastRefreshTime > CACHE_TIMEOUT_MS;
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<Entry> getAllEntries() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(entryMap.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    public Map<String, List<Entry>> getEntriesByCategory() {
        lock.readLock().lock();
        try {
            Map<String, List<Entry>> copy = new HashMap<>();
            categoryMap.forEach((k, v) -> copy.put(k, new ArrayList<>(v)));
            return copy;
        } finally {
            lock.readLock().unlock();
        }
    }

    public Optional<Entry> getEntryById(String id) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(entryMap.get(id));
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<Entry> getEntriesByCategory(String category) {
        lock.readLock().lock();
        try {
            return new ArrayList<>(categoryMap.getOrDefault(category, Collections.emptyList()));
        } finally {
            lock.readLock().unlock();
        }
    }

    public void addEntry(Entry entry) {
        Objects.requireNonNull(entry, "Entry cannot be null");

        lock.writeLock().lock();
        try {
            entryMap.put(entry.id(), entry);
            categoryMap.computeIfAbsent(entry.category(), k -> new ArrayList<>()).add(entry);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean removeEntry(String entryId) {
        lock.writeLock().lock();
        try {
            Entry entry = entryMap.remove(entryId);
            if (entry != null) {
                List<Entry> categoryEntries = categoryMap.get(entry.category());
                if (categoryEntries != null) {
                    categoryEntries.removeIf(e -> e.id().equals(entryId));
                    if (categoryEntries.isEmpty()) {
                        categoryMap.remove(entry.category());
                    }
                }
                return true;
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void invalidateCache() {
        lock.writeLock().lock();
        try {
            lastRefreshTime = 0;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void clearCache() {
        lock.writeLock().lock();
        try {
            entryMap.clear();
            categoryMap.clear();
            lastRefreshTime = 0;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int size() {
        lock.readLock().lock();
        try {
            return entryMap.size();
        } finally {
            lock.readLock().unlock();
        }
    }
}