package org.example.ui.controller;

import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import org.example.model.Problem;

import java.util.List;

public class ProblemInputController {
    @FXML private TextField titleField;
    @FXML private TextArea inputFormatArea;
    @FXML private TextArea outputFormatArea;
    @FXML private TextArea descriptionArea;
    @FXML private ListView<String> constraintsList;
    @FXML private TextField problemTypeField;
    @FXML private TextArea tleStrategyArea;

    public void displayProblem(Problem problem) {
        if (problem == null) {
            clear();
            return;
        }

        titleField.setText(valueOrEmpty(problem.getTitle()));
        inputFormatArea.setText(valueOrEmpty(problem.getInputFormat()));
        outputFormatArea.setText(valueOrEmpty(problem.getOutputFormat()));
        descriptionArea.setText(valueOrEmpty(problem.getDescription()));
        problemTypeField.setText(problemTypeText(problem));
        tleStrategyArea.setText(valueOrEmpty(problem.getTleStrategy()));
        constraintsList.getItems().setAll(
                problem.getConstraints() == null ? List.of() : problem.getConstraints()
        );
    }

    public void clear() {
        titleField.clear();
        inputFormatArea.clear();
        outputFormatArea.clear();
        descriptionArea.clear();
        problemTypeField.clear();
        tleStrategyArea.clear();
        constraintsList.getItems().clear();
    }

    private String problemTypeText(Problem problem) {
        String primary = valueOrEmpty(problem.getProblemType());
        if (primary.isBlank()) {
            return "";
        }

        String secondary = valueOrEmpty(problem.getSecondaryType());
        StringBuilder builder = new StringBuilder(primary);
        if (!secondary.isBlank()) {
            builder.append(" / ").append(secondary);
        }
        if (problem.getTypeConfidence() > 0) {
            builder.append(" (")
                    .append(Math.round(problem.getTypeConfidence() * 100))
                    .append("%)");
        }
        return builder.toString();
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
