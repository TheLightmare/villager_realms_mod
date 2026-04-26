package com.lightmare.villagerrealms.core.persist.migration;

import java.io.IOException;
import java.util.TreeMap;

public final class MigrationChain {

    private final String typeName;
    private final int currentVersion;
    private final TreeMap<Integer, Migration> byFrom = new TreeMap<>();

    public MigrationChain(String typeName, int currentVersion) {
        if (currentVersion < 1) throw new IllegalArgumentException("currentVersion must be >= 1");
        this.typeName = typeName;
        this.currentVersion = currentVersion;
    }

    public String typeName() { return typeName; }
    public int currentVersion() { return currentVersion; }

    public MigrationChain register(Migration m) {
        if (m.fromVersion() < 1) throw new IllegalArgumentException("fromVersion must be >= 1");
        if (m.fromVersion() >= currentVersion) {
            throw new IllegalArgumentException(
                    "Migration fromVersion " + m.fromVersion() + " >= currentVersion " + currentVersion);
        }
        var prior = byFrom.put(m.fromVersion(), m);
        if (prior != null) {
            throw new IllegalStateException(
                    "Duplicate migration registered for " + typeName + " from v" + m.fromVersion());
        }
        return this;
    }

    public byte[] upgrade(int fromVersion, byte[] payload) throws IOException {
        if (fromVersion > currentVersion) {
            throw new FutureVersionException(
                    "Refusing to read " + typeName + ": payload version " + fromVersion
                            + " is newer than supported " + currentVersion);
        }
        if (fromVersion == currentVersion) return payload;

        int v = fromVersion;
        byte[] cur = payload;
        while (v < currentVersion) {
            Migration m = byFrom.get(v);
            if (m == null) {
                throw new IllegalStateException(
                        "No migration registered for " + typeName + " from v" + v);
            }
            cur = m.migrate(cur);
            v++;
        }
        return cur;
    }
}
