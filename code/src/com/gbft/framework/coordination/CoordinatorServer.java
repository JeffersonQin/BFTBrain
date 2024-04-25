package com.gbft.framework.coordination;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Stream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;

import com.gbft.framework.data.Event;
import com.gbft.framework.data.Event.EventType;
import com.gbft.framework.utils.Config;
import com.gbft.framework.utils.ConfigObject;
import com.gbft.framework.utils.DataUtils;
import com.gbft.framework.utils.EntityMapUtils;
import com.gbft.framework.utils.Printer;

public class CoordinatorServer extends CoordinatorBase {
    private String protocol;
    private Map<String, String> configContent;

    private Map<EventType, Integer> responseCounter;

    private Benchmarker benchmarker;

    public CoordinatorServer(String protocol, int port) {
        super(port);

        this.protocol = protocol;

        responseCounter = new HashMap<>();
        configContent = new HashMap<>();

        try {
            var frameworkConfig = Files.readString(Path.of("../config/config.framework.yaml"));
            var protocolPool = new ConfigObject(frameworkConfig, "").stringList("switching.protocol-pool");

            configContent.put("framework", frameworkConfig);

            for (var pname : protocolPool) {
                var protocolConfig = Files.readString(Path.of("../config/config." + pname + ".yaml"));
                configContent.put(pname, protocolConfig);
            }

            initFromConfig(configContent, protocol);
        } catch (IOException e) {
            System.err.println("Error reading config files.");
            System.exit(1);
        }

        benchmarker = new Benchmarker();
    }

    public void run() {
        System.out.println("Press Enter after all units are connected, to start the benchmark.");
        System.console().readLine();

        System.out.println("Clients: " + EntityMapUtils.getAllClients());
        System.out.println("Nodes: " + EntityMapUtils.getAllNodes());
        System.out.print("Initializing units ...");

        var units = EntityMapUtils.getAllUnits();

        var configData = DataUtils.createConfigData(configContent, protocol, EntityMapUtils.allUnitData());
        var configEvent = DataUtils.createEvent(configData);

        sendEvent(units, configEvent);

        var unitCount = EntityMapUtils.unitCount();

        waitResponse(EventType.READY, unitCount);

        var initPluginsEvent = DataUtils.createEvent(EventType.PLUGIN_INIT);
        sendEvent(units, initPluginsEvent);

        waitResponse(EventType.READY, unitCount);

        var initConnectionsEvent = DataUtils.createEvent(EventType.CONNECTION, SERVER);
        sendEvent(units, initConnectionsEvent);

        waitResponse(EventType.READY, unitCount);

        System.out.println("\rUnits initialized.       ");

        var startEvent = DataUtils.createEvent(EventType.START);
        sendEvent(units, startEvent);

        benchmarker.start();
        System.out.println("Benchmark started.");

        System.out.println("Available commands: \"stop\"");
        try (var scanner = new Scanner(System.in)) {
            while (isRunning) {
                System.out.print("$ ");

                var command = System.console().readLine();
                if (command.equals("stop")) {
                    isRunning = false;
                } else if (command.startsWith("block")) {
                    var split = command.split(" ");
                    int target = Integer.parseInt(split[1]);
                    var blockEvent = DataUtils.createEvent(EventType.BLOCK, target);
                    sendEvent(units, blockEvent);
                }
            }
        }

        benchmarker.stop();

        println("Stopping all entities.");
        var stopEvent = DataUtils.createEvent(EventType.STOP);
        sendEvent(units, stopEvent);

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
        }

        stop();
    }

    @Override
    public void receiveEvent(Event event, Socket socket) {

        var eventType = event.getEventType();
        if (eventType == EventType.INIT) {
            var unitData = event.getUnitData();
            EntityMapUtils.addUnitData(unitData);
            println("Unit " + unitData.getUnit() + " is connected.");
        } else if (eventType == EventType.BENCHMARK_REPORT) {
            var report = Printer.convertToString(event.getReportData());
            benchmarker.print(report);
        }

        synchronized (responseCounter) {
            responseCounter.put(eventType, responseCounter.getOrDefault(eventType, 0) + 1);
            responseCounter.notify();
        }
    }

    private void waitResponse(EventType eventType, int expectedCount) {
        synchronized (responseCounter) {
            while (responseCounter.getOrDefault(eventType, 0) < expectedCount) {
                try {
                    responseCounter.wait();
                } catch (InterruptedException e) {
                }
            }

            responseCounter.put(eventType, 0);
        }
    }

    private class Benchmarker {
        private final String outputFile;
        private Timer benchmarkTimer;
        private final long benchmarkInterval;

        public Benchmarker() {
            benchmarkInterval = Config.integer("benchmark.benchmark-interval-ms");

            var directory = new File("benchmarks/");
            directory.mkdirs();
            var lastBenchmarkId = Stream.of(directory.listFiles())
                                        .map(file -> file.getName().split("-")[0])
                                        .filter(id -> StringUtils.isNumeric(id))
                                        .sorted(Comparator.reverseOrder()).findFirst().orElse("0000");

            var num = Integer.parseInt(lastBenchmarkId) + 1;
            var nextBenchmarkId = StringUtils.leftPad(num + "", 4).replace(' ', '0');
            outputFile = "benchmarks/" + nextBenchmarkId + "-" + protocol + ".txt";

            benchmarkTimer = new Timer();

            printTitle();
        }

        private void start() {
            benchmarkTimer.schedule(new BenchmarkTask(), benchmarkInterval, benchmarkInterval);
        }

        private void stop() {
            benchmarkTimer.cancel();
        }

        private void printTitle() {
            var reqinterval = Config.integer("benchmark.request-interval-micros") * 1000L;
            var blockSize = Config.integer("benchmark.block-size");
            var f = Config.integer("general.f");

            var title = new StringBuilder();
            title.append("== Benchmark Parameters: request-interval (initial)=").append(Printer.timeFormat(reqinterval, true))
                 .append(", block-size=").append(blockSize).append(", nodes=").append(EntityMapUtils.nodeCount())
                 .append(", f=").append(f).append("\n");

            print(title.toString());
        }

        private void print(String str) {
            try {
                Files.writeString(Path.of(outputFile), str,
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class BenchmarkTask extends TimerTask {

        private int reportnum = 0;

        @Override
        public void run() {
            if (isRunning) {
                reportnum += 1;
                var t = benchmarker.benchmarkInterval * reportnum;
                benchmarker.print("-- Report #" + reportnum + " @ t=" + Printer.timeFormat(t * 1000000L, true) + " --\n");

                var units = EntityMapUtils.getAllUnits();
                var reportEvent = DataUtils.createEvent(EventType.BENCHMARK_REPORT);
                sendEvent(units, reportEvent);
            }

        }
    }

    public static void main(String[] args) {
        Options options = new Options();

        var portOption = new Option("p", "port", true, "the coordination server port");
        var protocolOption = new Option("r", "protocol", true, "the benchmark protocol");
        portOption.setType(Number.class);
        portOption.setRequired(true);
        protocolOption.setRequired(true);

        options.addOption(portOption);
        options.addOption(protocolOption);

        CommandLineParser parser = new DefaultParser();
        try {
            CommandLine cmd = parser.parse(options, args);
            Number port = (Number) cmd.getParsedOptionValue("port");
            var protocol = cmd.getOptionValue("protocol");

            new CoordinatorServer(protocol, port.intValue()).run();
        } catch (ParseException e) {
            System.err.println("Command parsing error: " + e.getMessage());
            var formatter = new HelpFormatter();
            formatter.printHelp("Usage:", options);
        }
    }
}
