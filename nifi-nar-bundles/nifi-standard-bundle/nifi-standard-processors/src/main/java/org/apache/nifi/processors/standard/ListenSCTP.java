package org.apache.nifi.processors.standard;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.sctp.SctpChannel;
import io.netty.channel.sctp.SctpMessage;
import io.netty.channel.sctp.nio.NioSctpServerChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.bytes.ByteArrayDecoder;
import io.netty.handler.codec.sctp.SctpMessageCompletionHandler;
import io.netty.handler.codec.sctp.SctpMessageToMessageDecoder;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.event.transport.message.ByteArrayMessage;
import org.apache.nifi.event.transport.netty.channel.ByteArrayMessageChannelHandler;
import org.apache.nifi.event.transport.netty.channel.LogExceptionChannelHandler;
import org.apache.nifi.event.transport.netty.channel.StandardChannelInitializer;
import org.apache.nifi.event.transport.netty.codec.SocketByteArrayMessageDecoder;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.DataUnit;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.listen.EventBatcher;
import org.apache.nifi.processor.util.listen.FlowFileEventBatch;
import org.apache.nifi.processor.util.listen.ListenerProperties;
import sun.nio.ch.sctp.AssociationImpl;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Supplier;

public class ListenSCTP extends AbstractProcessor {
    //SctpMessageToMessageDecoder
    //SctpOutboundByteStreamHandler

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
        serverBootstrap.childHandler(new StandardChannelInitializer<>(handlerSupplier));

        // TODO set proper host and port
        InetSocketAddress localAddress = new InetSocketAddress("192.168.0.1", 22222);

        // Bind the server to primary address.
        try {
            ChannelFuture channelFuture = serverBootstrap.bind(localAddress).sync();
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

            final Map<String,String> attributes = getAttributes(entry.getValue());
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

        final Map<String,String> attributes = new HashMap<>(3);
        attributes.put("sctp.address", sender);

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
            SctpMessage sctpMessage = (SctpMessage) msg;

            ByteBuf content = sctpMessage.content();

            byte[] array = new byte[content.readableBytes()];
            content.getBytes(0, array);

            messages.add(new ByteArrayMessage(array, sctpMessage.messageInfo().address().toString()));

            sctpMessage.release();
        }
    }
}
