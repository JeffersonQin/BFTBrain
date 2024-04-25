package com.gbft.framework.data;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.55.1)",
    comments = "Source: gbft.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class AgentCommGrpc {

  private AgentCommGrpc() {}

  public static final String SERVICE_NAME = "AgentComm";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.gbft.framework.data.LearningData,
      com.google.protobuf.Empty> getSendDataMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "send_data",
      requestType = com.gbft.framework.data.LearningData.class,
      responseType = com.google.protobuf.Empty.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.gbft.framework.data.LearningData,
      com.google.protobuf.Empty> getSendDataMethod() {
    io.grpc.MethodDescriptor<com.gbft.framework.data.LearningData, com.google.protobuf.Empty> getSendDataMethod;
    if ((getSendDataMethod = AgentCommGrpc.getSendDataMethod) == null) {
      synchronized (AgentCommGrpc.class) {
        if ((getSendDataMethod = AgentCommGrpc.getSendDataMethod) == null) {
          AgentCommGrpc.getSendDataMethod = getSendDataMethod =
              io.grpc.MethodDescriptor.<com.gbft.framework.data.LearningData, com.google.protobuf.Empty>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "send_data"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.gbft.framework.data.LearningData.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.google.protobuf.Empty.getDefaultInstance()))
              .setSchemaDescriptor(new AgentCommMethodDescriptorSupplier("send_data"))
              .build();
        }
      }
    }
    return getSendDataMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static AgentCommStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<AgentCommStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<AgentCommStub>() {
        @java.lang.Override
        public AgentCommStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new AgentCommStub(channel, callOptions);
        }
      };
    return AgentCommStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static AgentCommBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<AgentCommBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<AgentCommBlockingStub>() {
        @java.lang.Override
        public AgentCommBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new AgentCommBlockingStub(channel, callOptions);
        }
      };
    return AgentCommBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static AgentCommFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<AgentCommFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<AgentCommFutureStub>() {
        @java.lang.Override
        public AgentCommFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new AgentCommFutureStub(channel, callOptions);
        }
      };
    return AgentCommFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void sendData(com.gbft.framework.data.LearningData request,
        io.grpc.stub.StreamObserver<com.google.protobuf.Empty> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getSendDataMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service AgentComm.
   */
  public static abstract class AgentCommImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return AgentCommGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service AgentComm.
   */
  public static final class AgentCommStub
      extends io.grpc.stub.AbstractAsyncStub<AgentCommStub> {
    private AgentCommStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected AgentCommStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new AgentCommStub(channel, callOptions);
    }

    /**
     */
    public void sendData(com.gbft.framework.data.LearningData request,
        io.grpc.stub.StreamObserver<com.google.protobuf.Empty> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getSendDataMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service AgentComm.
   */
  public static final class AgentCommBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<AgentCommBlockingStub> {
    private AgentCommBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected AgentCommBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new AgentCommBlockingStub(channel, callOptions);
    }

    /**
     */
    public com.google.protobuf.Empty sendData(com.gbft.framework.data.LearningData request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getSendDataMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service AgentComm.
   */
  public static final class AgentCommFutureStub
      extends io.grpc.stub.AbstractFutureStub<AgentCommFutureStub> {
    private AgentCommFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected AgentCommFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new AgentCommFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.google.protobuf.Empty> sendData(
        com.gbft.framework.data.LearningData request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getSendDataMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_SEND_DATA = 0;

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
        case METHODID_SEND_DATA:
          serviceImpl.sendData((com.gbft.framework.data.LearningData) request,
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
          getSendDataMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.gbft.framework.data.LearningData,
              com.google.protobuf.Empty>(
                service, METHODID_SEND_DATA)))
        .build();
  }

  private static abstract class AgentCommBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    AgentCommBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.gbft.framework.data.Gbft.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("AgentComm");
    }
  }

  private static final class AgentCommFileDescriptorSupplier
      extends AgentCommBaseDescriptorSupplier {
    AgentCommFileDescriptorSupplier() {}
  }

  private static final class AgentCommMethodDescriptorSupplier
      extends AgentCommBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final String methodName;

    AgentCommMethodDescriptorSupplier(String methodName) {
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
      synchronized (AgentCommGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new AgentCommFileDescriptorSupplier())
              .addMethod(getSendDataMethod())
              .build();
        }
      }
    }
    return result;
  }
}
