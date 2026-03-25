package org.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import oshi.SystemInfo;
import oshi.hardware.GlobalMemory;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.io.File;
import java.io.FileWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class Main extends Application {

    private final AtomicBoolean executando = new AtomicBoolean(false);
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> tarefaAgendada;

    // UI state
    private String caminhoArquivoSelecionado = "";

    @Override
    public void start(Stage stage) {

        // ── Cabeçalho ──────────────────────────────────────────────────
        Label titulo = new Label("MONITOR DE PROCESSOS");
        titulo.setFont(Font.font("Monospace", FontWeight.BOLD, 13));
        titulo.setTextFill(Color.web("#cccccc"));
        titulo.setStyle("-fx-letter-spacing: 2px;");

        HBox trafficLights = new HBox(6);
        trafficLights.setAlignment(Pos.CENTER_LEFT);
        Circle c1 = circulo("#ff5f57");
        Circle c2 = circulo("#ffbd2e");
        Circle c3 = circulo("#28c840");
        trafficLights.getChildren().addAll(c1, c2, c3);

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 14, 0));
        header.setStyle("-fx-border-color: #333333; -fx-border-width: 0 0 1 0;");
        header.getChildren().addAll(trafficLights, titulo);

        // ── Campo: Duração ─────────────────────────────────────────────
        Label lblDuracao = labelCampo("DURAÇÃO (MIN)");
        ComboBox<Integer> tempoExecucao = new ComboBox<>();
        tempoExecucao.getItems().addAll(1, 5, 10, 30, 60);
        tempoExecucao.setValue(1);
        estilizarCombo(tempoExecucao);

        HBox rowDuracao = rowCampo(lblDuracao, tempoExecucao);

        // ── Campo: Arquivo ─────────────────────────────────────────────
        Label lblArquivo = labelCampo("ARQUIVO DE LOG");
        Label pathDisplay = new Label("nenhum arquivo selecionado");
        pathDisplay.setFont(Font.font("Monospace", 12));
        pathDisplay.setTextFill(Color.web("#666666"));
        pathDisplay.setMaxWidth(200);
        pathDisplay.setEllipsisString("…");

        Button btnEscolher = botaoSecundario("escolher");
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Salvar arquivo de log");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Arquivo TXT", "*.txt")
        );
        btnEscolher.setOnAction(e -> {
            File file = fileChooser.showSaveDialog(stage);
            if (file != null) {
                caminhoArquivoSelecionado = file.getAbsolutePath();
                pathDisplay.setText(file.getName());
                pathDisplay.setTextFill(Color.web("#aaaaaa"));
            }
        });

        HBox pathBox = new HBox(8, pathDisplay, btnEscolher);
        pathBox.setAlignment(Pos.CENTER_LEFT);
        HBox rowArquivo = rowCampo(lblArquivo, pathBox);

        // ── Barra de progresso ─────────────────────────────────────────
        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(Double.MAX_VALUE);
        progressBar.setPrefHeight(3);
        progressBar.setStyle(
                "-fx-accent: #1D9E75;" +
                        "-fx-background-color: #2a2a2a;" +
                        "-fx-background-radius: 0;" +
                        "-fx-border-radius: 0;"
        );
        progressBar.setVisible(false);

        // ── Status ─────────────────────────────────────────────────────
        Circle statusDot = new Circle(4);
        statusDot.setFill(Color.web("#555555"));

        Label statusLabel = new Label("parado");
        statusLabel.setFont(Font.font("Monospace", 12));
        statusLabel.setTextFill(Color.web("#666666"));

        HBox statusBox = new HBox(8, statusDot, statusLabel);
        statusBox.setAlignment(Pos.CENTER_LEFT);

        // ── Botões ─────────────────────────────────────────────────────
        Button btnParar = botaoSecundario("parar");
        btnParar.setDisable(true);

        Button btnExecutar = botaoPrimario("executar");

        HBox botoesBox = new HBox(8, btnParar, btnExecutar);
        botoesBox.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(botoesBox, Priority.ALWAYS);

        HBox footer = new HBox(statusBox, botoesBox);
        footer.setAlignment(Pos.CENTER);
        footer.setPadding(new Insets(14, 0, 0, 0));
        footer.setStyle("-fx-border-color: #333333; -fx-border-width: 1 0 0 0;");

        // ── Layout principal ───────────────────────────────────────────
        VBox layout = new VBox(12,
                header,
                rowDuracao,
                rowArquivo,
                progressBar,
                footer
        );
        layout.setPadding(new Insets(20));
        layout.setStyle("-fx-background-color: #1a1a1a;");

        // ── Ações ──────────────────────────────────────────────────────
        btnExecutar.setOnAction(e -> {
            if (caminhoArquivoSelecionado.isEmpty()) {
                setStatus(statusDot, statusLabel, "escolha um arquivo primeiro", "#BA7517", "#ffbd2e");
                return;
            }
            int minutos = tempoExecucao.getValue();
            executando.set(true);
            btnExecutar.setDisable(true);
            btnParar.setDisable(false);
            progressBar.setVisible(true);
            progressBar.setProgress(0);
            setStatus(statusDot, statusLabel, "executando...", "#1D9E75", "#28c840");

            iniciarMonitor(minutos, caminhoArquivoSelecionado, statusDot, statusLabel,
                    progressBar, btnExecutar, btnParar);
        });

        btnParar.setOnAction(e -> {
            pararMonitor();
            btnExecutar.setDisable(false);
            btnParar.setDisable(true);
            setStatus(statusDot, statusLabel, "parado manualmente", "#993C1D", "#ff5f57");
        });

        // ── Cena ───────────────────────────────────────────────────────
        Scene scene = new Scene(layout, 500, 240);
        stage.setTitle("Monitor de Processos");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();

        stage.setOnCloseRequest(e -> pararMonitor());
    }

    // ── Lógica de monitoramento ────────────────────────────────────────

    private void iniciarMonitor(int minutos, String caminho,
                                Circle dot, Label statusLabel,
                                ProgressBar progressBar,
                                Button btnExecutar, Button btnParar) {

        scheduler = Executors.newSingleThreadScheduledExecutor();
        long totalMs = minutos * 60 * 1000L;
        long inicio = System.currentTimeMillis();

        tarefaAgendada = scheduler.scheduleAtFixedRate(() -> {

            if (!executando.get()) {
                return;
            }

            long agora = System.currentTimeMillis();
            double progresso = Math.min((double)(agora - inicio) / totalMs, 1.0);
            long restanteSeg = Math.max((totalMs - (agora - inicio)) / 1000, 0);
            long m = restanteSeg / 60;
            long s = restanteSeg % 60;
            String textoStatus = String.format("executando — %d:%02d restantes", m, s);

            Platform.runLater(() -> {
                progressBar.setProgress(progresso);
                statusLabel.setText(textoStatus);
            });

            coletarERegistrar(caminho);

            if (agora - inicio >= totalMs) {
                executando.set(false);
                Platform.runLater(() -> {
                    progressBar.setProgress(1.0);
                    setStatus(dot, statusLabel, "finalizado", "#0F6E56", "#28c840");
                    btnExecutar.setDisable(false);
                    btnParar.setDisable(true);
                });
                tarefaAgendada.cancel(false);
            }

        }, 0, 6, TimeUnit.SECONDS);
    }

    private void pararMonitor() {
        executando.set(false);
        if (tarefaAgendada != null) tarefaAgendada.cancel(false);
        if (scheduler != null) scheduler.shutdownNow();
    }

    private void coletarERegistrar(String caminho) {
        try (FileWriter writer = new FileWriter(caminho, true)) {

            SystemInfo si = new SystemInfo();
            OperatingSystem os = si.getOperatingSystem();
            GlobalMemory memory = si.getHardware().getMemory();
            long totalMemory = memory.getTotal();

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
            String dataHora = LocalDateTime.now().format(fmt);

            // Snapshot antes e depois para calcular CPU
            List<OSProcess> processosAntes = os.getProcesses(
                    null, OperatingSystem.ProcessSorting.RSS_DESC, 30);

            Thread.sleep(1000);

            List<OSProcess> processosDepois = os.getProcesses(
                    null, OperatingSystem.ProcessSorting.RSS_DESC, 30);

            // Cabeçalho do bloco
            writer.write("\n===============================\n");
            writer.write("Data/Hora: " + dataHora + "\n");

            // Uso total de memória do sistema
            long memoriaUsada = totalMemory - memory.getAvailable();
            double percentSistema = (memoriaUsada * 100.0) / totalMemory;
            writer.write(String.format(
                    "Uso total do sistema: %.2f%% (%.2f GB / %.2f GB)\n\n",
                    percentSistema,
                    toGB(memoriaUsada),
                    toGB(totalMemory)
            ));

            // Agrupamento por nome de programa
            writer.write("=== Consumo TOTAL por Programa ===\n");
            Map<String, Long> memoriaPorPrograma = new HashMap<>();
            for (OSProcess p : processosDepois) {
                memoriaPorPrograma.merge(p.getName(), p.getResidentSetSize(), Long::sum);
            }
            memoriaPorPrograma.entrySet().stream()
                    .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                    .limit(10)
                    .forEach(entry -> {
                        double pct = (entry.getValue() * 100.0) / totalMemory;
                        try {
                            writer.write(String.format(
                                    "  %-30s %.2f%% (%.2f MB)\n",
                                    entry.getKey(),
                                    pct,
                                    toMB(entry.getValue())
                            ));
                        } catch (Exception ignored) {}
                    });

            // Processos individuais com CPU
            writer.write("\n=== Processos Individuais ===\n");
            Map<Integer, OSProcess> mapaAntes = new HashMap<>();
            for (OSProcess p : processosAntes) mapaAntes.put(p.getProcessID(), p);

            for (OSProcess novo : processosDepois) {
                OSProcess antigo = mapaAntes.get(novo.getProcessID());
                if (antigo == null) continue;

                double ram = (novo.getResidentSetSize() * 100.0) / totalMemory;
                double cpu = novo.getProcessCpuLoadBetweenTicks(antigo) * 100;

                writer.write(String.format(
                        "  PID: %-6d | %-25s | RAM: %5.2f%% (%6.1f MB) | CPU: %5.2f%%\n",
                        novo.getProcessID(),
                        novo.getName(),
                        ram,
                        toMB(novo.getResidentSetSize()),
                        cpu
                ));
            }

            writer.flush();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ── Utilitários de conversão ───────────────────────────────────────

    private double toMB(long bytes) { return bytes / 1024.0 / 1024; }
    private double toGB(long bytes) { return bytes / 1024.0 / 1024 / 1024; }

    // ── Utilitários de UI ──────────────────────────────────────────────

    private void setStatus(Circle dot, Label label, String texto, String corDot, String corLabel) {
        dot.setFill(Color.web(corDot));
        label.setTextFill(Color.web(corLabel));
        label.setText(texto);
    }

    private Circle circulo(String cor) {
        Circle c = new Circle(6);
        c.setFill(Color.web(cor));
        return c;
    }

    private Label labelCampo(String texto) {
        Label l = new Label(texto);
        l.setFont(Font.font("Monospace", 10));
        l.setTextFill(Color.web("#555555"));
        l.setPrefWidth(130);
        return l;
    }

    private HBox rowCampo(Label label, javafx.scene.Node value) {
        HBox row = new HBox(12, label, value);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Button botaoPrimario(String texto) {
        Button b = new Button(texto);
        b.setFont(Font.font("Monospace", 12));
        b.setStyle(
                "-fx-background-color: #0F6E56;" +
                        "-fx-text-fill: #9FE1CB;" +
                        "-fx-border-color: #1D9E75;" +
                        "-fx-border-width: 1;" +
                        "-fx-border-radius: 4;" +
                        "-fx-background-radius: 4;" +
                        "-fx-cursor: hand;" +
                        "-fx-padding: 6 16 6 16;"
        );
        b.setOnMouseEntered(e -> b.setStyle(b.getStyle().replace("#0F6E56", "#085041")));
        b.setOnMouseExited(e -> b.setStyle(b.getStyle().replace("#085041", "#0F6E56")));
        return b;
    }

    private Button botaoSecundario(String texto) {
        Button b = new Button(texto);
        b.setFont(Font.font("Monospace", 12));
        b.setStyle(
                "-fx-background-color: transparent;" +
                        "-fx-text-fill: #888888;" +
                        "-fx-border-color: #333333;" +
                        "-fx-border-width: 1;" +
                        "-fx-border-radius: 4;" +
                        "-fx-background-radius: 4;" +
                        "-fx-cursor: hand;" +
                        "-fx-padding: 6 14 6 14;"
        );
        b.setOnMouseEntered(e -> {
            if (!b.isDisabled())
                b.setStyle(b.getStyle().replace("-fx-text-fill: #888888;", "-fx-text-fill: #cccccc;"));
        });
        b.setOnMouseExited(e -> {
            b.setStyle(b.getStyle().replace("-fx-text-fill: #cccccc;", "-fx-text-fill: #888888;"));
        });
        return b;
    }

    private void estilizarCombo(ComboBox<?> combo) {
        combo.setStyle(
                "-fx-background-color: #2a2a2a;" +
                        "-fx-text-fill: #aaaaaa;" +
                        "-fx-border-color: #333333;" +
                        "-fx-border-width: 1;" +
                        "-fx-border-radius: 4;" +
                        "-fx-background-radius: 4;" +
                        "-fx-font-family: Monospace;" +
                        "-fx-font-size: 12px;"
        );
    }

    public static void main(String[] args) {
        launch();
    }
}