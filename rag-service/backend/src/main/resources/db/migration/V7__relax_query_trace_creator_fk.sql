-- V7: 解除 rag_query_trace.created_by 外键约束
--
-- 检索 Trace 的发起人既可能是管理后台用户（rag_admin_user.id），
-- 也可能是公共查询 API 的调用方（rag_client_application.id）。
-- 外键只指向 rag_admin_user 会导致公共查询在真实 MySQL 下外键失败（500），
-- 因此将该列降级为普通 bigint，仅作操作人 ID 快照，不再强制引用。
alter table rag_query_trace drop foreign key fk_rag_query_trace_creator;
