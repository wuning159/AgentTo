alter table rag_query_candidate
    add column title varchar(512) null after chunk_uid,
    add column content longtext null after title,
    add column document_id bigint null after content,
    add column version_id bigint null after document_id,
    add column metadata_json longtext null after version_id;
