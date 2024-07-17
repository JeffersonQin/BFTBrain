package com.gbft.framework.coordination;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;

import com.gbft.framework.data.Event;
import com.gbft.framework.data.Event.EventType;
import com.gbft.framework.demo.DemoDataUtil;
import com.gbft.framework.demo.SettingPatcherField;
import com.gbft.framework.statemachine.StateMachine;
import com.gbft.framework.utils.AdvanceConfig;
import com.gbft.framework.utils.Config;
import com.google.gson.Gson;

public abstract class CoordinatorBase {
    protected static final int SERVER = -1;
    private static SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss.S ");

    protected boolean isRunning;

    private ServerSocket serverSocket;
    protected Thread listenerThread;

    protected Map<Integer, Pair<String, Integer>> unitAddressMap;

    public CoordinatorBase(int port) {
        isRunning = true;
        unitAddressMap = new HashMap<>();

        try {
            serverSocket = new ServerSocket(port);
            listenerThread = new Thread(() -> netListen());
            listenerThread.start();
        } catch (IOException e) {
            System.err.println("Problem while creating socket.");
            isRunning = false;
        }
    }

    protected void initFromConfig(Map<String, String> yamlData, String defaultProtocol) {
        try {
            Config.load(yamlData, defaultProtocol);
            AdvanceConfig.load(yamlData.get("framework"));
            if (Config.bool("demo.enabled")) {
                Gson gson = new Gson();
                new Thread(() -> {
                    var last_config = "";
                    while (true) {
                        try {
                            Thread.sleep(Config.integer("demo.update_interval_ms"));
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        // pull data from server
                        var this_unit_id = 0;
                        if (this instanceof CoordinatorServer) {
                            this_unit_id = 0;
                        } else if (this instanceof CoordinatorUnit) {
                            this_unit_id = ((CoordinatorUnit) this).unit_id + 1;
                        } else {
                            System.out.println("[!!!] Unknown coordinator type.");
                            System.exit(1);
                        }
                        var demo_config = DemoDataUtil.getConfig(this_unit_id);

                        // check if same as last time
                        if (demo_config.equals(last_config)) {
                            continue;
                        }
                        last_config = demo_config;

                        // convert from json to SettingPatcherField[]
                        var config_patches = gson.fromJson(demo_config, SettingPatcherField[].class);
                        // update config cache
                        for (var patch : config_patches) {
                            System.out.println("Update config: " + patch.getKey() + " = " + patch.getValue() + " (" + patch.getType() + ")");
                            Config.getCurrentCache().put(patch.getKey(), patch.getValue());
                        }

                        System.out.println("Demo config updated. (" + config_patches.length + ")");
                    }
                }).start();
            }
        } catch (IOException e) {
            System.err.println("Error loading config.");
            System.exit(1);
        }

        var unitConfig = Config.list("network.units");
        for (var i = 0; i < unitConfig.size(); i++) {
            var address = unitConfig.get(i).split(":");
            unitAddressMap.put(i, Pair.of(address[0], Integer.parseInt(address[1])));
        }

        StateMachine.init();
    }

    public void sendEvent(List<Integer> units, Event event) {
        for (var unit : units) {
            sendEvent(unit, event);
        }
    }

    public void sendEvent(int unit, Event event) {
        var address = unitAddressMap.get(unit);

        try {
            var socket = new Socket(address.getLeft(), address.getRight());
            new Thread(() -> netSend(socket, event)).start();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (ConnectException e) {
            System.err.println("Could not connect to " + address);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected abstract void receiveEvent(Event event, Socket socket);

    protected void netSend(Socket socket, Event event) {
        DataOutputStream out;
        var is_connection = event.getEventType() == EventType.CONNECTION;
        try {
            out = new DataOutputStream(socket.getOutputStream());
            var bytes = event.toByteArray();

            out.writeInt(bytes.length);
            out.write(bytes);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (!is_connection) {
                    socket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    protected void netReceive(Socket socket) {
        DataInputStream in;
        boolean is_connection = false;
        try {
            in = new DataInputStream(socket.getInputStream());

            var messageLen = in.readInt();
            var message = in.readNBytes(messageLen);

            var event = Event.parseFrom(message);
            is_connection = event.getEventType() == EventType.CONNECTION;

            receiveEvent(event, socket);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (!is_connection) {
                    socket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void netListen() {
        while (isRunning) {
            try {
                if (serverSocket.isClosed()) {
                    isRunning = false;
                    break;
                }

                var socket = serverSocket.accept();
                // TODO: Use Virtual Threads
                new Thread(() -> netReceive(socket)).start();
            } catch (SocketException e) {
                isRunning = false;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        println("Net listener stopped.");
    }

    protected void stop() {
        isRunning = false;
        listenerThread.interrupt();
        try {
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected void println(String str) {
        var date = dateFormat.format(new Date(System.currentTimeMillis()));
        System.out.println(date + " " + str);
    }
}
