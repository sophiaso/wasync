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

import org.atmosphere.wasync.Future;
import org.atmosphere.wasync.Socket;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class DefaultFuture implements Future {

    private final Socket socket;
    private CountDownLatch latch = new CountDownLatch(1);
    private final AtomicBoolean done = new AtomicBoolean(false);

    public DefaultFuture(Socket socket) {
        this.socket = socket;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        latch.countDown();
        return true;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isCancelled() {
        return latch.getCount() == 0;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isDone() {
        return done.get();
    }

    // TODO: Not public
    /**
     * {@inheritDoc}
     */
    @Override
    public void done(){
        done.set(true);
        latch.countDown();
    }

    protected void reset(){
        done.set(false);
        latch = new CountDownLatch(1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Socket get() throws InterruptedException, ExecutionException {
        latch.await();
        return socket;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public Socket get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        latch.await(timeout, unit);
        return socket;
    }

    protected Socket socket() {
        return socket;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future fire(Object data) throws IOException {
        reset();
        ((DefaultSocket)socket).internalSocket().write(((DefaultSocket)socket).request(), data);
        return this;
    }
}
