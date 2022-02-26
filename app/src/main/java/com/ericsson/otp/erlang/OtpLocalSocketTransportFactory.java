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
import android.net.LocalSocketAddress;
import android.net.LocalSocketAddress.Namespace;

/**
 * Transport factory based on stream-oriented Unix Domain Sockets (of the
 * SOCK_STREAM type from the AF_UNIX socket family, also known as AF_LOCAL)
 * using the Android-specific LocalSocket implementation.
 */
public class OtpLocalSocketTransportFactory extends OtpGenericTransportFactory {

    // The namespace of the sockets created by the factory, it can be:
    // - FILESYSTEM: the sockets are created on the local filesystem using
    //               normal filesystem paths
    // - ABSTRACT:   the sockets are not created on the filesystem but
    //               in a Linux-specific abstract namespace
    final Namespace namespace;

    // Directory in the filesystem where the Unix Domain Socket files will be
    // created. This directory path can be either relative (such as "../") or
    // absolute (such as "/data/user/0/my.package.name/files/"), keeping in
    // mind that the Android operating system restricts per user in which
    // directories a given application has write access by default.
    //
    // For example, this can be a subdirectory within the app-specific per-user
    // private storage on the internal storage returned by getFilesDir().
    final String socketDir;

    /**
     * Constructor for a factory creating sockets on the local filesystem
     * and defining the base directory where the factory will create the
     * socket files and/or will connect to them.
     *
     * @param socketDir
     *            The directory where the socket files will be created, it
     *            must end with a file separator, usually the '/' character.
     *
     * @throws IOException
     *            When the specified directory is not valid.
     */
    public OtpLocalSocketTransportFactory(final String socketDir)
            throws IOException {
        namespace = Namespace.FILESYSTEM;
        if (socketDir == null ||
            socketDir.charAt(socketDir.length() - 1) != File.separatorChar) {
            throw new IOException("Invalid directory where to create " +
                                  "socket files, it must end with " +
                                  File.separatorChar + ".");
        }
        this.socketDir = socketDir;
    }

    /**
     * Constructor for a factory creating sockets in the abstract namespace.
     *
     */
    public OtpLocalSocketTransportFactory() {
        namespace = Namespace.ABSTRACT;
        // Use the abstract socket names as passed, so no prefix is added
        socketDir = "";
    }

    /**
     * Create an instance of a client-side {@link OtpLocalSocketTransport}
     *
     * @param node
     *            The node name, it defines the server Unix Domain Socket this
     *            new client-side transport connects to, it is a file pathname
     *            in the local filesystem.
     *
     *            The socket file pathname is the part before the '@' character
     *            for a full node name in Name@Host format (whether short or
     *            long names are used) or the entire node name otherwise.
     *
     * @return new transport object using an Android-specific LocalSocket.
     *
     * @throws IOException
     */
    public OtpTransport createTransport(final OtpPeer peer)
            throws IOException {
        // Use the part of the node name preceding the '@' character (if any)
        // as the name of the Unix Domain Socket to connect to, called the
        // alivename in Jinterface.
        String socketName = peer.alive();
        return new OtpLocalSocketTransport(socketDir + socketName, namespace);
    }

    /**
     * Create an instance of a server-side {@link OtpLocalServerSocketTransport}
     *
     * @param node
     *            The node name, it defines the listening Unix Domain Socket
     *            this new server-side transport listens at, it is a file
     *            pathname in the local filesystem.
     *
     *            The socket file pathname is the part before the '@' character
     *            for a full node name in Name@Host format (whether short or
     *            long names are used) or the entire node name otherwise.
     *
     * @return new transport object using an Android-specific LocalServerSocket
     *
     * @throws IOException
     */
    public OtpServerTransport createServerTransport(final OtpLocalNode node)
            throws IOException{
        // Use the part of the node name preceding the '@' character (if any)
        // as the name of the listening Unix Domain Socket to create, called the
        // alivename in Jinterface.
        String socketName = node.alive();;
        return new OtpLocalServerSocketTransport(socketDir + socketName,
                                                 namespace);
    }

}
