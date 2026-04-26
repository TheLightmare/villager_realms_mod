package com.lightmare.villagerrealms.core.persist.migration;

import java.io.IOException;

public interface Migration {

    int fromVersion();

    byte[] migrate(byte[] payload) throws IOException;
}
