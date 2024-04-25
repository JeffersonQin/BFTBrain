package com.gbft.framework.coordination;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.gbft.framework.data.Event;
import com.gbft.framework.data.Event.EventType;
import com.gbft.framework.statemachine.StateMachine;
import com.gbft.framework.utils.BenchmarkManager;
import com.gbft.framework.utils.DataUtils;

/**
 * Represents a connection to another CoordinatorUnit.
 */
public class Connection {

    private int remoteUnitId;
    private int myUnitId;
    private CoordinatorUnit myUnit;

    private Socket socket;
    private DataOutputStream socketOutStream = null;
    private DataInputStream socketInStream = null;

    protected LinkedBlockingQueue<Event> outQueue;
    protected LinkedBlockingQueue<Event> inQueueClient;
    protected LinkedBlockingQueue<Event> inQueueReplica;
    private boolean running;
    private static final long POLL_TIME = 5000;

    private Thread sender;
    private Thread receiver;

    private BenchmarkManager benchmarkManager;

    public Connection(CoordinatorUnit myUnit, int myUnitId, int remoteUnitId, LinkedBlockingQueue<Event> inQueueClient,
            LinkedBlockingQueue<Event> inQueueReplica, BenchmarkManager benchmarkManager) {
        this.myUnit = myUnit;
        this.myUnitId = myUnitId;
        this.remoteUnitId = remoteUnitId;

        this.inQueueClient = inQueueClient;
        this.inQueueReplica = inQueueReplica;
        outQueue = new LinkedBlockingQueue<>();

        this.benchmarkManager = benchmarkManager;
    }

    public boolean createSocket() {
        // active
        if (myUnitId < remoteUnitId) {
            var address = myUnit.unitAddressMap.get(remoteUnitId);
            try {
                socket = new Socket(address.getLeft(), address.getRight());
                var connectionEvent = DataUtils.createEvent(EventType.CONNECTION, myUnitId);
                myUnit.netSend(socket, connectionEvent);

                socketOutStream = new DataOutputStream(socket.getOutputStream());
                socketInStream = new DataInputStream(socket.getInputStream());
            } catch (UnknownHostException e) {
                e.printStackTrace();
            } catch (ConnectException e) {
                System.err.println("Could not connect to " + address);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return true;
        } else {
            return false;
        }
    }

    public void createSocket(Socket socket) {
        // passive
        try {
            this.socket = socket;
            socketOutStream = new DataOutputStream(socket.getOutputStream());
            socketInStream = new DataInputStream(socket.getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected void startSenderReceiver() {
        sender = new Thread(new SenderThread());
        sender.start();
        receiver = new Thread(new ReceiverThread());
        receiver.start();
    }

    protected void send(Event event) {
        benchmarkManager.add(BenchmarkManager.CONNECTION_BEGIN_SEND, 0, System.nanoTime());
        if (!outQueue.offer(event)) {
            System.out.println("unit " + myUnitId + ": Out queue for " + remoteUnitId + "full (message discarded).");
        } else {
            benchmarkManager.add(BenchmarkManager.CONNECTION_SEND, 0, System.nanoTime());
        }
    }

    protected void closeConnection() {
        try {
            running = false;
            sender.interrupt();
            receiver.interrupt();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    protected class SenderThread implements Runnable {

        @Override
        public void run() {
            running = true;
            while (running) {
                try {
                    var event = outQueue.poll(POLL_TIME, TimeUnit.MILLISECONDS);

                    if (event != null) {
                        var bytes = event.toByteArray();

                        socketOutStream.writeInt(bytes.length);
                        socketOutStream.write(bytes);
                        benchmarkManager.add(BenchmarkManager.SENDER_THREAD_WRITE, 0, System.nanoTime());
                    }
                } catch (InterruptedException e) {
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    protected class ReceiverThread implements Runnable {

        @Override
        public void run() {
            running = true;
            while (running) {
                try {
                    var messageLen = socketInStream.readInt();
                    var message = socketInStream.readNBytes(messageLen);

                    var event = Event.parseFrom(message);
                    if (event.getMessageBlock().getMessageData(0).getMessageType() == StateMachine.REQUEST) {
                        if (!inQueueClient.offer(event)) {
                            System.out.println(
                                    "unit " + myUnitId + ": In queue client full (message from unit " + remoteUnitId
                                            + " discarded.)");
                        } else {
                            benchmarkManager.add(BenchmarkManager.RECEIVER_THREAD_INQUEUE_CLIENT, 0, System.nanoTime());
                        }
                    } else {
                        if (!inQueueReplica.offer(event)) {
                            System.out.println(
                                    "unit " + myUnitId + ": In queue replica full (message from unit " + remoteUnitId
                                            + " discarded.)");
                        } else {
                            benchmarkManager.add(BenchmarkManager.RECEIVER_THREAD_INQUEUE_REPLICA, 0, System.nanoTime());
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();

                }

            }

        }
    }

}
