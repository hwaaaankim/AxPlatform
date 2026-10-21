-- HeidiSQL: connect to your LOCAL MariaDB as an account allowed to create databases/users.
-- Replace BOTH occurrences of CHANGE_ME_DB_PASSWORD with the SAME personal password.
-- Then copy that password into config/application-private.yml -> spring.datasource.password.
-- The server uses TCP 127.0.0.1. Both local host identities are provisioned for MariaDB name resolution.
-- Re-running this script does not reset an existing user's password.
CREATE DATABASE IF NOT EXISTS `AxPlatform`
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'axplatform_app'@'localhost' IDENTIFIED BY 'CHANGE_ME_DB_PASSWORD';
CREATE USER IF NOT EXISTS 'axplatform_app'@'127.0.0.1' IDENTIFIED BY 'CHANGE_ME_DB_PASSWORD';
GRANT SELECT, INSERT, UPDATE, DELETE ON `AxPlatform`.* TO 'axplatform_app'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE ON `AxPlatform`.* TO 'axplatform_app'@'127.0.0.1';

SELECT VERSION() AS mariadb_version;
SHOW GRANTS FOR 'axplatform_app'@'localhost';
SHOW GRANTS FOR 'axplatform_app'@'127.0.0.1';
