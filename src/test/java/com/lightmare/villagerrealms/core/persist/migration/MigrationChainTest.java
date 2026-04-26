package com.lightmare.villagerrealms.core.persist.migration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MigrationChainTest {

    @Test
    void v1NoOpReturnsSamePayload() throws Exception {
        var chain = new MigrationChain("Test", 1);
        byte[] payload = {1, 2, 3, 4};
        assertSame(payload, chain.upgrade(1, payload));
    }

    @Test
    void futureVersionRefused() {
        var chain = new MigrationChain("Test", 1);
        var ex = assertThrows(FutureVersionException.class,
                () -> chain.upgrade(2, new byte[0]));
        assertEquals(true, ex.getMessage().contains("newer than supported"));
    }

    @Test
    void missingMigrationThrows() {
        var chain = new MigrationChain("Test", 3);
        // No migrations registered. v1 -> v3 should fail at v1.
        assertThrows(IllegalStateException.class,
                () -> chain.upgrade(1, new byte[0]));
    }

    @Test
    void chainsAppliedInOrder() throws Exception {
        var chain = new MigrationChain("Test", 3);
        chain.register(new Migration() {
            @Override public int fromVersion() { return 1; }
            @Override public byte[] migrate(byte[] p) {
                byte[] out = new byte[p.length];
                for (int i = 0; i < p.length; i++) out[i] = (byte) (p[i] + 1);
                return out;
            }
        });
        chain.register(new Migration() {
            @Override public int fromVersion() { return 2; }
            @Override public byte[] migrate(byte[] p) {
                byte[] out = new byte[p.length];
                for (int i = 0; i < p.length; i++) out[i] = (byte) (p[i] * 2);
                return out;
            }
        });

        byte[] in = {10, 20, 30};
        byte[] out = chain.upgrade(1, in);
        // ((10+1)*2)=22, ((20+1)*2)=42, ((30+1)*2)=62
        assertArrayEquals(new byte[]{22, 42, 62}, out);
    }

    @Test
    void rejectsDuplicateRegistration() {
        var chain = new MigrationChain("Test", 3);
        Migration m1 = new Migration() {
            @Override public int fromVersion() { return 1; }
            @Override public byte[] migrate(byte[] p) { return p; }
        };
        chain.register(m1);
        assertThrows(IllegalStateException.class, () -> chain.register(m1));
    }
}
