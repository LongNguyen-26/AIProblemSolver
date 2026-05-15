# TASK_01_PROJECT_SETUP.md

## Mục tiêu
Cấu hình Maven project với JavaFX 17, tổ chức đúng cấu trúc thư mục, project chạy được với màn hình JavaFX trống.

---

## 1. Cập nhật `pom.xml`

Thay toàn bộ nội dung `pom.xml` bằng:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>org.example</groupId>
    <artifactId>AIProblemSolver</artifactId>
    <version>1.0-SNAPSHOT</version>
    <packaging>jar</packaging>

    <properties>
        <maven.compiler.source>16</maven.compiler.source>
        <maven.compiler.target>16</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <javafx.version>17.0.6</javafx.version>
    </properties>

    <dependencies>
        <!-- JavaFX -->
        <dependency>
            <groupId>org.openjfx</groupId>
            <artifactId>javafx-controls</artifactId>
            <version>${javafx.version}</version>
        </dependency>
        <dependency>
            <groupId>org.openjfx</groupId>
            <artifactId>javafx-fxml</artifactId>
            <version>${javafx.version}</version>
        </dependency>

        <!-- JSON -->
        <dependency>
            <groupId>com.google.code.gson</groupId>
            <artifactId>gson</artifactId>
            <version>2.10.1</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- JavaFX Maven Plugin -->
            <plugin>
                <groupId>org.openjfx</groupId>
                <artifactId>javafx-maven-plugin</artifactId>
                <version>0.0.8</version>
                <configuration>
                    <mainClass>org.example.Main</mainClass>
                </configuration>
            </plugin>

            <!-- Fat JAR (executable) -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.4.1</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals><goal>shade</goal></goals>
                        <configuration>
                            <transformers>
                                <transformer implementation=
                                  "org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                                    <mainClass>org.example.Main</mainClass>
                                </transformer>
                            </transformers>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

---

## 2. Tạo cấu trúc thư mục `src/`

Chạy lệnh (hoặc tạo tay trong IDE):
```
src/main/java/org/example/
    Main.java
    app/
        MainApp.java
    ui/
        controller/
            MainController.java
            ProblemInputController.java
            TestCaseController.java
            ResultController.java
        util/
            AlertUtil.java
    model/
        Problem.java
        TestCase.java
        CodeSubmission.java
        AnalysisReport.java
    service/
        AIBridgeService.java
        TestCaseService.java
        ExecutionService.java
        ReportService.java
    util/
        HttpUtil.java
        FileUtil.java
        AppConfig.java

src/main/resources/
    fxml/
        main.fxml
        problem_input.fxml
        testcase_view.fxml
        result_view.fxml
    css/
        style.css
    config.properties
```

---

## 3. `src/main/java/org/example/Main.java`

```java
package org.example;

import org.example.app.MainApp;
import javafx.application.Application;

public class Main {
    public static void main(String[] args) {
        Application.launch(MainApp.class, args);
    }
}
```

---

## 4. `src/main/java/org/example/app/MainApp.java`

```java
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
        primaryStage.setTitle("AIProblemSolver — IOI/ICPC Test Case Generator");
        primaryStage.setScene(scene);
        primaryStage.show();
    }
}
```

---

## 5. `src/main/resources/config.properties`

```properties
# Python AI Service
ai.service.baseUrl=http://localhost:8000

# Execution settings
execution.timeoutSeconds=5
execution.sandboxPath=sandbox

# App
app.version=1.0.0
```

---

## 6. `src/main/java/org/example/util/AppConfig.java`

```java
package org.example.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class AppConfig {
    private static final Properties props = new Properties();

    static {
        try (InputStream is = AppConfig.class
                .getResourceAsStream("/config.properties")) {
            props.load(is);
        } catch (IOException e) {
            throw new RuntimeException("Cannot load config.properties", e);
        }
    }

    public static String get(String key) {
        return props.getProperty(key, "");
    }

    public static int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(props.getProperty(key));
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
```

---

## 7. Kiểm tra chạy được

```bash
mvn clean javafx:run
```

Kết quả mong đợi: Cửa sổ JavaFX mở ra (có thể là blank stage). Không có compile error.

---

## Checklist

- [ ] `pom.xml` updated với JavaFX 17 + Gson + javafx-maven-plugin
- [ ] `Main.java` và `MainApp.java` tạo xong
- [ ] `config.properties` tạo xong
- [ ] `AppConfig.java` tạo xong
- [ ] `mvn clean javafx:run` chạy không lỗi
- [ ] Tạo đủ thư mục rỗng (placeholder) cho các bước tiếp theo
