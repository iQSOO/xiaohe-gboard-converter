package com.iqsoo.wechatguard;

interface IWatcherService {
    boolean startWatching(String masterPath) = 1;
    void stopWatching() = 2;
    boolean isWatching() = 3;
    String getLastMessage() = 4;
    int getReplaceCount() = 5;
    String getDiagnostics(String masterPath) = 6;
    void destroy() = 16777114;
}
