package org.leon.bean;

import java.util.List;

public class TableBean {

    private String tableName;
    private List<ColumnPair> columns;
    private List<ColumnPair> partColumns;

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public List<ColumnPair> getColumns() {
        return columns;
    }

    public void setColumns(List<ColumnPair> columns) {
        this.columns = columns;
    }

    public List<ColumnPair> getPartColumns() {
        return partColumns;
    }

    public void setPartColumns(List<ColumnPair> partColumns) {
        this.partColumns = partColumns;
    }
}
