create table rag_admin_user (
    id bigint primary key auto_increment,
    username varchar(64) not null,
    display_name varchar(128) not null,
    password_hash varchar(255) not null,
    enabled boolean not null default true,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    constraint uk_rag_admin_user_username unique (username)
);

create table rag_admin_session (
    id bigint primary key auto_increment,
    user_id bigint not null,
    token_hash varchar(64) not null,
    expires_at timestamp(6) not null,
    last_seen_at timestamp(6) not null,
    created_at timestamp(6) not null,
    constraint uk_rag_admin_session_token unique (token_hash),
    constraint fk_rag_admin_session_user foreign key (user_id) references rag_admin_user(id)
);

create table rag_document (
    id bigint primary key auto_increment,
    name varchar(255) not null,
    category varchar(128),
    source_type varchar(32) not null,
    status varchar(32) not null,
    current_version_id bigint,
    created_by bigint not null,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    constraint fk_rag_document_creator foreign key (created_by) references rag_admin_user(id)
);

create table rag_document_version (
    id bigint primary key auto_increment,
    document_id bigint not null,
    version_no int not null,
    original_filename varchar(255) not null,
    content_type varchar(128),
    file_size bigint not null,
    sha256 varchar(64) not null,
    object_bucket varchar(128) not null,
    object_key varchar(512) not null,
    processing_status varchar(32) not null,
    chunk_count int not null default 0,
    index_version varchar(64),
    created_by bigint not null,
    created_at timestamp(6) not null,
    constraint uk_rag_document_version_no unique (document_id, version_no),
    constraint uk_rag_document_version_object unique (object_bucket, object_key),
    constraint fk_rag_document_version_document foreign key (document_id) references rag_document(id),
    constraint fk_rag_document_version_creator foreign key (created_by) references rag_admin_user(id)
);

create table rag_ingestion_job (
    id bigint primary key auto_increment,
    document_id bigint not null,
    version_id bigint not null,
    status varchar(32) not null,
    current_stage varchar(32),
    attempt_no int not null default 1,
    error_code varchar(64),
    error_message varchar(1000),
    started_at timestamp(6),
    finished_at timestamp(6),
    created_at timestamp(6) not null,
    constraint fk_rag_ingestion_job_document foreign key (document_id) references rag_document(id),
    constraint fk_rag_ingestion_job_version foreign key (version_id) references rag_document_version(id)
);

create table rag_ingestion_stage (
    id bigint primary key auto_increment,
    job_id bigint not null,
    stage_code varchar(32) not null,
    status varchar(32) not null,
    detail_message varchar(1000),
    item_count int,
    started_at timestamp(6) not null,
    finished_at timestamp(6),
    elapsed_ms bigint,
    constraint fk_rag_ingestion_stage_job foreign key (job_id) references rag_ingestion_job(id)
);

create table rag_chunk (
    id bigint primary key auto_increment,
    chunk_uid varchar(64) not null,
    document_id bigint not null,
    version_id bigint not null,
    ordinal_no int not null,
    title varchar(512),
    content longtext not null,
    content_hash varchar(64) not null,
    page_no int,
    section_path varchar(1000),
    sheet_name varchar(255),
    row_start int,
    row_end int,
    metadata_json longtext,
    embedding_model varchar(128),
    embedding_dimensions int,
    indexed_at timestamp(6),
    created_at timestamp(6) not null,
    constraint uk_rag_chunk_uid unique (chunk_uid),
    constraint uk_rag_chunk_version_ordinal unique (version_id, ordinal_no),
    constraint fk_rag_chunk_document foreign key (document_id) references rag_document(id),
    constraint fk_rag_chunk_version foreign key (version_id) references rag_document_version(id)
);

create table rag_query_trace (
    id bigint primary key auto_increment,
    trace_uid varchar(64) not null,
    query_text varchar(2000) not null,
    retrieval_mode varchar(32) not null,
    filter_json longtext,
    keyword_limit int not null,
    vector_limit int not null,
    fusion_limit int not null,
    rerank_limit int not null,
    final_limit int not null,
    fallback_reason varchar(512),
    embedding_ms bigint,
    keyword_ms bigint,
    vector_ms bigint,
    fusion_ms bigint,
    rerank_ms bigint,
    total_ms bigint not null,
    result_count int not null,
    created_by bigint not null,
    created_at timestamp(6) not null,
    constraint uk_rag_query_trace_uid unique (trace_uid),
    constraint fk_rag_query_trace_creator foreign key (created_by) references rag_admin_user(id)
);

create table rag_query_candidate (
    id bigint primary key auto_increment,
    trace_id bigint not null,
    chunk_uid varchar(64) not null,
    keyword_score double,
    keyword_rank int,
    vector_score double,
    vector_rank int,
    rrf_score double,
    rrf_rank int,
    rerank_score double,
    rerank_rank int,
    final_rank int,
    selected boolean not null default false,
    created_at timestamp(6) not null,
    constraint uk_rag_query_candidate unique (trace_id, chunk_uid),
    constraint fk_rag_query_candidate_trace foreign key (trace_id) references rag_query_trace(id)
);

create index idx_rag_document_status on rag_document(status);
create index idx_rag_version_document on rag_document_version(document_id, created_at);
create index idx_rag_job_status on rag_ingestion_job(status, created_at);
create index idx_rag_stage_job on rag_ingestion_stage(job_id, id);
create index idx_rag_chunk_document on rag_chunk(document_id, version_id, ordinal_no);
create index idx_rag_trace_created on rag_query_trace(created_at);
create index idx_rag_candidate_trace_rank on rag_query_candidate(trace_id, final_rank);
