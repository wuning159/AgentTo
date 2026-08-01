-- V6: 建立公共查询编排 Trace 表

-- 公共 RAG 查询编排 Trace：一次编排（路由/检索/证据/改写/生成/引用校验）一条记录
create table rag_query_flow_trace (
    id bigint primary key auto_increment,
    flow_trace_uid varchar(64) not null,
    client_app_id bigint not null,
    original_query varchar(2000) not null,
    effective_query varchar(2000) not null,
    final_limit int not null,
    decision varchar(32) not null,
    routing_decision varchar(32) not null,
    profile_shortlist_json text,
    selected_kb_ids_json text,
    evidence_decision varchar(32),
    rewrite_attempted boolean not null default false,
    citation_valid boolean,
    failure_code varchar(64),
    attempt_count int not null default 1,
    trace_uids_json text,
    events_json longtext,
    answer_length int,
    total_ms bigint not null,
    created_at timestamp(6) not null,
    constraint uk_rag_flow_trace_uid unique (flow_trace_uid)
);

create index idx_rag_flow_trace_client_time on rag_query_flow_trace(client_app_id, created_at);
create index idx_rag_flow_trace_created on rag_query_flow_trace(created_at);
