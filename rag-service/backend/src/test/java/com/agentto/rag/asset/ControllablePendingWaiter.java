package com.agentto.rag.asset;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

final class ControllablePendingWaiter implements ContentAssetPendingWaiter {

    private volatile boolean hold;
    private volatile CountDownLatch waiting = new CountDownLatch(1);
    private volatile CountDownLatch released = new CountDownLatch(1);

    void reset() {
        hold = false;
        waiting = new CountDownLatch(1);
        released = new CountDownLatch(1);
    }

    void holdUntilReleased() {
        hold = true;
    }

    void release() {
        released.countDown();
    }

    boolean awaitWaiting() throws InterruptedException {
        return waiting.await(5, TimeUnit.SECONDS);
    }

    @Override
    public void awaitReady(String sha256) {
        if (!hold) {
            throw new ContentAssetBusyException("内容资产正在写入");
        }
        waiting.countDown();
        try {
            if (!released.await(10, TimeUnit.SECONDS)) {
                throw new ContentAssetBusyException("等待内容资产就绪超时");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ContentAssetBusyException("等待内容资产就绪被中断");
        }
    }
}
