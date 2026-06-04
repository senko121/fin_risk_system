-- MySQL Workbench Forward Engineering

SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0;
SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0;
SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION';

-- -----------------------------------------------------
-- Schema mydb
-- -----------------------------------------------------
-- -----------------------------------------------------
-- Schema fin_risk_db
-- -----------------------------------------------------

-- -----------------------------------------------------
-- Schema fin_risk_db
-- -----------------------------------------------------
CREATE SCHEMA IF NOT EXISTS `fin_risk_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci ;
USE `fin_risk_db` ;

-- -----------------------------------------------------
-- Table `fin_risk_db`.`users`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `fin_risk_db`.`users` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `username` VARCHAR(255) NOT NULL,
  `full_name` VARCHAR(255) NULL DEFAULT NULL,
  `phone_number` VARCHAR(255) NOT NULL,
  `email` VARCHAR(255) NULL DEFAULT NULL,
  `status` VARCHAR(255) NULL DEFAULT NULL,
  `created_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `role` VARCHAR(50) NULL DEFAULT 'USER',
  `current_refresh_token` VARCHAR(500) NULL DEFAULT NULL,
  `is_suspicious_session` TINYINT(1) NULL DEFAULT '0',
  `last_login_device` VARCHAR(255) NULL DEFAULT NULL,
  `last_login_ip` VARCHAR(45) NULL DEFAULT NULL,
  `admin_flagged` TINYINT(1) NULL DEFAULT '0',
  `face_embeddings` LONGTEXT NULL DEFAULT NULL,
  PRIMARY KEY (`id`))
ENGINE = InnoDB
AUTO_INCREMENT = 3101
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_unicode_ci;

CREATE UNIQUE INDEX `username` ON `fin_risk_db`.`users` (`username` ASC) VISIBLE;

CREATE UNIQUE INDEX `phone_number` ON `fin_risk_db`.`users` (`phone_number` ASC) VISIBLE;


-- -----------------------------------------------------
-- Table `fin_risk_db`.`accounts`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `fin_risk_db`.`accounts` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `account_number` VARCHAR(255) NULL DEFAULT NULL,
  `balance` DECIMAL(38,2) NULL DEFAULT NULL,
  `currency` VARCHAR(255) NULL DEFAULT NULL,
  `status` VARCHAR(255) NULL DEFAULT NULL,
  `created_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `version` INT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_accounts_users`
    FOREIGN KEY (`user_id`)
    REFERENCES `fin_risk_db`.`users` (`id`)
    ON DELETE RESTRICT)
ENGINE = InnoDB
AUTO_INCREMENT = 3101
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_unicode_ci;

CREATE UNIQUE INDEX `account_number` ON `fin_risk_db`.`accounts` (`account_number` ASC) VISIBLE;

CREATE INDEX `fk_accounts_users` ON `fin_risk_db`.`accounts` (`user_id` ASC) VISIBLE;


-- -----------------------------------------------------
-- Table `fin_risk_db`.`transactions`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `fin_risk_db`.`transactions` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `from_account_id` BIGINT NOT NULL,
  `to_account_number` VARCHAR(255) NULL DEFAULT NULL,
  `to_bank_code` VARCHAR(255) NULL DEFAULT NULL,
  `amount` DECIMAL(38,2) NULL DEFAULT NULL,
  `fee` DECIMAL(15,2) NULL DEFAULT '0.00',
  `transaction_type` VARCHAR(20) NULL DEFAULT 'TRANSFER',
  `device_fingerprint` VARCHAR(255) NULL DEFAULT NULL,
  `location_ip` VARCHAR(255) NULL DEFAULT NULL,
  `emotion_signal` VARCHAR(255) NULL DEFAULT NULL,
  `total_risk_score` INT NULL DEFAULT '0',
  `risk_level` VARCHAR(255) NULL DEFAULT NULL,
  `status` VARCHAR(255) NULL DEFAULT NULL,
  `created_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `description` VARCHAR(255) NULL DEFAULT NULL,
  `failed_ai_attempts` INT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_transactions_accounts`
    FOREIGN KEY (`from_account_id`)
    REFERENCES `fin_risk_db`.`accounts` (`id`)
    ON DELETE RESTRICT)
ENGINE = InnoDB
AUTO_INCREMENT = 4842
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_unicode_ci;

CREATE INDEX `fk_transactions_accounts` ON `fin_risk_db`.`transactions` (`from_account_id` ASC) VISIBLE;


-- -----------------------------------------------------
-- Table `fin_risk_db`.`ai_scan_logs`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `fin_risk_db`.`ai_scan_logs` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `transaction_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `scan_type` VARCHAR(50) NOT NULL,
  `result_label` VARCHAR(50) NULL DEFAULT NULL,
  `confidence_score` DOUBLE NULL DEFAULT NULL,
  `process_time_ms` DOUBLE NULL DEFAULT NULL,
  `emotion_details` TEXT NULL DEFAULT NULL,
  `created_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_ai_scan_transactions`
    FOREIGN KEY (`transaction_id`)
    REFERENCES `fin_risk_db`.`transactions` (`id`)
    ON DELETE CASCADE,
  CONSTRAINT `fk_ai_scan_users`
    FOREIGN KEY (`user_id`)
    REFERENCES `fin_risk_db`.`users` (`id`)
    ON DELETE CASCADE)
ENGINE = InnoDB
AUTO_INCREMENT = 377
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_unicode_ci;

CREATE INDEX `fk_ai_scan_transactions` ON `fin_risk_db`.`ai_scan_logs` (`transaction_id` ASC) VISIBLE;

CREATE INDEX `fk_ai_scan_users` ON `fin_risk_db`.`ai_scan_logs` (`user_id` ASC) VISIBLE;


-- -----------------------------------------------------
-- Table `fin_risk_db`.`audit_logs`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `fin_risk_db`.`audit_logs` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `action` VARCHAR(255) NOT NULL,
  `details` TEXT NULL DEFAULT NULL,
  `timestamp` DATETIME(6) NOT NULL,
  `username` VARCHAR(255) NOT NULL,
  PRIMARY KEY (`id`))
ENGINE = InnoDB
AUTO_INCREMENT = 2224
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_unicode_ci;


-- -----------------------------------------------------
-- Table `fin_risk_db`.`authentication_logs`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `fin_risk_db`.`authentication_logs` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `transaction_id` BIGINT NOT NULL,
  `auth_method` VARCHAR(255) NOT NULL,
  `auth_status` VARCHAR(255) NULL DEFAULT NULL,
  `attempt_count` INT NULL DEFAULT '0',
  `created_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_auth_transactions`
    FOREIGN KEY (`transaction_id`)
    REFERENCES `fin_risk_db`.`transactions` (`id`)
    ON DELETE CASCADE)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_unicode_ci;

CREATE INDEX `fk_auth_transactions` ON `fin_risk_db`.`authentication_logs` (`transaction_id` ASC) VISIBLE;


-- -----------------------------------------------------
-- Table `fin_risk_db`.`biometric_session`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `fin_risk_db`.`biometric_session` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `session_token` VARCHAR(255) NOT NULL,
  `transaction_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `used` TINYINT(1) NULL DEFAULT '0',
  `created_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  `expires_at` TIMESTAMP NOT NULL,
  `face_result` TINYINT(1) NULL DEFAULT NULL,
  `voice_result` TINYINT(1) NULL DEFAULT NULL,
  `finalized` TINYINT(1) NOT NULL DEFAULT '0',
  `error_message` TEXT NULL DEFAULT NULL,
  `version` BIGINT NOT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_biometric_session_transactions`
    FOREIGN KEY (`transaction_id`)
    REFERENCES `fin_risk_db`.`transactions` (`id`)
    ON DELETE CASCADE,
  CONSTRAINT `fk_biometric_session_users`
    FOREIGN KEY (`user_id`)
    REFERENCES `fin_risk_db`.`users` (`id`)
    ON DELETE CASCADE)
ENGINE = InnoDB
AUTO_INCREMENT = 100
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_unicode_ci;

CREATE UNIQUE INDEX `session_token` ON `fin_risk_db`.`biometric_session` (`session_token` ASC) VISIBLE;

CREATE INDEX `fk_biometric_session_transactions` ON `fin_risk_db`.`biometric_session` (`transaction_id` ASC) VISIBLE;

CREATE INDEX `fk_biometric_session_users` ON `fin_risk_db`.`biometric_session` (`user_id` ASC) VISIBLE;


-- -----------------------------------------------------
-- Table `fin_risk_db`.`risk_policies`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `fin_risk_db`.`risk_policies` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `min_score` INT NOT NULL,
  `max_score` INT NOT NULL,
  `risk_level` VARCHAR(255) NULL DEFAULT NULL,
  `action_bean_name` VARCHAR(255) NULL DEFAULT NULL,
  `description` VARCHAR(255) NULL DEFAULT NULL,
  `created_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`))
ENGINE = InnoDB
AUTO_INCREMENT = 5
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_unicode_ci;


-- -----------------------------------------------------
-- Table `fin_risk_db`.`rules`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `fin_risk_db`.`rules` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `rule_name` VARCHAR(255) NULL DEFAULT NULL,
  `description` TEXT NULL DEFAULT NULL,
  `conditions` JSON NOT NULL,
  `spel_expression` TEXT NULL DEFAULT NULL,
  `action_score` INT NOT NULL,
  `is_active` TINYINT(1) NULL DEFAULT '1',
  `created_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `min_policy_override` VARCHAR(20) NULL DEFAULT NULL COMMENT 'Nếu luật này khớp, Policy tối thiểu phải đạt mức này. NULL = không override.',
  `category` VARCHAR(20) NULL DEFAULT NULL,
  `rule_type` VARCHAR(20) NOT NULL DEFAULT 'ADDITIVE',
  PRIMARY KEY (`id`))
ENGINE = InnoDB
AUTO_INCREMENT = 17
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_unicode_ci;


-- -----------------------------------------------------
-- Table `fin_risk_db`.`risk_scores`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `fin_risk_db`.`risk_scores` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `transaction_id` BIGINT NOT NULL,
  `rule_id` BIGINT NOT NULL,
  `applied_score` INT NOT NULL,
  `created_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_risk_rules`
    FOREIGN KEY (`rule_id`)
    REFERENCES `fin_risk_db`.`rules` (`id`)
    ON DELETE CASCADE,
  CONSTRAINT `fk_risk_transactions`
    FOREIGN KEY (`transaction_id`)
    REFERENCES `fin_risk_db`.`transactions` (`id`)
    ON DELETE CASCADE)
ENGINE = InnoDB
AUTO_INCREMENT = 632
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_unicode_ci;

CREATE INDEX `fk_risk_transactions` ON `fin_risk_db`.`risk_scores` (`transaction_id` ASC) VISIBLE;

CREATE INDEX `fk_risk_rules` ON `fin_risk_db`.`risk_scores` (`rule_id` ASC) VISIBLE;


-- -----------------------------------------------------
-- Table `fin_risk_db`.`system_config_logs`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `fin_risk_db`.`system_config_logs` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `admin_username` VARCHAR(255) NULL DEFAULT NULL,
  `action_type` VARCHAR(255) NULL DEFAULT NULL,
  `target_table` VARCHAR(255) NULL DEFAULT NULL,
  `target_id` BIGINT NOT NULL,
  `old_value` JSON NULL DEFAULT NULL,
  `new_value` JSON NULL DEFAULT NULL,
  `created_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`))
ENGINE = InnoDB
AUTO_INCREMENT = 62
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_unicode_ci;


-- -----------------------------------------------------
-- Table `fin_risk_db`.`transaction_ai_insights`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `fin_risk_db`.`transaction_ai_insights` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `transaction_id` BIGINT NOT NULL,
  `feature_name` VARCHAR(50) NOT NULL,
  `insight_message` TEXT NOT NULL,
  `insight_type` VARCHAR(20) NOT NULL,
  `anomaly_score` DOUBLE NULL DEFAULT NULL,
  `created_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_insight_transactions`
    FOREIGN KEY (`transaction_id`)
    REFERENCES `fin_risk_db`.`transactions` (`id`)
    ON DELETE CASCADE)
ENGINE = InnoDB
AUTO_INCREMENT = 1126
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_unicode_ci;

CREATE INDEX `fk_insight_transactions` ON `fin_risk_db`.`transaction_ai_insights` (`transaction_id` ASC) VISIBLE;


-- -----------------------------------------------------
-- Table `fin_risk_db`.`transaction_ledgers`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `fin_risk_db`.`transaction_ledgers` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `transaction_id` BIGINT NOT NULL,
  `account_id` BIGINT NOT NULL,
  `entry_type` VARCHAR(255) NOT NULL,
  `amount` DECIMAL(38,2) NOT NULL,
  `balance_after` DECIMAL(38,2) NOT NULL,
  `created_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  `description` VARCHAR(255) NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_ledger_accounts`
    FOREIGN KEY (`account_id`)
    REFERENCES `fin_risk_db`.`accounts` (`id`)
    ON DELETE RESTRICT,
  CONSTRAINT `fk_ledger_transactions`
    FOREIGN KEY (`transaction_id`)
    REFERENCES `fin_risk_db`.`transactions` (`id`)
    ON DELETE CASCADE)
ENGINE = InnoDB
AUTO_INCREMENT = 4839
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_unicode_ci;

CREATE INDEX `fk_ledger_transactions` ON `fin_risk_db`.`transaction_ledgers` (`transaction_id` ASC) VISIBLE;

CREATE INDEX `fk_ledger_accounts` ON `fin_risk_db`.`transaction_ledgers` (`account_id` ASC) VISIBLE;


-- -----------------------------------------------------
-- Table `fin_risk_db`.`user_behavior_profiles`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `fin_risk_db`.`user_behavior_profiles` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `tx_count` INT NULL DEFAULT '0',
  `mean_vector` JSON NOT NULL,
  `covariance_matrix_c` JSON NOT NULL,
  `ewma_mean_vector` JSON NOT NULL,
  `ewma_variance` JSON NOT NULL,
  `last_tx_timestamp` TIMESTAMP NULL DEFAULT NULL,
  `version` INT NULL DEFAULT '0',
  `created_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_behavior_profiles_users`
    FOREIGN KEY (`user_id`)
    REFERENCES `fin_risk_db`.`users` (`id`)
    ON DELETE CASCADE)
ENGINE = InnoDB
AUTO_INCREMENT = 13
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_unicode_ci;

CREATE UNIQUE INDEX `user_id` ON `fin_risk_db`.`user_behavior_profiles` (`user_id` ASC) VISIBLE;


-- -----------------------------------------------------
-- Table `fin_risk_db`.`user_contacts`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `fin_risk_db`.`user_contacts` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `contact_account_number` VARCHAR(255) NULL DEFAULT NULL,
  `contact_name` VARCHAR(255) NULL DEFAULT NULL,
  `is_pinned` TINYINT(1) NULL DEFAULT '0',
  `created_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_contacts_user`
    FOREIGN KEY (`user_id`)
    REFERENCES `fin_risk_db`.`users` (`id`)
    ON DELETE CASCADE)
ENGINE = InnoDB
AUTO_INCREMENT = 3
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_unicode_ci;

CREATE INDEX `fk_contacts_user` ON `fin_risk_db`.`user_contacts` (`user_id` ASC) VISIBLE;


-- -----------------------------------------------------
-- Table `fin_risk_db`.`user_devices`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `fin_risk_db`.`user_devices` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `device_fingerprint` VARCHAR(255) NOT NULL,
  `device_name` VARCHAR(255) NULL DEFAULT NULL,
  `is_trusted` TINYINT(1) NULL DEFAULT '0',
  `last_used_ip` VARCHAR(255) NULL DEFAULT NULL,
  `last_used_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `created_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_devices_users`
    FOREIGN KEY (`user_id`)
    REFERENCES `fin_risk_db`.`users` (`id`)
    ON DELETE CASCADE)
ENGINE = InnoDB
AUTO_INCREMENT = 6
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_unicode_ci;

CREATE UNIQUE INDEX `UK1cgnvoah1hbh8ls9l785okbkr` ON `fin_risk_db`.`user_devices` (`user_id` ASC, `device_fingerprint` ASC) VISIBLE;


-- -----------------------------------------------------
-- Table `fin_risk_db`.`user_securities`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `fin_risk_db`.`user_securities` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `pin_hash` VARCHAR(255) NULL DEFAULT NULL,
  `password_hash` VARCHAR(255) NOT NULL,
  `is_pin_setup` TINYINT(1) NULL DEFAULT '0',
  `two_factor_enabled` TINYINT(1) NULL DEFAULT '0',
  `failed_login_attempts` INT NULL DEFAULT '0',
  `failed_pin_attempts` INT NULL DEFAULT '0',
  `lock_until` TIMESTAMP NULL DEFAULT NULL,
  `last_password_change` TIMESTAMP NULL DEFAULT NULL,
  `last_pin_change` TIMESTAMP NULL DEFAULT NULL,
  `created_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_security_users`
    FOREIGN KEY (`user_id`)
    REFERENCES `fin_risk_db`.`users` (`id`)
    ON DELETE CASCADE)
ENGINE = InnoDB
AUTO_INCREMENT = 117
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_unicode_ci;

CREATE UNIQUE INDEX `user_id` ON `fin_risk_db`.`user_securities` (`user_id` ASC) VISIBLE;

USE `fin_risk_db` ;

-- -----------------------------------------------------
-- procedure CreateSinkAccounts
-- -----------------------------------------------------

DELIMITER $$
USE `fin_risk_db`$$
CREATE DEFINER=`root`@`localhost` PROCEDURE `CreateSinkAccounts`()
BEGIN
    DECLARE i INT DEFAULT 1;
    WHILE i <= 100 DO
        -- Chèn bảng users (Không có cột password_hash)
        INSERT INTO users (id, username, full_name, phone_number, email, role, status, is_suspicious_session, admin_flagged)
        VALUES (
            3000 + i, 
            CONCAT('sink_user_', i), 
            CONCAT('Cửa Hàng/Đối Tác Thứ ', i), 
            CONCAT('0300000', LPAD(i, 3, '0')), 
            CONCAT('sink_', i, '@bank.vn'), 
            'USER', 
            'ACTIVE',
            0,
            0
        );
        
        -- Chèn bảng user_securities liên ứng để tránh lỗi ràng buộc thực tế
        INSERT INTO user_securities (user_id, password_hash, pin_hash, is_pin_setup, two_factor_enabled, failed_login_attempts, failed_pin_attempts)
        VALUES (3000 + i, 'fake_or_no_need_hash', NULL, 0, 0, 0, 0);
        
        -- Chèn bảng accounts
        INSERT INTO accounts (id, user_id, account_number, balance, currency, status, version)
        VALUES (3000 + i, 3000 + i, CONCAT('SINK_ACC_', LPAD(i, 3, '0')), 0.00, 'VND', 'ACTIVE', 0);
        
        SET i = i + 1;
    END WHILE;
END$$

DELIMITER ;
USE `fin_risk_db`;

DELIMITER $$
USE `fin_risk_db`$$
CREATE
DEFINER=`root`@`localhost`
TRIGGER `fin_risk_db`.`trg_prevent_self_transfer`
BEFORE INSERT ON `fin_risk_db`.`transactions`
FOR EACH ROW
BEGIN
    DECLARE sender_account_num VARCHAR(20);
    
    -- Lấy số tài khoản của người gửi dựa vào from_account_id
    SELECT account_number INTO sender_account_num 
    FROM accounts 
    WHERE id = NEW.from_account_id;

    -- So sánh 2 số tài khoản
    IF sender_account_num = NEW.to_account_number THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'LỖI BẢO MẬT DB: Phát hiện gian lận tự chuyển tiền cho chính mình!';
    END IF;
END$$

USE `fin_risk_db`$$
CREATE
DEFINER=`root`@`localhost`
TRIGGER `fin_risk_db`.`trg_prevent_ledger_delete`
BEFORE DELETE ON `fin_risk_db`.`transaction_ledgers`
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
    SET MESSAGE_TEXT = 'LỖI BẢO MẬT KẾ TOÁN: Dữ liệu Sổ cái (Ledger) là bất biến. NGHIÊM CẤM xóa!';
END$$

USE `fin_risk_db`$$
CREATE
DEFINER=`root`@`localhost`
TRIGGER `fin_risk_db`.`trg_prevent_ledger_update`
BEFORE UPDATE ON `fin_risk_db`.`transaction_ledgers`
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
    SET MESSAGE_TEXT = 'LỖI BẢO MẬT KẾ TOÁN: Dữ liệu Sổ cái (Ledger) là bất biến. NGHIÊM CẤM sửa đổi!';
END$$


DELIMITER ;

SET SQL_MODE=@OLD_SQL_MODE;
SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS;
SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS;


[{"id":1, "rule_name":"T\u00e0i kho\u1ea3n nh\u1eadn l\u1ea1", "description":"C\u1ed9ng 25 \u0111i\u1ec3m n\u1ebfu ch\u01b0a t\u1eebng chuy\u1ec3n ti\u1ec1n", "conditions":{"field": "history", "value": "NEW_RECIPIENT", "operator": "=="}, "spel_expression":"#isNewRecipient == true", "action_score":20, "is_active":1, "created_at":"2026-04-25 15:08:10", "updated_at":"2026-05-27 17:01:25", "min_policy_override":"", "category":"CONTEXTUAL", "rule_type":"ADDITIVE"},
 {"id":2, "rule_name":"Ph\u00e1t hi\u1ec7n C\u0103ng th\u1eb3ng", "description":"C\u1ed9ng 20 \u0111i\u1ec3m n\u1ebfu bi\u1ec3u c\u1ea3m c\u0103ng th\u1eb3ng", "conditions":{"field": "emotion", "value": "STRESS", "operator": "=="}, "spel_expression":"#tx.emotionSignal == 'STRESS'", "action_score":20, "is_active":1, "created_at":"2026-04-25 15:08:10", "updated_at":"2026-05-27 17:01:25", "min_policy_override":"", "category":"BIOMETRIC", "rule_type":"ADDITIVE"},
 {"id":3, "rule_name":"Ph\u00e1t hi\u1ec7n S\u1ee3 h\u00e3i", "description":"C\u1ed9ng 50 \u0111i\u1ec3m n\u1ebfu bi\u1ec3u c\u1ea3m s\u1ee3 h\u00e3i, kh\u1ed1ng ch\u1ebf", "conditions":{"field": "emotion", "value": "FEAR", "operator": "=="}, "spel_expression":"#tx.emotionSignal == 'FEAR'", "action_score":45, "is_active":1, "created_at":"2026-04-25 15:08:10", "updated_at":"2026-05-27 17:01:25", "min_policy_override":"", "category":"BIOMETRIC", "rule_type":"VETO"},
 {"id":4, "rule_name":"Thi\u1ebft b\u1ecb l\u1ea1", "description":"C\u1ed9ng 30 \u0111i\u1ec3m n\u1ebfu thi\u1ebft b\u1ecb ch\u01b0a \u0111\u01b0\u1ee3c \u0111\u00e1nh d\u1ea5u Trusted", "conditions":{"field": "deviceTrusted", "value": "false", "operator": "=="}, "spel_expression":"#deviceTrusted == false", "action_score":25, "is_active":1, "created_at":"2026-04-25 15:08:15", "updated_at":"2026-05-27 17:01:25", "min_policy_override":"", "category":"DEVICE", "rule_type":"ADDITIVE"},
 {"id":5, "rule_name":"Phi\u00ean r\u1ee7i ro v\u1ecb tr\u00ed/IP", "description":"C\u1ed9ng 50 \u0111i\u1ec3m n\u1ebfu User \u0111ang c\u00f3 c\u1edd isSuspiciousSession = true", "conditions":{"field": "suspiciousSession", "value": "true", "operator": "=="}, "spel_expression":"#suspiciousSession == true", "action_score":40, "is_active":1, "created_at":"2026-04-25 15:08:15", "updated_at":"2026-05-27 17:01:25", "min_policy_override":"MEDIUM_1", "category":"DEVICE", "rule_type":"ADDITIVE"},
 {"id":6, "rule_name":"Ngo\u1ea1i l\u1ec7 giao d\u1ecbch vi m\u00f4", "description":"Tr\u1eeb 30 \u0111i\u1ec3m r\u1ee7i ro n\u1ebfu s\u1ed1 ti\u1ec1n chuy\u1ec3n d\u01b0\u1edbi 500.000 VN\u0110 \u0111\u1ec3 \u0111\u1ea3m b\u1ea3o tr\u1ea3i nghi\u1ec7m", "conditions":{"field": "amount", "value": "500000", "operator": "<"}, "spel_expression":"#tx.amount < 500000", "action_score":-20, "is_active":1, "created_at":"2026-04-25 15:08:19", "updated_at":"2026-05-27 17:01:25", "min_policy_override":"", "category":"FINANCIAL", "rule_type":"ADDITIVE"},
 {"id":7, "rule_name":"Tu\u00e2n th\u1ee7 Q\u0110 2345/NHNN", "description":"\u00c9p m\u1ee9c MEDIUM_2 theo Q\u0110 2345/NHNN \u2014 kh\u00f4ng c\u1ed9ng \u0111i\u1ec3m r\u1ee7i ro (action_score=0)", "conditions":{"field": "amount", "value": "10000000", "operator": ">="}, "spel_expression":"#tx.amount >= 10000000", "action_score":0, "is_active":1, "created_at":"2026-04-25 15:08:19", "updated_at":"2026-05-27 17:01:25", "min_policy_override":"MEDIUM_2", "category":"FINANCIAL", "rule_type":"ADDITIVE"},
 {"id":8, "rule_name":"V\u00e9t s\u1ea1ch t\u00e0i kho\u1ea3n", "description":"C\u1ed9ng 40 \u0111i\u1ec3m n\u1ebfu s\u1ed1 ti\u1ec1n chuy\u1ec3n >= 90% s\u1ed1 d\u01b0 hi\u1ec7n t\u1ea1i", "conditions":{"field": "balanceRatio", "value": "0.9", "operator": ">="}, "spel_expression":"#balanceRatio >= 0.9", "action_score":35, "is_active":1, "created_at":"2026-04-28 18:27:22", "updated_at":"2026-05-27 17:01:25", "min_policy_override":"", "category":"FINANCIAL", "rule_type":"ADDITIVE"},
 {"id":9, "rule_name":"Giao d\u1ecbch \u0111\u00eam khuya", "description":"C\u1ed9ng 30 \u0111i\u1ec3m n\u1ebfu giao d\u1ecbch t\u1eeb 23h \u0111\u00eam \u0111\u1ebfn 5h s\u00e1ng", "conditions":{"field": "isNightTime", "value": "true", "operator": "=="}, "spel_expression":"#isNightTime == true", "action_score":25, "is_active":1, "created_at":"2026-04-28 18:27:26", "updated_at":"2026-05-27 17:01:25", "min_policy_override":"", "category":"CONTEXTUAL", "rule_type":"ADDITIVE"},
 {"id":10, "rule_name":"T\u1ea7n su\u1ea5t giao d\u1ecbch cao", "description":"C\u1ed9ng 40 \u0111i\u1ec3m n\u1ebfu c\u00f3 t\u1eeb 3 giao d\u1ecbch tr\u1edf l\u00ean trong 1 ph\u00fat", "conditions":{"field": "recentTxCount", "value": "3", "operator": ">="}, "spel_expression":"#recentTxCount >= 3", "action_score":35, "is_active":1, "created_at":"2026-04-28 18:27:29", "updated_at":"2026-05-27 17:01:25", "min_policy_override":"", "category":"VELOCITY", "rule_type":"ADDITIVE"},
 {"id":11, "rule_name":"H\u1ea1n m\u1ee9c t\u00edch l\u0169y ng\u00e0y cao", "description":"C\u1ed9ng 50 \u0111i\u1ec3m n\u1ebfu t\u1ed5ng ti\u1ec1n chuy\u1ec3n trong ng\u00e0y >= 20.000.000 VN\u0110", "conditions":{"field": "dailyTotal", "value": "20000000", "operator": ">="}, "spel_expression":"#dailyTotalAmount >= 20000000", "action_score":40, "is_active":1, "created_at":"2026-05-04 15:16:53", "updated_at":"2026-05-27 17:01:25", "min_policy_override":"", "category":"FINANCIAL", "rule_type":"ADDITIVE"},
 {"id":12, "rule_name":"Test Lu\u1eadt", "description":"", "conditions":{"field": "amount", "value": "11", "operator": ">="}, "spel_expression":"#tx.amount >= 11", "action_score":1, "is_active":0, "created_at":"2026-05-12 13:09:28", "updated_at":"2026-05-14 16:27:35", "min_policy_override":"", "category":"", "rule_type":"ADDITIVE"},
 {"id":13, "rule_name":"Test Demo Face id 3trieu", "description":"", "conditions":{"field": "amount", "value": "3000000", "operator": ">="}, "spel_expression":"#tx.amount >= 3000000", "action_score":40, "is_active":1, "created_at":"2026-05-12 13:47:40", "updated_at":"2026-05-27 14:45:37", "min_policy_override":"", "category":"", "rule_type":"ADDITIVE"},
 {"id":14, "rule_name":"C\u1ea3nh b\u00e1o Cold Start nghi\u00eam tr\u1ecdng", "description":"Thi\u1ebft b\u1ecb l\u1ea1 + ng\u01b0\u1eddi l\u1ea1 + s\u1ed1 ti\u1ec1n >= 5tr \u2014 t\u00edn hi\u1ec7u Money Mule", "conditions":{"field": "coldStart+newRecipient+largeAmount", "operator": "AND"}, "spel_expression":"#isNewRecipient == true AND #tx.amount >= 5000000 AND #deviceTrusted == false", "action_score":45, "is_active":1, "created_at":"2026-05-23 11:40:28", "updated_at":"2026-05-27 17:01:25", "min_policy_override":"MEDIUM_2", "category":"COMPOSITE", "rule_type":"ADDITIVE"},
 {"id":15, "rule_name":"ATO Drain Pattern", "description":"Thi\u1ebft b\u1ecb l\u1ea1 + chuy\u1ec3n ti\u1ec1n >= 5tr \u2014 d\u1ea5u hi\u1ec7u Account Takeover drain", "conditions":{}, "spel_expression":"#deviceTrusted == false AND #tx.amount >= 5000000", "action_score":25, "is_active":1, "created_at":"2026-05-27 17:02:46", "updated_at":"2026-05-27 17:02:46", "min_policy_override":"", "category":"COMPOSITE", "rule_type":"ADDITIVE"},
 {"id":16, "rule_name":"Money Mule Triple Signal", "description":"Thi\u1ebft b\u1ecb l\u1ea1 + ng\u01b0\u1eddi l\u1ea1 + s\u1ed1 ti\u1ec1n >= 5tr \u2014 d\u1ea5u hi\u1ec7u Money Mule", "conditions":{}, "spel_expression":"#isNewRecipient == true AND #tx.amount >= 5000000 AND #deviceTrusted == false", "action_score":30, "is_active":1, "created_at":"2026-05-27 17:02:46", "updated_at":"2026-05-27 17:02:46", "min_policy_override":"MEDIUM_2", "category":"COMPOSITE", "rule_type":"ADDITIVE"}]