package com.lightmare.villagerrealms.core.persist.migration;

import java.io.IOException;

public final class FutureVersionException extends IOException {
    public FutureVersionException(String message) {
        super(message);
    }
}
