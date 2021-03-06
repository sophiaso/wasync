/*
 * Copyright 2013 Jeanfrancois Arcand
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.atmosphere.wasync.impl;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.ListenableFuture;
import com.ning.http.client.Response;
import com.ning.http.client.websocket.WebSocket;
import org.atmosphere.wasync.Encoder;
import org.atmosphere.wasync.Future;
import org.atmosphere.wasync.Options;
import org.atmosphere.wasync.Request;
import org.atmosphere.wasync.util.ReaderInputStream;
import org.atmosphere.wasync.util.TypeResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;

public class SocketRuntime {

    private final static Logger logger = LoggerFactory.getLogger(SocketRuntime.class);

    protected final WebSocket webSocket;
    protected final Options options;
    protected final DefaultFuture rootFuture;

    public SocketRuntime(WebSocket webSocket, Options options, DefaultFuture rootFuture) {
        this.webSocket = webSocket;
        this.options = options;
        this.rootFuture = rootFuture;
    }

    public SocketRuntime(Options options, DefaultFuture rootFuture) {
        this(null, options, rootFuture);
    }

    public DefaultFuture future() {
        return rootFuture;
    }

    public void close() {
    }

    protected Object invokeEncoder(List<Encoder<? extends Object, ?>> encoders, Object instanceType) {
        for (Encoder e : encoders) {
            Class<?>[] typeArguments = TypeResolver.resolveArguments(e.getClass(), Encoder.class);

            if (typeArguments.length > 0 && typeArguments[0].isAssignableFrom(instanceType.getClass())) {
                instanceType = e.encode(instanceType);
            }
        }
        return instanceType;
    }

    public Future write(Request request, Object data) throws IOException {
        // Execute encoder
        Object object = invokeEncoder(request.encoders(), data);
        if (webSocket != null) {
            webSocketWrite(request, object, data);
        } else {
            httpWrite(request, object, data);
        }
        rootFuture.done();

        return rootFuture;
    }

    public void webSocketWrite(Request request, Object object, Object data) throws IOException {
        if (InputStream.class.isAssignableFrom(object.getClass())) {
            InputStream is = (InputStream) object;
            ByteArrayOutputStream bs = new ByteArrayOutputStream();
            //TODO: We need to stream directly, in AHC!
            byte[] buffer = new byte[8192];
            int n = 0;
            while (-1 != (n = is.read(buffer))) {
                bs.write(buffer, 0, n);
            }
            webSocket.sendMessage(bs.toByteArray());
        } else if (Reader.class.isAssignableFrom(object.getClass())) {
            Reader is = (Reader) object;
            StringWriter bs = new StringWriter();
            //TODO: We need to stream directly, in AHC!
            char[] chars = new char[8192];
            int n = 0;
            while (-1 != (n = is.read(chars))) {
                bs.write(chars, 0, n);
            }
            webSocket.sendTextMessage(bs.getBuffer().toString());
        } else if (String.class.isAssignableFrom(object.getClass())) {
            webSocket.sendTextMessage(object.toString());
        } else if (byte[].class.isAssignableFrom(object.getClass())) {
            webSocket.sendMessage((byte[]) object);
        } else {
            throw new IllegalStateException("No Encoder for " + data);
        }
    }

    public ListenableFuture<Response> httpWrite(Request request, Object object, Object data) throws IOException {
        // Only for Atmosphere
        if (AtmosphereRequest.class.isAssignableFrom(request.getClass())) {
            request.queryString().put("X-Atmosphere-Transport", Arrays.asList(new String[]{"polling"}));
            request.queryString().remove("X-atmo-protocol");
        }

        AsyncHttpClient.BoundRequestBuilder b = options.runtime().preparePost(request.uri())
                .setHeaders(request.headers())
                .setQueryParameters(DefaultSocket.decodeQueryString(request))
                .setMethod(Request.METHOD.POST.name());

        if (InputStream.class.isAssignableFrom(object.getClass())) {
            //TODO: Allow reading the response.
            return b.setBody((InputStream) object).execute();
        } else if (Reader.class.isAssignableFrom(object.getClass())) {
            return b.setBody(new ReaderInputStream((Reader) object)).execute();
        } else if (String.class.isAssignableFrom(object.getClass())) {
            return b.setBody((String) object) .execute();
        } else if (byte[].class.isAssignableFrom(object.getClass())) {
            return b.setBody((byte[]) object).execute();
        } else {
            throw new IllegalStateException("No Encoder for " + data);
        }
    }
}