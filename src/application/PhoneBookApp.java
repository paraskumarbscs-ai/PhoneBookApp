
package application;

import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class PhoneBookApp extends Application {

    private static final String FILE_NAME = "phonebook.txt";
    private static final String PASSWORD_FILE = "password.txt";
    private static final String RECOVERY_FILE = "recovery.txt";

    private final ObservableList<Map.Entry<String, String>> contacts =
            FXCollections.observableArrayList();

    private final Map<String, String> contactMap = new LinkedHashMap<>();

    private final TableView<Map.Entry<String, String>> tableView =
            new TableView<>();

    private final TextField nameField = new TextField();
    private final TextField numberField = new TextField();
    private final TextField searchField = new TextField();

    private final ToggleGroup searchToggleGroup = new ToggleGroup();

    private RadioButton rbSearchBoth;
    private RadioButton rbSearchName;
    private RadioButton rbSearchNumber;

    private final Deque<Map<String, String>> undoStack = new ArrayDeque<>();
    private final Deque<Map<String, String>> redoStack = new ArrayDeque<>();

    private Stage primaryStage;

    @Override
    public void start(Stage stage) {
        primaryStage = stage;

        if (!checkPassword()) {
            return;
        }

        loadFromFile();

        TableColumn<Map.Entry<String, String>, String> nameCol =
                new TableColumn<>("Name");

        nameCol.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getKey()));

        TableColumn<Map.Entry<String, String>, String> numberCol =
                new TableColumn<>("Number");

        numberCol.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getValue()));

        tableView.getColumns().addAll(nameCol, numberCol);
        tableView.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY);

        tableView.getSelectionModel().setSelectionMode(
                SelectionMode.MULTIPLE);

        tableView.setItems(contacts);

        nameField.setPromptText("Enter contact name");
        numberField.setPromptText("Enter phone number");
        searchField.setPromptText("Search contacts...");

        Button addBtn = new Button("Add");
        Button updateBtn = new Button("Update");
        Button deleteBtn = new Button("Delete");
        Button clearBtn = new Button("Clear Fields");
        Button chatBtn = new Button("Chat");
        Button undoBtn = new Button("Undo");
        Button redoBtn = new Button("Redo");
        Button saveBtn = new Button("Save & Exit");
        Button exportBtn = new Button("Export All CSV");
        Button exportSelectedBtn = new Button("Export Selected");
        Button changePasswordBtn = new Button("Change Password");
        Button clearSearchBtn = new Button("Clear Search");

        rbSearchBoth = new RadioButton("Name & Number");
        rbSearchName = new RadioButton("Name Only");
        rbSearchNumber = new RadioButton("Number Only");

        rbSearchBoth.setToggleGroup(searchToggleGroup);
        rbSearchName.setToggleGroup(searchToggleGroup);
        rbSearchNumber.setToggleGroup(searchToggleGroup);
        rbSearchBoth.setSelected(true);

        HBox searchBox = new HBox(
                8, new Label("Search:"), searchField, clearSearchBtn);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(searchField, Priority.ALWAYS);

        HBox searchModes = new HBox(
                15, rbSearchBoth, rbSearchName, rbSearchNumber);

        HBox inputBox = new HBox(
                10,
                nameField,
                numberField,
                addBtn,
                updateBtn,
                deleteBtn,
                clearBtn
        );
        inputBox.setAlignment(Pos.CENTER_LEFT);

        nameField.setPrefWidth(180);
        numberField.setPrefWidth(180);

        FlowPane buttonBox = new FlowPane(
                10, 10,
                chatBtn,
                undoBtn,
                redoBtn,
                exportBtn,
                exportSelectedBtn,
                changePasswordBtn,
                saveBtn
        );

        VBox layout = new VBox(
                12, searchBox, searchModes, tableView, inputBox, buttonBox);

        layout.setPadding(new Insets(15));
        VBox.setVgrow(tableView, Priority.ALWAYS);

        addBtn.setOnAction(e -> addContact());
        updateBtn.setOnAction(e -> updateContact());
        deleteBtn.setOnAction(e -> deleteContact());

        clearBtn.setOnAction(e -> {
            nameField.clear();
            numberField.clear();
            tableView.getSelectionModel().clearSelection();
        });

        clearSearchBtn.setOnAction(e -> searchField.clear());

        searchField.textProperty().addListener(
                (obs, oldValue, newValue) -> filterContacts());

        searchToggleGroup.selectedToggleProperty().addListener(
                (obs, oldValue, newValue) -> filterContacts());

        undoBtn.setOnAction(e -> undoLastAction());
        redoBtn.setOnAction(e -> redoLastAction());

        exportBtn.setOnAction(e -> exportCSV(false));
        exportSelectedBtn.setOnAction(e -> exportCSV(true));

        changePasswordBtn.setOnAction(e -> changePassword());

        chatBtn.setOnAction(e -> {
            Map.Entry<String, String> selected =
                    tableView.getSelectionModel().getSelectedItem();

            if (selected == null) {
                showAlert("No Selection",
                        "Please select one contact to open chat.");
                return;
            }

            openChatWindow(selected.getKey());
        });

        saveBtn.setOnAction(e -> {
            if (saveToFile()) {
                stage.close();
            }
        });

        tableView.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldValue, newValue) -> {
                    if (newValue != null) {
                        nameField.setText(newValue.getKey());
                        numberField.setText(newValue.getValue());
                    }
                });

        tableView.setRowFactory(tv -> {
            TableRow<Map.Entry<String, String>> row =
                    new TableRow<>();

            MenuItem updateItem = new MenuItem("Update");
            MenuItem deleteItem = new MenuItem("Delete");
            MenuItem messageItem = new MenuItem("Chat");

            ContextMenu menu = new ContextMenu(
                    updateItem, deleteItem, messageItem);

            updateItem.setOnAction(e -> {
                tableView.getSelectionModel().clearSelection();
                tableView.getSelectionModel().select(row.getIndex());
                Map.Entry<String, String> entry = row.getItem();
                nameField.setText(entry.getKey());
                numberField.setText(entry.getValue());
                updateContact();
            });

            deleteItem.setOnAction(e -> {
                tableView.getSelectionModel().clearSelection();
                tableView.getSelectionModel().select(row.getIndex());
                deleteContact();
            });

            messageItem.setOnAction(e -> {
                Map.Entry<String, String> entry = row.getItem();
                if (entry != null) {
                    openChatWindow(entry.getKey());
                }
            });

            row.setContextMenu(menu);

            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    nameField.setText(row.getItem().getKey());
                    numberField.setText(row.getItem().getValue());
                }
            });

            return row;
        });

        stage.setTitle("Phone Book Application");
        stage.setScene(new Scene(layout, 1100, 650));

        stage.setOnCloseRequest(e -> {
            if (!saveToFile()) {
                e.consume();
            }
        });

        stage.show();
    }

    // ---------------- PASSWORD AND LOGIN ----------------

    private boolean checkPassword() {
        Path passwordPath = Paths.get(PASSWORD_FILE);

        if (!Files.exists(passwordPath)) {
            Optional<String> password =
                    showPasswordDialog("Create Password",
                            "Create your application password");

            if (!password.isPresent() || password.get().isEmpty()) {
                showAlert("Error", "Password cannot be empty.");
                return false;
            }

            TextInputDialog recoveryDialog = new TextInputDialog();
            recoveryDialog.setTitle("Recovery Number");
            recoveryDialog.setHeaderText(
                    "Enter a mobile number for password recovery");

            Optional<String> recovery = recoveryDialog.showAndWait();

            if (!recovery.isPresent()
                    || !isValidNumber(recovery.get())
                    || recovery.get().trim().isEmpty()) {
                showAlert("Error", "A valid recovery number is required.");
                return false;
            }

            try {
                Files.write(passwordPath,
                        hashPassword(password.get()).getBytes(
                                StandardCharsets.UTF_8));

                Files.write(Paths.get(RECOVERY_FILE),
                        recovery.get().trim().getBytes(
                                StandardCharsets.UTF_8));

                return true;
            } catch (IOException ex) {
                showAlert("Error", "Unable to save login information.");
                return false;
            }
        }

        String storedHash;

        try {
            storedHash = new String(
                    Files.readAllBytes(passwordPath),
                    StandardCharsets.UTF_8).trim();
        } catch (IOException ex) {
            showAlert("Error", "Unable to read password file.");
            return false;
        }

        for (int attempt = 1; attempt <= 3; attempt++) {
            Optional<String> input =
                    showPasswordDialog("Login",
                            "Enter password (attempt "
                                    + attempt + " of 3)");

            if (!input.isPresent()) {
                return false;
            }

            if (hashPassword(input.get()).equals(storedHash)) {
                return true;
            }

            showAlert("Login Failed", "Incorrect password.");
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Access Denied");
        alert.setHeaderText("Three failed login attempts.");
        alert.setContentText("Do you want to recover your password?");

        ButtonType recover = new ButtonType("Recover Password");
        ButtonType exit = new ButtonType(
                "Exit", ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getButtonTypes().setAll(recover, exit);

        Optional<ButtonType> choice = alert.showAndWait();

        if (choice.isPresent() && choice.get() == recover) {
            return recoverPassword();
        }

        return false;
    }

    private Optional<String> showPasswordDialog(
            String title, String header) {

        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(header);

        PasswordField field = new PasswordField();
        field.setPromptText("Enter password");

        VBox box = new VBox(10, new Label("Password:"), field);
        box.setPadding(new Insets(15));

        dialog.getDialogPane().setContent(box);
        dialog.getDialogPane().getButtonTypes().addAll(
                ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(button ->
                button == ButtonType.OK ? field.getText() : null);

        return dialog.showAndWait();
    }

    private boolean recoverPassword() {
        String savedNumber;

        try {
            savedNumber = new String(
                    Files.readAllBytes(Paths.get(RECOVERY_FILE)),
                    StandardCharsets.UTF_8).trim();
        } catch (IOException ex) {
            showAlert("Error", "Recovery number is unavailable.");
            return false;
        }

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Password Recovery");
        dialog.setHeaderText("Enter your registered recovery number");

        Optional<String> entered = dialog.showAndWait();

        if (!entered.isPresent()
                || !entered.get().trim().equals(savedNumber)) {
            showAlert("Failed", "Recovery number does not match.");
            return false;
        }

        Optional<String> newPassword =
                showPasswordDialog("Reset Password", "Enter a new password");

        if (!newPassword.isPresent() || newPassword.get().isEmpty()) {
            return false;
        }

        try {
            Files.write(Paths.get(PASSWORD_FILE),
                    hashPassword(newPassword.get()).getBytes(
                            StandardCharsets.UTF_8));

            showAlert("Success", "Password reset successfully.");
            return true;
        } catch (IOException ex) {
            showAlert("Error", "Unable to reset password.");
            return false;
        }
    }

    private void changePassword() {
        Optional<String> oldPassword =
                showPasswordDialog("Change Password", "Enter old password");

        if (!oldPassword.isPresent()) {
            return;
        }

        try {
            String storedHash = new String(
                    Files.readAllBytes(Paths.get(PASSWORD_FILE)),
                    StandardCharsets.UTF_8).trim();

            if (!hashPassword(oldPassword.get()).equals(storedHash)) {
                showAlert("Error", "Old password is incorrect.");
                return;
            }
        } catch (IOException ex) {
            showAlert("Error", "Unable to read password.");
            return;
        }

        Optional<String> newPassword =
                showPasswordDialog("Change Password", "Enter new password");

        if (!newPassword.isPresent() || newPassword.get().isEmpty()) {
            showAlert("Error", "New password cannot be empty.");
            return;
        }

        Optional<String> confirmation =
                showPasswordDialog("Confirm Password",
                        "Enter new password again");

        if (!confirmation.isPresent()
                || !newPassword.get().equals(confirmation.get())) {
            showAlert("Error", "Passwords do not match.");
            return;
        }

        try {
            Files.write(Paths.get(PASSWORD_FILE),
                    hashPassword(newPassword.get()).getBytes(
                            StandardCharsets.UTF_8));

            showAlert("Success", "Password changed successfully.");
        } catch (IOException ex) {
            showAlert("Error", "Unable to save new password.");
        }
    }

    private String hashPassword(String password) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    password.getBytes(StandardCharsets.UTF_8));

            StringBuilder result = new StringBuilder();

            for (byte b : hash) {
                result.append(String.format("%02x", b & 0xff));
            }

            return result.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    // ---------------- CONTACT OPERATIONS ----------------

    private void addContact() {
        String name = nameField.getText().trim();
        String number = numberField.getText().trim();

        if (name.isEmpty() || number.isEmpty()) {
            showAlert("Validation Error",
                    "Name and number cannot be empty.");
            return;
        }

        if (!isValidNumber(number)) {
            showAlert("Validation Error", "Invalid phone number.");
            return;
        }

        if (findActualName(name) != null) {
            showAlert("Duplicate", "This contact already exists.");
            return;
        }

        recordUndo();
        contactMap.put(name, number);
        afterChange();
    }

    private void updateContact() {
        Map.Entry<String, String> selected =
                tableView.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showAlert("No Selection", "Select a contact to update.");
            return;
        }

        String oldName = selected.getKey();
        String newName = nameField.getText().trim();
        String newNumber = numberField.getText().trim();

        if (newName.isEmpty() || newNumber.isEmpty()) {
            showAlert("Validation Error",
                    "Name and number cannot be empty.");
            return;
        }

        if (!isValidNumber(newNumber)) {
            showAlert("Validation Error", "Invalid phone number.");
            return;
        }

        String existing = findActualName(newName);

        if (existing != null && !existing.equals(oldName)) {
            showAlert("Duplicate", "Another contact has this name.");
            return;
        }

        recordUndo();

        Map<String, String> replacement = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : contactMap.entrySet()) {
            if (entry.getKey().equals(oldName)) {
                replacement.put(newName, newNumber);
            } else {
                replacement.put(entry.getKey(), entry.getValue());
            }
        }

        contactMap.clear();
        contactMap.putAll(replacement);

        afterChange();
    }

    private void deleteContact() {
        Map.Entry<String, String> selected =
                tableView.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showAlert("No Selection", "Select a contact to delete.");
            return;
        }

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Delete Contact");
        confirmation.setHeaderText("Delete " + selected.getKey() + "?");
        confirmation.setContentText("This action can be undone.");

        Optional<ButtonType> result = confirmation.showAndWait();

        if (!result.isPresent() || result.get() != ButtonType.OK) {
            return;
        }

        recordUndo();
        contactMap.remove(selected.getKey());
        afterChange();
    }

    private void afterChange() {
        refreshContacts();
        nameField.clear();
        numberField.clear();

        if (!saveToFile()) {
            showAlert("Warning",
                    "The contact changed in memory but could not be saved.");
        }
    }

    private String findActualName(String name) {
        for (String key : contactMap.keySet()) {
            if (key.equalsIgnoreCase(name)) {
                return key;
            }
        }
        return null;
    }

    private boolean isValidNumber(String number) {
        return number.matches("[0-9+\\-\\s]+");
    }

    // ---------------- SEARCH ----------------

    private void filterContacts() {
        refreshContacts();
    }

    private void refreshContacts() {
        contacts.clear();

        String query = searchField.getText().trim().toLowerCase();

        boolean searchName = rbSearchName.isSelected();
        boolean searchNumber = rbSearchNumber.isSelected();

        String[] keywords = query.isEmpty()
                ? new String[0] : query.split("\\s+");

        contactMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        String.CASE_INSENSITIVE_ORDER))
                .filter(entry -> {
                    String name = entry.getKey().toLowerCase();
                    String number = entry.getValue().toLowerCase();

                    for (String keyword : keywords) {
                        boolean nameMatch = name.contains(keyword);
                        boolean numberMatch = number.contains(keyword);

                        if (searchName && !nameMatch) {
                            return false;
                        }

                        if (searchNumber && !numberMatch) {
                            return false;
                        }

                        if (!searchName && !searchNumber
                                && !(nameMatch || numberMatch)) {
                            return false;
                        }
                    }

                    return true;
                })
                .forEach(entry -> contacts.add(
                        new AbstractMap.SimpleImmutableEntry<>(
                                entry.getKey(), entry.getValue())));
    }

    // ---------------- UNDO AND REDO ----------------

    private void recordUndo() {
        undoStack.push(new LinkedHashMap<>(contactMap));
        redoStack.clear();
    }

    private void undoLastAction() {
        if (undoStack.isEmpty()) {
            showAlert("Undo", "Nothing to undo.");
            return;
        }

        redoStack.push(new LinkedHashMap<>(contactMap));
        restoreContacts(undoStack.pop());
    }

    private void redoLastAction() {
        if (redoStack.isEmpty()) {
            showAlert("Redo", "Nothing to redo.");
            return;
        }

        undoStack.push(new LinkedHashMap<>(contactMap));
        restoreContacts(redoStack.pop());
    }

    private void restoreContacts(Map<String, String> snapshot) {
        contactMap.clear();
        contactMap.putAll(snapshot);

        refreshContacts();
        nameField.clear();
        numberField.clear();

        if (!saveToFile()) {
            showAlert("Warning", "Unable to save restored contacts.");
        }
    }

    // ---------------- CONTACT FILE ----------------

    private void loadFromFile() {
        File file = new File(FILE_NAME);

        if (!file.exists()) {
            return;
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        new FileInputStream(file),
                        StandardCharsets.UTF_8))) {

            String line;

            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",", 2);

                if (parts.length == 2) {
                    String name = parts[0].trim();
                    String number = parts[1].trim();

                    if (!name.isEmpty() && !number.isEmpty()) {
                        contactMap.put(name, number);
                    }
                }
            }

            refreshContacts();
        } catch (IOException ex) {
            showAlert("Error", "Unable to load contacts.");
        }
    }

    private boolean saveToFile() {
        try (PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(
                        new FileOutputStream(FILE_NAME),
                        StandardCharsets.UTF_8))) {

            for (Map.Entry<String, String> entry : contactMap.entrySet()) {
                writer.println(
                        escapeCSV(entry.getKey()) + ","
                                + escapeCSV(entry.getValue()));
            }

            return !writer.checkError();
        } catch (IOException ex) {
            showAlert("Save Error", "Unable to save contacts.");
            return false;
        }
    }

    // ---------------- CSV EXPORT ----------------

    private void exportCSV(boolean selectedOnly) {
        List<Map.Entry<String, String>> exportList;

        if (selectedOnly) {
            exportList = new ArrayList<>(
                    tableView.getSelectionModel().getSelectedItems());

            if (exportList.isEmpty()) {
                showAlert("No Selection",
                        "Select at least one contact to export.");
                return;
            }
        } else {
            exportList = new ArrayList<>(contacts);
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Contacts");
        chooser.setInitialFileName("contacts.csv");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV Files", "*.csv"));

        File file = chooser.showSaveDialog(primaryStage);

        if (file == null) {
            return;
        }

        try (PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(
                        new FileOutputStream(file),
                        StandardCharsets.UTF_8))) {

            writer.println("Name,Number");

            for (Map.Entry<String, String> entry : exportList) {
                writer.println(
                        escapeCSV(entry.getKey()) + ","
                                + escapeCSV(entry.getValue()));
            }

            if (writer.checkError()) {
                showAlert("Export Failed", "Unable to write CSV file.");
                return;
            }

            showAlert("Success", "Contacts exported successfully.");
        } catch (IOException ex) {
            showAlert("Export Failed", "Unable to export contacts.");
        }
    }

    private String escapeCSV(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    // ---------------- LOCAL CHAT ----------------

    private void openChatWindow(String contactName) {
        Stage chatStage = new Stage();
        chatStage.setTitle("Chat with " + contactName);

        TextArea chatArea = new TextArea();
        chatArea.setEditable(false);
        chatArea.setWrapText(true);

        TextField messageField = new TextField();
        messageField.setPromptText("Type a message...");

        Button sendButton = new Button("Send");

        HBox inputBox = new HBox(10, messageField, sendButton);
        inputBox.setAlignment(Pos.CENTER);

        VBox root = new VBox(10, chatArea, inputBox);
        root.setPadding(new Insets(10));
        VBox.setVgrow(chatArea, Priority.ALWAYS);

        File directory = new File("messages");
        if (!directory.exists() && !directory.mkdirs()) {
            showAlert("Error", "Unable to create messages directory.");
            return;
        }

        String safeName = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(contactName.getBytes(StandardCharsets.UTF_8));

        File chatFile = new File(directory, safeName + ".txt");

        if (chatFile.exists()) {
            try {
                chatArea.setText(new String(
                        Files.readAllBytes(chatFile.toPath()),
                        StandardCharsets.UTF_8));
            } catch (IOException ex) {
                showAlert("Error", "Unable to load chat history.");
            }
        }

        Runnable sendMessage = () -> {
            String message = messageField.getText().trim();

            if (message.isEmpty()) {
                return;
            }

            String time = LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

            String formatted = "You (" + time + "): "
                    + message + System.lineSeparator();

            try (Writer writer = new OutputStreamWriter(
                    new FileOutputStream(chatFile, true),
                    StandardCharsets.UTF_8)) {

                writer.write(formatted);
                chatArea.appendText(formatted);
                messageField.clear();
            } catch (IOException ex) {
                showAlert("Error", "Unable to save message.");
            }
        };

        sendButton.setOnAction(e -> sendMessage.run());
        messageField.setOnAction(e -> sendMessage.run());

        chatStage.setScene(new Scene(root, 450, 500));
        chatStage.show();
    }

    // ---------------- ALERTS ----------------

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
