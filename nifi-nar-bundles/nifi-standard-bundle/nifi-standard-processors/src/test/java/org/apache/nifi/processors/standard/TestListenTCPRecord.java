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

import java.io.Closeable;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.net.ssl.SSLContext;
import org.apache.commons.io.IOUtils;
import org.apache.nifi.json.JsonTreeReader;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSessionFactory;
import org.apache.nifi.reporting.InitializationException;
import org.apache.nifi.schema.access.SchemaAccessUtils;
import org.apache.nifi.security.util.SslContextFactory;
import org.apache.nifi.security.util.TlsConfiguration;
import org.apache.nifi.security.util.TlsException;
import org.apache.nifi.serialization.RecordReaderFactory;
import org.apache.nifi.serialization.RecordSetWriterFactory;
import org.apache.nifi.serialization.record.MockRecordWriter;
import org.apache.nifi.ssl.SSLContextService;
import org.apache.nifi.ssl.StandardRestrictedSSLContextService;
import org.apache.nifi.ssl.StandardSSLContextService;
import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestListenTCPRecord {
    static final Logger LOGGER = LoggerFactory.getLogger(TestListenTCPRecord.class);

    private static final String KEYSTORE = "src/test/resources/keystore.jks";
    private static final String KEYSTORE_PASSWORD = "passwordpassword";
    private static final String KEYSTORE_TYPE = "JKS";
    private static final String TRUSTSTORE = "src/test/resources/truststore.jks";
    private static final String TRUSTSTORE_PASSWORD = "passwordpassword";
    private static final String TRUSTSTORE_TYPE = "JKS";
    private static final String CLIENT_KEYSTORE = "src/test/resources/client-keystore.p12";
    private static final String CLIENT_KEYSTORE_TYPE = "PKCS12";

    // TODO: The NiFi SSL classes don't yet support TLSv1.3, so set the CS version explicitly
    private static final String TLS_PROTOCOL_VERSION = "TLSv1.2";

    private static TlsConfiguration clientTlsConfiguration;
    private static TlsConfiguration trustOnlyTlsConfiguration;

    static final String SCHEMA_TEXT = "{\n" +
            "  \"name\": \"syslogRecord\",\n" +
            "  \"namespace\": \"nifi\",\n" +
            "  \"type\": \"record\",\n" +
            "  \"fields\": [\n" +
            "    { \"name\": \"timestamp\", \"type\": \"string\" },\n" +
            "    { \"name\": \"logsource\", \"type\": \"string\" },\n" +
            "    { \"name\": \"message\", \"type\": \"string\" }\n" +
            "  ]\n" +
            "}";

    static final List<String> DATA;

    static {
        final List<String> data = new ArrayList<>();
        data.add("[");
        data.add("{\"timestamp\" : \"123456789\", \"logsource\" : \"syslog\", \"message\" : \"This is a test 1\"},");
        data.add("{\"timestamp\" : \"123456789\", \"logsource\" : \"syslog\", \"message\" : \"This is a test 2\"},");
        data.add("{\"timestamp\" : \"123456789\", \"logsource\" : \"syslog\", \"message\" : \"This is a test 3\"}");
        data.add("]");
        DATA = Collections.unmodifiableList(data);
    }

    private ListenTCPRecord proc;
    private TestRunner runner;

    @Before
    public void setup() throws InitializationException {
        proc = new ListenTCPRecord();
        runner = TestRunners.newTestRunner(proc);
        runner.setProperty(ListenTCPRecord.PORT, "0");

        final String readerId = "record-reader";
        final RecordReaderFactory readerFactory = new JsonTreeReader();
        runner.addControllerService(readerId, readerFactory);
        runner.setProperty(readerFactory, SchemaAccessUtils.SCHEMA_ACCESS_STRATEGY, SchemaAccessUtils.SCHEMA_TEXT_PROPERTY.getValue());
        runner.setProperty(readerFactory, SchemaAccessUtils.SCHEMA_TEXT, SCHEMA_TEXT);
        runner.enableControllerService(readerFactory);

        final String writerId = "record-writer";
        final RecordSetWriterFactory writerFactory = new MockRecordWriter("timestamp, logsource, message");
        runner.addControllerService(writerId, writerFactory);
        runner.enableControllerService(writerFactory);

        runner.setProperty(ListenTCPRecord.RECORD_READER, readerId);
        runner.setProperty(ListenTCPRecord.RECORD_WRITER, writerId);

        clientTlsConfiguration = new TlsConfiguration(CLIENT_KEYSTORE, KEYSTORE_PASSWORD, null, CLIENT_KEYSTORE_TYPE,
                TRUSTSTORE, TRUSTSTORE_PASSWORD, TRUSTSTORE_TYPE, TLS_PROTOCOL_VERSION);
        trustOnlyTlsConfiguration = new TlsConfiguration(null, null, null, null,
                TRUSTSTORE, TRUSTSTORE_PASSWORD, TRUSTSTORE_TYPE, TLS_PROTOCOL_VERSION);
    }

    @Test
    public void testCustomValidate() throws InitializationException {
        runner.setProperty(ListenTCPRecord.PORT, "1");
        runner.assertValid();

        configureProcessorSslContextService();
        runner.setProperty(ListenTCPRecord.CLIENT_AUTH, "");
        runner.assertNotValid();

        runner.setProperty(ListenTCPRecord.CLIENT_AUTH, SslContextFactory.ClientAuth.REQUIRED.name());
        runner.assertValid();
    }

    @Test
    public void testOneRecordPerFlowFile() throws IOException, InterruptedException {
        runner.setProperty(ListenTCPRecord.RECORD_BATCH_SIZE, "1");

        runTCP(DATA, 3, null);

        List<MockFlowFile> mockFlowFiles = runner.getFlowFilesForRelationship(ListenTCPRecord.REL_SUCCESS);
        for (int i = 0; i < mockFlowFiles.size(); i++) {
            final MockFlowFile flowFile = mockFlowFiles.get(i);
            flowFile.assertAttributeEquals("record.count", "1");

            final String content = new String(flowFile.toByteArray(), StandardCharsets.UTF_8);
            Assert.assertNotNull(content);
            Assert.assertTrue(content.contains("This is a test " + (i + 1)));
        }
    }

    @Test
    public void testMultipleRecordsPerFlowFileLessThanBatchSize() throws IOException, InterruptedException {
        runner.setProperty(ListenTCPRecord.RECORD_BATCH_SIZE, "5");

        runTCP(DATA, 1, null);

        final List<MockFlowFile> mockFlowFiles = runner.getFlowFilesForRelationship(ListenTCPRecord.REL_SUCCESS);
        Assert.assertEquals(1, mockFlowFiles.size());

        final MockFlowFile flowFile = mockFlowFiles.get(0);
        flowFile.assertAttributeEquals("record.count", "3");

        final String content = new String(flowFile.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertNotNull(content);
        Assert.assertTrue(content.contains("This is a test " + 1));
        Assert.assertTrue(content.contains("This is a test " + 2));
        Assert.assertTrue(content.contains("This is a test " + 3));
    }

    @Test
    public void testTLSClientAuthRequiredAndClientCertProvided() throws InitializationException, IOException, InterruptedException, TlsException {

        runner.setProperty(ListenTCPRecord.CLIENT_AUTH, SslContextFactory.ClientAuth.REQUIRED.name());
        configureProcessorSslContextService();

        // Make an SSLContext with a key and trust store to send the test messages
        final SSLContext clientSslContext = SslContextFactory.createSslContext(clientTlsConfiguration);

        runTCP(DATA, 1, clientSslContext);

        final List<MockFlowFile> mockFlowFiles = runner.getFlowFilesForRelationship(ListenTCPRecord.REL_SUCCESS);
        Assert.assertEquals(1, mockFlowFiles.size());

        final String content = new String(mockFlowFiles.get(0).toByteArray(), StandardCharsets.UTF_8);
        Assert.assertNotNull(content);
        Assert.assertTrue(content.contains("This is a test " + 1));
        Assert.assertTrue(content.contains("This is a test " + 2));
        Assert.assertTrue(content.contains("This is a test " + 3));
    }

    @Test
    public void testTLSClientAuthRequiredAndClientCertNotProvided() throws InitializationException, IOException, InterruptedException, TlsException {

        runner.setProperty(ListenTCPRecord.CLIENT_AUTH, SslContextFactory.ClientAuth.REQUIRED.name());
        runner.setProperty(ListenTCPRecord.READ_TIMEOUT, "5 seconds");
        configureProcessorSslContextService();

        // Make an SSLContext that only has the trust store, this should not work since the processor has client auth REQUIRED
        final SSLContext clientSslContext = SslContextFactory.createSslContext(trustOnlyTlsConfiguration);

        runTCP(DATA, 0, clientSslContext);
    }

    @Test
    public void testTLSClientAuthNoneAndClientCertNotProvided() throws InitializationException, IOException, InterruptedException, TlsException {

        runner.setProperty(ListenTCPRecord.CLIENT_AUTH, SslContextFactory.ClientAuth.NONE.name());
        configureProcessorSslContextService();

        // Make an SSLContext that only has the trust store, this should work since the processor has client auth NONE
        final SSLContext clientSslContext = SslContextFactory.createSslContext(trustOnlyTlsConfiguration);

        runTCP(DATA, 1, clientSslContext);

        final List<MockFlowFile> mockFlowFiles = runner.getFlowFilesForRelationship(ListenTCPRecord.REL_SUCCESS);
        Assert.assertEquals(1, mockFlowFiles.size());

        final String content = new String(mockFlowFiles.get(0).toByteArray(), StandardCharsets.UTF_8);
        Assert.assertNotNull(content);
        Assert.assertTrue(content.contains("This is a test " + 1));
        Assert.assertTrue(content.contains("This is a test " + 2));
        Assert.assertTrue(content.contains("This is a test " + 3));
    }

    protected void runTCP(final List<String> messages, final int expectedTransferred, final SSLContext sslContext)
            throws IOException, InterruptedException {

        SocketSender sender = null;
        try {
            // schedule to start listening on a random port
            final ProcessSessionFactory processSessionFactory = runner.getProcessSessionFactory();
            final ProcessContext context = runner.getProcessContext();
            proc.onScheduled(context);
            Thread.sleep(100);

            sender = new SocketSender(proc.getDispatcherPort(), "localhost", sslContext, messages, 0);

            final Thread senderThread = new Thread(sender);
            senderThread.setDaemon(true);
            senderThread.start();

            long timeout = 10000;

            // call onTrigger until we processed all the records, or a certain amount of time passes
            int numTransferred = 0;
            long startTime = System.currentTimeMillis();
            while (numTransferred < expectedTransferred && (System.currentTimeMillis() - startTime < timeout)) {
                proc.onTrigger(context, processSessionFactory);
                numTransferred = runner.getFlowFilesForRelationship(ListenTCPRecord.REL_SUCCESS).size();
                Thread.sleep(100);
            }

            // should have transferred the expected events
            runner.assertTransferCount(ListenTCPRecord.REL_SUCCESS, expectedTransferred);
        } finally {
            // unschedule to close connections
            proc.onUnscheduled();
            IOUtils.closeQuietly(sender);
        }
    }

    private SSLContextService configureProcessorSslContextService() throws InitializationException {
        final SSLContextService sslContextService = new StandardRestrictedSSLContextService();
        runner.addControllerService("ssl-context", sslContextService);
        runner.setProperty(sslContextService, StandardSSLContextService.TRUSTSTORE, "src/test/resources/truststore.jks");
        runner.setProperty(sslContextService, StandardSSLContextService.TRUSTSTORE_PASSWORD, "passwordpassword");
        runner.setProperty(sslContextService, StandardSSLContextService.TRUSTSTORE_TYPE, "JKS");
        runner.setProperty(sslContextService, StandardSSLContextService.KEYSTORE, "src/test/resources/keystore.jks");
        runner.setProperty(sslContextService, StandardSSLContextService.KEYSTORE_PASSWORD, "passwordpassword");
        runner.setProperty(sslContextService, StandardSSLContextService.KEYSTORE_TYPE, "JKS");
        runner.enableControllerService(sslContextService);

        runner.setProperty(ListenTCPRecord.SSL_CONTEXT_SERVICE, "ssl-context");
        return sslContextService;
    }

    private static class SocketSender implements Runnable, Closeable {

        private final int port;
        private final String host;
        private final SSLContext sslContext;
        private final List<String> data;
        private final long delay;

        private Socket socket;

        public SocketSender(final int port, final String host, final SSLContext sslContext, final List<String> data, final long delay) {
            this.port = port;
            this.host = host;
            this.sslContext = sslContext;
            this.data = data;
            this.delay = delay;
        }

        @Override
        public void run() {
            try {
                if (sslContext != null) {
                    socket = sslContext.getSocketFactory().createSocket(host, port);
                } else {
                    socket = new Socket(host, port);
                }

                for (final String message : data) {
                    socket.getOutputStream().write(message.getBytes(StandardCharsets.UTF_8));
                    if (delay > 0) {
                        Thread.sleep(delay);
                    }
                }

                socket.getOutputStream().flush();
            } catch (final Exception e) {
                LOGGER.error(e.getMessage(), e);
            } finally {
                IOUtils.closeQuietly(socket);
            }
        }

        public void close() {
            IOUtils.closeQuietly(socket);
        }
    }

}
