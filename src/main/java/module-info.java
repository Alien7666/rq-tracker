module com.rqtracker {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.fasterxml.jackson.databind;
    requires java.logging;
    requires java.desktop;
    requires java.net.http;

    opens com.rqtracker to javafx.fxml;
    opens com.rqtracker.model to com.fasterxml.jackson.databind;
    opens com.rqtracker.ui.controller to javafx.fxml;
    opens com.rqtracker.ui.component to javafx.fxml;
    opens com.rqtracker.ui.dialog to javafx.fxml;

    exports com.rqtracker;
    exports com.rqtracker.model;
    exports com.rqtracker.service;
    exports com.rqtracker.ui.controller;
    exports com.rqtracker.ui.component;
    exports com.rqtracker.ui.dialog;
    exports com.rqtracker.util;
}
