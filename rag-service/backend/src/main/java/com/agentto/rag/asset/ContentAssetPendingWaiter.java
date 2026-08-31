package com.agentto.rag.asset;

public interface ContentAssetPendingWaiter {

    void awaitReady(String sha256);
}
