package com.yourorg.livealerts.fetcher;

import java.util.List;

import com.yourorg.livealerts.model.DisasterEvent;

public interface Fetcher {
    List<DisasterEvent> fetch() throws Exception;
    String sourceName();
}
