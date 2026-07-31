alter table rag_ingestion_stage
    add column technical_detail_json longtext null after detail_message;

alter table rag_query_trace
    add column rank_constant int not null default 60 after final_limit,
    add column deduplicated_count int not null default 0 after result_count,
    add column execution_report_json longtext null after deduplicated_count;

alter table rag_query_candidate
    add column content_hash varchar(64) null after chunk_uid,
    add column dedupe_status varchar(16) not null default 'KEPT' after content_hash,
    add column duplicate_of_chunk_uid varchar(64) null after dedupe_status;

create index idx_rag_version_sha256 on rag_document_version(sha256);
create index idx_rag_candidate_trace_hash on rag_query_candidate(trace_id, content_hash);
