package org.leon.util;

import org.leon.bean.ColumnPair;
import org.leon.bean.TableBean;

import java.sql.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class HiveUtil {
    private final static String driverName = "org.apache.hive.jdbc.HiveDriver";

    private String ip = "127.0.0.1";
    private String port = "10000";
    private String database = "default";
    private Statement statement;

    public HiveUtil(String ip, String port, String database) {
        this.ip = ip;
        this.port = port;

        if (database != "") {
            this.database = database;
        }
    }

    /**
     * 连接hive
     * @return
     * @throws ClassNotFoundException
     * @throws SQLException
     */
    public boolean getConnection() {
        boolean isConnected = true;

        if (null == database || "".equals(database.trim())) database = "default";
        String hiveUrl = String.format("jdbc:hive2://%s:%s/%s", ip, port, database);

        try {
            Class.forName(driverName);
            Connection connection = DriverManager.getConnection(hiveUrl, "hive", "hive");
            statement = connection.createStatement();
        } catch (ClassNotFoundException | SQLException e ) {
            isConnected = false;
        }

        return isConnected;
    }

    /**
     * 执行sql语句
     * @param sqlTxt
     * @return
     * @throws SQLException
     * @throws ClassNotFoundException
     */
    public ResultSet query(String sqlTxt) throws SQLException {
        ResultSet resultSet = statement.executeQuery(sqlTxt);
        return resultSet;
    }

    public int update(String sqlTxt) throws SQLException {
        int num = statement.executeUpdate(sqlTxt);
        return num;

    }

    public List<String> getTables() throws SQLException {
        List<String> tables = new ArrayList<>();

        ResultSet resultSet = query("show tables");
        while (resultSet.next()) {
            tables.add(resultSet.getString(1).toLowerCase());
        }
        resultSet.close();

        return tables;
    }

    public TableBean descTable(String table) throws SQLException {
        List<String> columns = new ArrayList<>();
        List<ColumnPair> partColumns = new ArrayList<>();

        ResultSet resultSet = query("desc " + table);
        TableBean tableBean = new TableBean();
        tableBean.setTableName(table);

        Boolean endFlag = false;
        while (resultSet.next()) {
            String colName = resultSet.getString(1);
            String colType = resultSet.getString(2);

            if (colName.equals("") || colName.startsWith("#")) {
                endFlag = true;
                continue;
            }

            String key = String.format("%s|%s", colName, colType);
            if (! endFlag) {
                columns.add(key);
            } else {
                partColumns.add(new ColumnPair(colName, colType));
                if (columns.contains(key)) {
                    columns.remove(key);
                }
            }
        }

        List<ColumnPair> nonPartColumns = columns.stream().map(s -> {
            String[] kv = s.split("\\|");
            return new ColumnPair(kv[0], kv[1]);
        }).collect(Collectors.toList());
        columns.clear();

        tableBean.setColumns(nonPartColumns);
        tableBean.setPartColumns(partColumns);

        return tableBean;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }
}
