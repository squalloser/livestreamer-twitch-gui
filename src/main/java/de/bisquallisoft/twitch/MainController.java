package de.bisquallisoft.twitch;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class MainController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    private Stage primaryStage;

    @FXML
    private ListView<Stream> streamList;
    @FXML
    private ImageView previewImage;
    @FXML
    private ProgressIndicator livestreamerProgess;
    @FXML
    private TextField streamLink;
    @FXML
    private TextField txtStreamStatus;
    @FXML
    private TextField txtViewers;
    @FXML
    private Pane imageParent;
    @FXML
    private TextField txtGame;

    private TwitchApi api;
    private Settings settings = Settings.getInstance();

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        previewImage.fitWidthProperty().bind(imageParent.widthProperty());
        previewImage.fitHeightProperty().bind(imageParent.heightProperty());

        if (settings.getAuthToken() == null) {
            String authToken = authenticate(primaryStage);
            settings.setAuthToken(authToken);
            settings.save();
        }
        api = new TwitchApi(settings.getAuthToken());

        loadStreams();
        streamList.getSelectionModel().selectedItemProperty().addListener((observableValue, ov, nv) -> {
            if (nv != null) setPreview(nv);
        });
        //refresh streams every 3 minutes
        FxScheduler.schedule(Duration.minutes(3), () -> {
            log.debug("refreshing streams");
            Stream selectedItem = streamList.getSelectionModel().getSelectedItem();
            streamList.getItems().setAll(api.getStreams());
            if (!streamList.getItems().isEmpty()) {
                if (selectedItem != null && streamList.getItems().contains(selectedItem)) {
                    streamList.getSelectionModel().select(selectedItem);
                } else {
                    streamList.getSelectionModel().select(0);
                }
            }
        });

        streamLink.setOnAction(this::streamLinkAction);
        Platform.runLater(streamList::requestFocus);
    }

    void streamLinkAction(ActionEvent event) {
        launchLivestreamer(streamLink.getText());
        streamLink.setText("");
    }

    void loadStreams() {
        List<Stream> streams = api.getStreams();
        streamList.getItems().setAll(streams);

        if (!streams.isEmpty()) {
            streamList.getSelectionModel().select(0);
            setPreview(streams.get(0));
        }
    }

    @FXML
    void previewClicked(MouseEvent event) {
        launchLivestreamer(streamList.getSelectionModel().getSelectedItem().getUrl());
    }

    @FXML
    void streamListClicked(MouseEvent event) {
        Stream selectedItem = streamList.getSelectionModel().getSelectedItem();
        if (selectedItem != null && event.getClickCount() == 2) {
            launchLivestreamer(selectedItem.getUrl());
        }
    }

    private void setPreview(Stream stream) {
        previewImage.setImage(new Image(stream.getPreviewImage()));
        txtStreamStatus.setText(stream.getStatus());
        txtViewers.setText(stream.getViewers() + "");
        txtGame.setText(stream.getGame());
    }

    @FXML
    void refreshPressed(ActionEvent event) {
        streamList.getItems().setAll(api.getStreams());
    }

    private void launchLivestreamer(String url) {
        livestreamerProgess.setPrefHeight(imageParent.getHeight());
        System.out.println("height:" + imageParent.getHeight() + " width:" + imageParent.getWidth());
        livestreamerProgess.setPrefWidth(imageParent.getWidth());

        livestreamerProgess.setVisible(true);
        livestreamerProgess.setProgress(-1);
        try {
            new ProcessBuilder("livestreamer", url, "source").start();
        } catch (Exception e) {
            throw new RuntimeException("error executing livestreamer", e);
        }

        FxScheduler.scheduleOnce(Duration.seconds(6), () -> {
            livestreamerProgess.setProgress(1);
            livestreamerProgess.setVisible(false);
        });
    }

    private String authenticate(Window parent) {
        WebView webView = new WebView();
        webView.getEngine().load(TwitchApi.AUTH_URL);

        Stage popup = new Stage();
        popup.setScene(new Scene((webView)));
        popup.initModality(Modality.APPLICATION_MODAL);
        popup.initOwner(parent);

        StringBuilder result = new StringBuilder();
        webView.getEngine().locationProperty().addListener((observableValue, ov, url) -> {
            if (url.startsWith("http://localhost")) {
                String token = StringUtils.substringBetween(url, "=", "&");
                result.append(token);
                popup.close();
            }
        });
        popup.showAndWait();
        return result.toString();
    }

    void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
        primaryStage.setMinWidth(100.0);
        primaryStage.setMinHeight(80.0);
    }
}
