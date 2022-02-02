/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nifi.processors.standard;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.sctp.SctpMessage;
import io.netty.channel.sctp.SctpServerChannel;
import io.netty.channel.sctp.nio.NioSctpServerChannel;
import io.netty.handler.codec.sctp.SctpMessageCompletionHandler;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.apache.nifi.annotation.behavior.InputRequirement;
import org.apache.nifi.annotation.behavior.SupportsBatching;
import org.apache.nifi.annotation.behavior.WritesAttribute;
import org.apache.nifi.annotation.behavior.WritesAttributes;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.event.transport.message.ByteArrayMessage;
import org.apache.nifi.event.transport.netty.channel.LogExceptionChannelHandler;
import org.apache.nifi.event.transport.netty.channel.StandardChannelInitializer;
import org.apache.nifi.event.transport.netty.channel.ssl.ServerSslHandlerChannelInitializer;
import org.apache.nifi.expression.ExpressionLanguageScope;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.processor.util.listen.EventBatcher;
import org.apache.nifi.processor.util.listen.FlowFileEventBatch;
import org.apache.nifi.processor.util.listen.ListenerProperties;
import org.apache.nifi.security.util.ClientAuth;
import org.apache.nifi.ssl.RestrictedSSLContextService;
import org.apache.nifi.ssl.SSLContextService;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Supplier;

@SupportsBatching
@InputRequirement(InputRequirement.Requirement.INPUT_FORBIDDEN)
@Tags({"listen", "sctp", "tls", "ssl"})
@CapabilityDescription("Listens for incoming SCTP messages. The default behavior is for each message to produce a single FlowFile, however this can " +
    "be controlled by increasing the Batch Size to a larger value for higher throughput.")
@WritesAttributes({
    @WritesAttribute(attribute="sctp.sender", description="The sender socket (host:port) of the messages.")
})
public class ListenSCTP extends AbstractProcessor {
    public static final PropertyDescriptor LOCAL_IP_ADDRESSES = new PropertyDescriptor.Builder()
        .displayName("Local IP Address(es)")
        .name("local-ip-addresses")
        .description("The IP address (or addresses in a multihoming setup) to listen on." +
            " Can be a comma-separated list in which case the first one will be considered the primary.")
        .required(true)
        .addValidator(StandardValidators.createListValidator(true, false, StandardValidators.NON_EMPTY_VALIDATOR))
        .expressionLanguageSupported(ExpressionLanguageScope.VARIABLE_REGISTRY)
        .build();

    public static final PropertyDescriptor SSL_CONTEXT_SERVICE = new PropertyDescriptor.Builder()
        .name("SSL Context Service")
        .description("The Controller Service to use in order to obtain an SSL Context. If this property is set, " +
            "messages will be received over a secure connection.")
        .required(false)
        .identifiesControllerService(RestrictedSSLContextService.class)
        .build();

    public static final PropertyDescriptor CLIENT_AUTH = new PropertyDescriptor.Builder()
        .name("Client Auth")
        .description("The client authentication policy to use for the SSL Context. Only used if an SSL Context Service is provided.")
        .required(false)
        .allowableValues(ClientAuth.values())
        .defaultValue(ClientAuth.REQUIRED.name())
        .build();

    public static final Relationship REL_SUCCESS = new Relationship.Builder()
        .name("success")
        .description("Messages received successfully will be sent out this relationship.")
        .build();

    private volatile EventBatcher<ByteArrayMessage> eventBatcher;

    private volatile LinkedBlockingQueue<ByteArrayMessage> messages;
    private volatile BlockingQueue<ByteArrayMessage> errors;

    private List<PropertyDescriptor> descriptors;
    private Set<Relationship> relationships;

    @Override
    protected void init(final ProcessorInitializationContext context) {
        this.descriptors = Collections.unmodifiableList(Arrays.asList(
            LOCAL_IP_ADDRESSES,
            ListenerProperties.PORT,
            SSL_CONTEXT_SERVICE,
            CLIENT_AUTH,
            ListenerProperties.MAX_BATCH_SIZE,
            ListenerProperties.MAX_MESSAGE_QUEUE_SIZE
        ));

        this.relationships = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            REL_SUCCESS
        )));
    }

    @Override
    public final Set<Relationship> getRelationships() {
        return this.relationships;
    }

    @Override
    public List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return descriptors;
    }

    @OnScheduled
    public void onScheduled(ProcessContext context) throws IOException {
        LinkedList<String> addresses = new LinkedList<>(Arrays.asList(
            context.getProperty(LOCAL_IP_ADDRESSES).evaluateAttributeExpressions().getValue().split(","))
        );

        String primaryAddress = addresses.pop();

        Integer port = context.getProperty(ListenerProperties.PORT).evaluateAttributeExpressions().asInteger();

        messages = new LinkedBlockingQueue<>(context.getProperty(ListenerProperties.MAX_MESSAGE_QUEUE_SIZE).asInteger());
        errors = new LinkedBlockingQueue<>();
        eventBatcher = new EventBatcher<ByteArrayMessage>(getLogger(), messages, errors) {
            @Override
            protected String getBatchKey(ByteArrayMessage event) {
                return event.getSender();
            }
        };

        ServerBootstrap serverBootstrap = new ServerBootstrap()
            .group(
                new NioEventLoopGroup(1, new DefaultThreadFactory("SCTP-Acceptor-" + this)),
                new NioEventLoopGroup(0, new DefaultThreadFactory("SCTP-Client-" + this))
            )
            .channel(NioSctpServerChannel.class)
            .option(ChannelOption.SO_BACKLOG, 100);

        Supplier<List<ChannelHandler>> handlerSupplier = () -> Arrays.asList(
            new LogExceptionChannelHandler(getLogger()),
            new SctpMessageCompletionHandler(),
            new CompleteSctpMessageHandler()
        );

        final SSLContextService sslContextService = context.getProperty(SSL_CONTEXT_SERVICE).asControllerService(SSLContextService.class);
        if (sslContextService == null) {
            serverBootstrap.childHandler(new StandardChannelInitializer<>(handlerSupplier));
        } else {
            final String clientAuthValue = context.getProperty(CLIENT_AUTH).getValue();
            ClientAuth clientAuth = ClientAuth.valueOf(clientAuthValue);
            SSLContext sslContext = sslContextService.createContext();

            serverBootstrap.childHandler(new ServerSslHandlerChannelInitializer<>(handlerSupplier, sslContext, clientAuth));
        }

        try {
            ChannelFuture channelFuture = serverBootstrap.bind(new InetSocketAddress(primaryAddress, port)).sync();

            SctpServerChannel sctpServerChannel = (SctpServerChannel) channelFuture.channel();
            for (String address : addresses) {
                sctpServerChannel.bindAddress(InetAddress.getByName(address));
            }
        } catch (InterruptedException e) {
            // TODO handle InterruptedException
            e.printStackTrace();
        }
    }

    @Override
    public void onTrigger(final ProcessContext context, final ProcessSession session) throws ProcessException {
        final int batchSize = context.getProperty(ListenerProperties.MAX_BATCH_SIZE).asInteger();
        Map<String, FlowFileEventBatch<ByteArrayMessage>> batches = eventBatcher.getBatches(session, batchSize, new byte[0]);
        processEvents(session, batches);
    }

    private void processEvents(final ProcessSession session, final Map<String, FlowFileEventBatch<ByteArrayMessage>> batches) {
        for (Map.Entry<String, FlowFileEventBatch<ByteArrayMessage>> entry : batches.entrySet()) {
            FlowFile flowFile = entry.getValue().getFlowFile();
            final List<ByteArrayMessage> events = entry.getValue().getEvents();

            if (flowFile.getSize() == 0L || events.size() == 0) {
                session.remove(flowFile);
                getLogger().debug("No data written to FlowFile from batch {}; removing FlowFile", entry.getKey());
                continue;
            }

            final Map<String, String> attributes = getAttributes(entry.getValue());
            flowFile = session.putAllAttributes(flowFile, attributes);

            getLogger().debug("Transferring {} to success", flowFile);
            session.transfer(flowFile, REL_SUCCESS);
            session.adjustCounter("FlowFiles Transferred to Success", 1L, false);

            final String transitUri = getTransitUri(entry.getValue());
            session.getProvenanceReporter().receive(flowFile, transitUri);
        }
    }

    private Map<String, String> getAttributes(final FlowFileEventBatch<ByteArrayMessage> batch) {
        final List<ByteArrayMessage> events = batch.getEvents();
        final String sender = events.get(0).getSender();

        final Map<String, String> attributes = new HashMap<>();
        attributes.put("sctp.sender", sender);

        return attributes;
    }

    private String getTransitUri(final FlowFileEventBatch<ByteArrayMessage> batch) {
        final List<ByteArrayMessage> events = batch.getEvents();
        final String sender = events.get(0).getSender();

        String transitUri = String.format("sctp://%s", sender);

        return transitUri;
    }

    private class CompleteSctpMessageHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            ListenSCTP.this.channelRead(ctx, msg);
        }
    }

    void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        SctpMessage sctpMessage = (SctpMessage) msg;

        ByteBuf content = sctpMessage.content();

        byte[] array = new byte[content.readableBytes()];
        content.getBytes(0, array);

        messages.add(new ByteArrayMessage(array, sctpMessage.messageInfo().address().toString()));

        sctpMessage.release();
    }
}
