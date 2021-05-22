/*
 * Copyright 2022 Jérôme de Bretagne
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * %ExternalCopyright%
 */

package com.ericsson.otp.erlang;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;

/**
 * Client-side transport based on stream-oriented Unix Domain Sockets (of the
 * SOCK_STREAM type from the AF_UNIX socket family, also known as AF_LOCAL)
 * using the Android-specific LocalSocket implementation.
 */
public class OtpLocalSocketTransport implements OtpTransport {

    // Underlying Unix Domain Socket used to connect to a server-side socket
    private final LocalSocket localSocket;

    /**
     * Create a new Unix Domain Socket and connect it to the server Unix
     * Domain Socket listening at the specified name.
     *
     * @param name
     *            The name of the server Unix Domain Socket to connect to, a
     *            file pathname in the local filesystem. The socket pathname
     *            is limited in length to 107 bytes on Android, is encoded
     *            according to the current file system encoding mode and can
     *            be either relative or absolute.
     *
     * When interacting with actual Erlang nodes, keep in mind that Erlang
     * node names have some restrictions. As of this writing, they are
     * limited to the following character set: 0-9 A-Z a-z _ and -
     * (cf. net_kernel:valid_name_head/1) so they cannot contain . / or \.
     * As a consequence, the socket file pathname is relative to the current
     * working directory on the Erlang side. This limitation does not apply
     * to the node names defined within JInterface.
     *
     * @see LocalSocket#LocalSocket()
     *
     * @throws IOException
     */
    public OtpLocalSocketTransport(final String name)
            throws IOException {
        // Create a new stream-oriented socket
        localSocket = new LocalSocket();
        LocalSocketAddress endPoint =
            new LocalSocketAddress(name,
                                   LocalSocketAddress.Namespace.FILESYSTEM);
        localSocket.connect(endPoint);
    }

    /**
     * Constructor creating a client-side transport by wrapping the
     * specified LocalSocket.
     *
     * @param socket
     *            the LocalSocket to wrap, usually returned by
     *            LocalServerSocket#accept().
     */
    public OtpLocalSocketTransport(final LocalSocket socket) {
        localSocket = socket;
    }

    /**
     * @see LocalSocket#getInputStream()
     */
    public InputStream getInputStream() throws IOException {
        return localSocket.getInputStream();
    }

    /**
     * @see LocalSocket#getOutputStream()
     */
    @Override public OutputStream getOutputStream() throws IOException {
        return localSocket.getOutputStream();
    }

    /**
     * @see LocalSocket#close()
     */
    @Override public void close() throws IOException {
        // Close the socket's InputStream and OutputStream explicitly, to get
        // the same behavior as when calling close() on a java.net.Socket which
        // closes the 2 streams implicitly. Any thread currently blocked in an
        // I/O read operation upon the socket will return -1 (end of stream).
        localSocket.shutdownInput();
        localSocket.shutdownOutput();
        // Then close the socket.
        localSocket.close();
    }

}
