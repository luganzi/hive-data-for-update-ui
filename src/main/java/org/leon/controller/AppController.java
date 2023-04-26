package org.leon.controller;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import org.leon.bean.ColumnPair;
import org.leon.bean.TableBean;
import org.leon.util.HiveUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class AppController {
    @FXML private TextField hiveIPText;
    @FXML private TextField hivePortText;
    @FXML private TextField databaseText;
    @FXML private Label statusLabel;

    @FXML private TextField searchText;
    @FXML private ListView<String> tableListView;
    @FXML private TextField currentTableLabel;

    @FXML private HBox tableDisplayBox;

    @FXML private Button deleteLineBtn;
    @FXML private Button copyLineBtn;
    @FXML private Button addLineBtn;
    @FXML private Button loadHiveBtn;
    @FXML private RadioButton insertOverwrite;
    @FXML private RadioButton insertAppend;
    @FXML private Button writeBtn;

    @FXML private TableView<List<String>> dataTable;
    @FXML private TextArea logTextArea;

    private TableBean tableBean;
    private String currentTable;

    private HiveUtil hiveUtil;
    private List<String> tableList;

    private ObservableList<List<String>> tableRows = FXCollections.observableArrayList();

    private final int limitNum = 100;
    private int addIndex = 0;
    private int indexFactor = 1000;
    private final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
    private final String[] hiveColNumberType = new String[] {"int", "bigint", "long", "float", "double"};
    private final String labelPrefix = "partColumnLabel";
    private final String textPrefix = "partColumnText";
    private List<Node> partColumnNodes = new ArrayList<>();

    @FXML
    private void initialize() {
        ToggleGroup insertStyle = new ToggleGroup();
        insertOverwrite.setToggleGroup(insertStyle);
        insertAppend.setToggleGroup(insertStyle);
        // 写入方式默认覆盖
        insertOverwrite.setSelected(true);

        // 注册鼠标双击事件
        tableListView.setOnMouseClicked(this::handleTableClick);
        // table可编辑
        dataTable.setEditable(true);
    }

    @FXML
    private void handleConnect() {
        String ip = hiveIPText.getText().trim();
        String port = hivePortText.getText().trim();
        String database = databaseText.getText().trim();

        hiveUtil = new HiveUtil(ip, port, database);

        // 连接hive
        logTextArea.setText("");
        boolean isConnected = hiveUtil.getConnection();
        if (! isConnected) {
            statusLabel.setText("连接失败...");
            logTextArea.setText(String.format("[ERROR] Hive连接失败: %s:%s/%s\n", ip, port, database));
            return;
        }
        statusLabel.setText("连接成功!");

        // 获取tables
        try {
            tableList = hiveUtil.getTables();
            tableListView.setItems(FXCollections.observableArrayList(tableList));
            logTextArea.setText(String.format("[INFO] 共发现 %s 张表\n", tableList.size()));
        } catch (SQLException throwables) {
            logTextArea.setText(String.format("[ERROR] 执行sql失败: show tables\n"));
            logTextArea.setText(throwables.getMessage() + "\n");
        }
    }

    @FXML
    public void searchTable() {
        String key = searchText.getText().trim().toLowerCase();
        tableListView.setItems(FXCollections.observableArrayList(
                    tableList.stream()
                        .filter(t -> t.contains(key))
                        .collect(Collectors.toList())
        ));
    }

    @FXML
    public void handleTableClick(MouseEvent click) {
        // 双击
        if (click.getClickCount() == 2) {
            dataTable.getColumns().clear();
            tableRows.clear();
            // 清理分区组件
            tableDisplayBox.getChildren().removeAll(partColumnNodes);
            partColumnNodes.clear();

            // 功能按钮生效
            deleteLineBtn.setDisable(false);
            copyLineBtn.setDisable(false);
            addLineBtn.setDisable(false);
            loadHiveBtn.setDisable(false);
            writeBtn.setDisable(false);

            //重选table, 写数据库方式重置为覆盖
            insertOverwrite.setSelected(true);

            addIndex = 0;
            logTextArea.clear();

            currentTable =  tableListView.getSelectionModel().getSelectedItem();
            currentTableLabel.setText(currentTable);
            logTextArea.appendText(String.format("[INFO] hive表: %s\n", currentTable));

            try {
                // 获取hive表字段
                tableBean = hiveUtil.descTable(currentTable);

                // 获取分区信息, UI展示
                List<ColumnPair> partColumns = tableBean.getPartColumns();
                if (partColumns.size() > 0) {
                    ObservableList<Node> children = tableDisplayBox.getChildren();

                    Label partInfoLabel = new Label("分区信息");
                    partColumnNodes.add(partInfoLabel);
                    for (int i=0; i<partColumns.size(); i++) {
                        Label partLabel = new Label();
                        partLabel.setText(partColumns.get(i).getName() + "=");
                        partLabel.setId(labelPrefix + i);
                        partColumnNodes.add(partLabel);

                        TextField partText = new TextField();
                        if (partColumns.get(i).getType().equals("string") && partColumns.get(i).getName().equals("ds")) {
                            Calendar calendar = Calendar.getInstance();
                            calendar.add(Calendar.DAY_OF_MONTH, -1);
                            partText.setText(simpleDateFormat.format(calendar.getTime()));
                        } else {
                            partText.setText("");
                        }
                        partText.setId(textPrefix + i);
                        partColumnNodes.add(partText);
                    }
                    children.addAll(partColumnNodes);
                }

                // 根据字段创建table列名
                List<ColumnPair> currentTableColumns = tableBean.getColumns();

                for(int i = 0; i< currentTableColumns.size(); i++) {
                    ColumnPair pair = currentTableColumns.get(i);

                    TableColumn<List<String>, String> column = new TableColumn(pair.getName());

                    column.setPrefWidth(100.0D);
                    // table字段可编辑
                    column.setCellFactory(TextFieldTableCell.forTableColumn());
                    // 字段编辑事件
                    column.setOnEditCommit(event -> {
                        int rowNum = event.getTablePosition().getRow();
                        int colNum = event.getTablePosition().getColumn();

                        tableRows.get(rowNum).set(colNum, event.getNewValue());
                    });

                    int j = i;
                    column.setCellValueFactory(data -> {
                        List<String> rowValues = data.getValue();
                        if (rowValues.get(j) == null || "[null]".equals(rowValues.get(j))) {
                            return new ReadOnlyStringWrapper("[null]");
                        } else {
                            return new ReadOnlyStringWrapper(rowValues.get(j));
                        }
                    });

                    dataTable.getColumns().add(column);
                }

                // 初始化一行记录, 根据列名及类型自动生成
                List<String> row = currentTableColumns.stream().map(col -> {
                    if (col.getType().equals("string")) {
                        return col.getName();
                    } else if (col.getType().contains("int")) {
                        return "0";
                    } else {
                        return "";
                    }
                }).collect(Collectors.toList());
                tableRows.add(row);
                dataTable.setItems(tableRows);
            } catch (SQLException e) {
                logTextArea.appendText(String.format("[ERROR] 执行sql失败: desc \n", currentTable));
                logTextArea.appendText(e.getMessage() + "\n");
            }
        }
    }

    @FXML
    public void handleCopyLine() {
        List<String> copyRow = dataTable.getSelectionModel().getSelectedItem();
        if (null == copyRow) {
            logTextArea.appendText(String.format("[WARN] 请选择一行数据 !!!!"));
            return;
        }

        tableRows.add(new ArrayList<>(copyRow));
        dataTable.setItems(tableRows);
    }

    @FXML
    public void handleAddLine() {
        List<String> row = new ArrayList<>();
        addIndex++;

        List<ColumnPair> columnPairs = tableBean.getColumns();
        for(int i=0;i<columnPairs.size();i++) {
            ColumnPair col = columnPairs.get(i);
            if (col.getType().equals("string")) {
                row.add(String.format("%s_%s", col.getName(), (addIndex * indexFactor + i)));
            } else {
                row.add(String.format("%s", (addIndex * indexFactor + i)));
            }
        }

        tableRows.add(row);
        dataTable.setItems(tableRows);
    }

    @FXML
    public void handleDeleteLine() {
        int index = dataTable.getSelectionModel().getSelectedIndex();
        tableRows.remove(index);
        dataTable.setItems(tableRows);
    }

    @FXML
    public void handleWrite() {
        new Thread(new Task<Integer>() {
            @Override
            protected Integer call() throws Exception {
                write();
                return 0;
            }
        }).start();
    }

    private void write() {
        String sqlTxt = "";
        String partSQL = "";

        // 初始化分区
        partSQL = IntStream.range(0, tableBean.getPartColumns().size()).mapToObj(idx -> {
            return String.format("%s='%s'",
                    tableBean.getPartColumns().get(idx).getName(),
                    ((TextField)tableDisplayBox.lookup("#" + textPrefix + idx)).getText());
        }).collect(Collectors.joining(","));

        // 如果是覆盖
        if (insertOverwrite.isSelected()) {
            // 非分区表
            if (tableBean.getPartColumns().size() == 0) {
                try {
                    sqlTxt = "truncate table " + currentTable;
                    logTextArea.appendText(String.format("[INFO] 执行SQL语句: %s\n", sqlTxt));
                    hiveUtil.update(sqlTxt);
                } catch (SQLException e) {
                    logTextArea.appendText(String.format("[ERROR] %s\n", e.getMessage()));
                    return ;
                }
            } else {
                try {
                    sqlTxt = String.format("alter table %s drop partition(%s)", currentTable, partSQL);
                    logTextArea.appendText(String.format("[INFO] 执行SQL语句: %s\n", sqlTxt));
                    hiveUtil.update(sqlTxt);
                } catch (SQLException e) {
                    logTextArea.appendText(String.format("[ERROR] %s\n", e.getMessage()));
                    return ;
                }
            }
        }

        for(List<String> row: tableRows) {
            String values = IntStream.range(0, tableBean.getColumns().size()).mapToObj(x -> {
                if (row.get(x) == null || "[null]".equals(row.get(x))) {
                    return null;
                }
                return String.format("\"%s\"", row.get(x));
            }).collect(Collectors.joining(","));

            if (tableBean.getPartColumns().size() == 0) {
                sqlTxt = String.format("insert into %s values(%s) ", currentTable, values);
            } else {
                sqlTxt = String.format("insert into %s partition(%s) values(%s) ", currentTable, partSQL, values);
            }
            logTextArea.appendText(String.format("[INFO] 执行SQL语句: %s\n", sqlTxt));

            try {
                hiveUtil.update(sqlTxt);
            } catch (SQLException e) {
                e.printStackTrace();
                logTextArea.appendText(String.format("[ERROR] %s\n", e.getMessage()));
                return ;
            }
        }
        logTextArea.appendText(String.format("[INFO] 插入成功\n"));
    }

    @FXML
    public void handleLoad() {
        StringBuilder sb = new StringBuilder();

        // 拼接列名
        List<String> selectColumns = tableBean.getColumns().stream().map(item -> {
            return item.getName();
        }).collect(Collectors.toList());
        sb.append(String.format("select %s from %s where 1=1", String.join(",", selectColumns), tableBean.getTableName()));

        // 拼接分区信息
        for(int i=0; i<tableBean.getPartColumns().size(); i++) {
            String partKey = tableBean.getPartColumns().get(i).getName();
            String partValue = ((TextField)tableDisplayBox.lookup("#" + textPrefix + i)).getText();
            if (partValue.trim().equals(""))
                continue;
            sb.append(String.format(" and %s='%s'", partKey, partValue));
        }
        sb.append(" limit " + limitNum);

        String sql = sb.toString();
        logTextArea.appendText(String.format("[INFO] 执行SQL语句: %s\n", sql));
        try {
            ResultSet resultSet = hiveUtil.query(sql);

            tableRows.clear();
            while (resultSet.next()) {
                List<String> row = IntStream.range(1, selectColumns.size()+1).mapToObj(idx -> {
                    try {
                        return resultSet.getString(idx);
                    } catch (SQLException throwables) {
                        return null;
                    }
                }).collect(Collectors.toList());
                tableRows.add(row);
            }
            dataTable.setItems(tableRows);
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
    }
}
