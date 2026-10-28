package com.mywebsite.agent.mapper;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/**
 * PG {@code jsonb} ↔ Java {@code String}。H2 测试夹具把同一列存成文本，这里按数据库产品名分支。
 *
 * <p>反射构造 {@code org.postgresql.util.PGobject}，agent-core 不在编译期依赖 postgresql。
 */
public class JsonbStringTypeHandler extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        Connection connection = ps.getConnection();
        String product = connection.getMetaData().getDatabaseProductName();
        if (product != null && product.toLowerCase().contains("postgresql")) {
            try {
                Object pgObject = Class.forName("org.postgresql.util.PGobject").getDeclaredConstructor().newInstance();
                pgObject.getClass().getMethod("setType", String.class).invoke(pgObject, "jsonb");
                pgObject.getClass().getMethod("setValue", String.class).invoke(pgObject, parameter);
                ps.setObject(i, pgObject);
            } catch (ReflectiveOperationException ex) {
                throw new SQLException("Failed to bind jsonb parameter via PGobject", ex);
            }
        } else {
            ps.setString(i, parameter);
        }
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return rs.getString(columnName);
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return rs.getString(columnIndex);
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return cs.getString(columnIndex);
    }
}
