-- V8: 内容寻址资产登记与对象清理任务
create table rag_content_asset (
    id bigint primary key auto_increment,
    sha256 varchar(64) not null,
    bucket varchar(128) not null,
    object_key varchar(512) not null,
    content_length bigint not null,
    content_type varchar(128),
    storage_state varchar(32) not null,
    lease_owner varchar(64),
    lease_expires_at timestamp(6) null,
    last_touched_at timestamp(6) not null,
    unreferenced_since timestamp(6) null,
    delete_attempt_count int not null default 0,
    next_delete_retry_at timestamp(6) null,
    last_error_code varchar(64),
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    constraint uk_rag_content_asset_sha256 unique (sha256),
    constraint uk_rag_content_asset_object unique (bucket, object_key)
);

create index idx_rag_content_asset_state on rag_content_asset(storage_state, id);
create index idx_rag_content_asset_unreferenced on rag_content_asset(storage_state, unreferenced_since);
create index idx_rag_content_asset_delete_retry on rag_content_asset(storage_state, next_delete_retry_at);

create table rag_object_cleanup_task (
    id bigint primary key auto_increment,
    bucket varchar(128) not null,
    object_key varchar(512) not null,
    status varchar(32) not null,
    attempt_count int not null default 0,
    next_retry_at timestamp(6) null,
    last_error_code varchar(64),
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null
);

create index idx_rag_object_cleanup_status on rag_object_cleanup_task(status, next_retry_at);

alter table rag_document_version
    add column content_asset_id bigint null;

alter table rag_document_version
    add constraint fk_rag_document_version_asset
        foreign key (content_asset_id) references rag_content_asset(id);

create index idx_rag_document_version_asset on rag_document_version(content_asset_id);
