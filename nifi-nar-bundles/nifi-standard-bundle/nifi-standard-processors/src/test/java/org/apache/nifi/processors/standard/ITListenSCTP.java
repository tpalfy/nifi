package org.apache.nifi.processors.standard;

import com.sun.nio.sctp.MessageInfo;
import com.sun.nio.sctp.SctpChannel;
import io.netty.channel.ChannelHandlerContext;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Processor;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ITListenSCTP {
    @Test
    void test() throws Exception {
        CountDownLatch serverReady = new CountDownLatch(1);
        CountDownLatch messageReceived = new CountDownLatch(1);
        CountDownLatch onTriggerFinished = new CountDownLatch(1);

        Processor processor = new ListenSCTP() {
            @Override
            public void onTrigger(ProcessContext context, ProcessSession session) throws ProcessException {
                try {
                    serverReady.countDown();
                    messageReceived.await();
                } catch (InterruptedException e) {
                    throw new ProcessException(e);
                }

                super.onTrigger(context, session);
            }

            @Override
            void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                super.channelRead(ctx, msg);
                messageReceived.countDown();
            }
        };

        TestRunner runner = TestRunners.newTestRunner(processor);

        Executors.newSingleThreadExecutor().submit(() -> {
            runner.run();
            onTriggerFinished.countDown();
        });

        serverReady.await();

        SocketAddress serverAddress = new InetSocketAddress( 22222);
        serverAddress = new InetSocketAddress("127.0.0.1", 22222);

        SctpChannel clientChannel = SctpChannel.open();
        clientChannel.bind(new InetSocketAddress( 0));
        clientChannel.connect(serverAddress, 1 ,1);

        final ByteBuffer byteBuffer = ByteBuffer.allocate(64000);
        //Simple M3ua ASP_Up message
//        byte [] message = new byte []{1,0,3,1,0,0,0,24,0,17,0,8,0,0,0,1,0,4,0,8,84,101,115,116};
        byte [] message = "message_content".getBytes();

        final MessageInfo messageInfo = MessageInfo.createOutgoing(null, 0);
        System.out.println("messageInfo = " + messageInfo);
        System.out.println("messageInfo.streamNumber() = " + messageInfo.streamNumber());

        byteBuffer.put(message);
        byteBuffer.flip();

        try {
            clientChannel.send(byteBuffer, messageInfo);
        } catch (Exception e) {
            e.printStackTrace();
        }
        clientChannel.close();

        onTriggerFinished.await();

        runner.assertAllFlowFilesTransferred(ConvertRecord.REL_SUCCESS, 1);
        MockFlowFile flowFile = runner.getFlowFilesForRelationship(ConvertRecord.REL_SUCCESS).get(0);

        assertEquals(new String(message), flowFile.getContent());
    }
}
