
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.dellroad.msrp;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.dellroad.msrp.msg.ByteRange;
import org.dellroad.msrp.msg.Header;
import org.dellroad.msrp.msg.Status;
import org.dellroad.stuff.main.MainClass;
import org.dellroad.stuff.string.ByteArrayEncoder;
import org.dellroad.stuff.string.ParseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jline.console.ConsoleReader;
import jline.console.CursorBuffer;
import jline.console.UserInterruptException;
import jline.console.history.FileHistory;

/**
 * Command line utility.
 *
 * <p>
 * This class depends on the <a href="https://github.com/jline/jline2">JLine</a> and
 * <a href="http://dellroad-stuff.googlecode.com/">dellroad-stuff</a> Java libraries.
 * </p>
 */
public class Main extends MainClass {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final SessionListener sessionListener = new MainSessionListener();
    private final MainReportListener reportListener = new MainReportListener();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final Msrp msrp = new Msrp();

    private int port = MsrpConstants.DEFAULT_PORT;
    private ConsoleReader console;
    private PrintWriter writer;
    private FileHistory history;
    private CursorBuffer cursorBuffer;
    private boolean verbose;

    private volatile MsrpUri currentSession;

    @Override
    public int run(String[] args) throws Exception {

        // Parse command line
        final ArrayDeque<String> params = new ArrayDeque<String>(Arrays.asList(args));
        while (!params.isEmpty() && params.peekFirst().startsWith("-")) {
            final String option = params.removeFirst();
            if (option.equals("-h") || option.equals("--help")) {
                this.usageMessage();
                return 0;
            } else if (option.equals("-v") || option.equals("--verbose"))
                this.verbose = true;
            else if (option.equals("--port"))
                this.port = this.parseIntParam(params, "port");
            else if (option.equals("--max-sessions"))
                this.msrp.setMaxSessions(this.parseIntParam(params, "max-sessions"));
            else if (option.equals("--max-content-length"))
                this.msrp.setMaxContentLength(this.parseIntParam(params, "max-content-length"));
            else if (option.equals("--idle-timeout"))
                this.msrp.setMaxIdleTime(this.parseIntParam(params, "idle-timeout") * 1000L);
            else if (option.equals("--connect-timeout"))
                this.msrp.setConnectTimeout(this.parseIntParam(params, "connect-timeout") * 1000L);
            else if (option.equals("--"))
                break;
            else {
                System.err.println(this.getName() + ": unknown option `" + option + "'");
                this.usageError();
            }
        }
        switch (params.size()) {
        case 0:
            break;
        default:
            this.usageError();
            return 1;
        }

        // Set up console
        this.console = new ConsoleReader(new FileInputStream(FileDescriptor.in), System.out);
        this.console.setBellEnabled(true);
        this.console.setHistoryEnabled(true);
        this.console.setHandleUserInterrupt(true);
        this.writer = new PrintWriter(console.getOutput(), true);
        try {
            this.history = new FileHistory(new File(new File(System.getProperty("user.home")), ".msrp_history"));
        } catch (IOException e) {
            // ignore
        }
        this.console.setHistory(this.history);

        // Start up MSRP stack
        this.msrp.setListenAddress(new InetSocketAddress(this.port));
        this.msrp.start();

        // Main command loop
        this.writer.println("Welcome to msrp4j. Type `help' for help.");
        this.writer.println("Listening on address " + this.msrp.getListenAddress());
        final StringBuilder lineBuffer = new StringBuilder();
        try {
            for (boolean done = false; !done; ) {

                // Read command line
                String line;
                try {
                    line = this.console.readLine(lineBuffer.length() == 0 ? "msrp4j> " : "     -> ");
                } catch (UserInterruptException e) {
                    this.writer.print("^C");
                    line = null;
                }
                if (line == null) {
                    this.writer.println();
                    break;
                }

                // Detect backslash continuations
                boolean continuation = false;
                if (line.length() > 0 && line.charAt(line.length() - 1) == '\\') {
                    line = line.substring(0, line.length() - 1) + "\n";
                    continuation = true;
                }

                // Append line to buffer
                lineBuffer.append(line);

                // Handle backslash continuations
                if (continuation)
                    continue;
                final ParseContext ctx = new ParseContext(lineBuffer.toString());
                lineBuffer.setLength(0);

                // Skip initial whitespace
                ctx.skipWhitespace();

                // Ignore blank input
                if (ctx.getInput().length() == 0)
                    continue;

                // Get command and handle
                final String command = ctx.matchPrefix("[^\\s]+").group();
                ctx.skipWhitespace();
                try {
                    switch (command) {
                    case "connect":
                    case "accept":
                    {
                        final MsrpUri localURI = new MsrpUri(ctx.matchPrefix("[^\\s]+").group());
                        ctx.skipWhitespace();
                        final MsrpUri remoteURI = new MsrpUri(ctx.matchPrefix("[^\\s]+").group());
                        this.open(localURI, remoteURI, command.equals("connect"));
                        break;
                    }
                    case "close":
                        this.close();
                        break;
                    case "list":
                        this.list();
                        break;
                    case "select":
                        this.currentSession = new MsrpUri(ctx.matchPrefix("[^\\s]+").group());
                        this.writer.println("* New current session is " + this.currentSession);
                        break;
                    case "text":
                        this.send(new ByteArrayInputStream(ctx.getInput().getBytes(UTF8)), "text/plain; charset=utf-8");
                        break;
                    case "send":
                    {
                        if (ctx.getInput().length() == 0) {
                            this.send(null, null);
                            break;
                        }
                        final File file = new File(ctx.matchPrefix("[^\\s]+").group());
                        ctx.skipWhitespace();
                        final String contentType = ctx.matchPrefix("[^\\s]+").group();
                        this.send(new FileInputStream(file), contentType);
                        break;
                    }
                    case "success":
                    {
                        final String messageId = ctx.matchPrefix("[^\\s]+").group();
                        ctx.skipWhitespace();
                        final ByteRange byteRange = ctx.getInput().length() != 0 ?
                          ByteRange.fromString(ctx.getInput()) : ByteRange.ALL;
                        this.success(messageId, byteRange);
                        break;
                    }
                    case "failure":
                    {
                        final String messageId = ctx.matchPrefix("[^\\s]+").group();
                        ctx.skipWhitespace();
                        this.failure(messageId, Status.fromString(ctx.getInput()));
                        break;
                    }
                    case "quit":
                        this.writer.println("* Bye");
                        done = true;
                        break;
                    case "help":
                        this.writer.println("Available commands:");
                        this.writer.println("  connect localURI remoteURI");
                        this.writer.println("      Create an active session using the given URIs");
                        this.writer.println("  accept localURI remoteURI");
                        this.writer.println("      Create a passive session using the given URIs");
                        this.writer.println("  close");
                        this.writer.println("      Shutdown selected session");
                        this.writer.println("  list");
                        this.writer.println("      List all known sessions");
                        this.writer.println("  select localURI");
                        this.writer.println("      Select current session");
                        this.writer.println("  text ...");
                        this.writer.println("      Send a text message");
                        this.writer.println("  send");
                        this.writer.println("      Send a message with no content");
                        this.writer.println("  send name content-type");
                        this.writer.println("      Send arbitrary file");
                        this.writer.println("  success messageId [byte-range]");
                        this.writer.println("      Send success report");
                        this.writer.println("  failure messageId nscode code [comment]");
                        this.writer.println("      Send failure report");
                        this.writer.println("  quit");
                        this.writer.println("      Close all sessions and quit");
                        this.writer.println("  help");
                        this.writer.println("      Show commands");
                        break;
                    default:
                        throw new Exception("unknown command `" + command + "'; type `help' for help.");
                    }
                } catch (Exception e) {
                    final String msg = e.getMessage() != null ? e.getMessage() : "" + e;
                    this.writer.println("Error: " + msg);
                    if (this.verbose)
                        e.printStackTrace(this.writer);
                }

                // Flush output
                this.writer.flush();
            }
        } finally {
            if (this.history != null)
                this.history.flush();
            this.writer.flush();
            this.console.flush();
            this.console.shutdown();
        }

        // Stop MSRP
        this.msrp.stop();

        // Done
        return 0;
    }

    private void stashLine() {
        this.cursorBuffer = this.console.getCursorBuffer().copy();
        try {
            this.console.getOutput().write("\u001b[1G\u001b[K");
            this.console.flush();
        } catch (IOException e) {
            // ignore
        }
    }

    private void unstashLine() {
        try {
            this.console.resetPromptLine(this.console.getPrompt(), this.cursorBuffer.toString(), this.cursorBuffer.cursor);
        } catch (IOException e) {
            // ignore
        }
    }

    protected String getName() {
        return "msrp";
    }

    @Override
    protected void usageMessage() {
        System.err.println("Usage:");
        System.err.println("  " + this.getName() + " [options]");
        System.err.println("Options:");
        System.err.println("  --port port           Port for incoming connections (default " + MsrpConstants.DEFAULT_PORT + ")");
        System.err.println("  --verbose             Include exception traces when reporting errors");
        System.err.println("  --max-sessions        Set maximum allowed number of sessions");
        System.err.println("  --max-content-length  Set maximum allowed message content length");
        System.err.println("  --idle-timeout        Set maximum allowed time for idle connections (in seconds)");
        System.err.println("  --connect-timeout     Set connection timeout for outbound connections (in seconds)");
    }

    public static void main(String[] args) throws Exception {
        new Main().doMain(args);
    }

    private int parseIntParam(ArrayDeque<String> params, String name) {
        if (params.isEmpty()) {
            this.usageError();
            return 0;
        }
        final String string = params.removeFirst();
        try {
            return Integer.parseInt(string, 10);
        } catch (Exception e) {
            System.err.println(this.getName() + ": invalid " + name + " `" + string + "'");
            this.usageError();
            return 0;
        }
    }

// Commmands

    private void open(MsrpUri localURI, MsrpUri remoteURI, boolean active) throws Exception {
        final Session session = this.msrp.createSession(localURI, remoteURI, null, this.sessionListener, this.executor, active);
        Main.this.writer.println("* Created session: local=" + session.getLocalUri() + " remote=" + session.getRemoteUri());
        Main.this.writer.println("* New current session is " + session.getLocalUri());
        this.currentSession = localURI;
    }

    private void send(InputStream input, String contentType) throws Exception {
        final Session session = this.findCurrentSession();
        if (input == null) {
            session.send(null, this.reportListener);
            return;
        }
        final String messageId = session.send(input, -1, contentType, null, this.reportListener);
        Main.this.writer.println("* Sent message with message ID " + messageId);
    }

    private Session findCurrentSession() {
        if (this.currentSession == null)
            throw new RuntimeException("no current session; use `select' command to select one");
        final Session session = this.msrp.getSessions().get(this.currentSession);
        if (session == null)
            throw new RuntimeException("no session found corresponding to local URI " + this.currentSession);
        return session;
    }

    private void close() {
        final Session session = this.findCurrentSession();
        this.writer.println("* Closing session " + session.getLocalUri());
        session.close(null);
    }

    private void list() {
        final SortedMap<MsrpUri, Session> sessionMap = this.msrp.getSessions();
        this.writer.println("* " + sessionMap.size() + " active sessions:");
        for (Session session : sessionMap.values()) {
            final MsrpUri localURI = session.getLocalUri();
            this.writer.println((localURI.equals(this.currentSession) ? "* " : "  ") + localURI + " -> " + session.getRemoteUri());
        }
    }

    private void success(String messageId, ByteRange byteRange) {
        final Session session = this.findCurrentSession();
        this.writer.println("* Sending success report to " + session.getRemoteUri());
        session.sendSuccessReport(Collections.singletonList(session.getRemoteUri()), messageId, byteRange, null);
    }

    private void failure(String messageId, Status status) {
        final Session session = this.findCurrentSession();
        this.writer.println("* Sending failure report to " + session.getRemoteUri());
        session.sendFailureReport(Collections.singletonList(session.getRemoteUri()), messageId, status);
    }

// MainSessionListener

    private class MainSessionListener implements SessionListener {

        @Override
        public void sessionClosed(Session session, Exception cause) {
            Main.this.stashLine();
            Main.this.writer.println("* Session " + session.getLocalUri() + " closed: " + cause);
            if (session.getLocalUri().equals(Main.this.currentSession)) {
                try {
                    Main.this.currentSession = Main.this.msrp.getSessions().values().iterator().next().getLocalUri();
                } catch (NoSuchElementException e) {
                    Main.this.currentSession = null;
                }
            }
            Main.this.unstashLine();
        }

        @Override
        public void sessionReceivedMessage(Session session, List<MsrpUri> fromPath, String messageId, byte[] content,
          String contentType, SortedSet<Header> headers, boolean successReport, boolean failureReport) {
            Main.this.stashLine();
            Main.this.writer.println("* Rec'd message from " + session.getRemoteUri() + ":");
            Main.this.writer.println("  Message-ID: " + messageId);
            Main.this.writer.println("  Success-Report: " + (successReport ? "yes" : "no"));
            Main.this.writer.println("  Failure-Report: " + (failureReport ? "yes" : "no"));
            for (Header header : headers)
                Main.this.writer.println("  " + header);
            if (content == null)
                Main.this.writer.println("  [ Message contains no content ]");
            else {
                Main.this.writer.println("  Content-Type: " + contentType);
                final MessageDigest sha1;
                try {
                    sha1 = MessageDigest.getInstance("SHA");
                } catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException(e);
                }
                Main.this.writer.println("  Actual Length: " + content.length + " bytes");
                Main.this.writer.println("  Content SHA1: " + ByteArrayEncoder.encode(sha1.digest(content)));
                if (contentType.startsWith("text/plain")) {
                    String name = "utf-8";
                    final Matcher matcher = Pattern.compile(".*charset=([^; ]+).*").matcher(contentType);
                    if (matcher.matches())
                        name = matcher.group(1);
                    Charset charset = null;
                    try {
                        charset = Charset.forName(name);
                    } catch (IllegalArgumentException e) {
                        // ignore
                    }
                    if (charset != null) {
                        Main.this.writer.println();
                        Main.this.writer.println(new String(content, charset));
                        Main.this.writer.println();
                    }
                }
            }
            Main.this.unstashLine();
        }
    }

// MainReportListener

    private class MainReportListener implements SuccessListener, FailureListener {

        @Override
        public void reportFailure(Session session, String messageId, Status status) {
            Main.this.stashLine();
            Main.this.writer.println("* Rec'd failure from " + session.getRemoteUri() + ":");
            Main.this.writer.println("  Message-ID: " + messageId);
            Main.this.writer.println("  Status: " + status);
            Main.this.unstashLine();
        }

        @Override
        public void reportSuccess(Session session, String messageId, ByteRange byteRange) {
            Main.this.stashLine();
            Main.this.writer.println("* Rec'd success from " + session.getRemoteUri() + ":");
            Main.this.writer.println("  Message-ID: " + messageId);
            Main.this.writer.println("  ByteRange: " + byteRange);
            Main.this.unstashLine();
        }
    }
}

