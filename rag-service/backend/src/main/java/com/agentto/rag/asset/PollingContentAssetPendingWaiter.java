package com.agentto.rag.asset;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;

@Service
public class PollingContentAssetPendingWaiter implements ContentAssetPendingWaiter {

    private final ContentAssetRepository repository;
    private final Clock clock;
    private final Duration timeout;
    private final Duration poll;

    public PollingContentAssetPendingWaiter(ContentAssetRepository repository, Clock clock,
            ContentAssetLifecycleProperties properties) {
        this.repository = repository;
        this.clock = clock;
        this.timeout = properties.pendingWaitTimeout();
        this.poll = properties.pendingWaitPoll();
    }

    @Override
    public void awaitReady(String sha256) {
        Instant deadline = clock.instant().plus(timeout);
        while (!clock.instant().isAfter(deadline)) {
            if (repository.findBySha256(sha256).filter(ContentAsset::isReady).isPresent()) {
                return;
            }
            try {
                Thread.sleep(poll.toMillis());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new ContentAssetBusyException("等待内容资产就绪被中断");
            }
        }
        throw new ContentAssetBusyException("内容资产正在写入");
    }
}
