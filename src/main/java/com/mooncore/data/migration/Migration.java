package com.mooncore.data.migration;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Une migration de schéma versionnée. Les versions sont appliquées dans l'ordre
 * croissant ; chacune au plus une fois (suivi dans {@code mooncore_schema_version}).
 */
public interface Migration {

    /** Numéro de version (strictement croissant, unique). */
    int version();

    /** Description courte (journalisée). */
    String description();

    /** Applique la migration. Doit être idempotente autant que possible (CREATE IF NOT EXISTS…). */
    void apply(Connection connection) throws SQLException;
}
