-- V4: 建立知识库、调用方和共享授权表

-- 外部调用方应用
create table rag_client_application (
    id bigint primary key auto_increment,
    app_uid varchar(64) not null,
    name varchar(128) not null,
    status varchar(32) not null default 'ACTIVE',
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    constraint uk_rag_client_app_uid unique (app_uid)
);

-- 知识库
create table rag_knowledge_base (
    id bigint primary key auto_increment,
    kb_uid varchar(64) not null,
    name varchar(255) not null,
    description varchar(2000),
    visibility varchar(32) not null default 'PRIVATE',
    owner_app_id bigint,
    status varchar(32) not null default 'ACTIVE',
    profile_version int not null default 1,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    constraint uk_rag_kb_uid unique (kb_uid),
    constraint fk_rag_kb_owner foreign key (owner_app_id) references rag_client_application(id)
);

-- 知识库共享授权
create table rag_knowledge_base_grant (
    id bigint primary key auto_increment,
    knowledge_base_id bigint not null,
    client_app_id bigint not null,
    permission varchar(32) not null default 'READ',
    created_at timestamp(6) not null,
    constraint uk_rag_kb_grant unique (knowledge_base_id, client_app_id),
    constraint fk_rag_kb_grant_kb foreign key (knowledge_base_id) references rag_knowledge_base(id),
    constraint fk_rag_kb_grant_app foreign key (client_app_id) references rag_client_application(id)
);

-- 为现有文档和切片添加知识库关联字段（先允许 null，回填后改为非空）
alter table rag_document add column knowledge_base_id bigint;
alter table rag_chunk add column knowledge_base_id bigint;

-- 插入默认调用方和默认知识库（用于兼容现有文档）
insert into rag_client_application (app_uid, name, status, created_at, updated_at)
    values ('legacy-default', '默认调用方', 'ACTIVE', current_timestamp, current_timestamp);

insert into rag_knowledge_base (kb_uid, name, description, visibility, owner_app_id, status, profile_version, created_at, updated_at)
    values ('legacy-default-kb', '默认知识库', '兼容现有文档的默认知识库', 'SHARED',
            (select id from rag_client_application where app_uid = 'legacy-default'),
            'ACTIVE', 1, current_timestamp, current_timestamp);

-- 回填现有文档和切片的默认知识库
update rag_document set knowledge_base_id = (select id from rag_knowledge_base where kb_uid = 'legacy-default-kb')
    where knowledge_base_id is null;

update rag_chunk set knowledge_base_id = (select id from rag_knowledge_base where kb_uid = 'legacy-default-kb')
    where knowledge_base_id is null;

-- 改为非空并建立外键和索引
alter table rag_document modify column knowledge_base_id bigint not null;
alter table rag_chunk modify column knowledge_base_id bigint not null;

alter table rag_document add constraint fk_rag_document_kb foreign key (knowledge_base_id) references rag_knowledge_base(id);
alter table rag_chunk add constraint fk_rag_chunk_kb foreign key (knowledge_base_id) references rag_knowledge_base(id);

create index idx_rag_document_kb on rag_document(knowledge_base_id);
create index idx_rag_chunk_kb on rag_chunk(knowledge_base_id);
create index idx_rag_kb_visibility on rag_knowledge_base(visibility, status);
