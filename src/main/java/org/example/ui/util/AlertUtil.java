package org.example.ui.util;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

public final class AlertUtil {
    private AlertUtil() {
    }

    public static void showError(Window owner, String message) {
        show(owner, Alert.AlertType.ERROR, "Error", message);
    }

    public static void showInfo(Window owner, String message) {
        show(owner, Alert.AlertType.INFORMATION, "Info", message);
    }

    public static void showWarning(Window owner, String message) {
        show(owner, Alert.AlertType.WARNING, "Warning", message);
    }

    private static void show(Window owner, Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        if (owner != null) {
            alert.initOwner(owner);
        }
        alert.showAndWait();
    }
}
