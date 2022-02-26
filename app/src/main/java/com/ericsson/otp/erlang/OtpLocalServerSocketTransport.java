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

import java.io.File;
import java.io.IOException;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.LocalSocketAddress.Namespace;

/**
 * Server-side transport based on stream-oriented Unix Domain Sockets (of the
 * SOCK_STREAM type from the AF_UNIX socket family, also known as AF_LOCAL)
 * using the Android-specific LocalServerSocket implementation.
 */
public class OtpLocalServerSocketTransport implements OtpServerTransport {

    // Underlying Unix Domain Socket listening for incoming connection requests
    private final LocalServerSocket listeningSocket;

    // Underlying socket address
    private final LocalSocketAddress socketAddress;

    /**
     * Create a new server Unix Domain Socket listening on the specified name.
     *
     * @param name
     *            The name of the server Unix Domain Socket to create, it can
     *            be either a name in the Linux-specific abstract namespace or
     *            a file pathname in the local filesystem. A socket pathname
     *            is limited in length to 107 bytes on Android, is encoded
     *            according to the current file system encoding mode and can
     *            be either relative or absolute.
     *
     * @param namespace
     *            The namespace, either ABSTRACT or FILESYSTEM, of the Unix
     *            Domain Socket to create.
     *
     * When interacting with actual Erlang nodes, keep in mind that Erlang
     * node names have some restrictions. As of this writing, they are
     * limited to the following character set: 0-9 A-Z a-z _ and -
     * (cf. net_kernel:valid_name_head/1) so they cannot contain . / or \.
     * As a consequence, a socket file pathname is relative to the current
     * working directory on the Erlang side. This limitation does not apply
     * to the node names defined within JInterface.
     *
     * @see LocalServerSocket#LocalServerSocket(FileDescriptor) and
     * @see LocalSocket#bind(LocalSocketAddress)
     *
     * @throws IOException
     */
    public OtpLocalServerSocketTransport(String    name,
                                         Namespace namespace)
        throws IOException {

        LocalSocketAddress socketAddress =
            new LocalSocketAddress(name, namespace);

        // With the Android API it is not possible to create directly a
        // LocalServerSocket listening on a file in the local filesystem.
        // However it can be created indirectly through a file descriptor
        // used as a handle to a LocalSocket already created and bound.
        if (namespace == Namespace.FILESYSTEM) {

            // First create a LocalSocket and bind it to name
            LocalSocket socket = new LocalSocket();
            socket.bind(socketAddress);

            // Create the server Unix Domain Socket through a file descriptor
            // representing the above socket.
            listeningSocket = new LocalServerSocket(socket.getFileDescriptor());

        } else { // namespace == Namespace.ABSTRACT
            listeningSocket = new LocalServerSocket(name);
        }

        // Save the underlying socket address
        this.socketAddress = socketAddress;
    }

    /**
     * Return the socket address on which this server socket is listening.
     * It is a file pathname in the local filesystem.
     *
     * @see LocalServerSocket#getLocalSocketAddress()
     */
    public LocalSocketAddress getLocalSocketAddress() {
        return socketAddress;
    }

    /**
     * Accept a new connection to the socket.
     *
     * @see LocalServerSocket#accept()
     */
    @Override public OtpTransport accept() throws IOException {
        final LocalSocket socket = listeningSocket.accept();
        return new OtpLocalSocketTransport(socket);
    }

    /**
     * Close the server socket.
     *
     * @see LocalServerSocket#close()
     */
    @Override public void close() throws IOException {
        listeningSocket.close();
        // Delete the socket file from the filesystem once the socket is closed
        if (socketAddress.getNamespace() == Namespace.FILESYSTEM) {
            File file = new File(socketAddress.getName());
            if (file.exists()) {
                file.delete();
            }
        }
    }

    /**
     * The notion of socket port is not used with this alternative distribution
     * protocol. Return 0 arbitrarily to override this abstract method as it
     * will not be used anyway.
     */
    @Override public int getLocalPort() {
        return 0;
    }

}
