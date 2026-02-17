package org.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
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
import java.util.List;

public class Main extends Application {

    private volatile boolean executando = false;

    @Override
    public void start(Stage stage) {

        // Tempo de execução
        ComboBox<Integer> tempoExecucao = new ComboBox<>();
        tempoExecucao.getItems().addAll(1, 5, 10, 30, 60);
        tempoExecucao.setValue(1);

        // Campo de arquivo
        TextField caminhoArquivo = new TextField();
        caminhoArquivo.setEditable(false);
        caminhoArquivo.setPrefWidth(250);

        Button escolherArquivo = new Button("Escolher");

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Salvar arquivo de log");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Arquivo TXT", "*.txt")
        );

        escolherArquivo.setOnAction(e -> {
            File file = fileChooser.showSaveDialog(stage);
            if (file != null) {
                caminhoArquivo.setText(file.getAbsolutePath());
            }
        });

        Button executar = new Button("Executar");
        Button parar = new Button("Parar");

        Label status = new Label("Parado");

        executar.setOnAction(e -> {

            if (caminhoArquivo.getText().isEmpty()) {
                status.setText("Escolha um arquivo!");
                return;
            }

            int minutos = tempoExecucao.getValue();

            executando = true;
            status.setText("Executando...");

            new Thread(() ->
                    registrar(minutos, caminhoArquivo.getText(), status)
            ).start();
        });

        parar.setOnAction(e -> {
            executando = false;
            status.setText("Parado manualmente.");
        });

        // Layout
        GridPane grid = new GridPane();
        grid.setPadding(new Insets(20));
        grid.setVgap(10);
        grid.setHgap(10);

        grid.add(new Label("Tempo (min):"), 0, 0);
        grid.add(tempoExecucao, 1, 0);

        grid.add(new Label("Arquivo de log:"), 0, 1);
        grid.add(caminhoArquivo, 1, 1);
        grid.add(escolherArquivo, 2, 1);

        grid.add(executar, 1, 2);
        grid.add(parar, 2, 2);

        grid.add(status, 1, 3);

        Scene scene = new Scene(grid, 500, 220);

        stage.setTitle("Monitor de Processos");
        stage.setScene(scene);
        stage.show();
    }
    public void registrar(int minutos,
                          String caminho,
                          Label status) {

        try (FileWriter writer = new FileWriter(caminho, false)) {

            SystemInfo si = new SystemInfo();
            OperatingSystem os = si.getOperatingSystem();
            GlobalMemory memory = si.getHardware().getMemory();

            long totalMemory = memory.getTotal();

            DateTimeFormatter formatter =
                    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

            long tempoFinal =
                    System.currentTimeMillis() + (minutos * 60 * 1000L);

            while (executando &&
                    System.currentTimeMillis() < tempoFinal) {

                String dataHora =
                        LocalDateTime.now().format(formatter);

                List<OSProcess> processosAntigos =
                        os.getProcesses(null,
                                OperatingSystem.ProcessSorting.RSS_DESC,
                                30);

                Thread.sleep(1000);

                List<OSProcess> processosNovos =
                        os.getProcesses(null,
                                OperatingSystem.ProcessSorting.RSS_DESC,
                                30);

                writer.write("\n===============================\n");
                writer.write("Data/Hora: " + dataHora + "\n");

                // 🔥 Uso total do sistema
                long memoriaUsadaTotal =
                        memory.getTotal() - memory.getAvailable();

                double percentSistema =
                        (memoriaUsadaTotal * 100.0) / totalMemory;

                writer.write(String.format(
                        "Uso total do sistema: %.2f%% (%.2f GB / %.2f GB)\n\n",
                        percentSistema,
                        memoriaUsadaTotal / 1024.0 / 1024 / 1024,
                        totalMemory / 1024.0 / 1024 / 1024
                ));

                // 🔥 Agrupar por nome do programa
                java.util.Map<String, Long> memoriaPorPrograma =
                        new java.util.HashMap<>();

                for (OSProcess p : processosNovos) {
                    memoriaPorPrograma.merge(
                            p.getName(),
                            p.getResidentSetSize(),
                            Long::sum
                    );
                }

                writer.write("=== Consumo TOTAL por Programa ===\n");

                memoriaPorPrograma.entrySet().stream()
                        .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                        .limit(10)
                        .forEach(entry -> {

                            double percent =
                                    (entry.getValue() * 100.0) / totalMemory;

                            try {
                                writer.write(String.format(
                                        "%s -> %.2f%% (%.2f MB)\n",
                                        entry.getKey(),
                                        percent,
                                        entry.getValue() / 1024.0 / 1024
                                ));
                            } catch (Exception ignored) {}
                        });

                writer.write("\n=== Processos Individuais ===\n");

                // 🔥 Mapa para CPU correta por PID
                java.util.Map<Integer, OSProcess> mapaAntigos =
                        new java.util.HashMap<>();

                for (OSProcess p : processosAntigos) {
                    mapaAntigos.put(p.getProcessID(), p);
                }

                for (OSProcess novo : processosNovos) {

                    OSProcess antigo =
                            mapaAntigos.get(novo.getProcessID());

                    if (antigo == null) continue;

                    long usedMemory = novo.getResidentSetSize();

                    double ramPercent =
                            (usedMemory * 100.0) / totalMemory;

                    double cpuPercent =
                            novo.getProcessCpuLoadBetweenTicks(antigo) * 100;

                    writer.write(String.format(
                            "PID: %d | Nome: %s | RAM: %.2f%% (%.2f MB) | CPU: %.2f%%\n",
                            novo.getProcessID(),
                            novo.getName(),
                            ramPercent,
                            usedMemory / 1024.0 / 1024,
                            cpuPercent
                    ));
                }

                writer.flush();
                Thread.sleep(5000);
            }

            executando = false;

            Platform.runLater(() ->
                    status.setText("Finalizado."));

        } catch (Exception e) {
            e.printStackTrace();
            Platform.runLater(() ->
                    status.setText("Erro ao executar."));
        }
    }


    public static void main(String[] args) {
        launch();
    }
}
