## wAsync: A WebSockets/HTTP Client Library for Asynchronous Communication

wAsync is a Java based library allowing asynchronous communication with any WebServer supporting the WebSocket or Http Protocol.
wAsync can be used with Node.js, Android, Atmosphere or any WebSocket Framework.

[Getting Started with Android, Node.js and Atmosphere](http://jfarcand.wordpress.com/2013/04/04/wasync-websockets-with-fallbacks-transports-for-android-node-js-and-atmosphere/)

You can browse the [javadoc](http://atmosphere.github.com/wasync/apidocs/) or browse our [samples](https://github.com/Atmosphere/wasync/tree/master/samples).

You can [download the jar](http://search.maven.org/#search%7Cga%7C1%7Ca%3A%22wasync%22) or use Maven
```xml
          <dependency>
              <groupId>org.atmosphere</groupId>
              <artifactId>wasync</artifactId>
              <version>1.0.0.RC2</version>
          </dependency>

```
As simple as

```java
        Client client = ClientFactory.getDefault().newClient();

        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri("http://async-io.org")
                .encoder(new Encoder<String, Reader>() {        // Stream the request body
                    @Override
                    public Reader encode(String s) {
                        return new StringReader(s);
                    }
                })
                .decoder(new Decoder<String, Reader>() {
                    @Override
                    public Reader decode(Event type, String s) {
                        return new StringReader(s);
                    }
                })
                .transport(Request.TRANSPORT.WEBSOCKET)                        // Try WebSocket
                .transport(Request.TRANSPORT.LONG_POLLING);                    // Fallback to Long-Polling

        Socket socket = client.create();
        socket.on(new Function<Reader>() {
            @Override
            public void on(Reader r) {
                // Read the response
            }
        }).on(new Function<IOException>() {

            @Override
            public void on(Throwable t) {
                // Some IOException occurred
            }

        }).open(request.build())
            .fire("echo")
            .fire("bong");
```

You can also use the specialized clients. For example, to transparently enable Atmosphere's Protocol

```java
       AtmosphereClient client = ClientFactory.getDefault().newClient(AtmosphereClient.class);

       RequestBuilder request = client.newRequestBuilder()
    		   .method(Request.METHOD.GET)
    		   .uri(targetUrl + "/suspend")
               .trackMessageLength(true)
    		   .transport(Request.TRANSPORT.LONG_POLLING);
```

or if you want to serialize the fire() method call so events are asynchronously sent in the order the fire method is called

```java
        SerializedClient client = ClientFactory.getDefault().newClient(SerializedClient.class);

        SerializedOptionsBuilder b = client.newOptionsBuilder();
        b.serializedFireStage(new DefaultSerializedFireStage());

        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(targetUrl + "/suspend")
                .transport(transport());

        Socket socket = client.create(b.build());
```

By default, the [FunctionResolver](http://atmosphere.github.com/wasync/apidocs/org/atmosphere/wasync/FunctionResolver.html) will associate the Decoder's type will be used to invoke the appropriate Function, if defined. For
example,

```java
   Decoder<String, POJO> d = new Decoder<String, POJO>() {
             @Override
             public POJO decode(Event type, String s) {
                 if (type.equals(Event.MESSAGE)) {
                    return new POJO(s);
                 } else {
                    return s;
                 }
             }
         }
```
will be associated to
```java
   Function<String> f = new Function<POJO>() {
             @Override
             public void on(POJO t) {

             }
        }
```
You can also implement your own FunctionResolver to associate the Function with Decoder
```java
         Socket socket = client.create();
         socket.on("myEvent", new Function<Reader>() { ...}
```
where myEvent could be read from the response's body.

