package com.mooncore.data.sql;

import java.sql.Connection;
import java.sql.SQLException;

/** Action SQL sans retour, propageant {@link SQLException}. */
@FunctionalInterface
public interface SqlConsumer {
    void accept(Connection connection) throws SQLException;
}
