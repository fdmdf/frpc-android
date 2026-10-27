use crate::models::FrpcSystemConfiguration;
use rusqlite::{params, Connection, Result};
use std::collections::HashMap;

pub struct AppConfigRepository;

impl AppConfigRepository {
    pub fn get_system_config(conn: &Connection) -> Result<FrpcSystemConfiguration> {
        let mut stmt = conn.prepare(
            "SELECT config_key, config_value FROM t_frpcd_app_config
             WHERE scope_type = 'global' AND scope_id IS NULL AND namespace = 'app' AND deleted_at IS NULL"
        )?;

        let rows = stmt.query_map([], |row| {
            let key: String = row.get(0)?;
            let val: String = row.get(1)?;
            Ok((key, val))
        })?;

        let mut values: HashMap<String, String> = HashMap::new();
        for r in rows {
            if let Ok((k, v)) = r {
                values.insert(k, v);
            }
        }

        let auto_connect_on_startup = values
            .get("auto_connect_on_startup")
            .map(|v| v == "true" || v == "1")
            .unwrap_or(false);

        let language = values
            .get("language")
            .cloned()
            .unwrap_or_else(|| "en-US".to_string());

        Ok(FrpcSystemConfiguration {
            auto_connect_on_startup,
            language,
        })
    }

    pub fn save_system_config(
        conn: &Connection,
        config: &FrpcSystemConfiguration,
    ) -> Result<()> {
        Self::upsert(conn, "app", "auto_connect_on_startup", "boolean", &config.auto_connect_on_startup.to_string())?;
        Self::upsert(conn, "app", "language", "string", &config.language)?;
        Ok(())
    }

    pub fn save_language(conn: &Connection, language: &str) -> Result<()> {
        Self::upsert(conn, "app", "language", "string", language)
    }

    /// Onboarding state lives in its own `onboarding` namespace so that
    /// "reset all config" does not replay the guide.
    pub fn is_guide_completed(conn: &Connection) -> Result<bool> {
        conn.query_row(
            "SELECT EXISTS(
               SELECT 1 FROM t_frpcd_app_config
               WHERE scope_type = 'global' AND scope_id IS NULL AND namespace = 'onboarding'
                 AND config_key = 'guide_completed' AND config_value = 'true'
                 AND deleted_at IS NULL)",
            [],
            |row| row.get(0),
        )
    }

    pub fn save_guide_completed(conn: &Connection) -> Result<()> {
        Self::upsert(conn, "onboarding", "guide_completed", "boolean", "true")
    }

    fn upsert(
        conn: &Connection,
        namespace: &str,
        key: &str,
        val_type: &str,
        value: &str,
    ) -> Result<()> {
        let id = format!("{}_{}", namespace, key);
        conn.execute(
            "INSERT INTO t_frpcd_app_config (
                id, scope_type, scope_id, namespace, config_key, value_type, config_value, is_secret, version, created_at, updated_at
             ) VALUES (?1, 'global', NULL, ?2, ?3, ?4, ?5, 0, 1, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
             ON CONFLICT(scope_type, COALESCE(scope_id, ''), namespace, config_key) WHERE deleted_at IS NULL
             DO UPDATE SET
                config_value = excluded.config_value,
                value_type = excluded.value_type,
                version = t_frpcd_app_config.version + 1,
                updated_at = strftime('%Y-%m-%dT%H:%M:%fZ', 'now')",
            params![id, namespace, key, val_type, value],
        )?;
        Ok(())
    }
}
