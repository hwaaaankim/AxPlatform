SELECT * FROM ap_schema_migration WHERE version='V005';
SELECT COUNT(*) AS invalid_owner FROM ap_asset a LEFT JOIN ap_account u ON u.id=a.owner_account_id WHERE u.id IS NULL;
SELECT root_kind,scope_kind,storage_state,COUNT(*) AS files,SUM(byte_size) AS bytes FROM ap_asset GROUP BY root_kind,scope_kind,storage_state;
SELECT * FROM ap_operation_settings;
SELECT COUNT(*) FROM ap_usage_policy;
SELECT COUNT(*) FROM ap_ai_call;
SELECT COUNT(*) FROM ap_activity_event;
SELECT COUNT(*) FROM ap_operations_audit;

SELECT * FROM ap_storage_binding;
SELECT state,COUNT(*) AS retained_copies,COALESCE(SUM(byte_size),0) AS bytes FROM ap_storage_copy GROUP BY state;
