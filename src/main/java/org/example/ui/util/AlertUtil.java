package org.example.ui.util;

import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;

public final class AlertUtil {
    private AlertUtil() {
    }

    public static void showError(Window owner, String message) {
        show(owner, "Error", "Loi", message, "dialog-error", "x");
    }

    public static void showInfo(Window owner, String message) {
        show(owner, "Info", "Thong bao", message, "dialog-info", "i");
    }

    public static void showWarning(Window owner, String message) {
        show(owner, "Warning", "Can chu y", message, "dialog-warning", "!");
    }

    private static void show(
            Window owner,
            String windowTitle,
            String bodyTitle,
            String message,
            String variantClass,
            String iconText
    ) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(windowTitle);
        dialog.initModality(Modality.WINDOW_MODAL);
        if (owner != null) {
            dialog.initOwner(owner);
        }

        DialogPane pane = dialog.getDialogPane();
        pane.getStyleClass().addAll("app-dialog", variantClass);
        pane.getButtonTypes().setAll(ButtonType.OK);
        pane.setHeaderText(null);
        pane.setContent(createContent(bodyTitle, message, iconText));

        String stylesheet = AlertUtil.class.getResource("/css/style.css").toExternalForm();
        if (!pane.getStylesheets().contains(stylesheet)) {
            pane.getStylesheets().add(stylesheet);
        }

        Node okButton = pane.lookupButton(ButtonType.OK);
        if (okButton != null) {
            okButton.getStyleClass().addAll("dialog-action", "btn-primary");
        }

        dialog.showAndWait();
    }

    private static HBox createContent(String title, String message, String iconText) {
        Label icon = new Label(iconText);
        icon.getStyleClass().add("dialog-icon");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("dialog-title");

        Label messageLabel = new Label(message == null ? "" : message);
        messageLabel.getStyleClass().add("dialog-message");
        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(440.0);

        VBox textBox = new VBox(5.0, titleLabel, messageLabel);
        textBox.getStyleClass().add("dialog-text-box");

        HBox content = new HBox(14.0, icon, textBox);
        content.getStyleClass().add("dialog-content");
        return content;
    }
}
