package com.gbft.framework.core;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import com.gbft.framework.data.LearningData;
import com.gbft.framework.data.EntityCommGrpc.EntityCommImplBase;
import com.google.protobuf.Empty;

import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.stub.StreamObserver;

public class EntityCommServer {
    protected Entity entity;

    private Server server;
    public int myRPCPort;
    public int agentPort;
    private static SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss.S ");

    public EntityCommServer(Entity entity) {
        this.entity = entity;
        var bedrockPort = this.entity.coordinator.port;
        // set port for RPC server and agent
        myRPCPort = bedrockPort + 10;
        agentPort = bedrockPort + 20;
    }

    public void start() {
        try {
            server = Grpc.newServerBuilderForPort(myRPCPort, InsecureServerCredentials.create())
                    .addService(new EntityCommImpl())
                    .build()
                    .start();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        println("GRPC server started, listening on " + myRPCPort);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                // Use stderr here since the logger may have been reset by its JVM shutdown
                // hook.
                System.err.println("*** shutting down gRPC server since JVM is shutting down");
                try {
                    EntityCommServer.this.stop();
                } catch (InterruptedException e) {
                    e.printStackTrace(System.err);
                }
                System.err.println("*** server shut down");
            }
        });

    }

    private void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(1, TimeUnit.SECONDS);
        }
    }

    class EntityCommImpl extends EntityCommImplBase {
        @Override
        public void sendDecision(LearningData request, StreamObserver<Empty> responseObserver) {
            var epoch = entity.currentEpisodeNum.get();
            System.out.println("epoch: " + epoch + ", received next protocol: " + request.getNextProtocol());

            var checkpoint = entity.getCheckpointManager().getCheckpoint(epoch);
            checkpoint.tallyDecision(request.getNextProtocol());

            responseObserver.onNext(Empty.newBuilder().build());
            responseObserver.onCompleted();
        }
    }

    protected void println(String str) {
        var date = dateFormat.format(new Date(System.currentTimeMillis()));
        System.out.println(date + " " + str);
    }
}
