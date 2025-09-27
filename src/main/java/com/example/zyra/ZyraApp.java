package com.example.zyra;

import java.awt.Desktop;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Random;

import org.json.JSONArray;
import org.json.JSONObject;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class ZyraApp extends Application {

    private VBox chatBox;
    private TextField userInput;

    private static final String WEATHER_API_KEY = "YOUR_OPENWEATHERMAP_API_KEY";
    private static final String NEWS_API_KEY = "YOUR_NEWSAPI_KEY";

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Zyra 2.0");

        chatBox = new VBox(10);
        chatBox.setPadding(new Insets(10));
        ScrollPane scrollPane = new ScrollPane(chatBox);
        scrollPane.setFitToWidth(true);

        userInput = new TextField();
        userInput.setPromptText("Type your message...");
        userInput.setOnAction(e -> sendMessage());

        Button sendButton = new Button("Send");
        sendButton.setOnAction(e -> sendMessage());

        HBox inputBox = new HBox(5, userInput, sendButton);
        inputBox.setPadding(new Insets(10));
        inputBox.setAlignment(Pos.CENTER);

        BorderPane root = new BorderPane();
        root.setCenter(scrollPane);
        root.setBottom(inputBox);

        addBotMessage("Hello! I am Zyra 2.0. How can I help you today?");

        Scene scene = new Scene(root, 500, 650);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void sendMessage() {
        String text = userInput.getText().trim();
        if (text.isEmpty()) return;
        addUserMessage(text);
        userInput.clear();

        new Thread(() -> processCommand(text)).start();
    }

    private void addUserMessage(String text) {
        Label label = new Label("You: " + text);
        label.setStyle("-fx-background-color: #ECECEC; -fx-padding: 8;");
        Platform.runLater(() -> chatBox.getChildren().add(label));
    }

    private void addBotMessage(String text) {
        Label label = new Label("Zyra: " + text);
        label.setStyle("-fx-background-color: #DCF8C6; -fx-padding: 8;");
        Platform.runLater(() -> chatBox.getChildren().add(label));
        speak(text);
    }

    private void processCommand(String cmd) {
        cmd = cmd.toLowerCase();

        if (cmd.contains("hi") || cmd.contains("hello") || cmd.contains("hey")) {
            addBotMessage("Hello! How are you today?");
        } else if (cmd.contains("i am fine") || cmd.contains("i'm fine")) {
            addBotMessage("Good to hear!");
        } else if (cmd.contains("joke")) {
            String[] jokes = {
                    "Why did the computer show up at work late? It had a hard drive!",
                    "Why do programmers prefer dark mode? Because light attracts bugs!",
                    "Why did the developer go broke? Because he used up all his cache!"
            };
            addBotMessage(jokes[new Random().nextInt(jokes.length)]);
        } else if (cmd.contains("flip a coin")) {
            addBotMessage(new Random().nextBoolean() ? "Heads!" : "Tails!");
        } else if (cmd.contains("roll a dice") || cmd.contains("random number")) {
            int num = cmd.contains("dice") ? new Random().nextInt(6) + 1 : new Random().nextInt(100) + 1;
            addBotMessage("Your random number is " + num);
        } else if (cmd.contains("tell me a fact") || cmd.contains("tell me something")) {
            String[] facts = {
                    "Honey never spoils.",
                    "Octopuses have three hearts.",
                    "Bananas are berries, but strawberries are not.",
                    "A day on Venus is longer than a year on Venus."
            };
            addBotMessage(facts[new Random().nextInt(facts.length)]);
        } else if (cmd.contains("weather")) {
            String city = cmd.replace("weather","").trim();
            if (!city.isEmpty()) getWeather(city);
            else addBotMessage("Please specify a city.");
        } else if (cmd.contains("news")) {
            getNews();
        } else if (cmd.contains("play")) {
            String query = cmd.replace("play","").trim();
            if(!query.isEmpty()) {
                try {
                    String url = "https://www.youtube.com/results?search_query=" + query.replace(" ", "+");
                    Desktop.getDesktop().browse(new URI(url));
                    addBotMessage("Opening YouTube search for: " + query);
                } catch (Exception e) {
                    addBotMessage("Cannot open YouTube.");
                }
            }
        } else if (cmd.contains("time")) {
            if (cmd.contains("in")) {
                String city = cmd.split("in")[1].trim();
                try {
                    ZoneId zone = ZoneId.of(city.replace(" ", "_"));
                    LocalDateTime now = LocalDateTime.now(zone);
                    addBotMessage("Current time in " + city + " is " +
                            now.format(DateTimeFormatter.ofPattern("hh:mm a")));
                } catch (Exception e) {
                    addBotMessage("Couldn't find the time for " + city + ". Showing IST instead.");
                    LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
                    addBotMessage("Current time: " +
                            now.format(DateTimeFormatter.ofPattern("hh:mm a")));
                }
            } else {
                LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
                addBotMessage("Current time: " +
                        now.format(DateTimeFormatter.ofPattern("hh:mm a")));
            }
        } else if (cmd.contains("open")) {
            try {
                String url = cmd.replace("open", "").trim();
                if (!url.startsWith("http")) url = "https://" + url;
                Desktop.getDesktop().browse(new URI(url));
                addBotMessage("Opening " + url);
            } catch (Exception e) {
                addBotMessage("Cannot open the link.");
            }
        } else if (cmd.contains("exit") || cmd.contains("bye") || cmd.contains("quit")) {
            addBotMessage("Goodbye! Have a nice day.");
            Platform.exit();
        } else {
            addBotMessage("I didn't understand that. Try another command.");
        }
    }

    private void speak(String text) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                new ProcessBuilder("PowerShell", "-Command",
                        "Add-Type –AssemblyName System.Speech; " +
                                "$speak = New-Object System.Speech.Synthesis.SpeechSynthesizer; " +
                                "$speak.Speak('" + text + "');").start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("say", text).start();
            } else {
                new ProcessBuilder("espeak", text).start();
            }
        } catch (Exception e) {
            System.out.println("TTS not available: " + e.getMessage());
        }
    }

    private void getWeather(String city) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI("http://api.openweathermap.org/data/2.5/weather?q=" +
                            city + "&appid=" + WEATHER_API_KEY + "&units=metric"))
                    .GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JSONObject obj = new JSONObject(response.body());
            if (obj.has("main")) {
                double temp = obj.getJSONObject("main").getDouble("temp");
                String desc = obj.getJSONArray("weather").getJSONObject(0).getString("description");
                addBotMessage("Current temperature in " + city + " is " + temp + "°C with " + desc + ".");
            } else {
                addBotMessage("Couldn't fetch weather for " + city + ".");
            }
        } catch (Exception e) {
            addBotMessage("Weather service is unavailable.");
        }
    }

    private void getNews() {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI("https://newsapi.org/v2/top-headlines?country=us&apiKey=" + NEWS_API_KEY))
                    .GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JSONObject obj = new JSONObject(response.body());
            JSONArray articles = obj.getJSONArray("articles");
            if (articles.length() > 0) {
                addBotMessage("Here are the top 5 news headlines:");
                for (int i = 0; i < Math.min(5, articles.length()); i++) {
                    addBotMessage((i+1) + ". " + articles.getJSONObject(i).getString("title"));
                }
            } else {
                addBotMessage("No news found.");
            }
        } catch (Exception e) {
            addBotMessage("News service is unavailable.");
        }
    }
}
