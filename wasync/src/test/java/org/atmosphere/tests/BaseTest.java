package org.atmosphere.tests;

import com.ning.http.client.AsyncHttpClient;
import org.atmosphere.cpr.AtmosphereHandler;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.AtmosphereResourceEvent;
import org.atmosphere.cpr.AtmosphereResourceEventListenerAdapter;
import org.atmosphere.nettosphere.Config;
import org.atmosphere.nettosphere.Nettosphere;
import org.atmosphere.wasync.Client;
import org.atmosphere.wasync.ClientFactory;
import org.atmosphere.wasync.Decoder;
import org.atmosphere.wasync.Encoder;
import org.atmosphere.wasync.Event;
import org.atmosphere.wasync.Function;
import org.atmosphere.wasync.Options;
import org.atmosphere.wasync.Request;
import org.atmosphere.wasync.RequestBuilder;
import org.atmosphere.wasync.Socket;
import org.atmosphere.wasync.serial.DefaultSerializedFireStage;
import org.atmosphere.wasync.serial.SerializedClient;
import org.atmosphere.wasync.serial.SerializedOptionsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.AssertJUnit.fail;

public abstract class BaseTest {
    public final static String RESUME = "Resume";

    public Nettosphere server;
    public String targetUrl;
    public static final Logger logger = LoggerFactory.getLogger(BaseTest.class);
    public int port;

    public int findFreePort() throws IOException {
        ServerSocket socket = null;

        try {
            socket = new ServerSocket(0);

            return socket.getLocalPort();
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }

    @AfterMethod(alwaysRun = true)
    public void tearDownGlobal() throws Exception {
        if (server != null) {
            server.stop();
        }
    }

    abstract Request.TRANSPORT transport();

    abstract int statusCode();

    abstract int notFoundCode();

    abstract int getCount();

    @BeforeMethod(alwaysRun = true)
    public void start() throws IOException {
        port = 8080;
        targetUrl = "http://127.0.0.1:" + port;
    }

    @Test
    public void closeTest() throws Exception {
        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .resource("/suspend", new AtmosphereHandler() {

                    private final AtomicBoolean b = new AtomicBoolean(false);

                    @Override
                    public void onRequest(AtmosphereResource r) throws IOException {
                        if (!b.getAndSet(true)) {
                            r.suspend(-1);
                        } else {
                            r.getBroadcaster().broadcast(RESUME);
                        }
                    }

                    @Override
                    public void onStateChange(AtmosphereResourceEvent r) throws IOException {
                        if (!r.isResuming() || !r.isCancelled()) {
                            r.getResource().getResponse().getWriter().print(r.getMessage());
                            r.getResource().resume();
                        }
                    }

                    @Override
                    public void destroy() {

                    }
                }).build();

        server = new Nettosphere.Builder().config(config).build();
        assertNotNull(server);
        server.start();

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> response = new AtomicReference<String>();
        Client client = ClientFactory.getDefault().newClient();

        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(targetUrl + "/suspend")
                .transport(transport());

        Socket socket = client.create(client.newOptionsBuilder().reconnect(false).build());
        socket.on(Event.MESSAGE.name(), new Function<String>() {
            @Override
            public void on(String t) {
                logger.info("Function invoked {}", t);
                response.set(t);
                latch.countDown();
            }
        }).on(new Function<Throwable>() {

            @Override
            public void on(Throwable t) {
                t.printStackTrace();
                latch.countDown();
            }

        }).open(request.build()).fire("PING");

        latch.await(10, TimeUnit.SECONDS);
        socket.close();
        server.stop();

        assertEquals(socket.status(), Socket.STATUS.CLOSE);
    }

    @Test
    public void closeWithoutOpenTest() throws Exception {
        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .resource("/suspend", new AtmosphereHandler() {

                    private final AtomicBoolean b = new AtomicBoolean(false);

                    @Override
                    public void onRequest(AtmosphereResource r) throws IOException {
                        if (!b.getAndSet(true)) {
                            r.suspend(-1);
                        } else {
                            r.getBroadcaster().broadcast(RESUME);
                        }
                    }

                    @Override
                    public void onStateChange(AtmosphereResourceEvent r) throws IOException {
                        if (!r.isResuming() || !r.isCancelled()) {
                            r.getResource().getResponse().getWriter().print(r.getMessage());
                            r.getResource().resume();
                        }
                    }

                    @Override
                    public void destroy() {

                    }
                }).build();

        server = new Nettosphere.Builder().config(config).build();
        assertNotNull(server);
        server.start();

        Client client = ClientFactory.getDefault().newClient();
        Socket socket = client.create();
        socket.close();
        assertEquals(socket.status(), Socket.STATUS.CLOSE);
    }

    @Test
    public void basicWebSocketTest() throws Exception {
        final CountDownLatch l = new CountDownLatch(1);

        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .resource("/suspend", new AtmosphereHandler() {

                    private final AtomicBoolean b = new AtomicBoolean(false);

                    @Override
                    public void onRequest(AtmosphereResource r) throws IOException {
                        if (!b.getAndSet(true)) {
                            r.suspend(-1);
                        } else {
                            r.getBroadcaster().broadcast(RESUME);
                        }
                    }

                    @Override
                    public void onStateChange(AtmosphereResourceEvent r) throws IOException {
                        if (!r.isResuming() || !r.isCancelled()) {
                            r.getResource().getResponse().getWriter().print(r.getMessage());
                            r.getResource().resume();
                        }
                    }

                    @Override
                    public void destroy() {

                    }
                }).build();

        server = new Nettosphere.Builder().config(config).build();
        assertNotNull(server);
        server.start();

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> response = new AtomicReference<String>();
        Client client = ClientFactory.getDefault().newClient();

        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(targetUrl + "/suspend")
                .transport(transport());

        Socket socket = client.create();
        ;
        socket.on("message", new Function<String>() {
            @Override
            public void on(String t) {
                logger.info("Function invoked {}", t);
                response.set(t);
                latch.countDown();
            }
        }).on(new Function<Throwable>() {

            @Override
            public void on(Throwable t) {
                t.printStackTrace();
                latch.countDown();
            }

        }).open(request.build()).fire("PING");

        latch.await(10, TimeUnit.SECONDS);
        server.stop();
        socket.close();

        assertEquals(response.get(), RESUME);
    }

    @Test
    public void allStringFunctionTest() throws Exception {
        final CountDownLatch latch = new CountDownLatch(3);

        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .resource("/suspend", new AtmosphereHandler() {

                    private final AtomicBoolean b = new AtomicBoolean(false);

                    @Override
                    public void onRequest(AtmosphereResource r) throws IOException {
                        if (!b.getAndSet(true)) {
                            r.suspend(-1);
                            latch.countDown();
                        } else {
                            r.getBroadcaster().broadcast(RESUME);
                        }
                    }

                    @Override
                    public void onStateChange(AtmosphereResourceEvent r) throws IOException {
                        if (!r.isResuming() || !r.isCancelled()) {
                            r.getResource().getResponse().getWriter().print(r.getMessage());
                            r.getResource().resume();
                        }
                    }

                    @Override
                    public void destroy() {

                    }
                }).build();

        server = new Nettosphere.Builder().config(config).build();
        assertNotNull(server);
        server.start();

        final StringBuilder builder = new StringBuilder();

        Client client = ClientFactory.getDefault().newClient();

        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(targetUrl + "/suspend")
                .transport(transport());

        Socket socket = client.create();

        socket.on(new Function<String>() {
            @Override
            public void on(String m) {
                builder.append(m);
                latch.countDown();
            }
        }).open(request.build()).fire("PING");

        latch.await(20, TimeUnit.SECONDS);
        assertEquals(builder.toString(), Event.OPEN + RESUME);
    }

    @Test
    public void statusHeaderFunctionTest() throws Exception {
        final CountDownLatch l = new CountDownLatch(1);

        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .resource("/suspend", new AtmosphereHandler() {

                    private final AtomicBoolean b = new AtomicBoolean(false);

                    @Override
                    public void onRequest(AtmosphereResource r) throws IOException {
                        if (!b.getAndSet(true)) {
                            r.suspend(-1);
                        } else {
                            r.getBroadcaster().broadcast(RESUME);
                        }
                    }

                    @Override
                    public void onStateChange(AtmosphereResourceEvent r) throws IOException {
                        if (!r.isResuming() || !r.isCancelled()) {
                            r.getResource().getResponse().getWriter().print(r.getMessage());
                            r.getResource().resume();
                        }
                    }

                    @Override
                    public void destroy() {

                    }
                }).build();

        server = new Nettosphere.Builder().config(config).build();
        assertNotNull(server);
        server.start();

        final CountDownLatch latch = new CountDownLatch(2);
        final AtomicInteger status = new AtomicInteger();
        final AtomicReference<Map> map = new AtomicReference<Map>();

        Client client = ClientFactory.getDefault().newClient();

        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(targetUrl + "/suspend")
                .transport(transport());

        Socket socket = client.create();
        socket.on(new Function<Integer>() {
            @Override
            public void on(Integer statusCode) {
                status.set(statusCode);
                latch.countDown();
            }
        }).on(new Function<Map>() {

            @Override
            public void on(Map t) {
                map.set(t);
                latch.countDown();
            }

        }).open(request.build()).fire("PING");

        latch.await(10, TimeUnit.SECONDS);
        socket.close();

        assertEquals(status.get(), statusCode());
        assertNotNull(map.get());
        assertEquals(map.get().getClass().getInterfaces()[0], Map.class);

    }

    @Test
    public void status404FunctionTest() throws Exception {
        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .resource("/suspend", new AtmosphereHandler() {

                    private final AtomicBoolean b = new AtomicBoolean(false);

                    @Override
                    public void onRequest(AtmosphereResource r) throws IOException {
                        if (!b.getAndSet(true)) {
                            r.suspend(-1);
                        } else {
                            r.getBroadcaster().broadcast(RESUME);
                        }
                    }

                    @Override
                    public void onStateChange(AtmosphereResourceEvent r) throws IOException {
                        if (!r.isResuming() || !r.isCancelled()) {
                            r.getResource().getResponse().getWriter().print(r.getMessage());
                            r.getResource().resume();
                        }
                    }

                    @Override
                    public void destroy() {

                    }
                }).build();

        server = new Nettosphere.Builder().config(config).build();
        assertNotNull(server);
        server.start();

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicInteger status = new AtomicInteger();

        Client client = ClientFactory.getDefault().newClient();

        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(targetUrl + "/ratata")
                .transport(transport());

        final Socket socket = client.create();
        socket.on(new Function<Integer>() {
            @Override
            public void on(Integer statusCode) {
                status.set(statusCode);
                socket.close();
                latch.countDown();
            }
        }).open(request.build());

        latch.await(10, TimeUnit.SECONDS);
        socket.close();

        assertEquals(status.get(), notFoundCode());

    }

    @Test
    public void closedFireTest() throws Exception {
        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .resource("/suspend", new AtmosphereHandler() {

                    private final AtomicBoolean b = new AtomicBoolean(false);

                    @Override
                    public void onRequest(AtmosphereResource r) throws IOException {
                    }

                    @Override
                    public void onStateChange(AtmosphereResourceEvent r) throws IOException {
                    }

                    @Override
                    public void destroy() {

                    }
                }).build();

        server = new Nettosphere.Builder().config(config).build();
        assertNotNull(server);
        server.start();

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicInteger status = new AtomicInteger();

        Client client = ClientFactory.getDefault().newClient();

        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(targetUrl + "/ratata")
                .transport(transport());

        try {
            final Socket socket = client.create();
            socket.on(new Function<Integer>() {
                @Override
                public void on(Integer statusCode) {
                    status.set(statusCode);
                    socket.close();
                    latch.countDown();
                }
            }).open(request.build());

            latch.await(10, TimeUnit.SECONDS);

            socket.fire("Yo");

            fail();
        } catch (IOException ex) {
            assertEquals(ex.getClass(), IOException.class);
        }
    }

    @Test
    public void basicConnectExceptionTest() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<ConnectException> response = new AtomicReference<ConnectException>();
        Client client = ClientFactory.getDefault().newClient();

        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(targetUrl + "/suspend")
                .transport(transport());

        Socket socket = client.create();
        ;
        socket.on(new Function<ConnectException>() {

            @Override
            public void on(ConnectException t) {
                response.set(t);
                latch.countDown();
            }

        }).open(request.build());

        latch.await(10, TimeUnit.SECONDS);
        socket.close();
        assertEquals(response.get().getClass(), ConnectException.class);
    }

    @Test
    public void basicIOExceptionTest() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<IOException> response = new AtomicReference<IOException>();
        Client client = ClientFactory.getDefault().newClient();

        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(targetUrl + "/suspend")
                .transport(transport());

        Socket socket = client.create();

        socket.on(new Function<IOException>() {

            @Override
            public void on(IOException t) {
                response.set(t);
                latch.countDown();
            }

        }).open(request.build());

        latch.await(10, TimeUnit.SECONDS);
        socket.close();
        assertTrue(IOException.class.isAssignableFrom(response.get().getClass()));
    }

    @Test
    public void basicEchoEncoderTest() throws Exception {
        final CountDownLatch l = new CountDownLatch(1);

        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .resource("/suspend", new AtmosphereHandler() {

                    private final AtomicBoolean b = new AtomicBoolean(false);

                    @Override
                    public void onRequest(AtmosphereResource r) throws IOException {
                        if (!b.getAndSet(true)) {
                            r.suspend(-1);
                        } else {
                            r.getBroadcaster().broadcast(r.getRequest().getReader().readLine());
                        }
                    }

                    @Override
                    public void onStateChange(AtmosphereResourceEvent r) throws IOException {
                        if (!r.isResuming() || !r.isCancelled()) {
                            r.getResource().getResponse().getWriter().print(r.getMessage());
                            r.getResource().resume();
                        }
                    }

                    @Override
                    public void destroy() {

                    }
                }).build();

        server = new Nettosphere.Builder().config(config).build();
        assertNotNull(server);
        server.start();

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> response = new AtomicReference<String>();
        Client client = ClientFactory.getDefault().newClient();

        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(targetUrl + "/suspend")
                .encoder(new Encoder<String, String>() {
                    @Override
                    public String encode(String s) {
                        return "<-" + s.toString() + "->";
                    }
                })
                .transport(transport());

        Socket socket = client.create();
        ;
        socket.on("message", new Function<String>() {
            @Override
            public void on(String t) {
                response.set(t);
                latch.countDown();
            }
        }).on(new Function<Throwable>() {

            @Override
            public void on(Throwable t) {
                t.printStackTrace();
                latch.countDown();
            }

        }).open(request.build()).fire("echo");

        latch.await(10, TimeUnit.SECONDS);
        socket.close();

        assertEquals(response.get(), "<-echo->");
    }

    @Test
    public void requestTimeoutTest() throws Exception {
        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .resource("/suspend", new AtmosphereHandler() {

                    private final AtomicBoolean b = new AtomicBoolean(false);

                    @Override
                    public void onRequest(AtmosphereResource r) throws IOException {
                        if (!b.getAndSet(true)) {
                            r.suspend(-1);
                        }
                    }

                    @Override
                    public void onStateChange(AtmosphereResourceEvent r) throws IOException {
                    }

                    @Override
                    public void destroy() {

                    }
                }).build();

        server = new Nettosphere.Builder().config(config).build();
        assertNotNull(server);
        server.start();

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Class<? extends TimeoutException>> response = new AtomicReference<Class<? extends TimeoutException>>();
        Client client = ClientFactory.getDefault().newClient();

        Options o = client.newOptionsBuilder().reconnect(false).requestTimeoutInSeconds(5).build();
        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(targetUrl + "/suspend")
                .encoder(new Encoder<String, String>() {
                    @Override
                    public String encode(String s) {
                        return "<-" + s.toString() + "->";
                    }
                })
                .transport(transport());

        Socket socket = client.create(o);

        socket.on(new Function<TimeoutException>() {

            @Override
            public void on(TimeoutException t) {
                response.set(t.getClass());
                latch.countDown();
            }

        }).open(request.build());

        latch.await(10, TimeUnit.SECONDS);
        socket.close();

        assertEquals(response.get(), TimeoutException.class);
    }

    @Test
    public void basicDecoderTest() throws Exception {
        final CountDownLatch l = new CountDownLatch(1);

        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .resource("/suspend", new AtmosphereHandler() {

                    private final AtomicBoolean b = new AtomicBoolean(false);

                    @Override
                    public void onRequest(AtmosphereResource r) throws IOException {
                        if (!b.getAndSet(true)) {
                            r.suspend(-1);
                        } else {
                            r.getBroadcaster().broadcast(r.getRequest().getReader().readLine());
                        }
                    }

                    @Override
                    public void onStateChange(AtmosphereResourceEvent r) throws IOException {
                        if (!r.isResuming() || !r.isCancelled()) {
                            r.getResource().getResponse().getWriter().print(r.getMessage());
                            r.getResource().resume();
                        }
                    }

                    @Override
                    public void destroy() {

                    }
                }).build();

        server = new Nettosphere.Builder().config(config).build();
        assertNotNull(server);
        server.start();

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<POJO> response = new AtomicReference<POJO>();
        Client client = ClientFactory.getDefault().newClient();

        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(targetUrl + "/suspend")
                .decoder(new Decoder<String, POJO>() {
                    @Override
                    public POJO decode(Event e, String s) {
                        return new POJO(s);
                    }
                })
                .transport(transport());

        Socket socket = client.create();
        socket.on(Event.MESSAGE.name(), new Function<POJO>() {
            @Override
            public void on(POJO t) {
                response.set(t);
                latch.countDown();
            }
        }).open(request.build()).fire("echo");

        latch.await(10, TimeUnit.SECONDS);

        assertNotNull(response.get());
        assertEquals(response.get().getClass(), POJO.class);
        socket.close();

    }

    @Test
    public void basicTransportTest() throws Exception {
        final CountDownLatch l = new CountDownLatch(1);

        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .resource("/suspend", new AtmosphereHandler() {

                    private final AtomicBoolean b = new AtomicBoolean(false);

                    @Override
                    public void onRequest(AtmosphereResource r) throws IOException {
                        if (!b.getAndSet(true)) {
                            r.suspend(-1);
                        } else {
                            r.getBroadcaster().broadcast(r.getRequest().getReader().readLine());
                        }
                    }

                    @Override
                    public void onStateChange(AtmosphereResourceEvent r) throws IOException {
                        if (!r.isResuming() || !r.isCancelled()) {
                            r.getResource().getResponse().getWriter().print(r.getMessage());
                            r.getResource().resume();
                        }
                    }

                    @Override
                    public void destroy() {

                    }
                }).build();

        server = new Nettosphere.Builder().config(config).build();
        assertNotNull(server);
        server.start();

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Request.TRANSPORT> response = new AtomicReference<Request.TRANSPORT>();
        Client client = ClientFactory.getDefault().newClient();

        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(targetUrl + "/suspend")
                .transport(transport());

        Socket socket = client.create();
        socket.on(Event.TRANSPORT.name(), new Function<Request.TRANSPORT>() {
            @Override
            public void on(Request.TRANSPORT t) {
                response.set(t);
                latch.countDown();
            }
        }).open(request.build()).fire("yo");

        latch.await(10, TimeUnit.SECONDS);

        assertNotNull(response.get());
        assertEquals(response.get(), transport());

    }
    @Test
    public void chainingDecoder() throws Exception {
        final CountDownLatch l = new CountDownLatch(1);

        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .resource("/suspend", new AtmosphereHandler() {

                    private final AtomicBoolean b = new AtomicBoolean(false);

                    @Override
                    public void onRequest(AtmosphereResource r) throws IOException {
                        if (!b.getAndSet(true)) {
                            r.suspend(-1);
                        } else {
                            r.getBroadcaster().broadcast(r.getRequest().getReader().readLine());
                        }
                    }

                    @Override
                    public void onStateChange(AtmosphereResourceEvent r) throws IOException {
                        if (!r.isResuming() || !r.isCancelled()) {
                            r.getResource().getResponse().getWriter().print(r.getMessage());
                            r.getResource().resume();
                        }
                    }

                    @Override
                    public void destroy() {

                    }
                }).build();

        server = new Nettosphere.Builder().config(config).build();
        assertNotNull(server);
        server.start();

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<POJO> response = new AtomicReference<POJO>();
        Client client = ClientFactory.getDefault().newClient();

        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(targetUrl + "/suspend")
                .decoder(new Decoder<String, POJO>() {
                    @Override
                    public POJO decode(Event e, String s) {
                        return new POJO(s);
                    }
                })
                .decoder(new Decoder<POJO, POJO2>() {
                    @Override
                    public POJO2 decode(Event e, POJO s) {
                        return new POJO2(s);
                    }
                })
                .transport(transport());

        Socket socket = client.create();
        socket.on("message", new Function<POJO2>() {
            @Override
            public void on(POJO2 t) {
                response.set(t.message);
                latch.countDown();
            }
        }).open(request.build()).fire("echo");

        latch.await(10, TimeUnit.SECONDS);

        assertNotNull(response.get());
        assertEquals(response.get().getClass(), POJO.class);
        socket.close();
    }


    @Test
    public void encodersChainingTests() throws Exception {
        final CountDownLatch l = new CountDownLatch(1);

        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .resource("/suspend", new AtmosphereHandler() {

                    private final AtomicBoolean b = new AtomicBoolean(false);

                    @Override
                    public void onRequest(AtmosphereResource r) throws IOException {
                        if (!b.getAndSet(true)) {
                            r.suspend(-1);
                        } else {
                            r.getBroadcaster().broadcast(r.getRequest().getReader().readLine());
                        }
                    }

                    @Override
                    public void onStateChange(AtmosphereResourceEvent r) throws IOException {
                        if (!r.isResuming() || !r.isCancelled()) {
                            r.getResource().getResponse().getWriter().print(r.getMessage());
                            r.getResource().resume();
                        }
                    }

                    @Override
                    public void destroy() {

                    }
                }).build();

        server = new Nettosphere.Builder().config(config).build();
        assertNotNull(server);
        server.start();

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> response = new AtomicReference<String>();
        Client client = ClientFactory.getDefault().newClient();

        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(targetUrl + "/suspend")
                .encoder(new Encoder<String, POJO>() {
                    @Override
                    public POJO encode(String s) {
                        return new POJO("<-" + s + "->");
                    }
                })
                .encoder(new Encoder<POJO, Reader>() {
                    @Override
                    public Reader encode(POJO s) {
                        return new StringReader(s.message);
                    }
                })
                .transport(transport());

        Socket socket = client.create();
        socket.on(Event.MESSAGE.name(), new Function<String>() {
            @Override
            public void on(String t) {
                response.set(t);
                latch.countDown();
            }
        }).on(new Function<Throwable>() {

            @Override
            public void on(Throwable t) {
                t.printStackTrace();
                latch.countDown();
            }

        }).open(request.build()).fire("echo");

        latch.await(10, TimeUnit.SECONDS);
        socket.close();

        assertEquals(response.get(), "<-echo->");
    }

    public final static class POJO {

        public final String message;

        public POJO(String message) {
            this.message = message;
        }
    }

    public final static class POJO2 {

        public final POJO message;

        public POJO2(POJO message) {
            this.message = message;
        }
    }

    @Test
    public void multipleFireBlockingTest() throws Exception {
        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .resource("/suspend", new AtmosphereHandler() {

                    private final AtomicBoolean b = new AtomicBoolean(false);
                    private final AtomicInteger count = new AtomicInteger(2);
                    private final AtomicReference<StringBuffer> response = new AtomicReference<StringBuffer>(new StringBuffer());

                    @Override
                    public void onRequest(AtmosphereResource r) throws IOException {
                        if (!b.getAndSet(true)) {
                            r.suspend(-1);
                        } else {
                            r.getBroadcaster().broadcast(r.getRequest().getReader().readLine());
                        }
                    }

                    @Override
                    public void onStateChange(AtmosphereResourceEvent r) throws IOException {
                        response.get().append(r.getMessage());
                        if (count.decrementAndGet() == 0 && (!r.isResuming() || !r.isCancelled())) {
                            r.getResource().getResponse().write(response.toString());
                            r.getResource().resume();
                        }
                    }

                    @Override
                    public void destroy() {

                    }
                }).build();

        server = new Nettosphere.Builder().config(config).build();
        assertNotNull(server);
        server.start();

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<StringBuffer> response = new AtomicReference<StringBuffer>(new StringBuffer());
        Client client = ClientFactory.getDefault().newClient();

        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(targetUrl + "/suspend")
                .transport(transport());

        Socket socket = client.create();

        socket.on("message", new Function<String>() {
            @Override
            public void on(String t) {
                logger.info("Function invoked {}", t);
                response.get().append(t);
                latch.countDown();
            }
        }).on(new Function<Throwable>() {

            @Override
            public void on(Throwable t) {
                t.printStackTrace();
                latch.countDown();
            }

        }).open(request.build())
                .fire("PING").get()
                .fire("PONG").get();

        latch.await(10, TimeUnit.SECONDS);
        socket.close();

        // We can't predict the order of requests send
        try {
            assertEquals(response.get().toString(), "PONGPING");
        } catch (AssertionError e) {
            assertEquals(response.get().toString(), "PINGPONG");
        }
    }

    @Test
    public void multipleFireTest() throws Exception {
        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .resource("/suspend", new AtmosphereHandler() {

                    private final AtomicBoolean b = new AtomicBoolean(false);
                    private final AtomicInteger count = new AtomicInteger(2);
                    private final AtomicReference<StringBuffer> response = new AtomicReference<StringBuffer>(new StringBuffer());

                    @Override
                    public void onRequest(AtmosphereResource r) throws IOException {
                        if (!b.getAndSet(true)) {
                            r.suspend(-1);
                        } else {
                            r.getBroadcaster().broadcast(r.getRequest().getReader().readLine());
                        }
                    }

                    @Override
                    public void onStateChange(AtmosphereResourceEvent r) throws IOException {
                        response.get().append(r.getMessage());
                        if (count.decrementAndGet() == 0 && (!r.isResuming() || !r.isCancelled())) {
                            r.getResource().getResponse().write(response.toString());
                            r.getResource().resume();
                        }
                    }

                    @Override
                    public void destroy() {

                    }
                }).build();

        server = new Nettosphere.Builder().config(config).build();
        assertNotNull(server);
        server.start();

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<StringBuffer> response = new AtomicReference<StringBuffer>(new StringBuffer());
        Client client = ClientFactory.getDefault().newClient();

        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(targetUrl + "/suspend")
                .transport(transport());

        Socket socket = client.create();

        socket.on("message", new Function<String>() {
            @Override
            public void on(String t) {
                logger.info("Function invoked {}", t);
                response.get().append(t);
                latch.countDown();
            }
        }).on(new Function<Throwable>() {

            @Override
            public void on(Throwable t) {
                t.printStackTrace();
                latch.countDown();
            }

        }).open(request.build())
                .fire("PING")
                .fire("PONG");

        latch.await(10, TimeUnit.SECONDS);
        socket.close();

        // We can't predict the order of requests send
        try {
            assertEquals(response.get().toString(), "PONGPING");
        } catch (AssertionError e) {
            assertEquals(response.get().toString(), "PINGPONG");
        }
    }

    @Test(enabled = true)
    public void basicLoadTest() throws IOException, InterruptedException {
        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .resource("/", new AtmosphereHandler() {

                    private final AtomicBoolean b = new AtomicBoolean(false);

                    @Override
                    public void onRequest(final AtmosphereResource r) throws IOException {
                        if (r.getRequest().getMethod().equalsIgnoreCase("GET")) {
                            r.addEventListener(new AtmosphereResourceEventListenerAdapter() {
                                @Override
                                public void onSuspend(AtmosphereResourceEvent event) {
                                    r.removeEventListener(this);
                                    try {
                                        r.getResponse().write("yo!".getBytes()).flushBuffer();
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }).suspend();
                        }
                    }

                    @Override
                    public void onStateChange(AtmosphereResourceEvent r) throws IOException {
                    }

                    @Override
                    public void destroy() {

                    }
                }).build();

        server = new Nettosphere.Builder().config(config).build();
        assertNotNull(server);
        server.start();

        final AsyncHttpClient c = new AsyncHttpClient();
        final CountDownLatch l = new CountDownLatch(getCount());
        Client client = ClientFactory.getDefault().newClient();
        RequestBuilder request = client.newRequestBuilder();
        request.method(Request.METHOD.GET).uri(targetUrl);
        request.transport(transport());
        request.encoder(new Encoder<String, String>() {
            @Override
            public String encode(String s) {
                return s;
            }
        });
        request.decoder(new Decoder<String, String>() {
            @Override
            public String decode(Event evntp, String s) {
                return s;
            }
        });
        for (int i = 0; i < getCount(); i++) {
            Socket socket = client.create(client.newOptionsBuilder().runtime(c).build());
            socket.on(new Function<Integer>() {
                @Override
                public void on(Integer statusCode) {
                }
            });
            socket.on(new Function<String>() {
                @Override
                public void on(String s) {
                    if (s.equalsIgnoreCase("yo!")) {
                        l.countDown();
                        System.out.println(l.getCount());
                    }
                }
            });
            socket.on(new Function<Throwable>() {
                @Override
                public void on(Throwable t) {
                }
            });

            socket.open(request.build());
            socket.fire("OPEN");
        }
        l.await(60, TimeUnit.SECONDS);
    }

    @Test (enabled = true)
    public void serializeTest() throws Exception {
        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .resource("/suspend", new AtmosphereHandler() {

                    private final AtomicBoolean b = new AtomicBoolean(false);
                    private final AtomicInteger count = new AtomicInteger(2);
                    private final AtomicReference<StringBuffer> response = new AtomicReference<StringBuffer>(new StringBuffer());

                    @Override
                    public void onRequest(AtmosphereResource r) throws IOException {
                        if (!b.getAndSet(true)) {
                            r.suspend(-1);
                        } else {
                            try {
                                r.getBroadcaster().broadcast(r.getRequest().getReader().readLine()).get();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            } catch (ExecutionException e) {
                                e.printStackTrace();
                            }
                        }
                    }

                    @Override
                    public void onStateChange(AtmosphereResourceEvent r) throws IOException {
                        response.get().append(r.getMessage());
                        if (count.decrementAndGet() == 0 && (!r.isResuming() || !r.isCancelled())) {
                            r.getResource().getResponse().write(response.toString());
                            r.getResource().resume();
                        }
                    }

                    @Override
                    public void destroy() {

                    }
                }).build();

        server = new Nettosphere.Builder().config(config).build();
        assertNotNull(server);
        server.start();

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<StringBuffer> response = new AtomicReference<StringBuffer>(new StringBuffer());
        SerializedClient client = ClientFactory.getDefault().newClient(SerializedClient.class);

        SerializedOptionsBuilder b = client.newOptionsBuilder();
        b.serializedFireStage(new DefaultSerializedFireStage());

        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(targetUrl + "/suspend")
                .transport(transport());

        Socket socket = client.create(b.build());

        socket.on("message", new Function<String>() {
            @Override
            public void on(String t) {
                logger.info("Function invoked {}", t);
                response.get().append(t);
                latch.countDown();
            }
        }).on(new Function<Throwable>() {

            @Override
            public void on(Throwable t) {
                t.printStackTrace();
                latch.countDown();
            }

        }).open(request.build())
                .fire("PING")
                .fire("PONG");

        latch.await(10, TimeUnit.SECONDS);
        socket.close();

        assertEquals(response.get().toString(), "PINGPONG");
    }
}
