package com.gbft.framework.data;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.55.1)",
    comments = "Source: gbft.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class EntityCommGrpc {

  private EntityCommGrpc() {}

  public static final String SERVICE_NAME = "EntityComm";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.gbft.framework.data.LearningData,
      com.google.protobuf.Empty> getSendDecisionMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "send_decision",
      requestType = com.gbft.framework.data.LearningData.class,
      responseType = com.google.protobuf.Empty.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.gbft.framework.data.LearningData,
      com.google.protobuf.Empty> getSendDecisionMethod() {
    io.grpc.MethodDescriptor<com.gbft.framework.data.LearningData, com.google.protobuf.Empty> getSendDecisionMethod;
    if ((getSendDecisionMethod = EntityCommGrpc.getSendDecisionMethod) == null) {
      synchronized (EntityCommGrpc.class) {
        if ((getSendDecisionMethod = EntityCommGrpc.getSendDecisionMethod) == null) {
          EntityCommGrpc.getSendDecisionMethod = getSendDecisionMethod =
              io.grpc.MethodDescriptor.<com.gbft.framework.data.LearningData, com.google.protobuf.Empty>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "send_decision"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.gbft.framework.data.LearningData.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.google.protobuf.Empty.getDefaultInstance()))
              .setSchemaDescriptor(new EntityCommMethodDescriptorSupplier("send_decision"))
              .build();
        }
      }
    }
    return getSendDecisionMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static EntityCommStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<EntityCommStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<EntityCommStub>() {
        @java.lang.Override
        public EntityCommStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new EntityCommStub(channel, callOptions);
        }
      };
    return EntityCommStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static EntityCommBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<EntityCommBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<EntityCommBlockingStub>() {
        @java.lang.Override
        public EntityCommBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new EntityCommBlockingStub(channel, callOptions);
        }
      };
    return EntityCommBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static EntityCommFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<EntityCommFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<EntityCommFutureStub>() {
        @java.lang.Override
        public EntityCommFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new EntityCommFutureStub(channel, callOptions);
        }
      };
    return EntityCommFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void sendDecision(com.gbft.framework.data.LearningData request,
        io.grpc.stub.StreamObserver<com.google.protobuf.Empty> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getSendDecisionMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service EntityComm.
   */
  public static abstract class EntityCommImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return EntityCommGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service EntityComm.
   */
  public static final class EntityCommStub
      extends io.grpc.stub.AbstractAsyncStub<EntityCommStub> {
    private EntityCommStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected EntityCommStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new EntityCommStub(channel, callOptions);
    }

    /**
     */
    public void sendDecision(com.gbft.framework.data.LearningData request,
        io.grpc.stub.StreamObserver<com.google.protobuf.Empty> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getSendDecisionMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service EntityComm.
   */
  public static final class EntityCommBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<EntityCommBlockingStub> {
    private EntityCommBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected EntityCommBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new EntityCommBlockingStub(channel, callOptions);
    }

    /**
     */
    public com.google.protobuf.Empty sendDecision(com.gbft.framework.data.LearningData request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getSendDecisionMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service EntityComm.
   */
  public static final class EntityCommFutureStub
      extends io.grpc.stub.AbstractFutureStub<EntityCommFutureStub> {
    private EntityCommFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected EntityCommFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new EntityCommFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.google.protobuf.Empty> sendDecision(
        com.gbft.framework.data.LearningData request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getSendDecisionMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_SEND_DECISION = 0;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final AsyncService serviceImpl;
    private final int methodId;

    MethodHandlers(AsyncService serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_SEND_DECISION:
          serviceImpl.sendDecision((com.gbft.framework.data.LearningData) request,
              (io.grpc.stub.StreamObserver<com.google.protobuf.Empty>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getSendDecisionMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.gbft.framework.data.LearningData,
              com.google.protobuf.Empty>(
                service, METHODID_SEND_DECISION)))
        .build();
  }

  private static abstract class EntityCommBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    EntityCommBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.gbft.framework.data.Gbft.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("EntityComm");
    }
  }

  private static final class EntityCommFileDescriptorSupplier
      extends EntityCommBaseDescriptorSupplier {
    EntityCommFileDescriptorSupplier() {}
  }

  private static final class EntityCommMethodDescriptorSupplier
      extends EntityCommBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final String methodName;

    EntityCommMethodDescriptorSupplier(String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (EntityCommGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new EntityCommFileDescriptorSupplier())
              .addMethod(getSendDecisionMethod())
              .build();
        }
      }
    }
    return result;
  }
}
