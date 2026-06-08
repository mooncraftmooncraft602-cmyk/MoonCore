package com.mooncore.data.sql;

import java.sql.Connection;
import java.sql.SQLException;

/** Requête SQL avec retour, propageant {@link SQLException}. */
@FunctionalInterface
public interface SqlFunction<T> {
    T apply(Connection connection) throws SQLException;
}
