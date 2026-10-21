-- ONLY after stopping all instances and copying the complete DB + managed + legacy roots.
-- Normal 0.4.2 -> 0.5.0 upgrade does NOT run this file.
-- Choose the intended database in HeidiSQL. Edit all REPLACE values before running.
-- The new root must include the original storage-volume.id with exactly the expected ID.
SET @expected_old_volume = 'REPLACE_WITH_ORIGINAL_STORAGE_VOLUME_ID';
SET @target_environment = 'test';
SET @target_root = 'D:/AxPlatform/test';
START TRANSACTION;
SELECT * FROM ap_storage_binding WHERE id=1 FOR UPDATE;
UPDATE ap_storage_binding
 SET environment_key=@target_environment, root_path=@target_root, bound_at=CURRENT_TIMESTAMP
 WHERE id=1 AND volume_id=@expected_old_volume
 AND @expected_old_volume <> 'REPLACE_WITH_ORIGINAL_STORAGE_VOLUME_ID'
 AND @target_environment IN ('dev','test');
SELECT ROW_COUNT() AS changed_rows_must_be_1;
SELECT * FROM ap_storage_binding WHERE id=1;
COMMIT;
-- 0 changed rows = stop and check volume identity. Do not delete the binding to bypass it.
