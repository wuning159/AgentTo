package com.agentto.rag.asset;

record UploadClaim(ContentAsset asset, String owner, boolean reused, boolean waiting) {

    static UploadClaim reused(ContentAsset asset) {
        return new UploadClaim(asset, null, true, false);
    }

    static UploadClaim owner(ContentAsset asset, String owner) {
        return new UploadClaim(asset, owner, false, false);
    }

    static UploadClaim pendingWait(ContentAsset asset) {
        return new UploadClaim(asset, null, false, true);
    }
}
