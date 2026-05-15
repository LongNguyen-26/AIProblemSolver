package org.example.app;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) throws IOException {
        URL fxml = getClass().getResource("/fxml/main.fxml");
        FXMLLoader loader = new FXMLLoader(fxml);
        Scene scene = new Scene(loader.load(), 1200, 750);
        scene.getStylesheets().add(
                getClass().getResource("/css/style.css").toExternalForm()
        );
        primaryStage.setTitle("AIProblemSolver - IOI/ICPC Test Case Generator");
        primaryStage.setScene(scene);
        primaryStage.show();
    }
}
