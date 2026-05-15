package org.example.app;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.util.AppConfig;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

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

    @Override
    public void stop() throws Exception {
        Path sandbox = Paths.get(AppConfig.get("execution.sandboxPath")).toAbsolutePath().normalize();
        if (Files.exists(sandbox)) {
            try (var paths = Files.walk(sandbox)) {
                paths.filter(path -> !path.equals(sandbox))
                        .filter(path -> !".gitkeep".equals(path.getFileName().toString()))
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        }
        super.stop();
    }
}
